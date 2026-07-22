package takagi.ru.monica.steam.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.MdbxRepository
import takagi.ru.monica.repository.MdbxVaultStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.core.SteamTotp
import takagi.ru.monica.steam.confirmations.SteamConfirmationRiskEvaluator
import takagi.ru.monica.steam.analytics.SteamInventoryValuation
import takagi.ru.monica.steam.analytics.SteamListingAnalysis
import takagi.ru.monica.steam.analytics.SteamMarketAnalytics
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamSecurityEvent
import takagi.ru.monica.steam.data.SteamSecurityEventRepository
import takagi.ru.monica.steam.data.SteamSecurityEventSeverity
import takagi.ru.monica.steam.data.SteamSecurityEventType
import takagi.ru.monica.steam.data.SteamMaFileTransferAction
import takagi.ru.monica.steam.data.SteamMdbxAccountRecord
import takagi.ru.monica.steam.data.SteamMdbxAccountStore
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.importer.SteamMaFileBackupCodec
import takagi.ru.monica.steam.importer.SteamMaFileParser
import takagi.ru.monica.steam.importer.SteamMaFilePayload
import takagi.ru.monica.steam.market.SteamInventoryGame
import takagi.ru.monica.steam.market.SteamInventoryItem
import takagi.ru.monica.steam.market.SteamInventoryItemStack
import takagi.ru.monica.steam.market.SteamInventoryOverview
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.market.SteamBatchSellEntry
import takagi.ru.monica.steam.market.SteamMarketListing
import takagi.ru.monica.steam.market.SteamMarketListingsPage
import takagi.ru.monica.steam.market.SteamMarketHistoryPoint
import takagi.ru.monica.steam.market.SteamMarketPrice
import takagi.ru.monica.steam.market.SteamMarketQuote
import takagi.ru.monica.steam.market.SteamMarketService
import takagi.ru.monica.steam.market.SteamWalletInfo
import takagi.ru.monica.steam.market.allocateSteamInventorySale
import takagi.ru.monica.steam.market.findNewSteamMarketConfirmations
import takagi.ru.monica.steam.market.isMarketListingConfirmation
import takagi.ru.monica.steam.market.mergeSteamInventoryStacks
import takagi.ru.monica.steam.market.removeCancelledSteamMarketListings
import takagi.ru.monica.steam.market.removeSteamInventoryAmount
import takagi.ru.monica.steam.network.SteamAuthenticatorService
import takagi.ru.monica.steam.network.SteamAuthorizedDevice
import takagi.ru.monica.steam.network.SteamAuthorizedDeviceService
import takagi.ru.monica.steam.network.SteamBatchResult
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.network.SteamConfirmationService
import takagi.ru.monica.steam.network.SteamLoginApprovalService
import takagi.ru.monica.steam.network.SteamPendingLogin
import takagi.ru.monica.steam.network.SteamQrChallenge
import takagi.ru.monica.steam.network.SteamSessionRefreshService
import takagi.ru.monica.steam.notifications.SteamGiftAction
import takagi.ru.monica.steam.notifications.SteamGiftService
import takagi.ru.monica.steam.notifications.SteamNotificationCache
import takagi.ru.monica.steam.notifications.SteamNotificationPreferencesCache
import takagi.ru.monica.steam.notifications.SteamNotificationService
import takagi.ru.monica.steam.notifications.SteamNotificationsUiState
import takagi.ru.monica.steam.notifications.SteamPendingGift
import takagi.ru.monica.steam.organization.SteamAccountOrganizationRules
import takagi.ru.monica.steam.service.SteamLoginImportService
import takagi.ru.monica.steam.trade.SteamTradeOffer
import takagi.ru.monica.steam.trade.SteamTradeOfferAction
import takagi.ru.monica.steam.trade.SteamTradeOfferActionResult
import takagi.ru.monica.steam.trade.SteamTradeOfferService
import takagi.ru.monica.steam.trade.SteamTradeOffersSnapshot

enum class SteamMarketActionType {
    SELL,
    CANCEL_LISTING
}

data class SteamMarketActionResult(
    val action: SteamMarketActionType,
    val success: Boolean,
    val affectedCount: Int = 0,
    val failedCount: Int = 0,
    val requiresConfirmation: Boolean = false,
    val autoConfirmed: Boolean = false,
    val message: String? = null
)

private data class SteamCancelListingsOutcome(
    val cancelledIds: Set<String>,
    val failedCount: Int,
    val message: String?
)

data class SteamInventoryMarketUiState(
    val language: String = "english",
    val overview: SteamInventoryOverview? = null,
    val selectedGame: SteamInventoryGame? = null,
    val inventoryStacks: List<SteamInventoryItemStack> = emptyList(),
    val inventoryLastAssetId: String? = null,
    val inventoryHasMore: Boolean = false,
    val inventoryLoading: Boolean = false,
    val inventoryLoadingMore: Boolean = false,
    val inventoryError: String? = null,
    val inventoryPriceQuotes: Map<String, SteamMarketPrice> = emptyMap(),
    val inventoryValuation: SteamInventoryValuation? = null,
    val inventoryValuationLoading: Boolean = false,
    val inventoryValuationFetchedAt: Long? = null,
    val inventoryValuationError: String? = null,
    val listings: List<SteamMarketListing> = emptyList(),
    val listingsTotal: Int = 0,
    val listingsNextStart: Int = 0,
    val listingsHasMore: Boolean = false,
    val listingsLoading: Boolean = false,
    val listingsLoadingMore: Boolean = false,
    val listingsError: String? = null,
    val listingAnalysis: SteamListingAnalysis? = null,
    val quoteItemKey: String? = null,
    val quotePrice: SteamMarketPrice? = null,
    val quoteHistory: List<SteamMarketHistoryPoint> = emptyList(),
    val quoteLoading: Boolean = false,
    val batchQuotes: Map<String, SteamMarketQuote> = emptyMap(),
    val batchQuoteLoading: Boolean = false,
    val batchQuoteCompleted: Int = 0,
    val batchQuoteTotal: Int = 0,
    val batchQuoteError: String? = null,
    val actionLoading: Boolean = false,
    val lastActionResult: SteamMarketActionResult? = null
)

data class SteamTradeOffersUiState(
    val snapshot: SteamTradeOffersSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val actionLoadingOfferId: String? = null,
    val lastActionResult: SteamTradeOfferActionResult? = null
)

private data class SteamSellOutcome(
    val listedByStackKey: Map<String, Int>,
    val requiresConfirmation: Boolean,
    val autoConfirmed: Boolean,
    val message: String?
) {
    val listedCount: Int
        get() = listedByStackKey.values.sum()
}

data class SteamUiState(
    val storageSource: SteamStorageSource = SteamStorageSource.Local,
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val currentCode: String = "",
    val secondsRemaining: Int = 30,
    val periodProgress: Float = 1f,
    val confirmations: List<SteamConfirmation> = emptyList(),
    val confirmationHistory: List<SteamSecurityEvent> = emptyList(),
    val pendingLogins: List<SteamPendingLogin> = emptyList(),
    val authorizedDevices: List<SteamAuthorizedDevice> = emptyList(),
    val selectedConfirmationIds: Set<String> = emptySet(),
    val inventoryMarket: SteamInventoryMarketUiState = SteamInventoryMarketUiState(),
    val tradeOffers: SteamTradeOffersUiState = SteamTradeOffersUiState(),
    val notifications: SteamNotificationsUiState = SteamNotificationsUiState(),
    val pendingLoginChallenge: SteamLoginChallengeUi? = null,
    val pendingQrLoginChallenge: SteamQrLoginChallengeUi? = null,
    val pendingMaFileSteamIdRequest: SteamMaFileSteamIdRequestUi? = null,
    val loading: Boolean = false,
    val message: String? = null
)

data class SteamLoginChallengeUi(
    val pendingSessionId: String,
    val steamId: String,
    val confirmationType: Int,
    val message: String,
    val requiresCode: Boolean,
    val canPoll: Boolean,
    val canUseMonicaCode: Boolean
)

data class SteamQrLoginChallengeUi(
    val pendingSessionId: String,
    val challengeUrl: String
)

data class SteamMaFileSteamIdRequestUi(
    val maFileUri: Uri,
    val manifestUri: Uri?,
    val password: String,
    val displayName: String,
    val fileName: String
)

