package takagi.ru.monica.steam.store

internal open class SteamStoreSessionException(message: String) : IllegalStateException(message)

internal class SteamStoreAccountRegionException(
    message: String = "无法读取当前 Steam 账号的商店地区，请刷新后重试"
) : SteamStoreSessionException(message)

internal class SteamStoreWishlistSessionException(
    message: String = "Steam 愿望单会话已失效，请刷新后重试"
) : SteamStoreSessionException(message)

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
    } catch (error: SteamStoreSessionException) {
        val refreshedCredentials = forceRefreshCredentials()
        if (refreshedCredentials == initialCredentials) throw error
        request(refreshedCredentials)
    }
}
