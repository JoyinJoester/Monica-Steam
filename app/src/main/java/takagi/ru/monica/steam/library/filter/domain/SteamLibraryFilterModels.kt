package takagi.ru.monica.steam.library.filter.domain

import java.util.Locale
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.hasBeenPlayed
import takagi.ru.monica.steam.library.steamGameActivityComparator
import takagi.ru.monica.steam.library.wasRecentlyPlayed

internal enum class SteamLibraryOwnershipFilter {
    ALL,
    OWNED,
    FAMILY_SHARED
}

internal enum class SteamLibraryPlayStatusFilter {
    ALL,
    UNPLAYED,
    PLAYED,
    RECENT
}

internal enum class SteamLibraryAchievementStatusFilter {
    ALL,
    PERFECT,
    INCOMPLETE,
    NO_ACHIEVEMENTS
}

internal enum class SteamLibraryPlaytimeFilter {
    ANY,
    UNDER_TWO_HOURS,
    TWO_TO_TWENTY_HOURS,
    OVER_TWENTY_HOURS
}

internal enum class SteamLibrarySortOrder {
    SMART,
    RECENT_PLAYTIME,
    TOTAL_PLAYTIME,
    NAME_ASCENDING,
    NAME_DESCENDING
}

internal data class SteamLibraryFilterSelection(
    val ownership: SteamLibraryOwnershipFilter = SteamLibraryOwnershipFilter.ALL,
    val playStatus: SteamLibraryPlayStatusFilter = SteamLibraryPlayStatusFilter.ALL,
    val achievementStatus: SteamLibraryAchievementStatusFilter =
        SteamLibraryAchievementStatusFilter.ALL,
    val playtime: SteamLibraryPlaytimeFilter = SteamLibraryPlaytimeFilter.ANY,
    val sortOrder: SteamLibrarySortOrder = SteamLibrarySortOrder.SMART,
    val requiresSteamCloud: Boolean = false
) {
    val hasActiveFilters: Boolean
        get() = ownership != SteamLibraryOwnershipFilter.ALL ||
            playStatus != SteamLibraryPlayStatusFilter.ALL ||
            achievementStatus != SteamLibraryAchievementStatusFilter.ALL ||
            playtime != SteamLibraryPlaytimeFilter.ANY ||
            requiresSteamCloud

    val activeChoiceCount: Int
        get() = listOf(
            ownership != SteamLibraryOwnershipFilter.ALL,
            playStatus != SteamLibraryPlayStatusFilter.ALL,
            achievementStatus != SteamLibraryAchievementStatusFilter.ALL,
            playtime != SteamLibraryPlaytimeFilter.ANY,
            sortOrder != SteamLibrarySortOrder.SMART,
            requiresSteamCloud
        ).count { it }
}

internal fun filterSteamLibraryGames(
    games: List<SteamGame>,
    query: String,
    selection: SteamLibraryFilterSelection,
    nowSeconds: Long = System.currentTimeMillis() / 1000L
): List<SteamGame> = steamLibraryFilterSequence(games, query, selection, nowSeconds)
    .sortedWith(selection.sortOrder.comparator(nowSeconds))
    .toList()

/**
 * Counts matches without sorting them.  The filter sheet asks for this value
 * on every pending selection; sorting here made each tap do unnecessary work
 * on the main thread and made the sheet feel unresponsive on larger libraries.
 */
internal fun countSteamLibraryGames(
    games: List<SteamGame>,
    query: String,
    selection: SteamLibraryFilterSelection,
    nowSeconds: Long = System.currentTimeMillis() / 1000L
): Int = steamLibraryFilterSequence(games, query, selection, nowSeconds).count()

