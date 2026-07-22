package takagi.ru.monica.steam.store

internal class SteamStoreAccountRegionException(
    message: String = "无法读取当前 Steam 账号的商店地区，请刷新后重试"
) : IllegalStateException(message)

internal data class SteamStoreAccountCredentials(
    val accessToken: String?,
    val steamLoginSecure: String?
)

internal suspend fun <T> executeSteamStoreAccountRetry(
    initialCredentials: SteamStoreAccountCredentials,
    forceRefreshCredentials: suspend () -> SteamStoreAccountCredentials,
    request: suspend (SteamStoreAccountCredentials) -> T
): T {
    return try {
        request(initialCredentials)
    } catch (error: SteamStoreAccountRegionException) {
        val refreshedCredentials = forceRefreshCredentials()
        if (refreshedCredentials == initialCredentials) throw error
        request(refreshedCredentials)
    }
}
