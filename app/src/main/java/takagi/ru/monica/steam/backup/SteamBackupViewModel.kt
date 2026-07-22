package takagi.ru.monica.steam.backup

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
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase

data class SteamBackupUiState(
    val accountCount: Int = 0,
    val isWorking: Boolean = false,
    val preview: SteamBackupPreview? = null,
    val previewPayload: SteamBackupPayload? = null,
    val error: String? = null
)

class SteamBackupViewModel(
    private val accountRepository: SteamAccountRepository,
    private val backupRepository: SteamBackupRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamBackupUiState())
    val uiState: StateFlow<SteamBackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accountCount = accounts.size)
            }
        }
    }

    fun createEnvelope(password: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    SteamBackupCrypto.encrypt(
                        SteamBackupPayloadCodec.encode(accountRepository.getAccounts()),
                        password
                    )
                }
            }.onSuccess(onReady)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "备份失败")
                }
            _uiState.value = _uiState.value.copy(isWorking = false)
        }
    }

    fun inspect(content: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val payload = SteamBackupPayloadCodec.decode(
                        SteamBackupCrypto.decrypt(content, password)
                    )
                    payload to backupRepository.preview(payload)
                }
            }.onSuccess { (payload, preview) ->
                _uiState.value = _uiState.value.copy(
                    previewPayload = payload,
                    preview = preview
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: "无法读取备份")
            }
            _uiState.value = _uiState.value.copy(isWorking = false)
        }
    }

    fun restore(strategy: SteamBackupConflictStrategy, onComplete: (SteamBackupRestoreResult) -> Unit) {
        val payload = _uiState.value.previewPayload ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, error = null)
            runCatching { withContext(Dispatchers.IO) { backupRepository.restore(payload, strategy) } }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        preview = null,
                        previewPayload = null
                    )
                    onComplete(it)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        error = error.message ?: "恢复失败"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearPreview() {
        _uiState.value = _uiState.value.copy(preview = null, previewPayload = null, error = null)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    val securityManager = SecurityManager(appContext)
                    val accountRepository = SteamAccountRepository(
                        database.steamAccountDao(),
                        securityManager
                    )
                    return SteamBackupViewModel(
                        accountRepository = accountRepository,
                        backupRepository = SteamBackupRepository(accountRepository)
                    ) as T
                }
            }
        }
    }
}
