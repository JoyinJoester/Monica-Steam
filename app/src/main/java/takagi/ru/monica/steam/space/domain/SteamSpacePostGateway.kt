package takagi.ru.monica.steam.space.domain

import takagi.ru.monica.steam.data.SteamAccount

fun interface SteamSpacePostGateway {
    /** Return only after Steam acknowledges the service request. No automatic mutation retry. */
    fun publish(account: SteamAccount, text: String)
}

internal const val STEAM_SPACE_POST_LIMIT = 1_000
internal fun isValidSteamSpacePost(text: String): Boolean = text.isNotBlank() && text.length <= STEAM_SPACE_POST_LIMIT &&
    '\u0000' !in text
