package takagi.ru.monica.steam.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamLibraryStateRaceTest {
    @Test
    fun regionalPriceResultAfterDetailIsClosedIsIgnored() {
        val state = SteamLibraryUiState(
            selectedAccountId = 7L,
            selectedGame = null,
            loadingRegionalPrices = true
        )

        val updated = applyRegionalPricesToState(
            state = state,
            gameAppId = 730,
            freshPrices = listOf(
                SteamRegionalPrice("CN", "CNY", 990, 1_990, true, fetchedAt = 1L)
            )
        )

        assertNull(updated)
    }

    @Test
    fun regionalPriceResultForPreviousGameCannotOverwriteCurrentDetail() {
        val currentGame = SteamGame(
            appId = 570,
            name = "Dota 2",
            playtimeForeverMinutes = 1,
            playtimeRecentMinutes = 0
        )
        val state = SteamLibraryUiState(
            selectedAccountId = 7L,
            selectedGame = currentGame,
            loadingRegionalPrices = true
        )

        val updated = applyRegionalPricesToState(
            state = state,
            gameAppId = 730,
            freshPrices = listOf(
                SteamRegionalPrice("CN", "CNY", 990, 1_990, true, fetchedAt = 1L)
            )
        )

        assertNull(updated)
        assertEquals(570, state.selectedGame?.appId)
    }
}
