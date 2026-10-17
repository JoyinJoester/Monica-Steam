package takagi.ru.monica.steam.library

import java.util.Locale

internal val SteamGame.hasBeenPlayed: Boolean
    get() = playtimeForeverMinutes > 0 || playtimeRecentMinutes > 0 || lastPlayedAt > 0L

/** Family-list timestamps also identify recent games when supplemental minutes are unavailable. */
internal fun SteamGame.wasRecentlyPlayed(nowSeconds: Long): Boolean =
    playtimeRecentMinutes > 0 || (
        lastPlayedAt > 0L &&
            lastPlayedAt <= nowSeconds &&
            lastPlayedAt >= nowSeconds - RECENT_PLAY_WINDOW_SECONDS
        )

/**
 * Recent activity follows two-week minutes across owned and shared games. Dates
 * only break ties, including recent games whose supplemental minutes are unavailable.
 * Older activity retains the existing playtime order.
 */
internal fun steamGameActivityComparator(nowSeconds: Long): Comparator<SteamGame> =
    compareByDescending<SteamGame> { it.wasRecentlyPlayed(nowSeconds) }
        .thenByDescending { it.playtimeRecentMinutes }
        .thenByDescending { it.recentActivityTime(nowSeconds) }
        .thenByDescending { it.playtimeForeverMinutes }
        .thenBy { it.name.lowercase(Locale.ROOT) }
        .thenBy { it.appId }

private fun SteamGame.recentActivityTime(nowSeconds: Long): Long =
    if (wasRecentlyPlayed(nowSeconds) && lastPlayedAt in 1L..nowSeconds) lastPlayedAt else 0L

private const val RECENT_PLAY_WINDOW_SECONDS = 14L * 24L * 60L * 60L
