package takagi.ru.monica.steam.friends.chat.gameinvite.domain

import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent

data class SteamChatGameInviteMetadata(
    val appId: Int,
    val name: String,
    val headerImageUrl: String = ""
)

data class SteamChatGameInvitePresentation(
    val appId: Int?,
    val gameName: String?,
    val artworkUrl: String?
)

internal fun SteamChatRichContent.GameInvite.toGameInvitePresentation(
    metadata: SteamChatGameInviteMetadata?
): SteamChatGameInvitePresentation {
    val validAppId = appId?.takeIf { it > 0 }
    val matchingMetadata = metadata?.takeIf { it.appId == validAppId }
    return SteamChatGameInvitePresentation(
        appId = validAppId,
        gameName = matchingMetadata?.name?.trim()?.takeIf(String::isNotBlank)
            ?: meaningfulGameInviteLabel(label),
        artworkUrl = matchingMetadata?.headerImageUrl?.trim()?.takeIf(String::isNotBlank)
            ?: validAppId?.let(::steamGameInviteHeaderUrl)
    )
}

internal fun meaningfulGameInviteLabel(raw: String): String? {
    val label = raw.trim().replace(Regex("\\s+"), " ")
    if (label.isBlank()) return null
    if (label.matches(Regex("App\\s+\\d+", RegexOption.IGNORE_CASE))) return null
    if (label.contains("steam://", ignoreCase = true)) return null
    return label.takeUnless {
        it.lowercase() in setOf(
            "steam game invitation",
            "steam game invite",
            "game invitation",
            "game invite",
            "join",
            "join game"
        )
    }
}

internal fun steamGameInviteHeaderUrl(appId: Int): String =
    "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"

internal const val STEAM_GAME_HEADER_ASPECT_RATIO = 460f / 215f
