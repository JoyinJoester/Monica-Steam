package takagi.ru.monica.steam.library.analytics.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamLibrarySnapshot

class SteamPlayActivityTest {
    @Test
    fun firstSnapshotSeedsRecentPlaytimeAndEstablishesBaseline() {
        val history = updateSteamPlayActivity(
            previous = null,
            snapshot = snapshot(game(10, "Portal", 600, recentMinutes = 45)),
            localDate = "2026-07-24",
            recordedAt = 1L
        )

        assertEquals(45, history.days.single().totalMinutes)
        assertEquals("2026-07-24", history.days.single().date)
        assertEquals(600, history.baseline.single().cumulativeMinutes)
    }

    @Test
    fun existingBaselineWithoutDaysIsSeededAfterUpgrade() {
        val previous = SteamPlayActivityHistory(
            accountId = 1L,
            baseline = listOf(SteamPlaytimeBaseline(10, "Portal", 600))
        )

        val history = updateSteamPlayActivity(
            previous = previous,
            snapshot = snapshot(game(10, "Portal", 600, recentMinutes = 45)),
            localDate = "2026-07-24",
            recordedAt = 2L
        )

        assertEquals(45, history.days.single().totalMinutes)
    }

    @Test
    fun laterSnapshotsAccumulatePositiveDeltasForSameDay() {
        val baseline = updateSteamPlayActivity(
            previous = null,
            snapshot = snapshot(game(10, "Portal", 600)),
            localDate = "2026-07-24",
            recordedAt = 1L
        )
        val firstDelta = updateSteamPlayActivity(
            previous = baseline,
            snapshot = snapshot(game(10, "Portal", 625)),
            localDate = "2026-07-24",
            recordedAt = 2L
        )
        val secondDelta = updateSteamPlayActivity(
            previous = firstDelta,
            snapshot = snapshot(game(10, "Portal", 640)),
            localDate = "2026-07-24",
            recordedAt = 3L
        )

        assertEquals(40, secondDelta.days.single().totalMinutes)
        assertEquals(640, secondDelta.baseline.single().cumulativeMinutes)
    }

    @Test
    fun newGamesAndReducedTotalsDoNotFabricateActivity() {
        val previous = SteamPlayActivityHistory(
            accountId = 1L,
            baseline = listOf(SteamPlaytimeBaseline(10, "Portal", 600))
        )
        val history = updateSteamPlayActivity(
            previous = previous,
            snapshot = snapshot(
                game(10, "Portal", 590),
                game(20, "Half-Life", 120)
            ),
            localDate = "2026-07-24",
            recordedAt = 2L
        )

        assertTrue(history.days.isEmpty())
        assertEquals(2, history.baseline.size)
    }

    private fun game(
        appId: Int,
        name: String,
        minutes: Int,
        recentMinutes: Int = 0
    ) = SteamGame(
        appId = appId,
        name = name,
        playtimeForeverMinutes = minutes,
        playtimeRecentMinutes = recentMinutes
    )

    private fun snapshot(vararg games: SteamGame) = SteamLibrarySnapshot(
        accountId = 1L,
        games = games.toList(),
        fetchedAt = 0L
    )
}
