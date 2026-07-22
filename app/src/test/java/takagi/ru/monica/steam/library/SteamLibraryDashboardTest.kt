package takagi.ru.monica.steam.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.ui.SteamLibraryGameFilter
import takagi.ru.monica.steam.ui.SteamLibraryGameSectionType
import takagi.ru.monica.steam.ui.buildSteamLibrarySections
import takagi.ru.monica.steam.ui.steamGameImageUrls

class SteamLibraryDashboardTest {
    private val games = listOf(
        SteamGame(1, "Recent", 600, 90),
        SteamGame(2, "Veteran", 7_200, 0),
        SteamGame(3, "Unplayed", 0, 0)
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
        assertEquals(listOf("Recent"), sections[0].games.map(SteamGame::name))
        assertEquals(listOf("Veteran"), sections[1].games.map(SteamGame::name))
        assertEquals(listOf("Unplayed"), sections[2].games.map(SteamGame::name))
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
