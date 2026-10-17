package takagi.ru.monica.steam.library.ui

import java.util.Locale
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryFilterSelection
import takagi.ru.monica.steam.library.filter.domain.filterSteamLibraryGames
import takagi.ru.monica.steam.library.hasBeenPlayed
import takagi.ru.monica.steam.library.steamGameActivityComparator
import takagi.ru.monica.steam.library.wasRecentlyPlayed

internal enum class SteamLibraryGameFilter {
    ALL,
    UNPLAYED,
    RECENT,
    LONG_PLAYED,
    PERFECT,
    FAMILY_SHARED,
    STEAM_CLOUD
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
    game: SteamGame
): String = "${section.name}_${game.appId}"

internal const val LONG_PLAYTIME_MINUTES = 100 * 60

internal fun buildSteamLibrarySections(
    games: List<SteamGame>,
    query: String,
    filter: SteamLibraryGameFilter,
    nowSeconds: Long = System.currentTimeMillis() / 1000L
): List<SteamLibraryGameSection> {
    val searched = games.distinctBy(SteamGame::appId).filter { game ->
        query.isBlank() || game.name.contains(query.trim(), ignoreCase = true)
    }
    val scoped = when (filter) {
        SteamLibraryGameFilter.ALL -> searched
        SteamLibraryGameFilter.UNPLAYED -> searched.filterNot { it.hasBeenPlayed }
        SteamLibraryGameFilter.RECENT -> searched.filter { it.wasRecentlyPlayed(nowSeconds) }
        SteamLibraryGameFilter.LONG_PLAYED -> searched.filter {
            it.playtimeForeverMinutes >= LONG_PLAYTIME_MINUTES
        }
        SteamLibraryGameFilter.PERFECT -> searched.filter(SteamGame::isPerfectAchievementGame)
        SteamLibraryGameFilter.FAMILY_SHARED -> searched.filter(SteamGame::isFamilyShared)
        SteamLibraryGameFilter.STEAM_CLOUD -> searched.filter {
            it.supportsSteamCloud == true
        }
    }
    if (filter != SteamLibraryGameFilter.ALL) {
        return listOf(
            SteamLibraryGameSection(
                type = SteamLibraryGameSectionType.RESULTS,
                games = scoped.sortedWith(
                    if (filter == SteamLibraryGameFilter.RECENT) {
                        steamGameActivityComparator(nowSeconds)
                    } else {
                        compareByDescending<SteamGame> { it.playtimeRecentMinutes }
                            .thenByDescending { it.playtimeForeverMinutes }
                            .thenBy { it.name.lowercase(Locale.ROOT) }
                    }
                )
            )
        )
    }
    return buildActivitySections(scoped, nowSeconds)
}

private fun buildActivitySections(
    games: List<SteamGame>,
    nowSeconds: Long
): List<SteamLibraryGameSection> = listOf(
    SteamLibraryGameSection(
        type = SteamLibraryGameSectionType.RECENT,
        games = games.filter { it.wasRecentlyPlayed(nowSeconds) }
            .sortedWith(steamGameActivityComparator(nowSeconds))
    ),
    SteamLibraryGameSection(
        type = SteamLibraryGameSectionType.PLAYED,
        games = games.filter {
            it.hasBeenPlayed && !it.wasRecentlyPlayed(nowSeconds)
        }.sortedByDescending { it.playtimeForeverMinutes }
    ),
    SteamLibraryGameSection(
        type = SteamLibraryGameSectionType.UNPLAYED,
        games = games.filterNot { it.hasBeenPlayed }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    )
)

internal fun buildSteamLibrarySections(
    games: List<SteamGame>,
    query: String,
    selection: SteamLibraryFilterSelection,
    nowSeconds: Long = System.currentTimeMillis() / 1000L
): List<SteamLibraryGameSection> {
    val scoped = filterSteamLibraryGames(games, query, selection, nowSeconds)
    if (selection.hasActiveFilters ||
        selection.sortOrder != takagi.ru.monica.steam.library.filter.domain.SteamLibrarySortOrder.SMART
    ) {
        return listOf(
            SteamLibraryGameSection(
                type = SteamLibraryGameSectionType.RESULTS,
                games = scoped
            )
        )
    }
    return buildActivitySections(scoped, nowSeconds)
}