class SteamViewModel(
    private val appContext: Context,
    private val repository: SteamAccountRepository,
    private val mdbxRepository: MdbxRepository? = null,
    private val parser: SteamMaFileParser = SteamMaFileParser(),
    private val confirmationService: SteamConfirmationService = SteamConfirmationService(),
    private val authenticatorService: SteamAuthenticatorService = SteamAuthenticatorService(),
    private val authorizedDeviceService: SteamAuthorizedDeviceService = SteamAuthorizedDeviceService(),
    private val loginApprovalService: SteamLoginApprovalService = SteamLoginApprovalService(),
    private val sessionRefreshService: SteamSessionRefreshService = SteamSessionRefreshService(),
    private val loginImportService: SteamLoginImportService = SteamLoginImportService(),
    private val inventoryService: SteamInventoryService = SteamInventoryService(),
    private val marketService: SteamMarketService = SteamMarketService(),
    private val tradeOfferService: SteamTradeOfferService = SteamTradeOfferService(),
    private val notificationService: SteamNotificationService = SteamNotificationService(),
    private val giftService: SteamGiftService = SteamGiftService(),
    private val notificationCache: SteamNotificationCache = SteamNotificationPreferencesCache(appContext),
    private val securityEventRepository: SteamSecurityEventRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamUiState())
    val uiState: StateFlow<SteamUiState> = _uiState.asStateFlow()
    private var pendingLoginPollJob: Job? = null
    private val mdbxAccountStore = mdbxRepository?.let { SteamMdbxAccountStore(it, parser) }
    private var localAccounts: List<SteamAccount> = emptyList()
    private var mdbxAccountRecords: List<SteamMdbxAccountRecord> = emptyList()
    private var pendingLoginDisplayName: String? = null
    private var pendingLoginCredentialEntryId: Long? = null
    private var pendingLoginCompletionAccountId: Long? = null
    private var pendingLoginRebindAccount = false
    private var inventoryLoadGeneration: Long = 0L
    private var marketQuoteGeneration: Long = 0L
    private var batchQuoteGeneration: Long = 0L

    init {
        SteamDiagLogger.initialize(appContext.applicationContext)
        viewModelScope.launch {
            repository.observeAccounts().collect { accounts ->
                localAccounts = accounts
                if (_uiState.value.storageSource is SteamStorageSource.Local) {
                    updateForAccounts(
                        accounts = accounts,
                        nowMillis = System.currentTimeMillis(),
                        storageSource = SteamStorageSource.Local
                    )
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                updateCodeTick(System.currentTimeMillis())
                delay(CODE_TICK_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val account = selectedAccount()
                if (account?.canApproveLogins == true) {
                    refreshPendingLogins(silent = true)
                }
            }
        }
        val initialSource = readSteamStorageSource(appContext)
        if (initialSource is SteamStorageSource.Mdbx) {
            selectStorageSource(initialSource, persist = false)
        }
    }

    fun selectStorageSource(
        source: SteamStorageSource,
        persist: Boolean = true,
        forceRefresh: Boolean = false
    ) {
        if (!forceRefresh && source == _uiState.value.storageSource) return
        if (persist) saveSteamStorageSource(appContext, source)
        when (source) {
            SteamStorageSource.Local -> {
                mdbxAccountRecords = emptyList()
                updateForAccounts(
                    accounts = localAccounts,
                    nowMillis = System.currentTimeMillis(),
                    storageSource = SteamStorageSource.Local,
                    clearAccountScopedState = true
                )
            }
            is SteamStorageSource.Mdbx -> {
                viewModelScope.launch {
                    val store = mdbxAccountStore
                    if (store == null) {
                        setMessage(R.string.steam_cannot_load_mdbx_accounts)
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        storageSource = source,
                        accounts = emptyList(),
                        selectedAccountId = null,
                        confirmations = emptyList(),
                        pendingLogins = emptyList(),
                        authorizedDevices = emptyList(),
                        selectedConfirmationIds = emptySet(),
                        notifications = SteamNotificationsUiState(),
                        inventoryMarket = SteamInventoryMarketUiState(),
                        tradeOffers = SteamTradeOffersUiState()
                    )
                    setLoading(true)
                    runCatching {
                        withContext(Dispatchers.IO) { store.loadAccounts(source.databaseId) }
                    }.onSuccess { records ->
                        mdbxAccountRecords = records
                        updateForAccounts(
                            accounts = records.map { it.account },
                            nowMillis = System.currentTimeMillis(),
                            storageSource = source,
                            clearAccountScopedState = true
                        )
                    }.onFailure { error ->
                        mdbxAccountRecords = emptyList()
                        _uiState.value = _uiState.value.copy(accounts = emptyList())
                        setMessage(error.message ?: appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
                    }
                    setLoading(false)
                }
            }
        }
    }

    fun selectAccount(id: Long) {
        val selectedId = _uiState.value.accounts.firstOrNull { it.id == id }?.id ?: return
        selectRuntimeAccount(selectedId)
        if (_uiState.value.storageSource is SteamStorageSource.Mdbx) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.select(selectedId)
        }
    }

    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        if (_uiState.value.storageSource is SteamStorageSource.Mdbx) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSortOrders(items)
        }
    }

    fun updateDisplayName(accountId: Long, displayName: String) {
        viewModelScope.launch {
            runCatching {
                val storedAccount = accountById(accountId) ?: return@launch
                val normalizedDisplayName = displayName.trim()
                    .ifBlank { storedAccount.accountName.ifBlank { storedAccount.steamId } }
                when (val source = _uiState.value.storageSource) {
                    SteamStorageSource.Local -> withContext(Dispatchers.IO) {
                        repository.updateDisplayName(accountId, displayName)
                    }
                    is SteamStorageSource.Mdbx -> {
                        val store = mdbxAccountStore
                            ?: throw IllegalStateException(appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
                        val record = mdbxAccountRecords.firstOrNull { it.account.id == accountId }
                            ?: return@launch
                        withContext(Dispatchers.IO) {
                            store.upsertAccount(
                                databaseId = source.databaseId,
                                entryId = record.entryId,
                                account = storedAccount.copy(
                                    displayName = normalizedDisplayName,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        reloadMdbxAccounts(source)
                    }
                }
            }.onSuccess {
                setMessage(R.string.steam_remark_updated)
            }.onFailure { error ->
                setMessage(error.message ?: appContext.getString(R.string.steam_import_failed))
            }
        }
    }

    fun updateOrganization(
        accountId: Long,
        groupName: String,
        tags: Iterable<String>,
        accentArgb: Long?,
        note: String,
        pinned: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                val storedAccount = accountById(accountId) ?: return@launch
                val normalizedGroup = SteamAccountOrganizationRules.normalizeGroup(groupName)
                val normalizedTags = SteamAccountOrganizationRules.parseTags(tags.joinToString(","))
                val normalizedNote = note.trim().take(SteamAccountOrganizationRules.MAX_NOTE_LENGTH)
                when (val source = _uiState.value.storageSource) {
                    SteamStorageSource.Local -> withContext(Dispatchers.IO) {
                        repository.updateOrganization(
                            id = accountId,
                            groupName = normalizedGroup,
                            tags = normalizedTags,
                            accentArgb = accentArgb,
                            note = normalizedNote,
                            pinned = pinned
                        )
                    }
                    is SteamStorageSource.Mdbx -> {
                        val store = mdbxAccountStore
                            ?: throw IllegalStateException(appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
                        val record = mdbxAccountRecords.firstOrNull { it.account.id == accountId }
                            ?: return@launch
                        withContext(Dispatchers.IO) {
                            store.upsertAccount(
                                databaseId = source.databaseId,
                                entryId = record.entryId,
                                account = storedAccount.copy(
                                    groupName = normalizedGroup,
                                    tags = normalizedTags,
                                    accentArgb = accentArgb,
                                    note = normalizedNote,
                                    pinned = pinned,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        reloadMdbxAccounts(source)
                    }
                }
            }.onSuccess {
                setMessage(R.string.steam_organization_updated)
            }.onFailure { error ->
                setMessage(error.message ?: appContext.getString(R.string.steam_import_failed))
            }
        }
        securityEventRepository?.let { eventRepository ->
            viewModelScope.launch {
                eventRepository.observeRecent().collect { events ->
                    _uiState.value = _uiState.value.copy(
                        confirmationHistory = events
                            .filter { it.type == SteamSecurityEventType.CONFIRMATION_ACTION }
                            .take(CONFIRMATION_HISTORY_LIMIT)
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun clearPendingMaFileSteamIdRequest() {
        _uiState.value = _uiState.value.copy(pendingMaFileSteamIdRequest = null)
    }

    fun importMaFile(
        maFileUri: Uri,
        manifestUri: Uri?,
        password: String,
        displayName: String,
        steamId: String,
        allowMissingSteamId: Boolean = false
    ) {
        viewModelScope.launch {
            setLoading(true)
            runCatching {
                val maFileText = readText(maFileUri)
                val manifestText = manifestUri?.let { readText(it) }
                val payload = parser.parse(
                    maFileContent = maFileText,
                    fileName = maFileUri.lastPathSegment,
                    manifestContent = manifestText,
                    password = password.takeIf { it.isNotEmpty() },
                    displayNameOverride = displayName,
                    steamIdOverride = steamId.takeIf { it.isNotBlank() },
                    allowMissingSteamId = allowMissingSteamId
                )
                saveMaFilePayload(payload)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(pendingMaFileSteamIdRequest = null)
                setMessage(
                    if (allowMissingSteamId) {
                        R.string.steam_account_imported_code_only
                    } else {
                        R.string.steam_account_imported
                    }
                )
            }.onFailure { error ->
                if (error.message == "maFile missing steamid" && steamId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        pendingMaFileSteamIdRequest = SteamMaFileSteamIdRequestUi(
                            maFileUri = maFileUri,
                            manifestUri = manifestUri,
                            password = password,
                            displayName = displayName,
                            fileName = maFileUri.lastPathSegment.orEmpty()
                        )
                    )
                } else {
                    setMessage(steamImportErrorMessage(error))
                }
            }
            setLoading(false)
        }
    }

    fun importCodeOnlyKey(displayName: String, accountName: String, sharedSecret: String) {
        viewModelScope.launch {
            setLoading(true)
            runCatching {
                val resolvedAccountName = accountName.trim().ifBlank { displayName.trim() }
                    .ifBlank { "Steam" }
                val resolvedDisplayName = displayName.trim().ifBlank { resolvedAccountName }
                val maFileJson = JsonObject(
                    mapOf(
                        "account_name" to JsonPrimitive(resolvedAccountName),
                        "shared_secret" to JsonPrimitive(sharedSecret.trim()),
                        "monica_display_name" to JsonPrimitive(resolvedDisplayName),
                        "monica_missing_steamid" to JsonPrimitive(true)
                    )
                ).toString()
                val payload = parser.parse(
                    maFileContent = maFileJson,
                    fileName = null,
                    displayNameOverride = resolvedDisplayName,
                    allowMissingSteamId = true
                )
                saveMaFilePayload(payload)
            }.onSuccess {
                setMessage(R.string.steam_account_imported_code_only)
            }.onFailure { error ->
                setMessage(steamImportErrorMessage(error))
            }
            setLoading(false)
        }
    }

    private fun steamImportErrorMessage(error: Throwable): String {
        return when (error.message) {
            "maFile missing steamid" -> appContext.getString(R.string.steam_mafile_missing_steamid_message)
            "Invalid SteamID" -> appContext.getString(R.string.steam_mafile_invalid_steamid_message)
            else -> error.message ?: appContext.getString(R.string.steam_import_failed)
        }
    }

    fun beginSteamLogin(
        userName: String,
        password: String,
        displayName: String = "",
        credentialEntryId: Long? = null
    ) {
        viewModelScope.launch {
            pendingLoginPollJob?.cancel()
            clearPendingLoginTarget()
            pendingLoginDisplayName = displayName.trim().takeIf { it.isNotBlank() }
            pendingLoginCredentialEntryId = credentialEntryId
            setLoading(true)
            _uiState.value = _uiState.value.copy(pendingQrLoginChallenge = null)
            when (val result = withContext(Dispatchers.IO) {
                loginImportService.beginLogin(userName, password)
            }) {
                is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                    val challenge = result.toChallengeUi()
                    _uiState.value = _uiState.value.copy(
                        pendingLoginChallenge = challenge,
                        message = challenge.message
                    )
                    if (challenge.canPoll) {
                        startPendingLoginPolling(challenge.pendingSessionId)
                    }
                }
                is SteamLoginImportService.LoginResult.ReadyForImport -> {
                    pendingLoginPollJob?.cancel()
                    val successMessage = saveLoginResultSafely(result, pendingLoginDisplayName)
                    pendingLoginDisplayName = null
                    _uiState.value = _uiState.value.copy(pendingLoginChallenge = null)
                    successMessage?.let(::setMessage)
                }
                is SteamLoginImportService.LoginResult.Failure -> {
                    clearPendingLoginTarget()
                    pendingLoginDisplayName = null
                    setMessage(result.message)
                }
            }
            setLoading(false)
        }
    }

    fun beginSteamIdCompletionLogin(
        accountId: Long,
        userName: String,
        password: String,
        credentialEntryId: Long? = null
    ) {
        val account = accountById(accountId) ?: return
        if (account.hasRealSteamId) return
        viewModelScope.launch {
            pendingLoginPollJob?.cancel()
            pendingLoginCompletionAccountId = accountId
            pendingLoginRebindAccount = false
            pendingLoginDisplayName = null
            pendingLoginCredentialEntryId = credentialEntryId
            setLoading(true)
            _uiState.value = _uiState.value.copy(pendingQrLoginChallenge = null)
            when (val result = withContext(Dispatchers.IO) {
                loginImportService.beginSessionLogin(userName, password)
            }) {
                is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                    val challenge = result.toChallengeUi()
                    _uiState.value = _uiState.value.copy(
                        pendingLoginChallenge = challenge,
                        message = challenge.message
                    )
                    if (challenge.canPoll) {
                        startPendingLoginPolling(challenge.pendingSessionId)
                    }
                }
                is SteamLoginImportService.LoginResult.ReadyForImport -> {
                    pendingLoginPollJob?.cancel()
                    val successMessage = saveLoginResultSafely(result, null)
                    _uiState.value = _uiState.value.copy(pendingLoginChallenge = null)
                    successMessage?.let(::setMessage)
                }
                is SteamLoginImportService.LoginResult.Failure -> {
                    clearPendingLoginTarget()
                    setMessage(result.message)
                }
            }
            setLoading(false)
        }
    }

    fun beginSteamAccountRebindLogin(
        accountId: Long,
        userName: String,
        password: String,
        credentialEntryId: Long? = null
    ) {
        accountById(accountId) ?: return
        viewModelScope.launch {
            pendingLoginPollJob?.cancel()
            pendingLoginCompletionAccountId = accountId
            pendingLoginRebindAccount = true
            pendingLoginDisplayName = null
            pendingLoginCredentialEntryId = credentialEntryId
            setLoading(true)
            _uiState.value = _uiState.value.copy(pendingQrLoginChallenge = null)
            when (val result = withContext(Dispatchers.IO) {
                loginImportService.beginSessionLogin(userName, password)
            }) {
                is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                    val challenge = result.toChallengeUi()
                    _uiState.value = _uiState.value.copy(
                        pendingLoginChallenge = challenge,
                        message = challenge.message
                    )
                    if (challenge.canPoll) {
                        startPendingLoginPolling(challenge.pendingSessionId)
                    }
                }
                is SteamLoginImportService.LoginResult.ReadyForImport -> {
                    pendingLoginPollJob?.cancel()
                    val successMessage = saveLoginResultSafely(result, null)
                    _uiState.value = _uiState.value.copy(pendingLoginChallenge = null)
                    successMessage?.let(::setMessage)
                }
                is SteamLoginImportService.LoginResult.Failure -> {
                    clearPendingLoginTarget()
                    setMessage(result.message)
                }
            }
            setLoading(false)
        }
    }

    fun beginSteamQrLogin(displayName: String = "") {
        viewModelScope.launch {
            pendingLoginPollJob?.cancel()
            clearPendingLoginTarget()
            pendingLoginDisplayName = displayName.trim().takeIf { it.isNotBlank() }
            setLoading(true)
            _uiState.value = _uiState.value.copy(
                pendingLoginChallenge = null,
                pendingQrLoginChallenge = null
            )
            when (val result = withContext(Dispatchers.IO) {
                loginImportService.beginQrLogin()
            }) {
                is SteamLoginImportService.QrLoginResult.ChallengeRequired -> {
                    _uiState.value = _uiState.value.copy(
                        pendingQrLoginChallenge = SteamQrLoginChallengeUi(
                            pendingSessionId = result.pendingSessionId,
                            challengeUrl = result.challengeUrl
                        )
                    )
                    startPendingQrLoginPolling(result.pendingSessionId)
                }
                is SteamLoginImportService.QrLoginResult.LoginChallengeRequired -> {
                    handleLoginChallenge(result.challenge)
                }
                is SteamLoginImportService.QrLoginResult.ReadyForImport -> {
                    val successMessage = saveLoginResultSafely(result.result, pendingLoginDisplayName)
                    pendingLoginDisplayName = null
                    _uiState.value = _uiState.value.copy(
                        pendingLoginChallenge = null,
                        pendingQrLoginChallenge = null
                    )
                    successMessage?.let(::setMessage)
                }
                is SteamLoginImportService.QrLoginResult.Failure -> {
                    clearPendingLoginTarget()
                    pendingLoginDisplayName = null
                    setMessage(result.message)
                }
            }
            setLoading(false)
        }
    }

    fun submitSteamLoginCode(code: String) {
        val challenge = _uiState.value.pendingLoginChallenge ?: return
        if (!challenge.requiresCode) return
        viewModelScope.launch {
            pendingLoginPollJob?.cancel()
            setLoading(true)
            when (val result = withContext(Dispatchers.IO) {
                loginImportService.submitSteamGuardCode(
                    pendingSessionId = challenge.pendingSessionId,
                    code = code,
                    confirmationType = challenge.confirmationType
                )
            }) {
                is SteamLoginImportService.LoginResult.ReadyForImport -> {
                    pendingLoginPollJob?.cancel()
                    val successMessage = saveLoginResultSafely(result, pendingLoginDisplayName)
                    pendingLoginDisplayName = null
                    _uiState.value = _uiState.value.copy(
                        pendingLoginChallenge = null,
                        pendingQrLoginChallenge = null
                    )
                    successMessage?.let(::setMessage)
                }
                is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                    handleLoginChallenge(result, fallbackType = challenge.confirmationType)
                }
                is SteamLoginImportService.LoginResult.Failure -> {
                    clearPendingLoginTarget()
                    setMessage(result.message)
                }
            }
            setLoading(false)
        }
    }

    fun cancelSteamLoginChallenge() {
        pendingLoginPollJob?.cancel()
        pendingLoginPollJob = null
        _uiState.value.pendingLoginChallenge?.pendingSessionId?.let { sessionId ->
            loginImportService.clearPendingSession(sessionId)
        }
        _uiState.value.pendingQrLoginChallenge?.pendingSessionId?.let { sessionId ->
            loginImportService.clearPendingSession(sessionId)
        }
        _uiState.value = _uiState.value.copy(
            pendingLoginChallenge = null,
            pendingQrLoginChallenge = null
        )
        pendingLoginDisplayName = null
        clearPendingLoginTarget()
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            deleteAccountByActiveSource(id)
        }
    }

    fun deleteLocalAuthenticator(accountId: Long) {
        viewModelScope.launch {
            deleteAccountByActiveSource(accountId)
            setMessage(R.string.steam_remove_authenticator_local_done)
        }
    }

    fun transferAccounts(
        accountIds: List<Long>,
        targetSource: SteamStorageSource,
        action: SteamMaFileTransferAction
    ) {
        val source = _uiState.value.storageSource
        if (accountIds.isEmpty() || source == targetSource) return
        viewModelScope.launch {
            setLoading(true)
            runCatching {
                val accounts = accountIds.distinct().mapNotNull(::accountById)
                require(accounts.isNotEmpty()) {
                    appContext.getString(R.string.steam_transfer_mafile_failed)
                }
                withContext(Dispatchers.IO) {
                    writeAccountsToStorageSource(accounts, targetSource)
                    if (action == SteamMaFileTransferAction.MOVE) {
                        deleteAccountsFromStorageSource(source, accounts.map { it.id })
                    }
                }
                if (source is SteamStorageSource.Mdbx && action == SteamMaFileTransferAction.MOVE) {
                    reloadMdbxAccounts(source, clearAccountScopedState = true)
                }
            }.onSuccess {
                setMessage(R.string.steam_transfer_mafile_done)
            }.onFailure { error ->
                setMessage(error.message ?: appContext.getString(R.string.steam_transfer_mafile_failed))
            }
            setLoading(false)
        }
    }

    fun removeAuthenticator(accountId: Long) {
        viewModelScope.launch {
            val storedAccount = accountById(accountId) ?: return@launch
            val account = ensureSteamSession(storedAccount)
            if (account == null || account.accessToken.isNullOrBlank()) {
                setMessage(R.string.steam_remove_authenticator_missing_access_token)
                return@launch
            }
            if (account.revocationCode.isNullOrBlank()) {
                setMessage(R.string.steam_remove_authenticator_missing_revocation_code)
                return@launch
            }
            setLoading(true)
            runCatching {
                withContext(Dispatchers.IO) { authenticatorService.remove(account) }
            }.onSuccess { result ->
                if (result.success) {
                    deleteAccountByActiveSource(accountId)
                    setMessage(R.string.steam_remove_authenticator_done)
                } else {
                    val message = result.attemptsRemaining?.let { attempts ->
                        appContext.getString(R.string.steam_remove_authenticator_failed_attempts, attempts)
                    } ?: appContext.getString(R.string.steam_remove_authenticator_failed)
                    _uiState.value = _uiState.value.copy(message = message)
                }
            }.onFailure { error ->
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.steam_remove_authenticator_failed)
                _uiState.value = _uiState.value.copy(message = message)
            }
            setLoading(false)
        }
    }

    fun refreshConfirmations(silent: Boolean = false) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            if (!silent) setLoading(true)
            runCatching {
                account.confirmationUnavailableMessage()?.let { reason ->
                    throw IllegalStateException(reason)
                }
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException(account.confirmationUnavailableMessage())
                withContext(Dispatchers.IO) { confirmationService.fetch(freshAccount) }
            }.onSuccess { confirmations ->
                _uiState.value = _uiState.value.copy(
                    confirmations = confirmations,
                    selectedConfirmationIds = _uiState.value.selectedConfirmationIds.intersect(confirmations.map { it.id }.toSet())
                )
                if (!silent) {
                    recordConfirmationEvent(
                        account = account,
                        severity = SteamSecurityEventSeverity.INFO,
                        summary = appContext.getString(
                            R.string.steam_confirmation_history_refreshed,
                            confirmations.size
                        )
                    )
                }
            }.onFailure { error ->
                if (!silent) setMessage(
                    error.message ?: appContext.getString(R.string.steam_cannot_refresh_confirmations)
                )
                if (!silent) {
                    recordConfirmationEvent(
                        account = account,
                        severity = SteamSecurityEventSeverity.WARNING,
                        summary = appContext.getString(R.string.steam_confirmation_history_refresh_failed)
                    )
                }
            }
            if (!silent) setLoading(false)
        }
    }

    fun refreshSteamNotifications(silent: Boolean = false) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                notificationCache.load(account.steamId)
            }
            if (
                selectedAccount()?.id == account.id &&
                _uiState.value.notifications.snapshot == null &&
                cached != null
            ) {
                updateNotifications {
                    it.copy(snapshot = cached, fromCache = true, error = null)
                }
            }
            if (!silent) {
                updateNotifications { it.copy(loading = true, error = null) }
            }
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException(
                        appContext.getString(R.string.steam_notifications_session_required)
                    )
                withContext(Dispatchers.IO) {
                    val freshSnapshot = notificationService.fetch(freshAccount)
                    val giftResult: Result<List<SteamPendingGift>> =
                        if (freshSnapshot.pendingGiftCount > 0) {
                            runCatching { giftService.fetchPending(freshAccount) }
                        } else {
                            Result.success(emptyList())
                        }
                    val merged = freshSnapshot.copy(
                        pendingGifts = giftResult.getOrElse { cached?.pendingGifts.orEmpty() }
                    )
                    notificationCache.save(freshAccount.steamId, merged)
                    merged to giftResult.exceptionOrNull()
                }
            }.onSuccess { (snapshot, giftError) ->
                if (selectedAccount()?.id != account.id) return@onSuccess
                updateNotifications {
                    it.copy(
                        snapshot = snapshot,
                        loading = false,
                        fromCache = false,
                        error = giftError?.message
                    )
                }
            }.onFailure { error ->
                if (selectedAccount()?.id != account.id) return@onFailure
                updateNotifications {
                    it.copy(
                        loading = false,
                        error = error.message
                            ?: appContext.getString(R.string.steam_notifications_load_failed)
                    )
                }
            }
        }
    }

    fun respondGift(
        gift: SteamPendingGift,
        action: SteamGiftAction,
        note: String = ""
    ) {
        val account = selectedAccount() ?: return
        if (_uiState.value.notifications.actionGiftId != null) return
        updateNotifications { it.copy(actionGiftId = gift.id, error = null) }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException(
                        appContext.getString(R.string.steam_notifications_session_required)
                    )
                withContext(Dispatchers.IO) {
                    giftService.respond(freshAccount, gift, action, note)
                }
            }.onSuccess { result ->
                if (selectedAccount()?.id != account.id) return@onSuccess
                updateNotifications { it.copy(actionGiftId = null) }
                if (result.success) {
                    setMessage(
                        when (action) {
                            SteamGiftAction.ADD_TO_LIBRARY -> R.string.steam_gift_added_to_library
                            SteamGiftAction.KEEP_IN_INVENTORY -> R.string.steam_gift_kept_in_inventory
                            SteamGiftAction.DECLINE -> R.string.steam_gift_declined
                        }
                    )
                    refreshSteamNotifications(silent = true)
                } else {
                    setMessage(
                        result.message ?: appContext.getString(R.string.steam_gift_action_failed)
                    )
                }
            }.onFailure { error ->
                if (selectedAccount()?.id != account.id) return@onFailure
                updateNotifications {
                    it.copy(
                        actionGiftId = null,
                        error = error.message
                            ?: appContext.getString(R.string.steam_gift_action_failed)
                    )
                }
                setMessage(
                    error.message ?: appContext.getString(R.string.steam_gift_action_failed)
                )
            }
        }
    }

    fun toggleConfirmation(id: String) {
        val selected = _uiState.value.selectedConfirmationIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        _uiState.value = _uiState.value.copy(selectedConfirmationIds = selected)
    }

    fun selectAllConfirmations() {
        val ids = _uiState.value.confirmations.map { it.id }.toSet()
        if (ids.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(selectedConfirmationIds = ids)
        }
    }

    fun selectConfirmations(ids: Set<String>) {
        val visibleIds = ids.intersect(_uiState.value.confirmations.map { it.id }.toSet())
        _uiState.value = _uiState.value.copy(selectedConfirmationIds = visibleIds)
    }

    fun clearSelectedConfirmations() {
        _uiState.value = _uiState.value.copy(selectedConfirmationIds = emptySet())
    }

    fun respondSelectedConfirmations(accept: Boolean) {
        val selectedIds = _uiState.value.selectedConfirmationIds
        val confirmations = _uiState.value.confirmations.filter { it.id in selectedIds }
        if (confirmations.isEmpty()) return
        respondConfirmations(confirmations, accept)
    }

    fun respondConfirmations(confirmations: List<SteamConfirmation>, accept: Boolean) {
        val account = selectedAccount() ?: return
        if (confirmations.isEmpty()) return
        viewModelScope.launch {
            setLoading(true)
            val result = runCatching {
                account.confirmationUnavailableMessage()?.let { reason ->
                    throw IllegalStateException(reason)
                }
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException(account.confirmationUnavailableMessage())
                withContext(Dispatchers.IO) {
                    confirmationService.respondMultiple(freshAccount, confirmations, accept)
                }
            }.getOrElse { SteamBatchResult(ok = 0, failed = confirmations.size) }
            val highestRisk = confirmations
                .map { SteamConfirmationRiskEvaluator.evaluate(it).level }
                .maxByOrNull { it.ordinal }
            recordConfirmationEvent(
                account = account,
                severity = if (result.failed > 0) {
                    SteamSecurityEventSeverity.WARNING
                } else {
                    SteamSecurityEventSeverity.INFO
                },
                summary = appContext.getString(
                    if (accept) {
                        R.string.steam_confirmation_history_approved
                    } else {
                        R.string.steam_confirmation_history_rejected
                    },
                    result.ok
                ),
                detail = "risk=${highestRisk?.name ?: "UNKNOWN"}; succeeded=${result.ok}; failed=${result.failed}"
            )
            setMessage(R.string.steam_batch_done, result.ok, result.failed)
            _uiState.value = _uiState.value.copy(selectedConfirmationIds = emptySet())
            refreshConfirmations(silent = true)
            setLoading(false)
        }
    }

    fun respondConfirmation(confirmation: SteamConfirmation, accept: Boolean) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            setLoading(true)
            val failureReason = account.confirmationUnavailableMessage()
            val ok = if (failureReason != null) {
                false
            } else runCatching {
                val freshAccount = ensureSteamSession(account) ?: return@runCatching false
                withContext(Dispatchers.IO) { confirmationService.respond(freshAccount, confirmation, accept) }
            }.getOrDefault(false)
            val risk = SteamConfirmationRiskEvaluator.evaluate(confirmation)
            recordConfirmationEvent(
                account = account,
                severity = if (ok) SteamSecurityEventSeverity.INFO else SteamSecurityEventSeverity.WARNING,
                summary = appContext.getString(
                    when {
                        !ok -> R.string.steam_confirmation_history_action_failed
                        accept -> R.string.steam_confirmation_history_approved
                        else -> R.string.steam_confirmation_history_rejected
                    },
                    1
                ),
                detail = "risk=${risk.level.name}; succeeded=${if (ok) 1 else 0}; failed=${if (ok) 0 else 1}"
            )
            setMessage(if (ok) appContext.getString(R.string.steam_done) else failureReason ?: appContext.getString(R.string.steam_confirmation_failed))
            refreshConfirmations(silent = true)
            setLoading(false)
        }
    }

    fun refreshTradeOffers(language: String = "english", silent: Boolean = false) {
        val account = selectedAccount() ?: return
        if (!silent) {
            updateTradeOffers {
                it.copy(loading = true, error = null, lastActionResult = null)
            }
        }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException(
                        appContext.getString(R.string.steam_trade_offers_session_required)
                    )
                withContext(Dispatchers.IO) {
                    tradeOfferService.fetch(freshAccount, language = language)
                }
            }.onSuccess { snapshot ->
                if (selectedAccount()?.id != account.id) return@onSuccess
                updateTradeOffers {
                    it.copy(snapshot = snapshot, loading = false, error = null)
                }
            }.onFailure { error ->
                if (selectedAccount()?.id != account.id) return@onFailure
                updateTradeOffers {
                    it.copy(
                        loading = false,
                        error = error.message
                            ?: appContext.getString(R.string.steam_trade_offers_load_failed)
                    )
                }
            }
        }
    }

    fun respondTradeOffer(
        offer: SteamTradeOffer,
        action: SteamTradeOfferAction,
        language: String = "english"
    ) {
        val account = selectedAccount() ?: return
        if (_uiState.value.tradeOffers.actionLoadingOfferId != null) return
        updateTradeOffers {
            it.copy(
                actionLoadingOfferId = offer.id,
                error = null,
                lastActionResult = null
            )
        }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException(
                        appContext.getString(R.string.steam_trade_offers_session_required)
                    )
                withContext(Dispatchers.IO) {
                    tradeOfferService.respond(freshAccount, offer, action)
                }
            }.onSuccess { result ->
                if (selectedAccount()?.id != account.id) return@onSuccess
                updateTradeOffers {
                    it.copy(
                        actionLoadingOfferId = null,
                        lastActionResult = result,
                        error = result.message.takeUnless { result.success }
                    )
                }
                setMessage(
                    when {
                        !result.success -> result.message
                            ?: appContext.getString(R.string.steam_trade_offer_action_failed)
                        result.requiresMobileConfirmation -> appContext.getString(
                            R.string.steam_trade_offer_needs_confirmation
                        )
                        else -> appContext.getString(R.string.steam_done)
                    }
                )
                if (result.requiresMobileConfirmation) {
                    refreshConfirmations(silent = true)
                }
                if (result.success) {
                    refreshTradeOffers(language = language, silent = true)
                }
            }.onFailure { error ->
                if (selectedAccount()?.id != account.id) return@onFailure
                updateTradeOffers {
                    it.copy(
                        actionLoadingOfferId = null,
                        error = error.message
                            ?: appContext.getString(R.string.steam_trade_offer_action_failed)
                    )
                }
                setMessage(
                    error.message ?: appContext.getString(R.string.steam_trade_offer_action_failed)
                )
            }
        }
    }

    fun refreshInventory(language: String) {
        val account = selectedAccount() ?: return
        val generation = ++inventoryLoadGeneration
        updateInventoryMarket {
            it.copy(
                language = language,
                inventoryLoading = true,
                inventoryLoadingMore = false,
                inventoryError = null,
                inventoryPriceQuotes = emptyMap(),
                inventoryValuation = null,
                inventoryValuationFetchedAt = null,
                inventoryValuationError = null
            )
        }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException("Steam community session unavailable")
                withContext(Dispatchers.IO) {
                    val overview = inventoryService.fetchOverview(freshAccount)
                    val previousGame = _uiState.value.inventoryMarket.selectedGame
                    val selectedGame = overview.games.firstOrNull { game ->
                        game.appId == previousGame?.appId && game.contextId == previousGame.contextId
                    } ?: overview.games.firstOrNull()
                    val page = selectedGame?.let { game ->
                        inventoryService.fetchItems(
                            account = freshAccount,
                            appId = game.appId,
                            contextId = game.contextId,
                            language = language
                        )
                    }
                    Triple(overview, selectedGame, page)
                }
            }.onSuccess { (overview, selectedGame, page) ->
                if (generation != inventoryLoadGeneration) return@onSuccess
                updateInventoryMarket {
                    it.copy(
                        language = language,
                        overview = overview,
                        selectedGame = selectedGame,
                        inventoryStacks = page?.let { loaded ->
                            mergeSteamInventoryStacks(emptyList(), loaded.items)
                        }.orEmpty(),
                        inventoryLastAssetId = page?.lastAssetId,
                        inventoryHasMore = page?.hasMore == true,
                        inventoryLoading = false,
                        inventoryLoadingMore = false,
                        inventoryError = null,
                        inventoryPriceQuotes = emptyMap(),
                        inventoryValuation = null,
                        inventoryValuationFetchedAt = null,
                        inventoryValuationError = null
                    )
                }
            }.onFailure { error ->
                if (generation != inventoryLoadGeneration) return@onFailure
                updateInventoryMarket {
                    it.copy(
                        inventoryLoading = false,
                        inventoryLoadingMore = false,
                        inventoryError = error.message
                    )
                }
            }
        }
    }

    fun selectInventoryGame(game: SteamInventoryGame, language: String) {
        val account = selectedAccount() ?: return
        val generation = ++inventoryLoadGeneration
        updateInventoryMarket {
            it.copy(
                language = language,
                selectedGame = game,
                inventoryStacks = emptyList(),
                inventoryLastAssetId = null,
                inventoryHasMore = false,
                inventoryLoading = true,
                inventoryLoadingMore = false,
                inventoryError = null,
                inventoryPriceQuotes = emptyMap(),
                inventoryValuation = null,
                inventoryValuationFetchedAt = null,
                inventoryValuationError = null
            )
        }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException("Steam community session unavailable")
                withContext(Dispatchers.IO) {
                    inventoryService.fetchItems(
                        account = freshAccount,
                        appId = game.appId,
                        contextId = game.contextId,
                        language = language
                    )
                }
            }.onSuccess { page ->
                if (generation != inventoryLoadGeneration) return@onSuccess
                updateInventoryMarket {
                    it.copy(
                        inventoryStacks = mergeSteamInventoryStacks(emptyList(), page.items),
                        inventoryLastAssetId = page.lastAssetId,
                        inventoryHasMore = page.hasMore,
                        inventoryLoading = false,
                        inventoryError = null,
                        inventoryPriceQuotes = emptyMap(),
                        inventoryValuation = null,
                        inventoryValuationFetchedAt = null,
                        inventoryValuationError = null
                    )
                }
            }.onFailure { error ->
                if (generation != inventoryLoadGeneration) return@onFailure
                updateInventoryMarket {
                    it.copy(inventoryLoading = false, inventoryError = error.message)
                }
            }
        }
    }

    fun loadMoreInventory() {
        val state = _uiState.value.inventoryMarket
        val account = selectedAccount() ?: return
        val game = state.selectedGame ?: return
        if (!state.inventoryHasMore || state.inventoryLoading || state.inventoryLoadingMore) return
        val generation = inventoryLoadGeneration
        updateInventoryMarket { it.copy(inventoryLoadingMore = true, inventoryError = null) }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException("Steam community session unavailable")
                withContext(Dispatchers.IO) {
                    inventoryService.fetchItems(
                        account = freshAccount,
                        appId = game.appId,
                        contextId = game.contextId,
                        language = state.language,
                        startAssetId = state.inventoryLastAssetId
                    )
                }
            }.onSuccess { page ->
                if (generation != inventoryLoadGeneration) return@onSuccess
                updateInventoryMarket {
                    it.copy(
                        inventoryStacks = mergeSteamInventoryStacks(it.inventoryStacks, page.items),
                        inventoryLastAssetId = page.lastAssetId,
                        inventoryHasMore = page.hasMore,
                        inventoryLoadingMore = false,
                        inventoryError = null,
                        inventoryValuation = null,
                        inventoryValuationFetchedAt = null,
                        inventoryValuationError = null
                    )
                }
            }.onFailure { error ->
                if (generation != inventoryLoadGeneration) return@onFailure
                updateInventoryMarket {
                    it.copy(inventoryLoadingMore = false, inventoryError = error.message)
                }
            }
        }
    }

    fun refreshInventoryValuation() {
        val state = _uiState.value.inventoryMarket
        val stacks = state.inventoryStacks.filter { it.item.marketable }
        if (stacks.isEmpty() || state.inventoryValuationLoading) return
        val wallet = state.overview?.wallet ?: SteamWalletInfo.Fallback
        updateInventoryMarket {
            it.copy(
                inventoryValuationLoading = true,
                inventoryValuationError = null
            )
        }
        viewModelScope.launch {
            val prices = withContext(Dispatchers.IO) {
                buildMap {
                    stacks.distinctBy { it.item.stackKey }.forEach { stack ->
                        runCatching {
                            marketService.fetchPriceOverview(
                                appId = stack.item.appId,
                                marketHashName = stack.item.marketHashName,
                                currency = wallet.currency
                            )
                        }.getOrNull()?.let { price ->
                            put(stack.item.stackKey, price)
                        }
                    }
                }
            }
            val latestStacks = _uiState.value.inventoryMarket.inventoryStacks
            val valuation = SteamMarketAnalytics.inventoryValuation(
                stacks = latestStacks,
                prices = prices,
                wallet = wallet
            )
            updateInventoryMarket {
                it.copy(
                    inventoryPriceQuotes = prices,
                    inventoryValuation = valuation,
                    inventoryValuationLoading = false,
                    inventoryValuationFetchedAt = System.currentTimeMillis(),
                    inventoryValuationError = if (valuation.pricedItems == 0) {
                        appContext.getString(R.string.steam_analytics_no_prices)
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun refreshMarketListings(language: String) {
        val account = selectedAccount() ?: return
        updateInventoryMarket {
            it.copy(
                language = language,
                listingsLoading = true,
                listingsLoadingMore = false,
                listingsError = null
            )
        }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException("Steam community session unavailable")
                withContext(Dispatchers.IO) {
                    val overview = _uiState.value.inventoryMarket.overview
                        ?: inventoryService.fetchOverview(freshAccount)
                    overview to marketService.fetchMyListings(
                        freshAccount,
                        language = language
                    )
                }
            }.onSuccess { (overview, page) ->
                updateInventoryMarket { it.copy(overview = overview) }
                applyListingsPage(page, append = false)
            }.onFailure { error ->
                updateInventoryMarket {
                    it.copy(
                        listingsLoading = false,
                        listingsLoadingMore = false,
                        listingsError = error.message
                    )
                }
            }
        }
    }

    fun loadMoreMarketListings() {
        val state = _uiState.value.inventoryMarket
        val account = selectedAccount() ?: return
        if (!state.listingsHasMore || state.listingsLoading || state.listingsLoadingMore) return
        updateInventoryMarket { it.copy(listingsLoadingMore = true, listingsError = null) }
        viewModelScope.launch {
            runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException("Steam community session unavailable")
                withContext(Dispatchers.IO) {
                    marketService.fetchMyListings(
                        account = freshAccount,
                        language = state.language,
                        start = state.listingsNextStart
                    )
                }
            }.onSuccess { page ->
                applyListingsPage(page, append = true)
            }.onFailure { error ->
                updateInventoryMarket {
                    it.copy(listingsLoadingMore = false, listingsError = error.message)
                }
            }
        }
    }

    fun loadMarketQuote(item: SteamInventoryItem) {
        val account = selectedAccount() ?: return
        val wallet = _uiState.value.inventoryMarket.overview?.wallet ?: SteamWalletInfo.Fallback
        val generation = ++marketQuoteGeneration
        updateInventoryMarket {
            it.copy(
                quoteItemKey = item.stackKey,
                quotePrice = null,
                quoteHistory = emptyList(),
                quoteLoading = true
            )
        }
        viewModelScope.launch {
            val freshAccount = ensureSteamSession(account)
            val price = runCatching {
                withContext(Dispatchers.IO) {
                    marketService.fetchPriceOverview(
                        appId = item.appId,
                        marketHashName = item.marketHashName,
                        currency = wallet.currency
                    )
                }
            }.getOrNull()
            val history = if (freshAccount == null) {
                emptyList()
            } else {
                runCatching {
                    withContext(Dispatchers.IO) {
                        marketService.fetchPriceHistory(
                            account = freshAccount,
                            appId = item.appId,
                            marketHashName = item.marketHashName
                        )
                    }
                }.getOrDefault(emptyList())
            }
            if (generation == marketQuoteGeneration) {
                updateInventoryMarket {
                    it.copy(quotePrice = price, quoteHistory = history, quoteLoading = false)
                }
            }
        }
    }

    fun clearMarketQuote() {
        marketQuoteGeneration++
        updateInventoryMarket {
            it.copy(
                quoteItemKey = null,
                quotePrice = null,
                quoteHistory = emptyList(),
                quoteLoading = false
            )
        }
    }

    fun loadBatchMarketQuotes(stacks: List<SteamInventoryItemStack>) {
        val account = selectedAccount() ?: return
        val targets = stacks
            .filter { it.item.marketable }
            .distinctBy { it.item.stackKey }
        if (targets.isEmpty()) return
        val wallet = _uiState.value.inventoryMarket.overview?.wallet ?: SteamWalletInfo.Fallback
        val generation = ++batchQuoteGeneration
        updateInventoryMarket {
            it.copy(
                batchQuotes = emptyMap(),
                batchQuoteLoading = true,
                batchQuoteCompleted = 0,
                batchQuoteTotal = targets.size,
                batchQuoteError = null
            )
        }
        viewModelScope.launch {
            val freshAccount = ensureSteamSession(account)
            if (freshAccount == null) {
                if (generation == batchQuoteGeneration) {
                    updateInventoryMarket {
                        it.copy(
                            batchQuoteLoading = false,
                            batchQuoteError = "Steam community session unavailable"
                        )
                    }
                }
                return@launch
            }
            targets.forEach { stack ->
                if (generation != batchQuoteGeneration) return@launch
                val quote = withContext(Dispatchers.IO) {
                    val price = runCatching {
                        marketService.fetchPriceOverview(
                            appId = stack.item.appId,
                            marketHashName = stack.item.marketHashName,
                            currency = wallet.currency
                        )
                    }.getOrNull()
                    val history = runCatching {
                        marketService.fetchPriceHistory(
                            account = freshAccount,
                            appId = stack.item.appId,
                            marketHashName = stack.item.marketHashName
                        )
                    }.getOrDefault(emptyList())
                    SteamMarketQuote(price = price, history = history)
                }
                if (generation == batchQuoteGeneration) {
                    updateInventoryMarket { state ->
                        val completed = state.batchQuoteCompleted + 1
                        state.copy(
                            batchQuotes = state.batchQuotes + (stack.item.stackKey to quote),
                            batchQuoteCompleted = completed,
                            batchQuoteLoading = completed < state.batchQuoteTotal
                        )
                    }
                }
            }
        }
    }

    fun clearBatchMarketQuotes() {
        batchQuoteGeneration++
        updateInventoryMarket {
            it.copy(
                batchQuotes = emptyMap(),
                batchQuoteLoading = false,
                batchQuoteCompleted = 0,
                batchQuoteTotal = 0,
                batchQuoteError = null
            )
        }
    }

    fun sellInventoryItems(
        stack: SteamInventoryItemStack,
        priceReceive: Int,
        quantity: Int,
        autoConfirm: Boolean
    ) {
        sellInventoryBatch(
            entries = listOf(
                SteamBatchSellEntry(
                    stack = stack,
                    priceReceive = priceReceive,
                    quantity = quantity
                )
            ),
            autoConfirm = autoConfirm
        )
    }

    fun sellInventoryBatch(
        entries: List<SteamBatchSellEntry>,
        autoConfirm: Boolean
    ) {
        val account = selectedAccount() ?: return
        val validEntries = entries.filter {
            it.stack.item.marketable &&
                it.priceReceive > 0 &&
                it.quantity > 0 &&
                it.stack.assetIds.isNotEmpty()
        }
        if (validEntries.isEmpty()) return
        updateInventoryMarket {
            it.copy(actionLoading = true, lastActionResult = null)
        }
        viewModelScope.launch {
            val outcome = runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException("Steam community session unavailable")
                withContext(Dispatchers.IO) {
                    val preExistingMarketIds = if (autoConfirm) {
                        runCatching {
                            confirmationService.fetch(freshAccount)
                                .filter { it.isMarketListingConfirmation() }
                                .map { it.id }
                                .toSet()
                        }.getOrNull()
                    } else {
                        null
                    }
                    val listedByStackKey = mutableMapOf<String, Int>()
                    var requiresConfirmation = false
                    var failureMessage: String? = null
                    validEntries.forEach { entry ->
                        val stackKey = entry.stack.item.stackKey
                        val allocations = allocateSteamInventorySale(
                            stack = entry.stack,
                            requestedQuantity = entry.quantity
                        )
                        allocations.forEach { allocation ->
                            runCatching {
                                marketService.sell(
                                    account = freshAccount,
                                    item = entry.stack.item,
                                    priceReceive = entry.priceReceive,
                                    assetId = allocation.assetId,
                                    amount = allocation.amount
                                )
                            }.onSuccess { result ->
                                if (result.success) {
                                    listedByStackKey[stackKey] =
                                        (listedByStackKey[stackKey] ?: 0) + allocation.amount
                                    requiresConfirmation =
                                        requiresConfirmation || result.requiresConfirmation
                                } else {
                                    failureMessage = result.message
                                }
                            }.onFailure { error ->
                                failureMessage = error.message
                            }
                        }
                    }

                    var autoConfirmed = false
                    if (
                        listedByStackKey.isNotEmpty() &&
                        requiresConfirmation &&
                        autoConfirm &&
                        preExistingMarketIds != null
                    ) {
                        var newConfirmations: List<SteamConfirmation> = emptyList()
                        for (attempt in 0..3) {
                            val latest = confirmationService.fetch(freshAccount)
                            newConfirmations = findNewSteamMarketConfirmations(
                                preExistingIds = preExistingMarketIds,
                                latest = latest
                            )
                            if (newConfirmations.isNotEmpty()) break
                            if (attempt < 3) delay(600L)
                        }
                        if (newConfirmations.isNotEmpty()) {
                            val response = confirmationService.respondMultiple(
                                account = freshAccount,
                                confirmations = newConfirmations,
                                accept = true
                            )
                            autoConfirmed = response.ok == newConfirmations.size
                        }
                    }
                    SteamSellOutcome(
                        listedByStackKey = listedByStackKey,
                        requiresConfirmation = requiresConfirmation,
                        autoConfirmed = autoConfirmed,
                        message = failureMessage
                    )
                }
            }
            outcome.onSuccess { sellOutcome ->
                val succeeded = sellOutcome.listedCount > 0
                updateInventoryMarket { marketState ->
                    val updatedStacks = if (succeeded) {
                        marketState.inventoryStacks.mapNotNull { existing ->
                            val listedCount = sellOutcome.listedByStackKey[existing.item.stackKey] ?: 0
                            if (listedCount <= 0) {
                                existing
                            } else {
                                removeSteamInventoryAmount(existing, listedCount)
                            }
                        }
                    } else {
                        marketState.inventoryStacks
                    }
                    marketState.copy(
                        inventoryStacks = updatedStacks,
                        listings = if (succeeded) emptyList() else marketState.listings,
                        listingsNextStart = if (succeeded) 0 else marketState.listingsNextStart,
                        listingsHasMore = if (succeeded) false else marketState.listingsHasMore,
                        actionLoading = false,
                        lastActionResult = SteamMarketActionResult(
                            action = SteamMarketActionType.SELL,
                            success = succeeded,
                            affectedCount = sellOutcome.listedCount,
                            requiresConfirmation = sellOutcome.requiresConfirmation,
                            autoConfirmed = sellOutcome.autoConfirmed,
                            message = sellOutcome.message
                        )
                    )
                }
                if (succeeded && sellOutcome.requiresConfirmation && !sellOutcome.autoConfirmed) {
                    refreshConfirmations(silent = true)
                }
            }.onFailure { error ->
                updateInventoryMarket {
                    it.copy(
                        actionLoading = false,
                        lastActionResult = SteamMarketActionResult(
                            action = SteamMarketActionType.SELL,
                            success = false,
                            message = error.message
                        )
                    )
                }
            }
        }
    }

    fun cancelMarketListing(listing: SteamMarketListing) {
        cancelMarketListings(listOf(listing))
    }

    fun cancelMarketListings(listings: List<SteamMarketListing>) {
        val account = selectedAccount() ?: return
        val targets = listings
            .filter { it.listingId.isNotBlank() }
            .distinctBy { it.listingId }
        if (targets.isEmpty()) return
        updateInventoryMarket { it.copy(actionLoading = true, lastActionResult = null) }
        viewModelScope.launch {
            val result = runCatching {
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException("Steam community session unavailable")
                withContext(Dispatchers.IO) {
                    val cancelledIds = mutableSetOf<String>()
                    var failedCount = 0
                    var firstError: String? = null
                    targets.forEach { listing ->
                        runCatching {
                            marketService.cancelListing(freshAccount, listing.listingId)
                        }.onSuccess { success ->
                            if (success) {
                                cancelledIds += listing.listingId
                            } else {
                                failedCount++
                            }
                        }.onFailure { error ->
                            failedCount++
                            if (firstError == null) firstError = error.message
                        }
                    }
                    SteamCancelListingsOutcome(
                        cancelledIds = cancelledIds,
                        failedCount = failedCount,
                        message = firstError
                    )
                }
            }
            result.onSuccess { outcome ->
                updateInventoryMarket {
                    it.copy(
                        listings = removeCancelledSteamMarketListings(
                            existing = it.listings,
                            cancelledListingIds = outcome.cancelledIds
                        ),
                        listingsTotal = (
                            it.listingsTotal - outcome.cancelledIds.size
                        ).coerceAtLeast(0),
                        actionLoading = false,
                        lastActionResult = SteamMarketActionResult(
                            action = SteamMarketActionType.CANCEL_LISTING,
                            success = outcome.cancelledIds.isNotEmpty() &&
                                outcome.failedCount == 0,
                            affectedCount = outcome.cancelledIds.size,
                            failedCount = outcome.failedCount,
                            message = outcome.message
                        )
                    )
                }
            }.onFailure { error ->
                updateInventoryMarket {
                    it.copy(
                        actionLoading = false,
                        lastActionResult = SteamMarketActionResult(
                            action = SteamMarketActionType.CANCEL_LISTING,
                            success = false,
                            failedCount = targets.size,
                            message = error.message
                        )
                    )
                }
            }
        }
    }

    fun consumeMarketActionResult() {
        updateInventoryMarket { it.copy(lastActionResult = null) }
    }

    fun refreshPendingLogins(silent: Boolean = false) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            if (!silent) setLoading(true)
            runCatching {
                account.loginApprovalUnavailableMessage()?.let { reason ->
                    throw IllegalStateException(reason)
                }
                val freshAccount = ensureSteamSession(account)
                    ?: throw IllegalStateException(account.loginApprovalUnavailableMessage())
                withContext(Dispatchers.IO) { loginApprovalService.pendingLogins(freshAccount) }
            }.onSuccess { pending ->
                val previousSeenTimes = _uiState.value.pendingLogins.associate {
                    it.clientId to it.detectedAtMillis
                }
                val now = System.currentTimeMillis()
                val pendingWithSeenTimes = pending.map { login ->
                    login.copy(detectedAtMillis = previousSeenTimes[login.clientId] ?: now)
                }
                _uiState.value = _uiState.value.copy(pendingLogins = pendingWithSeenTimes)
            }.onFailure { error ->
                if (!silent) setMessage(
                    error.message ?: appContext.getString(R.string.steam_cannot_refresh_logins)
                )
            }
            if (!silent) setLoading(false)
        }
    }

    fun refreshAuthorizedDevices(accountId: Long, silent: Boolean = false) {
        viewModelScope.launch {
            val storedAccount = accountById(accountId) ?: return@launch
            val account = ensureSteamSession(storedAccount)
            if (account == null || account.accessToken.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(authorizedDevices = emptyList())
                return@launch
            }
            if (!silent) setLoading(true)
            runCatching {
                withContext(Dispatchers.IO) { authorizedDeviceService.fetch(account) }
            }.onSuccess { devices ->
                if (_uiState.value.accounts.any { it.id == accountId }) {
                    _uiState.value = _uiState.value.copy(authorizedDevices = devices)
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(authorizedDevices = emptyList())
                if (!silent) setMessage(
                    error.message ?: appContext.getString(R.string.steam_cannot_refresh_authorized_devices)
                )
            }
            if (!silent) setLoading(false)
        }
    }

    fun revokeAuthorizedDevice(
        accountId: Long,
        device: SteamAuthorizedDevice,
        userName: String,
        password: String
    ) {
        viewModelScope.launch {
            val account = accountById(accountId) ?: return@launch
            setLoading(true)
            val result = withContext(Dispatchers.IO) {
                loginImportService.revokeAuthorizedDevice(
                    userName = userName,
                    password = password,
                    sharedSecret = account.sharedSecret,
                    tokenId = device.tokenId
                )
            }
            when (result) {
                SteamLoginImportService.AuthorizedDeviceRevokeResult.Success -> {
                    SteamDiagLogger.append(
                        "authorized_device_revoke success transport=auth_poll current=false"
                    )
                    setMessage(R.string.steam_done)
                    refreshAuthorizedDevices(accountId, silent = true)
                }
                is SteamLoginImportService.AuthorizedDeviceRevokeResult.Failure -> {
                    val safeReason = result.message
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .take(160)
                    SteamDiagLogger.append(
                        "authorized_device_revoke failed transport=auth_poll " +
                            "current=${device.isCurrent} reason=$safeReason"
                    )
                    setMessage(result.message)
                }
            }
            setLoading(false)
        }
    }

    fun respondPendingLogin(login: SteamPendingLogin, approve: Boolean) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            setLoading(true)
            val failureReason = account.loginApprovalUnavailableMessage()
            val ok = if (failureReason != null) {
                false
            } else runCatching {
                val freshAccount = ensureSteamSession(account) ?: return@runCatching false
                withContext(Dispatchers.IO) {
                    loginApprovalService.respondToSession(
                        account = freshAccount,
                        clientId = login.clientId,
                        version = login.version,
                        approve = approve
                    )
                }
            }.getOrDefault(false)
            setMessage(if (ok) appContext.getString(R.string.steam_done) else failureReason ?: appContext.getString(R.string.steam_login_response_failed))
            refreshPendingLogins(silent = true)
            setLoading(false)
        }
    }

    fun respondQr(rawQr: String, approve: Boolean) {
        val account = selectedAccount() ?: return
        val challenge = SteamQrChallenge.parse(rawQr)
        if (challenge == null) {
            setMessage(R.string.steam_invalid_qr_link)
            return
        }
        viewModelScope.launch {
            setLoading(true)
            val failureReason = account.loginApprovalUnavailableMessage()
            val ok = if (failureReason != null) {
                false
            } else runCatching {
                val freshAccount = ensureSteamSession(account) ?: return@runCatching false
                withContext(Dispatchers.IO) { loginApprovalService.respondToQr(freshAccount, challenge, approve) }
            }.getOrDefault(false)
            setMessage(if (ok) appContext.getString(R.string.steam_done) else failureReason ?: appContext.getString(R.string.steam_qr_response_failed))
            setLoading(false)
        }
    }

    private suspend fun saveLoginResultSafely(
        result: SteamLoginImportService.LoginResult.ReadyForImport,
        displayNameOverride: String? = pendingLoginDisplayName
    ): Int? {
        return runCatching {
            val message = saveLoginResult(result, displayNameOverride)
            pendingLoginCredentialEntryId?.let { entryId ->
                appContext.getSharedPreferences(
                    "steam_credential_bindings",
                    Context.MODE_PRIVATE
                ).edit()
                    .putLong("steam_${result.steamId}_password_entry_id", entryId)
                    .apply()
            }
            pendingLoginCredentialEntryId = null
            message
        }.getOrElse { error ->
            clearPendingLoginTarget()
            pendingLoginDisplayName = null
            pendingLoginCredentialEntryId = null
            setMessage(error.message ?: appContext.getString(R.string.steam_import_failed))
            null
        }
    }

    private suspend fun saveLoginResult(
        result: SteamLoginImportService.LoginResult.ReadyForImport,
        displayNameOverride: String? = pendingLoginDisplayName
    ): Int {
        val completionAccountId = pendingLoginCompletionAccountId
        if (completionAccountId != null) {
            val isRebind = pendingLoginRebindAccount
            val account = accountById(completionAccountId)
                ?: throw IllegalStateException(appContext.getString(R.string.steam_steamid_completion_missing_account))
            if (account.hasRealSteamId && !isRebind) {
                clearPendingLoginTarget()
                return R.string.steam_steamid_completion_done
            }
            val payload = if (result.payload.sessionOnly) {
                result.toSteamIdCompletionPayload(account)
            } else {
                parser.parseSteamGuardJson(
                    steamId = result.steamId,
                    deviceId = result.payload.deviceId,
                    steamGuardJson = result.payload.steamGuardJson,
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    displayNameOverride = displayNameOverride
                )
            }
            if (_uiState.value.accounts.any { it.id != account.id && it.steamId == payload.steamId }) {
                clearPendingLoginTarget()
                throw IllegalStateException(appContext.getString(R.string.steam_steamid_completion_duplicate))
            }
            saveCompletedSteamIdAccount(
                account = account,
                loginPayload = payload,
                replaceExistingBinding = isRebind
            )
            clearPendingLoginTarget()
            if (isRebind) {
                return R.string.steam_account_rebind_done
            }
            return if (account.identitySecret.isNullOrBlank() && payload.identitySecret.isNullOrBlank()) {
                R.string.steam_steamid_completion_code_only_done
            } else {
                R.string.steam_steamid_completion_done
            }
        }
        val payload = parser.parseSteamGuardJson(
            steamId = result.steamId,
            deviceId = result.payload.deviceId,
            steamGuardJson = result.payload.steamGuardJson,
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            displayNameOverride = displayNameOverride
        )
        saveMaFilePayload(payload)
        return R.string.steam_account_imported
    }

    private fun SteamLoginImportService.LoginResult.ReadyForImport.toSteamIdCompletionPayload(
        account: SteamAccount
    ): SteamMaFilePayload {
        val accountName = payload.accountName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: account.accountName
                .takeIf { it.isNotBlank() }
            ?: steamId
        return SteamMaFilePayload(
            steamId = steamId,
            accountName = accountName,
            displayName = account.displayName,
            deviceId = payload.deviceId,
            sharedSecret = account.sharedSecret,
            identitySecret = account.identitySecret,
            revocationCode = account.revocationCode,
            tokenGid = account.tokenGid,
            accessToken = accessToken,
            refreshToken = refreshToken,
            steamLoginSecure = accessToken.takeIf { it.isNotBlank() }?.let { "$steamId||$it" },
            rawJson = account.rawSteamGuardJson
        )
    }

    private suspend fun saveCompletedSteamIdAccount(
        account: SteamAccount,
        loginPayload: SteamMaFilePayload,
        replaceExistingBinding: Boolean = false
    ) {
        val remark = account.steamRemarkNameOrNull()
        val resolvedAccountName = loginPayload.accountName.ifBlank { account.accountName }
        val resolvedDeviceId = if (replaceExistingBinding) {
            loginPayload.deviceId.ifBlank { account.deviceId }
        } else {
            account.deviceId.ifBlank { loginPayload.deviceId }
        }
        val completedBase = account.copy(
            steamId = loginPayload.steamId,
            accountName = resolvedAccountName,
            displayName = remark
                ?: resolvedAccountName.ifBlank { loginPayload.steamId },
            deviceId = resolvedDeviceId,
            sharedSecret = account.sharedSecret,
            identitySecret = account.identitySecret ?: loginPayload.identitySecret,
            revocationCode = account.revocationCode ?: loginPayload.revocationCode,
            tokenGid = account.tokenGid ?: loginPayload.tokenGid,
            accessToken = loginPayload.accessToken,
            refreshToken = loginPayload.refreshToken,
            steamLoginSecure = loginPayload.steamLoginSecure,
            updatedAt = System.currentTimeMillis()
        )
        val completed = completedBase.copy(
            rawSteamGuardJson = SteamMaFileBackupCodec.encode(completedBase)
        )
        persistCompletedSteamIdAccount(completed)
    }

    private suspend fun persistCompletedSteamIdAccount(account: SteamAccount) {
        when (val source = _uiState.value.storageSource) {
            SteamStorageSource.Local -> withContext(Dispatchers.IO) {
                repository.replaceAccount(account)
            }
            is SteamStorageSource.Mdbx -> {
                val store = mdbxAccountStore
                    ?: throw IllegalStateException(appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
                val record = mdbxAccountRecords.firstOrNull { it.account.id == account.id }
                    ?: throw IllegalStateException(appContext.getString(R.string.steam_steamid_completion_missing_account))
                withContext(Dispatchers.IO) {
                    store.upsertAccount(
                        databaseId = source.databaseId,
                        entryId = record.entryId,
                        account = account
                    )
                }
                reloadMdbxAccounts(source, clearAccountScopedState = true)
            }
        }
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        }.orEmpty()
    }

    private fun SteamLoginImportService.LoginResult.ChallengeRequired.toChallengeUi(
        fallbackType: Int = 0
    ): SteamLoginChallengeUi {
        val codeChallenge = challenges.firstOrNull {
            SteamLoginImportService.isCodeChallengeType(it.confirmationType)
        }
        val pollingChallenge = challenges.firstOrNull {
            SteamLoginImportService.isPollingChallengeType(it.confirmationType)
        }
        val selectedChallenge = codeChallenge ?: pollingChallenge ?: challenges.firstOrNull()
        val selectedType = selectedChallenge?.confirmationType ?: fallbackType
        val pollingManualCodeType = pollingChallenge?.let {
            SteamLoginImportService.manualCodeTypeForPollingChallenge(it.confirmationType)
        }
        val confirmationType = pollingManualCodeType
            ?: codeChallenge?.confirmationType
            ?: selectedType
        val requiresCode = codeChallenge != null ||
            pollingManualCodeType != null ||
            SteamLoginImportService.isCodeChallengeType(confirmationType)
        val canPoll = pollingChallenge != null ||
            SteamLoginImportService.isPollingChallengeType(selectedType)
        val serverMessage = message
            ?: selectedChallenge?.associatedMessage?.takeIf { it.isNotBlank() }
        val challengeMessage = if (requiresCode && canPoll) {
            appContext.getString(R.string.steam_login_code_or_approve_message)
        } else {
            serverMessage
            ?: appContext.getString(
                when {
                    SteamLoginImportService.isAddAuthenticatorEmailActivationType(confirmationType) ->
                        R.string.steam_activation_email_message
                    SteamLoginImportService.isAddAuthenticatorActivationType(confirmationType) ->
                        R.string.steam_activation_sms_message
                    requiresCode && canPoll -> R.string.steam_login_code_or_approve_message
                    canPoll -> R.string.steam_login_waiting_approval
                    else -> R.string.steam_verification_required
                }
            )
        }
        return SteamLoginChallengeUi(
            pendingSessionId = pendingSessionId,
            steamId = steamId,
            confirmationType = confirmationType,
            message = challengeMessage,
            requiresCode = requiresCode,
            canPoll = canPoll,
            canUseMonicaCode = SteamLoginImportService.isSteamGuardCodeChallengeType(confirmationType)
        )
    }

    private fun handleLoginChallenge(
        challenge: SteamLoginImportService.LoginResult.ChallengeRequired,
        fallbackType: Int = 0,
        startPolling: Boolean = true
    ) {
        val challengeUi = challenge.toChallengeUi(fallbackType = fallbackType)
        _uiState.value = _uiState.value.copy(
            pendingLoginChallenge = challengeUi,
            pendingQrLoginChallenge = null
        )
        setMessage(challengeUi.message)
        if (startPolling && challengeUi.canPoll) {
            startPendingLoginPolling(challengeUi.pendingSessionId)
        }
    }

    private fun startPendingLoginPolling(pendingSessionId: String) {
        pendingLoginPollJob?.cancel()
        pendingLoginPollJob = viewModelScope.launch {
            repeat(40) {
                delay(3_000L)
                val result = withContext(Dispatchers.IO) {
                    loginImportService.pollPendingSession(pendingSessionId)
                }
                when (result) {
                    is SteamLoginImportService.LoginResult.ReadyForImport -> {
                        setLoading(true)
                        val successMessage = saveLoginResultSafely(result, pendingLoginDisplayName)
                        pendingLoginDisplayName = null
                        _uiState.value = _uiState.value.copy(
                            pendingLoginChallenge = null,
                            pendingQrLoginChallenge = null
                        )
                        successMessage?.let(::setMessage)
                        setLoading(false)
                        pendingLoginPollJob = null
                        return@launch
                    }
                    is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                        handleLoginChallenge(result, startPolling = false)
                    }
                    is SteamLoginImportService.LoginResult.Failure -> {
                        pendingLoginDisplayName = null
                        clearPendingLoginTarget()
                        setMessage(result.message)
                        pendingLoginPollJob = null
                        return@launch
                    }
                }
            }
            pendingLoginDisplayName = null
            clearPendingLoginTarget()
            setMessage(R.string.steam_login_approval_timeout)
            pendingLoginPollJob = null
        }
    }

    private fun startPendingQrLoginPolling(pendingSessionId: String) {
        pendingLoginPollJob?.cancel()
        pendingLoginPollJob = viewModelScope.launch {
            repeat(60) {
                delay(2_000L)
                when (val result = withContext(Dispatchers.IO) {
                    loginImportService.pollQrLoginSession(pendingSessionId)
                }) {
                    is SteamLoginImportService.QrLoginResult.ChallengeRequired -> {
                        _uiState.value = _uiState.value.copy(
                            pendingQrLoginChallenge = SteamQrLoginChallengeUi(
                                pendingSessionId = result.pendingSessionId,
                                challengeUrl = result.challengeUrl
                            )
                        )
                    }
                    is SteamLoginImportService.QrLoginResult.LoginChallengeRequired -> {
                        handleLoginChallenge(result.challenge)
                        pendingLoginPollJob = null
                        return@launch
                    }
                    is SteamLoginImportService.QrLoginResult.ReadyForImport -> {
                        setLoading(true)
                        val successMessage = saveLoginResultSafely(result.result, pendingLoginDisplayName)
                        pendingLoginDisplayName = null
                        _uiState.value = _uiState.value.copy(
                            pendingLoginChallenge = null,
                            pendingQrLoginChallenge = null
                        )
                        successMessage?.let(::setMessage)
                        setLoading(false)
                        pendingLoginPollJob = null
                        return@launch
                    }
                    is SteamLoginImportService.QrLoginResult.Failure -> {
                        pendingLoginDisplayName = null
                        clearPendingLoginTarget()
                        _uiState.value = _uiState.value.copy(pendingQrLoginChallenge = null)
                        setMessage(result.message)
                        pendingLoginPollJob = null
                        return@launch
                    }
                }
            }
            pendingLoginDisplayName = null
            clearPendingLoginTarget()
            _uiState.value = _uiState.value.copy(pendingQrLoginChallenge = null)
            setMessage(R.string.steam_login_approval_timeout)
            pendingLoginPollJob = null
        }
    }

    private fun selectRuntimeAccount(id: Long) {
        val state = _uiState.value
        val accounts = state.accounts.map { account ->
            account.copy(selected = account.id == id)
        }
        mdbxAccountRecords = mdbxAccountRecords.map { record ->
            record.copy(account = record.account.copy(selected = record.account.id == id))
        }
        _uiState.value = state.copy(
            accounts = accounts,
            selectedAccountId = accounts.firstOrNull { it.id == id }?.id ?: accounts.firstOrNull()?.id,
            confirmations = emptyList(),
            pendingLogins = emptyList(),
            authorizedDevices = emptyList(),
            selectedConfirmationIds = emptySet(),
            notifications = SteamNotificationsUiState(),
            inventoryMarket = SteamInventoryMarketUiState(),
            tradeOffers = SteamTradeOffersUiState()
        )
    }

    private fun applyListingsPage(page: SteamMarketListingsPage, append: Boolean) {
        updateInventoryMarket {
            val listings = if (append) {
                (it.listings + page.items).distinctBy { listing -> listing.listingId }
            } else {
                page.items
            }
            it.copy(
                listings = listings,
                listingAnalysis = SteamMarketAnalytics.listings(listings),
                listingsTotal = page.totalActive,
                listingsNextStart = page.nextStart,
                listingsHasMore = page.hasMore,
                listingsLoading = false,
                listingsLoadingMore = false,
                listingsError = null
            )
        }
    }

    private fun updateInventoryMarket(
        transform: (SteamInventoryMarketUiState) -> SteamInventoryMarketUiState
    ) {
        _uiState.value = _uiState.value.copy(
            inventoryMarket = transform(_uiState.value.inventoryMarket)
        )
    }

    private fun updateTradeOffers(
        transform: (SteamTradeOffersUiState) -> SteamTradeOffersUiState
    ) {
        _uiState.value = _uiState.value.copy(
            tradeOffers = transform(_uiState.value.tradeOffers)
        )
    }

    private fun updateNotifications(
        transform: (SteamNotificationsUiState) -> SteamNotificationsUiState
    ) {
        _uiState.value = _uiState.value.copy(
            notifications = transform(_uiState.value.notifications)
        )
    }

    private suspend fun saveMaFilePayload(payload: SteamMaFilePayload) {
        when (val source = _uiState.value.storageSource) {
            SteamStorageSource.Local -> withContext(Dispatchers.IO) {
                repository.upsertFromMaFile(payload)
            }
            is SteamStorageSource.Mdbx -> {
                val store = mdbxAccountStore
                    ?: throw IllegalStateException(appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
                withContext(Dispatchers.IO) {
                    store.upsertPayload(source.databaseId, payload)
                }
                reloadMdbxAccounts(source)
            }
        }
    }

    private suspend fun writeAccountsToStorageSource(
        accounts: List<SteamAccount>,
        targetSource: SteamStorageSource
    ) {
        when (targetSource) {
            SteamStorageSource.Local -> {
                accounts.forEach { account ->
                    repository.upsertFromMaFile(account.toCompleteMaFilePayload())
                }
            }
            is SteamStorageSource.Mdbx -> {
                val store = mdbxAccountStore
                    ?: throw IllegalStateException(appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
                val existingBySteamId = store.loadAccounts(targetSource.databaseId)
                    .associateBy { it.account.steamId }
                accounts.forEach { account ->
                    store.upsertAccount(
                        databaseId = targetSource.databaseId,
                        entryId = existingBySteamId[account.steamId]?.entryId,
                        account = account
                    )
                }
            }
        }
    }

    private suspend fun deleteAccountsFromStorageSource(
        source: SteamStorageSource,
        accountIds: List<Long>
    ) {
        when (source) {
            SteamStorageSource.Local -> {
                accountIds.forEach { accountId -> repository.delete(accountId) }
            }
            is SteamStorageSource.Mdbx -> {
                val store = mdbxAccountStore
                    ?: throw IllegalStateException(appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
                val selectedIds = accountIds.toSet()
                val entryIds = mdbxAccountRecords
                    .filter { it.account.id in selectedIds }
                    .map { it.entryId }
                entryIds.forEach { entryId ->
                    store.deleteAccount(source.databaseId, entryId)
                }
            }
        }
    }

    private fun SteamAccount.toCompleteMaFilePayload(): SteamMaFilePayload {
        val maFileJson = SteamMaFileBackupCodec.encode(this)
        return parser.parse(
            maFileContent = maFileJson,
            fileName = SteamMaFileBackupCodec.fileName(this),
            displayNameOverride = displayName.takeIf { it.isNotBlank() },
            steamIdOverride = visibleSteamId.takeIf { it.isNotBlank() },
            allowMissingSteamId = !hasRealSteamId
        )
    }

    private suspend fun deleteAccountByActiveSource(accountId: Long) {
        when (val source = _uiState.value.storageSource) {
            SteamStorageSource.Local -> {
                withContext(Dispatchers.IO) { repository.delete(accountId) }
                _uiState.value = _uiState.value.copy(
                    confirmations = emptyList(),
                    pendingLogins = emptyList(),
                    authorizedDevices = emptyList(),
                    selectedConfirmationIds = emptySet(),
                    notifications = SteamNotificationsUiState(),
                    inventoryMarket = SteamInventoryMarketUiState(),
                    tradeOffers = SteamTradeOffersUiState()
                )
            }
            is SteamStorageSource.Mdbx -> {
                val store = mdbxAccountStore ?: return
                val entryId = mdbxAccountRecords.firstOrNull { it.account.id == accountId }?.entryId
                    ?: return
                withContext(Dispatchers.IO) {
                    store.deleteAccount(source.databaseId, entryId)
                }
                reloadMdbxAccounts(source, clearAccountScopedState = true)
            }
        }
    }

    private suspend fun reloadMdbxAccounts(
        source: SteamStorageSource.Mdbx,
        clearAccountScopedState: Boolean = false
    ) {
        val store = mdbxAccountStore
            ?: throw IllegalStateException(appContext.getString(R.string.steam_cannot_load_mdbx_accounts))
        val records = withContext(Dispatchers.IO) {
            store.loadAccounts(source.databaseId)
        }
        mdbxAccountRecords = records
        updateForAccounts(
            accounts = records.map { it.account },
            nowMillis = System.currentTimeMillis(),
            storageSource = source,
            clearAccountScopedState = clearAccountScopedState
        )
    }

    private suspend fun persistRefreshedSession(
        originalAccount: SteamAccount,
        refreshedAccount: SteamAccount
    ) {
        when (val source = _uiState.value.storageSource) {
            SteamStorageSource.Local -> {
                repository.updateSessionTokens(
                    id = originalAccount.id,
                    accessToken = refreshedAccount.accessToken.orEmpty(),
                    refreshToken = refreshedAccount.refreshToken,
                    steamLoginSecure = refreshedAccount.steamLoginSecure
                )
            }
            is SteamStorageSource.Mdbx -> {
                val store = mdbxAccountStore ?: return
                val record = mdbxAccountRecords.firstOrNull { it.account.id == originalAccount.id }
                    ?: return
                val updatedRecord = store.upsertAccount(
                    databaseId = source.databaseId,
                    entryId = record.entryId,
                    account = refreshedAccount
                )
                mdbxAccountRecords = mdbxAccountRecords.map { existing ->
                    if (existing.entryId == record.entryId) updatedRecord else existing
                }
            }
        }
    }

    private fun accountById(accountId: Long): SteamAccount? {
        return _uiState.value.accounts.firstOrNull { it.id == accountId }
            ?: mdbxAccountRecords.firstOrNull { it.account.id == accountId }?.account
    }

    private fun selectedAccount(): SteamAccount? {
        val state = _uiState.value
        return state.accounts.firstOrNull { it.id == state.selectedAccountId }
            ?: state.accounts.firstOrNull()
    }

    private suspend fun ensureSteamSession(account: SteamAccount): SteamAccount? = withContext(Dispatchers.IO) {
        if (!account.hasRealSteamId) {
            SteamDiagLogger.append("session_refresh skipped missing_steamid")
            return@withContext null
        }
        if (account.accessToken.isNullOrBlank() && account.refreshToken.isNullOrBlank()) {
            SteamDiagLogger.append("session_refresh skipped no_tokens")
            return@withContext null
        }
        if (!sessionRefreshService.shouldRefresh(account)) {
            return@withContext account
        }
        val refreshResult = sessionRefreshService.refreshIfNeeded(account)
        if (refreshResult == null) {
            SteamDiagLogger.append("session_refresh failed refresh_token_present=${!account.refreshToken.isNullOrBlank()}")
            if (account.accessToken.isNullOrBlank()) null else account
        } else {
            val refreshedAccount = account.copy(
                accessToken = refreshResult.accessToken,
                refreshToken = refreshResult.refreshToken ?: account.refreshToken,
                steamLoginSecure = "${account.steamId}||${refreshResult.accessToken}"
            )
            persistRefreshedSession(account, refreshedAccount)
            _uiState.value = _uiState.value.copy(
                accounts = _uiState.value.accounts.map { existing ->
                    if (existing.id == refreshedAccount.id) refreshedAccount else existing
                }
            )
            SteamDiagLogger.append("session_refresh success refresh_rotated=${!refreshResult.refreshToken.isNullOrBlank()}")
            refreshedAccount
        }
    }

    private fun updateForAccounts(
        accounts: List<SteamAccount>,
        nowMillis: Long,
        storageSource: SteamStorageSource = _uiState.value.storageSource,
        clearAccountScopedState: Boolean = false
    ) {
        val previous = _uiState.value
        val previousSelected = previous.selectedAccountId
        val selected = accounts.firstOrNull { it.id == previousSelected }
            ?: accounts.firstOrNull { it.selected }
            ?: accounts.firstOrNull()
        val selectedChanged = previous.selectedAccountId != selected?.id
        val nowSeconds = nowMillis / 1000L
        _uiState.value = previous.copy(
            storageSource = storageSource,
            accounts = accounts,
            selectedAccountId = selected?.id,
            currentCode = selected?.let { SteamTotp.generateAuthCode(it.sharedSecret, nowSeconds) }.orEmpty(),
            secondsRemaining = secondsRemaining(nowMillis),
            periodProgress = periodProgress(nowMillis),
            confirmations = if (selectedChanged || clearAccountScopedState) emptyList() else previous.confirmations,
            pendingLogins = if (selectedChanged || clearAccountScopedState) emptyList() else previous.pendingLogins,
            authorizedDevices = if (selectedChanged || clearAccountScopedState) emptyList() else previous.authorizedDevices,
            selectedConfirmationIds = if (selectedChanged || clearAccountScopedState) emptySet() else previous.selectedConfirmationIds,
            notifications = if (selectedChanged || clearAccountScopedState) {
                SteamNotificationsUiState()
            } else {
                previous.notifications
            },
            inventoryMarket = if (selectedChanged || clearAccountScopedState) {
                SteamInventoryMarketUiState()
            } else {
                previous.inventoryMarket
            },
            tradeOffers = if (selectedChanged || clearAccountScopedState) {
                SteamTradeOffersUiState()
            } else {
                previous.tradeOffers
            }
        )
    }

    private fun updateCodeTick(nowMillis: Long) {
        val account = selectedAccount()
        val nowSeconds = nowMillis / 1000L
        _uiState.value = _uiState.value.copy(
            currentCode = account?.let { SteamTotp.generateAuthCode(it.sharedSecret, nowSeconds) }.orEmpty(),
            secondsRemaining = secondsRemaining(nowMillis),
            periodProgress = periodProgress(nowMillis)
        )
    }

    private fun secondsRemaining(nowMillis: Long): Int {
        val remainingMillis = CODE_PERIOD_MS - Math.floorMod(nowMillis, CODE_PERIOD_MS)
        return ((remainingMillis + 999L) / 1000L).toInt().coerceIn(1, 30)
    }

    private fun periodProgress(nowMillis: Long): Float {
        val remainingMillis = CODE_PERIOD_MS - Math.floorMod(nowMillis, CODE_PERIOD_MS)
        return (remainingMillis.toFloat() / CODE_PERIOD_MS.toFloat()).coerceIn(0f, 1f)
    }

    private fun SteamAccount.steamRemarkNameOrNull(): String? {
        val remark = displayName.trim()
        val accountLabel = accountName.ifBlank { visibleSteamId }.trim()
        return remark.takeIf {
            it.isNotBlank() &&
                it != accountLabel &&
                it != steamId
        }
    }

    private fun setLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(loading = loading)
    }

    private fun clearPendingLoginTarget() {
        pendingLoginCompletionAccountId = null
        pendingLoginRebindAccount = false
        pendingLoginCredentialEntryId = null
    }

    private fun SteamAccount.loginApprovalUnavailableMessage(): String? {
        return when {
            !hasRealSteamId -> appContext.getString(R.string.steam_no_login_missing_steamid)
            sharedSecret.isBlank() -> appContext.getString(R.string.steam_no_login_missing_shared_secret)
            accessToken.isNullOrBlank() && refreshToken.isNullOrBlank() ->
                appContext.getString(R.string.steam_no_login_missing_session_detail)
            else -> null
        }
    }

    private fun SteamAccount.confirmationUnavailableMessage(): String? {
        return when {
            !hasRealSteamId -> appContext.getString(R.string.steam_no_confirmation_missing_steamid)
            identitySecret.isNullOrBlank() -> appContext.getString(R.string.steam_no_confirmation_missing_identity_secret)
            accessToken.isNullOrBlank() && refreshToken.isNullOrBlank() ->
                appContext.getString(R.string.steam_no_confirmation_missing_session_detail)
            else -> null
        }
    }

    private suspend fun recordConfirmationEvent(
        account: SteamAccount,
        severity: SteamSecurityEventSeverity,
        summary: String,
        detail: String? = null
    ) {
        val eventRepository = securityEventRepository ?: return
        runCatching {
            eventRepository.record(
                accountId = _uiState.value.storageSource
                    .takeIf { it is SteamStorageSource.Local }
                    ?.let { account.id },
                type = SteamSecurityEventType.CONFIRMATION_ACTION,
                severity = severity,
                summary = summary,
                detail = detail
            )
        }
    }

    private fun setMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    private fun setMessage(@StringRes resId: Int, vararg formatArgs: Any) {
        _uiState.value = _uiState.value.copy(message = appContext.getString(resId, *formatArgs))
    }

    companion object {
        private const val CODE_PERIOD_MS = 30_000L
        private const val CODE_TICK_INTERVAL_MS = 250L
        private const val CONFIRMATION_HISTORY_LIMIT = 50

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    val passwordDatabase = PasswordDatabase.getDatabase(appContext)
                    val securityManager = SecurityManager(appContext)
                    val mdbxRepository = MdbxVaultStore(
                        context = appContext,
                        databaseDao = passwordDatabase.localMdbxDatabaseDao(),
                        securityManager = securityManager,
                        remoteSourceDao = passwordDatabase.mdbxRemoteSourceDao(),
                        passwordEntryDao = passwordDatabase.passwordEntryDao(),
                        secureItemDao = passwordDatabase.secureItemDao(),
                        customFieldDao = passwordDatabase.customFieldDao()
                    )
                    return SteamViewModel(
                        appContext = appContext,
                        repository = SteamAccountRepository(database.steamAccountDao(), securityManager),
                        mdbxRepository = mdbxRepository,
                        securityEventRepository = SteamSecurityEventRepository(
                            database.steamSecurityEventDao(),
                            securityManager
                        )
                    ) as T
                }
            }
        }
    }
}
