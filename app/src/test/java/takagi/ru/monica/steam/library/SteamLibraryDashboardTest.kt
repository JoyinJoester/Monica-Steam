package takagi.ru.monica.steam.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.ui.SteamLibraryGameFilter
import takagi.ru.monica.steam.library.ui.SteamLibraryGameSectionType
import takagi.ru.monica.steam.library.ui.buildSteamLibrarySections
import takagi.ru.monica.steam.library.ui.steamGameImageUrls

class SteamLibraryDashboardTest {
    private val games = listOf(
        SteamGame(1, "Recent", 600, 90),
        SteamGame(2, "Veteran", 7_200, 0),
        SteamGame(3, "Unplayed", 0, 0),
        SteamGame(
            appId = 4,
            name = "Perfect",
            playtimeForeverMinutes = 1_200,
            playtimeRecentMinutes = 30,
            achievementUnlockedCount = 20,
            achievementTotalCount = 20,
            allAchievementsUnlocked = true
        )
    )

    @Test
    fun allFilterBuildsRecentPlayedAndUnplayedSections() {
        val sections = buildSteamLibrarySections(games, query = "", filter = SteamLibraryGameFilter.ALL)

        assertEquals(
            listOf(
                SteamLibraryGameSectionType.RECENT,
                SteamLibraryGameSectionType.PLAYED,
                SteamLibraryGameSectionType.UNPLAYED
            ),
            sections.map { it.type }
        )
        assertEquals(listOf("Recent", "Perfect"), sections[0].games.map(SteamGame::name))
        assertEquals(listOf("Veteran"), sections[1].games.map(SteamGame::name))
        assertEquals(listOf("Unplayed"), sections[2].games.map(SteamGame::name))
    }

    @Test
    fun perfectFilterIncludesOnlyGamesWithEveryNonZeroAchievementUnlocked() {
        val sections = buildSteamLibrarySections(
            games = games + SteamGame(
                appId = 5,
                name = "No achievements",
                playtimeForeverMinutes = 60,
                playtimeRecentMinutes = 0,
                achievementUnlockedCount = 0,
                achievementTotalCount = 0,
                allAchievementsUnlocked = true
            ),
            query = "",
            filter = SteamLibraryGameFilter.PERFECT
        )

        assertEquals(listOf("Perfect"), sections.single().games.map(SteamGame::name))
    }

    @Test
    fun searchAndLongPlayFilterDoNotInventCompletionData() {
        val sections = buildSteamLibrarySections(
            games = games,
            query = "vet",
            filter = SteamLibraryGameFilter.LONG_PLAYED
        )

        assertEquals(SteamLibraryGameSectionType.RESULTS, sections.single().type)
        assertEquals(listOf("Veteran"), sections.single().games.map(SteamGame::name))
        assertTrue(sections.single().games.all { it.playtimeForeverMinutes >= 6_000 })
    }

    @Test
    fun localizedHeaderIsPreferredBeforeCdnAndIconFallbacks() {
        val game = SteamGame(
            appId = 10,
            name = "Game",
            playtimeForeverMinutes = 1,
            playtimeRecentMinutes = 0,
            iconHash = "hash",
            headerImageUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/10/header_schinese.jpg"
        )

        val urls = steamGameImageUrls(game)

        assertEquals(game.headerImageUrl, urls.first())
        assertTrue(urls.any { it.contains("/header.jpg") })
        assertTrue(urls.any { it.contains("capsule_231x87.jpg") })
        assertTrue(urls.last().contains("/hash.jpg"))
    }
}
