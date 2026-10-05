package takagi.ru.monica.steam.data

import android.content.Context
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxSourceType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.MdbxRepositoryFactory
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.scanner.data.readSteamStorageSource
import takagi.ru.monica.steam.scanner.data.saveSteamStorageSource
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.data.SteamAccountSourceSessionStore
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

data class SteamAccountSourceState(
    val storageSource: SteamStorageSource = SteamStorageSource.Local,
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * One source-aware account state shared by Store, Library and future account consumers.
 * It keeps local Room accounts and MDBX maFile records behind the same selection API.
 */
class SteamAccountSourceRepository private constructor(
    private val appContext: Context,
    private val localRepository: SteamAccountRepository,
    private val mdbxAccountStore: SteamMdbxAccountStore,
    passwordDatabase: PasswordDatabase
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseDao = passwordDatabase.localMdbxDatabaseDao()
    private val _state = MutableStateFlow(
        SteamAccountSourceState(storageSource = readSteamStorageSource(appContext))
    )
    val state: StateFlow<SteamAccountSourceState> = _state.asStateFlow()

    private var localAccounts: List<SteamAccount> = emptyList()
    private var mdbxRecords: List<SteamMdbxAccountRecord> = emptyList()
    private var sourceLoadGeneration = 0L
    private val accountOrigins = ConcurrentHashMap<Long, SteamAccountSessionOrigin>()

    val sessionManager: SteamAccountSessionManager by lazy {
        SteamAccountSessionManager(
            store = SteamAccountSourceSessionStore(this)
        )
    }

    init {
        scope.launch {
            localRepository.observeAccounts().collect { accounts ->
                localAccounts = accounts
                if (_state.value.storageSource is SteamStorageSource.Local) {
                    publishLocalAccounts(accounts)
                }
            }
        }
        scope.launch {
            databaseDao.getAvailableDatabases().collect { databases ->
                val supported = databases.filter(LocalMdbxDatabase::supportsSteamAccounts)
                val currentSource = _state.value.storageSource
                _state.update { it.copy(mdbxDatabases = supported) }
                if (
                    currentSource is SteamStorageSource.Mdbx &&
                    supported.none { it.id == currentSource.databaseId }
                ) {
                    selectStorageSource(SteamStorageSource.Local)
                }
            }
        }
        when (val source = _state.value.storageSource) {
            SteamStorageSource.Local -> publishLocalAccounts(localAccounts)
            is SteamStorageSource.Mdbx -> loadMdbxAccounts(source)
        }
    }

    fun selectStorageSource(source: SteamStorageSource) {
        if (_state.value.storageSource == source) return
        sourceLoadGeneration++
        saveSteamStorageSource(appContext, source)
        when (source) {
            SteamStorageSource.Local -> {
                mdbxRecords = emptyList()
                _state.update {
                    it.copy(
                        storageSource = source,
                        loading = false,
                        errorMessage = null
                    )
                }
                publishLocalAccounts(localAccounts)
            }
            is SteamStorageSource.Mdbx -> {
                _state.update {
                    it.copy(
                        storageSource = source,
                        accounts = emptyList(),
                        selectedAccountId = null,
                        loading = true,
                        errorMessage = null
                    )
                }
                loadMdbxAccounts(source)
            }
        }
    }

    fun refreshCurrentSource() {
        when (val source = _state.value.storageSource) {
            SteamStorageSource.Local -> publishLocalAccounts(localAccounts)
            is SteamStorageSource.Mdbx -> loadMdbxAccounts(source)
        }
    }

    fun selectAccount(accountId: Long) {
        val current = _state.value
        if (current.accounts.none { it.id == accountId }) return
        if (current.selectedAccountId == accountId) return
        _state.update { it.copy(selectedAccountId = accountId) }
        if (current.storageSource is SteamStorageSource.Local) {
            scope.launch { localRepository.select(accountId) }
        }
    }

    suspend fun updateSessionTokens(
        id: Long,
        accessToken: String,
        refreshToken: String?,
        steamLoginSecure: String?
    ) {
        val origin = accountOrigins[id] ?: currentOriginFor(id) ?: return
        updateSessionTokens(
            origin = origin,
            id = id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            steamLoginSecure = steamLoginSecure
        )
    }

    /**
     * Writes rotated credentials to the source captured when the request began.
     * This remains correct if the active account/database changes meanwhile.
     */
    suspend fun updateSessionTokens(
        origin: SteamAccountSessionOrigin,
        id: Long,
        accessToken: String,
        refreshToken: String?,
        steamLoginSecure: String?
    ) {
        when (val source = origin.source) {
            SteamStorageSource.Local -> localRepository.updateSessionTokens(
                id = id,
                accessToken = accessToken,
                refreshToken = refreshToken,
                steamLoginSecure = steamLoginSecure
            )
            is SteamStorageSource.Mdbx -> {
                val record = mdbxRecords.firstOrNull {
                    it.account.id == id && it.entryId == origin.entryId
                } ?: runCatching {
                    mdbxAccountStore.loadAccounts(source.databaseId).firstOrNull {
                        it.account.id == id && it.entryId == origin.entryId
                    }
                }.getOrNull() ?: return
                val updatedAccount = record.account.copy(
                    accessToken = accessToken,
                    refreshToken = refreshToken ?: record.account.refreshToken,
                    steamLoginSecure = steamLoginSecure ?: record.account.steamLoginSecure,
                    updatedAt = System.currentTimeMillis()
                )
                val updatedRecord = mdbxAccountStore.upsertAccount(
                    databaseId = source.databaseId,
                    entryId = record.entryId,
                    account = updatedAccount
                )
                accountOrigins[id] = origin
                if (_state.value.storageSource == source) {
                    mdbxRecords = mdbxRecords
                        .filterNot { existing -> existing.entryId == record.entryId }
                        .plus(updatedRecord)
                    _state.update { current ->
                        current.copy(
                            accounts = current.accounts.map { account ->
                                if (account.id == id) updatedRecord.account else account
                            }
                        )
                    }
                }
            }
        }
    }

    fun sessionHandle(account: SteamAccount): SteamAccountSessionHandle? {
        val origin = accountOrigins[account.id] ?: currentOriginFor(account.id) ?: return null
        return SteamAccountSessionHandle(account = account, origin = origin)
    }

    /**
     * Resolves a handle from the visible source without consulting the
     * cross-source id cache. This is required by background work and deep
     * links because MDBX runtime ids and Room ids occupy different namespaces.
     */
    fun sessionHandleForSource(
        account: SteamAccount,
        source: SteamStorageSource
    ): SteamAccountSessionHandle? {
        val current = _state.value
        if (current.storageSource != source) return null
        val visibleAccount = current.accounts.firstOrNull { candidate ->
            candidate.id == account.id && candidate.steamId == account.steamId
        } ?: return null
        val origin = when (source) {
            SteamStorageSource.Local -> SteamAccountSessionOrigin(SteamStorageSource.Local)
            is SteamStorageSource.Mdbx -> mdbxRecords.firstOrNull { record ->
                record.account.id == visibleAccount.id &&
                    record.account.steamId == visibleAccount.steamId
            }?.let { record ->
                SteamAccountSessionOrigin(source = source, entryId = record.entryId)
            } ?: return null
        }
        accountOrigins[visibleAccount.id] = origin
        return SteamAccountSessionHandle(account = visibleAccount, origin = origin)
    }

    suspend fun resolveSession(
        account: SteamAccount,
        forceRefresh: Boolean = false
    ): SteamAccount {
        val handle = sessionHandle(account) ?: return account
        return sessionManager.resolve(handle, forceRefresh).account
    }

    /**
     * Creates the feature boundary used by chat and other social consumers.
     * The handle is captured immediately before each refresh, so a later
     * database/account selection change cannot redirect the write-back.
     */
    fun sessionResolver(): SteamAccountSessionResolver =
        SteamAccountSessionResolver { account, forceRefresh ->
            sessionHandle(account)?.let { handle ->
                sessionManager.resolve(handle, forceRefresh).account
            } ?: account
        }

    /**
     * Takes an immutable background-work snapshot across Room and every
     * configured Steam-capable MDBX source.  Each handle carries the exact
     * origin that owns any future token rotation.
     */
    suspend fun loadAllSessionHandles(): List<SteamAccountSessionHandle> {
        val localHandles = localRepository.observeAccounts().first().map { account ->
            SteamAccountSessionHandle(
                account = account,
                origin = SteamAccountSessionOrigin(SteamStorageSource.Local)
            ).also { handle -> accountOrigins[account.id] = handle.origin }
        }
        val mdbxHandles = databaseDao.getAvailableDatabases().first()
            .filter(LocalMdbxDatabase::supportsSteamAccounts)
            .flatMap { database ->
                runCatching { mdbxAccountStore.loadAccounts(database.id) }
                    .getOrDefault(emptyList())
                    .map { record ->
                        SteamAccountSessionHandle(
                            account = record.account,
                            origin = SteamAccountSessionOrigin(
                                source = SteamStorageSource.Mdbx(database.id),
                                entryId = record.entryId
                            )
                        ).also { handle -> accountOrigins[record.account.id] = handle.origin }
                    }
            }
        return (localHandles + mdbxHandles).distinctBy(SteamAccountSessionHandle::stableKey)
    }

    private fun publishLocalAccounts(accounts: List<SteamAccount>) {
        accounts.forEach { account ->
            accountOrigins[account.id] = SteamAccountSessionOrigin(SteamStorageSource.Local)
        }
        val previousId = _state.value.selectedAccountId
        val selected = accounts.firstOrNull { it.id == previousId }
            ?: accounts.firstOrNull(SteamAccount::selected)
            ?: accounts.firstOrNull()
        _state.update { current ->
            if (current.storageSource !is SteamStorageSource.Local) current
            else current.copy(
                accounts = accounts,
                selectedAccountId = selected?.id,
                loading = false,
                errorMessage = null
            )
        }
    }

    private fun loadMdbxAccounts(source: SteamStorageSource.Mdbx) {
        val generation = ++sourceLoadGeneration
        _state.update { current ->
            if (current.storageSource != source) current
            else current.copy(loading = true, errorMessage = null)
        }
        scope.launch {
            runCatching { mdbxAccountStore.loadAccounts(source.databaseId) }
                .onSuccess { records ->
                    if (generation != sourceLoadGeneration || _state.value.storageSource != source) {
                        return@onSuccess
                    }
                    mdbxRecords = records
                    records.forEach { record ->
                        accountOrigins[record.account.id] = SteamAccountSessionOrigin(
                            source = source,
                            entryId = record.entryId
                        )
                    }
                    val previousId = _state.value.selectedAccountId
                    val selected = records.firstOrNull { it.account.id == previousId }?.account
                        ?: records.firstOrNull { it.account.selected }?.account
                        ?: records.firstOrNull()?.account
                    _state.update {
                        it.copy(
                            accounts = records.map(SteamMdbxAccountRecord::account),
                            selectedAccountId = selected?.id,
                            loading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != sourceLoadGeneration || _state.value.storageSource != source) {
                        return@onFailure
                    }
                    mdbxRecords = emptyList()
                    _state.update {
                        it.copy(
                            accounts = emptyList(),
                            selectedAccountId = null,
                            loading = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    override fun close() = Unit

    private fun currentOriginFor(id: Long): SteamAccountSessionOrigin? {
        return when (val source = _state.value.storageSource) {
            SteamStorageSource.Local -> localAccounts.firstOrNull { it.id == id }
                ?.let { SteamAccountSessionOrigin(SteamStorageSource.Local) }
            is SteamStorageSource.Mdbx -> mdbxRecords.firstOrNull { it.account.id == id }
                ?.let {
                    SteamAccountSessionOrigin(
                        source = source,
                        entryId = it.entryId
                    )
                }
        }
    }

    companion object {
        @Volatile
        private var instance: SteamAccountSourceRepository? = null

        fun get(context: Context): SteamAccountSourceRepository = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(appContext: Context): SteamAccountSourceRepository {
            val steamDatabase = SteamDatabase.getDatabase(appContext)
            val passwordDatabase = PasswordDatabase.getDatabase(appContext)
            val securityManager = SecurityManager(appContext)
            val mdbxRepository = MdbxRepositoryFactory.create(
                context = appContext,
                database = passwordDatabase,
                securityManager = securityManager
            )
            return SteamAccountSourceRepository(
                appContext = appContext,
                localRepository = SteamAccountRepository(
                    steamDatabase.steamAccountDao(),
                    securityManager
                ),
                mdbxAccountStore = SteamMdbxAccountStore(mdbxRepository),
                passwordDatabase = passwordDatabase
            )
        }
    }
}

private fun LocalMdbxDatabase.supportsSteamAccounts(): Boolean =
    sourceTypeEnum == MdbxSourceType.LOCAL_INTERNAL ||
        sourceTypeEnum == MdbxSourceType.LOCAL_EXTERNAL ||
        sourceTypeEnum == MdbxSourceType.REMOTE_WEBDAV ||
        sourceTypeEnum == MdbxSourceType.REMOTE_ONEDRIVE
