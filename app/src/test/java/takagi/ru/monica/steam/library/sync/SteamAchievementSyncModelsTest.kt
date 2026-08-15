package takagi.ru.monica.steam.library.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.library.SteamAchievement
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

class SteamAchievementSyncModelsTest {
    @Test
    fun missingAchievementGamesAreOrderedByRecentActivity() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                game(appId = 10, recent = 0, lastPlayedAt = 10L),
                game(appId = 20, recent = 45, lastPlayedAt = 20L),
                game(appId = 30, recent = 5, lastPlayedAt = 30L)
            ),
            fetchedAt = 1L
        )

        val selection = selectSteamAchievementSyncGames(snapshot, forceFull = false)

        assertEquals(listOf(20, 30, 10), selection.games.map(SteamGame::appId))
        assertEquals(true, selection.isFullSync)
    }

    @Test
    fun achievementCheckpointUpdatesOnlyTheCompletedGame() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(game(10), game(20)),
            fetchedAt = 1L
        )
        val details = achievements(appId = 20, unlocked = 2, total = 3)

        val updated = snapshot.withAchievementCheckpoint(details)

        assertNull(updated.games.first().achievementTotalCount)
        assertEquals(2, updated.games.last().achievementUnlockedCount)
        assertEquals(3, updated.games.last().achievementTotalCount)
        assertEquals(120, updated.games.last().achievementProgressPlaytimeMinutes)
    }

    @Test
    fun fullSyncMarkerWaitsUntilEveryGameHasACheckpoint() {
        val incomplete = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                game(10).copy(
                    achievementUnlockedCount = 0,
                    achievementTotalCount = 0,
                    achievementProgressPlaytimeMinutes = 120
                ),
                game(20)
            ),
            fetchedAt = 1L
        )

        assertNull(incomplete.withCompletedAchievementFullSync(100L).achievementProgressFullSyncAt)

        val complete = incomplete.withAchievementCheckpoint(
            achievements(appId = 20, unlocked = 0, total = 0)
        )
        assertEquals(
            100L,
            complete.withCompletedAchievementFullSync(100L).achievementProgressFullSyncAt
        )
    }

    private fun game(
        appId: Int,
        recent: Int = 0,
        lastPlayedAt: Long = 0L
    ) = SteamGame(
        appId = appId,
        name = "Game $appId",
        playtimeForeverMinutes = 120,
        playtimeRecentMinutes = recent,
        lastPlayedAt = lastPlayedAt
    )

    private fun achievements(
        appId: Int,
        unlocked: Int,
        total: Int
    ) = SteamGameAchievements(
        accountId = 7L,
        appId = appId,
        gameName = "Game $appId",
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
        fetchedAt = 2L
    )
}
