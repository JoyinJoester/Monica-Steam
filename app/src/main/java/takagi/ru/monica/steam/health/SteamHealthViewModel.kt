package takagi.ru.monica.steam.health

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamSecurityEvent
import takagi.ru.monica.steam.data.SteamSecurityEventRepository
import takagi.ru.monica.steam.data.SteamSecurityEventSeverity
import takagi.ru.monica.steam.data.SteamSecurityEventType

data class SteamHealthUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val reports: Map<Long, SteamAccountHealthReport> = emptyMap(),
    val events: List<SteamSecurityEvent> = emptyList(),
    val clock: SteamClockSnapshot = SteamClockSnapshot(),
    val isChecking: Boolean = false,
    val networkUnavailable: Boolean = false
)

class SteamHealthViewModel(
    private val appContext: Context,
    private val accountRepository: SteamAccountRepository,
    private val eventRepository: SteamSecurityEventRepository,
    private val serverTimeService: SteamServerTimeService = SteamServerTimeService()
) : ViewModel() {
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        SteamHealthUiState(clock = readClockSnapshot())
    )
    val uiState: StateFlow<SteamHealthUiState> = _uiState.asStateFlow()
    private var autoRefreshStarted = false

    init {
        viewModelScope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
                if (accounts.isNotEmpty() && !autoRefreshStarted) {
                    autoRefreshStarted = true
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            eventRepository.observeRecent().collect { events ->
                _uiState.value = _uiState.value.copy(
                    events = events.filter { it.type == SteamSecurityEventType.HEALTH_CHECK }
                )
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            val accounts = _uiState.value.accounts
            if (accounts.isEmpty()) return@launch
            _uiState.value = _uiState.value.copy(isChecking = true, networkUnavailable = false)
            val checkedAt = System.currentTimeMillis()
            val serverTime = withContext(Dispatchers.IO) {
                runCatching(serverTimeService::queryServerTimeSeconds).getOrNull()
            }
            val clock = SteamClockSnapshot.merge(
                previous = _uiState.value.clock,
                checkedAt = checkedAt,
                serverTimeSeconds = serverTime
            )
            if (serverTime != null) persistClockSnapshot(clock)
            val reports = accounts.associate { account ->
                account.id to SteamAccountHealthEvaluator.evaluate(
                    account = account,
                    checkedAt = checkedAt,
                    serverTimeSeconds = serverTime
                )
            }
            _uiState.value = _uiState.value.copy(
                reports = reports,
                clock = clock,
                isChecking = false,
                networkUnavailable = serverTime == null
            )
            withContext(Dispatchers.IO) {
                reports.values.forEach { report ->
                    accountRepository.markHealthChecked(report.accountId, checkedAt)
                    eventRepository.record(
                        accountId = report.accountId,
                        type = SteamSecurityEventType.HEALTH_CHECK,
                        severity = report.status.toEventSeverity(),
                        summary = "health_status=${report.status.name.lowercase()}",
                        detail = "clock_status=${report.clockStatus.name.lowercase()}",
                        occurredAt = checkedAt
                    )
                }
            }
        }
    }

    fun diagnosticText(): String {
        return SteamHealthDiagnosticFormatter.format(
            reports = _uiState.value.reports.values.toList(),
            generatedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.FULL_VERSION_NAME,
            androidApi = android.os.Build.VERSION.SDK_INT
        )
    }

    private fun readClockSnapshot(): SteamClockSnapshot {
        if (!preferences.contains(KEY_LAST_CLOCK_OFFSET)) return SteamClockSnapshot()
        return SteamClockSnapshot(
            lastSuccessfulOffsetSeconds = preferences.getLong(KEY_LAST_CLOCK_OFFSET, 0L),
            lastSuccessfulAt = preferences.getLong(KEY_LAST_CLOCK_AT, 0L).takeIf { it > 0L }
        )
    }

    private fun persistClockSnapshot(clock: SteamClockSnapshot) {
        val offset = clock.lastSuccessfulOffsetSeconds ?: return
        preferences.edit()
            .putLong(KEY_LAST_CLOCK_OFFSET, offset)
            .putLong(KEY_LAST_CLOCK_AT, clock.lastSuccessfulAt ?: 0L)
            .apply()
    }

    private fun SteamHealthStatus.toEventSeverity(): SteamSecurityEventSeverity {
        return when (this) {
            SteamHealthStatus.CRITICAL -> SteamSecurityEventSeverity.CRITICAL
            SteamHealthStatus.ATTENTION, SteamHealthStatus.UNKNOWN -> SteamSecurityEventSeverity.WARNING
            SteamHealthStatus.HEALTHY -> SteamSecurityEventSeverity.INFO
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "steam_health_cache"
        private const val KEY_LAST_CLOCK_OFFSET = "last_clock_offset_seconds"
        private const val KEY_LAST_CLOCK_AT = "last_clock_checked_at"

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    val securityManager = SecurityManager(appContext)
                    return SteamHealthViewModel(
                        appContext = appContext,
                        accountRepository = SteamAccountRepository(
                            database.steamAccountDao(),
                            securityManager
                        ),
                        eventRepository = SteamSecurityEventRepository(
                            database.steamSecurityEventDao(),
                            securityManager
                        )
                    ) as T
                }
            }
        }
    }
}
