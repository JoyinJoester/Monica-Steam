package takagi.ru.monica.steam.library.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievementProgress
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.needsAchievementProgressSync

class SteamAchievementSyncRunnerTest {
    @Test
    fun firstFullSyncPersistsCompletedGamesAndOnlyQueuesTheRemainder() {
        val games = (1..266).map { appId ->
            game(appId).let { game ->
                if (appId <= 265) {
                    game.copy(
                        achievementUnlockedCount = 0,
                        achievementTotalCount = 0,
                        achievementProgressPlaytimeMinutes = game.playtimeForeverMinutes
                    )
                } else {
                    game
                }
            }
        }
        val prepared = snapshot(games).prepareAchievementSyncPlan(
            requestId = "request-a",
            forceFull = false,
            nowMillis = 100L
        )

        val plan = requireNotNull(prepared.achievementSyncPlan)
        assertEquals(265, plan.completedGames)
        assertEquals(266, plan.totalGames)
        assertEquals(listOf(266), plan.pendingAppIds)
    }

    @Test
    fun interruptedSyncResumesThePersistedPlanInsteadOfStartingOver() {
        val prepared = snapshot((1..266).map(::game)).prepareAchievementSyncPlan(
            requestId = "request-a",
            forceFull = false,
            nowMillis = 100L
        )
        val originalPlan = requireNotNull(prepared.achievementSyncPlan)
        val firstBatch = requireNotNull(originalPlan.nextAchievementSyncBatch())
        val checkpointed = prepared.applyAchievementSyncBatch(
            requestId = "request-a",
            batch = firstBatch,
            outcome = SteamAchievementSyncBatchOutcome.Success(emptyMap()),
            nowMillis = 200L
        )

        val resumed = checkpointed.prepareAchievementSyncPlan(
            requestId = "request-b",
            forceFull = false,
            nowMillis = 300L
        )
        val plan = requireNotNull(resumed.achievementSyncPlan)

        assertEquals("request-a", plan.requestId)
        assertEquals(100, plan.completedGames)
        assertEquals(originalPlan.pendingAppIds.drop(100), plan.pendingAppIds)
    }

    @Test
    fun replacedForceSyncRejectsAStaleWorkerCheckpoint() {
        val first = snapshot(listOf(game(10), game(20))).prepareAchievementSyncPlan(
            requestId = "request-a",
            forceFull = false,
            nowMillis = 100L
        )
        val staleBatch = requireNotNull(
            requireNotNull(first.achievementSyncPlan).nextAchievementSyncBatch()
        )
        val replacement = first.prepareAchievementSyncPlan(
            requestId = "request-b",
            forceFull = true,
            nowMillis = 200L
        )

        val afterStaleCheckpoint = replacement.applyAchievementSyncBatch(
            requestId = "request-a",
            batch = staleBatch,
            outcome = SteamAchievementSyncBatchOutcome.Success(emptyMap()),
            nowMillis = 300L
        )

        val plan = requireNotNull(afterStaleCheckpoint.achievementSyncPlan)
        assertEquals("request-b", plan.requestId)
        assertEquals(0, plan.completedGames)
        assertEquals(2, plan.pendingAppIds.size)
    }

    @Test
    fun successfulBatchStoresZeroProgressForGamesWithoutAchievements() {
        val prepared = snapshot(listOf(game(10), game(20))).prepareAchievementSyncPlan(
            requestId = "request-a",
            forceFull = false,
            nowMillis = 100L
        )
        val batch = requireNotNull(
            requireNotNull(prepared.achievementSyncPlan)
                .nextAchievementSyncBatch(batchSize = 2)
        )
        val updated = prepared.applyAchievementSyncBatch(
            requestId = "request-a",
            batch = batch,
            outcome = SteamAchievementSyncBatchOutcome.Success(
                mapOf(
                    10 to SteamGameAchievementProgress(
                        appId = 10,
                        unlocked = 3,
                        total = 8,
                        allUnlocked = false
                    )
                )
            ),
            nowMillis = 200L
        )

        val withAchievements = updated.games.first { it.appId == 10 }
        val withoutAchievements = updated.games.first { it.appId == 20 }
        assertEquals(3, withAchievements.achievementUnlockedCount)
        assertEquals(8, withAchievements.achievementTotalCount)
        assertEquals(0, withoutAchievements.achievementUnlockedCount)
        assertEquals(0, withoutAchievements.achievementTotalCount)
        assertEquals(2, requireNotNull(updated.achievementSyncPlan).completedGames)
    }

