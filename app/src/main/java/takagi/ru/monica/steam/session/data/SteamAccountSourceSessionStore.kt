package takagi.ru.monica.steam.session.data

import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionStore

/** Persists session rotation through the source captured at request start. */
class SteamAccountSourceSessionStore(
    private val accountSourceRepository: SteamAccountSourceRepository
) : SteamAccountSessionStore {
    override suspend fun load(handle: SteamAccountSessionHandle): SteamAccount =
        accountSourceRepository.loadSessionAccount(handle)

    override suspend fun persist(handle: SteamAccountSessionHandle) {
        val accessToken = handle.account.accessToken?.takeIf(String::isNotBlank)
            ?: return
        accountSourceRepository.updateSessionTokens(
            origin = handle.origin,
            id = handle.account.id,
            accessToken = accessToken,
            refreshToken = handle.account.refreshToken,
            steamLoginSecure = handle.account.steamLoginSecure
        )
    }
}
