package takagi.ru.monica.steam.library

internal val SteamGame.hasBeenPlayed: Boolean
    get() = playtimeForeverMinutes > 0 || playtimeRecentMinutes > 0 || lastPlayedAt > 0L

/** Shared apps expose the current user's last-played Unix seconds, but no two-week minutes. */
internal fun SteamGame.wasRecentlyPlayed(nowSeconds: Long): Boolean =
    playtimeRecentMinutes > 0 || (
        lastPlayedAt > 0L &&
            lastPlayedAt <= nowSeconds &&
            lastPlayedAt >= nowSeconds - RECENT_PLAY_WINDOW_SECONDS
        )

private const val RECENT_PLAY_WINDOW_SECONDS = 14L * 24L * 60L * 60L
