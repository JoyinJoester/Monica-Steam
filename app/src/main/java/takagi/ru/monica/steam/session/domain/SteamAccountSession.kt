package takagi.ru.monica.steam.session.domain

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource

/** Identifies the storage location that owns an account's session tokens. */
data class SteamAccountSessionOrigin(
    val source: SteamStorageSource,
    val entryId: String? = null
) {
    init {
        when (source) {
            SteamStorageSource.Local -> require(entryId == null) {
                "Room accounts cannot carry an MDBX entry id"
            }
            is SteamStorageSource.Mdbx -> require(!entryId.isNullOrBlank()) {
                "MDBX accounts require their source entry id"
            }
        }
    }

    val stableKey: String
        get() = when (val current = source) {
            SteamStorageSource.Local -> "room"
            is SteamStorageSource.Mdbx -> "mdbx:${current.databaseId}:$entryId"
        }
}

/** Account plus the immutable source identity used for session persistence. */
data class SteamAccountSessionHandle(
    val account: SteamAccount,
    val origin: SteamAccountSessionOrigin
) {
    val stableKey: String
        get() = "${origin.stableKey}|${account.id}|${account.steamId}"
}

data class SteamSessionTokens(
    val accessToken: String,
    val refreshToken: String?
)

data class SteamSessionResolution(
    val account: SteamAccount,
    val refreshed: Boolean,
    val refreshAttempted: Boolean
)

/**
 * Feature-facing session boundary.  Features receive an account value only;
 * the implementation captures its storage origin before refreshing and owns
 * persistence of rotated credentials.
 */
fun interface SteamAccountSessionResolver {
    suspend fun resolve(account: SteamAccount, forceRefresh: Boolean): SteamAccount
}

/** Keeps deterministic tests and read-only callers usable without a session store. */
suspend fun SteamAccountSessionResolver?.resolveOrKeep(
    account: SteamAccount,
    forceRefresh: Boolean = false
): SteamAccount = this?.resolve(account, forceRefresh) ?: account

/** Network-facing abstraction kept injectable for deterministic JVM tests. */
interface SteamAccountSessionRefresher {
    fun shouldRefresh(account: SteamAccount, nowSeconds: Long): Boolean

    suspend fun refresh(
        account: SteamAccount,
        force: Boolean
    ): SteamSessionTokens?
}

fun interface SteamAccountSessionStore {
    suspend fun persist(handle: SteamAccountSessionHandle)

    /** Read from the handle's original source; null is for stores without read support. */
    suspend fun load(handle: SteamAccountSessionHandle): SteamAccount? = null
}
