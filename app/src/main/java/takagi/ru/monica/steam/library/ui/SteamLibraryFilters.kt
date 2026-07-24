package takagi.ru.monica.steam.library.ui

import java.util.Locale
import takagi.ru.monica.steam.library.SteamGame

internal enum class SteamLibraryGameFilter {
    ALL,
    UNPLAYED,
    RECENT,
    LONG_PLAYED,
    PERFECT,
    FAMILY_SHARED
}

internal enum class SteamLibraryGameSectionType {
    RECENT,
    PLAYED,
    UNPLAYED,
    RESULTS
}

internal data class SteamLibraryGameSection(
    val type: SteamLibraryGameSectionType,
    val games: List<SteamGame>
)

internal fun steamLibraryGameLazyKey(
    section: SteamLibraryGameSectionType,
    index: Int,
    game: SteamGame
): String = "${section.name}_${game.appId}_$index"

internal const val LONG_PLAYTIME_MINUTES = 100 * 60

internal fun buildSteamLibrarySections(
    games: List<SteamGame>,
    query: String,
    filter: SteamLibraryGameFilter
): List<SteamLibraryGameSection> {
    val searched = games.filter { game ->
        query.isBlank() || game.name.contains(query.trim(), ignoreCase = true)
    }
    val scoped = when (filter) {
        SteamLibraryGameFilter.ALL -> searched
        SteamLibraryGameFilter.UNPLAYED -> searched.filter { it.playtimeForeverMinutes == 0 }
        SteamLibraryGameFilter.RECENT -> searched.filter { it.playtimeRecentMinutes > 0 }
        SteamLibraryGameFilter.LONG_PLAYED -> searched.filter {
            it.playtimeForeverMinutes >= LONG_PLAYTIME_MINUTES
        }
        SteamLibraryGameFilter.PERFECT -> searched.filter(SteamGame::isPerfectAchievementGame)
        SteamLibraryGameFilter.FAMILY_SHARED -> searched.filter(SteamGame::isFamilyShared)
    }
    if (filter != SteamLibraryGameFilter.ALL) {
        return listOf(
            SteamLibraryGameSection(
                type = SteamLibraryGameSectionType.RESULTS,
                games = scoped.sortedWith(
                    compareByDescending<SteamGame> { it.playtimeRecentMinutes }
                        .thenByDescending { it.playtimeForeverMinutes }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
            )
        )
    }
    return listOf(
        SteamLibraryGameSection(
            type = SteamLibraryGameSectionType.RECENT,
            games = scoped.filter { it.playtimeRecentMinutes > 0 }
                .sortedByDescending { it.playtimeRecentMinutes }
        ),
        SteamLibraryGameSection(
            type = SteamLibraryGameSectionType.PLAYED,
            games = scoped.filter {
                it.playtimeForeverMinutes > 0 && it.playtimeRecentMinutes == 0
            }.sortedByDescending { it.playtimeForeverMinutes }
        ),
        SteamLibraryGameSection(
            type = SteamLibraryGameSectionType.UNPLAYED,
            games = scoped.filter { it.playtimeForeverMinutes == 0 }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        )
    )
}
