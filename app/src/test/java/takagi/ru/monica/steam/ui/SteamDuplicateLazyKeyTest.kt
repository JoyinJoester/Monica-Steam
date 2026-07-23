package takagi.ru.monica.steam.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamRegionalPrice

class SteamDuplicateLazyKeyTest {
    @Test
    fun duplicateLibraryGamesAchievementsAndPricesReceiveUniqueKeys() {
        val game = SteamGame(730, "Counter-Strike 2", 1, 0)
        val gameKeys = listOf(game, game).mapIndexed { index, item ->
            steamLibraryGameLazyKey(SteamLibraryGameSectionType.PLAYED, index, item)
        }
        val achievement = SteamAchievement("ACH_WIN", "Winner", "", false, null, null, null)
        val achievementKeys = listOf(achievement, achievement).mapIndexed(::steamAchievementLazyKey)
        val price = SteamRegionalPrice("US", "USD", 999, 999, true, fetchedAt = 1L)
        val priceKeys = listOf(price, price).mapIndexed(::steamRegionalPriceLazyKey)

        assertEquals(2, gameKeys.distinct().size)
        assertEquals(2, achievementKeys.distinct().size)
        assertEquals(2, priceKeys.distinct().size)
    }
}