private fun steamLibraryFilterSequence(
    games: List<SteamGame>,
    query: String,
    selection: SteamLibraryFilterSelection,
    nowSeconds: Long
): Sequence<SteamGame> {
    val normalizedQuery = query.trim()
    return games.asSequence()
        .distinctBy(SteamGame::appId)
        .filter { game ->
            normalizedQuery.isBlank() || game.name.contains(normalizedQuery, ignoreCase = true)
        }
        .filter { game -> selection.ownership.matches(game) }
        .filter { game -> selection.playStatus.matches(game, nowSeconds) }
        .filter { game -> selection.achievementStatus.matches(game) }
        .filter { game -> selection.playtime.matches(game) }
        .filter { game -> !selection.requiresSteamCloud || game.supportsSteamCloud == true }
}

private fun SteamLibraryOwnershipFilter.matches(game: SteamGame): Boolean = when (this) {
    SteamLibraryOwnershipFilter.ALL -> true
    SteamLibraryOwnershipFilter.OWNED -> !game.isFamilyShared
    SteamLibraryOwnershipFilter.FAMILY_SHARED -> game.isFamilyShared
}

private fun SteamLibraryPlayStatusFilter.matches(game: SteamGame, nowSeconds: Long): Boolean = when (this) {
    SteamLibraryPlayStatusFilter.ALL -> true
    SteamLibraryPlayStatusFilter.UNPLAYED -> !game.hasBeenPlayed
    SteamLibraryPlayStatusFilter.PLAYED -> game.hasBeenPlayed
    SteamLibraryPlayStatusFilter.RECENT -> game.wasRecentlyPlayed(nowSeconds)
}

private fun SteamLibraryAchievementStatusFilter.matches(game: SteamGame): Boolean = when (this) {
    SteamLibraryAchievementStatusFilter.ALL -> true
    SteamLibraryAchievementStatusFilter.PERFECT -> game.isPerfectAchievementGame
    SteamLibraryAchievementStatusFilter.INCOMPLETE -> {
        val total = game.achievementTotalCount ?: 0
        total > 0 && !game.isPerfectAchievementGame
    }
    SteamLibraryAchievementStatusFilter.NO_ACHIEVEMENTS -> game.achievementTotalCount == 0
}

private fun SteamLibraryPlaytimeFilter.matches(game: SteamGame): Boolean = when (this) {
    SteamLibraryPlaytimeFilter.ANY -> true
    SteamLibraryPlaytimeFilter.UNDER_TWO_HOURS -> game.playtimeForeverMinutes < TWO_HOURS_MINUTES
    SteamLibraryPlaytimeFilter.TWO_TO_TWENTY_HOURS ->
        game.playtimeForeverMinutes in TWO_HOURS_MINUTES until TWENTY_HOURS_MINUTES
    SteamLibraryPlaytimeFilter.OVER_TWENTY_HOURS ->
        game.playtimeForeverMinutes >= TWENTY_HOURS_MINUTES
}

private fun SteamLibrarySortOrder.comparator(nowSeconds: Long): Comparator<SteamGame> = when (this) {
    SteamLibrarySortOrder.SMART -> steamGameActivityComparator(nowSeconds)
    SteamLibrarySortOrder.RECENT_PLAYTIME ->
        compareByDescending<SteamGame> { it.playtimeRecentMinutes }
            .thenByDescending { it.playtimeForeverMinutes }
            .thenBy { it.name.lowercase(Locale.ROOT) }
    SteamLibrarySortOrder.TOTAL_PLAYTIME ->
        compareByDescending<SteamGame> { it.playtimeForeverMinutes }
            .thenByDescending { it.playtimeRecentMinutes }
            .thenBy { it.name.lowercase(Locale.ROOT) }
    SteamLibrarySortOrder.NAME_ASCENDING ->
        compareBy { it.name.lowercase(Locale.ROOT) }
    SteamLibrarySortOrder.NAME_DESCENDING ->
        compareByDescending { it.name.lowercase(Locale.ROOT) }
}

private const val TWO_HOURS_MINUTES = 2 * 60
private const val TWENTY_HOURS_MINUTES = 20 * 60