    @Test
    fun failedBatchMovesToOneFinalRetryThenStopsBlockingThePlan() {
        val prepared = snapshot(listOf(game(10), game(20))).prepareAchievementSyncPlan(
            requestId = "request-a",
            forceFull = false,
            nowMillis = 100L
        )
        val firstBatch = requireNotNull(
            requireNotNull(prepared.achievementSyncPlan)
                .nextAchievementSyncBatch(batchSize = 2)
        )
        val waitingForRetry = prepared.applyAchievementSyncBatch(
            requestId = "request-a",
            batch = firstBatch,
            outcome = SteamAchievementSyncBatchOutcome.Failure(
                SteamLibraryFailureReason.NETWORK
            ),
            nowMillis = 200L
        )

        val retryPlan = requireNotNull(waitingForRetry.achievementSyncPlan)
        assertEquals(0, retryPlan.completedGames)
        assertTrue(retryPlan.pendingAppIds.isEmpty())
        assertEquals(listOf(10, 20), retryPlan.retryAppIds)
        val retryBatch = requireNotNull(retryPlan.nextAchievementSyncBatch(batchSize = 2))
        assertTrue(retryBatch.isRetry)

        val skipped = waitingForRetry.applyAchievementSyncBatch(
            requestId = "request-a",
            batch = retryBatch,
            outcome = SteamAchievementSyncBatchOutcome.Failure(
                SteamLibraryFailureReason.NETWORK
            ),
            nowMillis = 300L
        )
        val skippedPlan = requireNotNull(skipped.achievementSyncPlan)

        assertEquals(2, skippedPlan.completedGames)
        assertEquals(listOf(10, 20), skippedPlan.failedAppIds)
        assertTrue(skippedPlan.retryAppIds.isEmpty())
        assertTrue(skipped.games.all { it.achievementProgressPlaytimeMinutes == 10 })
        assertTrue(skipped.games.all { it.achievementTotalCount == null })
    }

    @Test
    fun finishingAFullPlanRecordsCompletionEvenWhenOneGameWasSkipped() {
        val prepared = snapshot(listOf(game(10))).prepareAchievementSyncPlan(
            requestId = "request-a",
            forceFull = false,
            nowMillis = 100L
        )
        val first = requireNotNull(
            requireNotNull(prepared.achievementSyncPlan).nextAchievementSyncBatch()
        )
        val retryQueued = prepared.applyAchievementSyncBatch(
            requestId = "request-a",
            batch = first,
            outcome = SteamAchievementSyncBatchOutcome.Failure(
                SteamLibraryFailureReason.NETWORK
            ),
            nowMillis = 200L
        )
        val retry = requireNotNull(
            requireNotNull(retryQueued.achievementSyncPlan).nextAchievementSyncBatch()
        )
        val exhausted = retryQueued.applyAchievementSyncBatch(
            requestId = "request-a",
            batch = retry,
            outcome = SteamAchievementSyncBatchOutcome.Failure(
                SteamLibraryFailureReason.NETWORK
            ),
            nowMillis = 300L
        )

        val finished = exhausted.finishAchievementSyncPlan(
            requestId = "request-a",
            nowMillis = 400L
        )

        assertNull(finished.achievementSyncPlan)
        assertEquals(400L, finished.achievementProgressFullSyncAt)
        assertFalse(finished.games.first().needsAchievementProgressSync())
    }

    private fun snapshot(games: List<SteamGame>) = SteamLibrarySnapshot(
        accountId = 7L,
        games = games,
        fetchedAt = 1L
    )

    private fun game(appId: Int) = SteamGame(
        appId = appId,
        name = "Game $appId",
        playtimeForeverMinutes = 10,
        playtimeRecentMinutes = 0
    )
}
