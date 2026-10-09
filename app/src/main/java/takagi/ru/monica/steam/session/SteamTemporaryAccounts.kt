package takagi.ru.monica.steam.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.importer.SteamMaFilePayload

/** Process-local accounts. No Context, preferences, database, or serialization dependency. */
class SteamTemporaryAccounts {
    private val mutableAccounts = MutableStateFlow<List<SteamAccount>>(emptyList())
    val accounts = mutableAccounts.asStateFlow()
    // Avoid reusing a previous process's cache key for an unrelated temporary account.
    private var nextId = (java.util.UUID.randomUUID().mostSignificantBits ushr 1) or (1L shl 62)

    @Synchronized
    fun add(payload: SteamMaFilePayload): SteamAccount {
        require(payload.sharedSecret.isBlank() && payload.identitySecret.isNullOrBlank())
        val account = SteamAccount(
            id = nextId--, steamId = payload.steamId, accountName = payload.accountName,
            displayName = payload.displayName, deviceId = payload.deviceId, sharedSecret = "",
            identitySecret = null, revocationCode = null, tokenGid = null,
            accessToken = payload.accessToken, refreshToken = payload.refreshToken,
            steamLoginSecure = payload.steamLoginSecure, rawSteamGuardJson = "{}",
            selected = true, sortOrder = 0, createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(), isTemporary = true
        )
        mutableAccounts.value = mutableAccounts.value.map { it.copy(selected = false) } + account
        return account
    }

    fun get(id: Long): SteamAccount? = accounts.value.firstOrNull { it.id == id }

    @Synchronized
    fun select(id: Long) { mutableAccounts.value = accounts.value.map { it.copy(selected = it.id == id) } }

    @Synchronized
    fun update(id: Long, change: (SteamAccount) -> SteamAccount) {
        mutableAccounts.value = accounts.value.map { account ->
            if (account.id != id) account else change(account).also {
                require(it.sharedSecret.isBlank() && it.identitySecret.isNullOrBlank())
            }.copy(id = id, steamId = account.steamId, isTemporary = true, rawSteamGuardJson = "{}")
        }
    }

    @Synchronized
    fun remove(id: Long) { mutableAccounts.value = accounts.value.filterNot { it.id == id } }

    @Synchronized
    fun clear() { mutableAccounts.value = emptyList() }

    companion object { val shared = SteamTemporaryAccounts() }
}
