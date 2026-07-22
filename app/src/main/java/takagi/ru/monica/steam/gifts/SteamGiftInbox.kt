package takagi.ru.monica.steam.gifts

internal fun steamGiftInboxUrl(steamId: String): String =
    "https://steamcommunity.com/profiles/$steamId/inventory/#pending_gifts"
