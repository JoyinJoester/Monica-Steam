package takagi.ru.monica.steam.confirmations

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamConfirmation

/** Retry only the read, once, after an explicit authentication failure. Never replay approvals. */
internal suspend fun fetchSteamConfirmationsWithSessionRecovery(
    account: SteamAccount,
    forceRefresh: suspend (SteamAccount) -> SteamAccount?,
    fetch: suspend (SteamAccount) -> List<SteamConfirmation>
): List<SteamConfirmation> {
    return try {
        fetch(account)
    } catch (error: SteamApiException) {
        if (!error.authenticationRequired || account.refreshToken.isNullOrBlank()) throw error
        val refreshed = forceRefresh(account) ?: throw error
        if (refreshed.id != account.id || refreshed.steamId != account.steamId ||
            refreshed.accessToken.isNullOrBlank() || refreshed.accessToken == account.accessToken
        ) throw error
        fetch(refreshed)
    }
}
