package takagi.ru.monica.steam.library.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult

class SteamAchievementSyncRunnerTest {
    @Test
    fun completedGamesAreCheckpointedBeforeALaterNetworkFailure() = runTest {
        val games = listOf(game(10, "First"), game(20, "Second"))
        val checkpoints = mutableListOf<Int>()

        val result = runSteamAchievementSync(
            games = games,
            forceFull = false,
            fetch = { game, _ ->
                if (game.appId == 10) {
                    SteamLibraryResult.Success(achievements(game, unlocked = 1, total = 2))
                } else {
                    SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
                }
            },
            checkpoint = { checkpoints += it.appId }
        )

        assertEquals(listOf(10), checkpoints)
        assertTrue(result is SteamAchievementSyncRunResult.Retry)
        result as SteamAchievementSyncRunResult.Retry
        assertEquals(1, result.completedGames)
        assertEquals(2, result.totalGames)
        assertEquals(SteamLibraryFailureReason.NETWORK, result.failure)
    }

    @Test
    fun explicitNoAchievementResultIsPersistedAsACompletedGame() = runTest {
        val game = game(30, "No achievements")
        val checkpoints = mutableListOf<SteamGameAchievements>()

        val result = runSteamAchievementSync(
            games = listOf(game),
            forceFull = false,
            fetch = { requested, _ ->
                SteamLibraryResult.Success(achievements(requested, unlocked = 0, total = 0))
            },
            checkpoint = { checkpoints += it }
        )

        assertTrue(result is SteamAchievementSyncRunResult.Completed)
        assertEquals(1, checkpoints.size)
        assertTrue(checkpoints.single().achievements.isEmpty())
    }

    @Test
    fun forceFullIsPassedToEveryPerGameRequest() = runTest {
        val forceFlags = mutableListOf<Boolean>()

        runSteamAchievementSync(
            games = listOf(game(10, "First"), game(20, "Second")),
            forceFull = true,
            fetch = { game, force ->
                forceFlags += force
                SteamLibraryResult.Success(achievements(game, unlocked = 0, total = 0))
            },
            checkpoint = {}
        )

        assertEquals(listOf(true, true), forceFlags)
    }

    private fun game(appId: Int, name: String) = SteamGame(
        appId = appId,
        name = name,
        playtimeForeverMinutes = 10,
        playtimeRecentMinutes = 0
    )

    private fun achievements(
        game: SteamGame,
        unlocked: Int,
        total: Int
    ) = SteamGameAchievements(
        accountId = 7L,
        appId = game.appId,
        gameName = game.name,
        achievements = (0 until total).map { index ->
            SteamAchievement(
                apiName = "ACH_$index",
                displayName = "Achievement $index",
                description = "",
                achieved = index < unlocked,
                unlockTimeSeconds = null,
                iconUrl = null,
                lockedIconUrl = null
            )
        },
        fetchedAt = 100L
    )
}
