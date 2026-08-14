package takagi.ru.monica.steam.library.analytics.domain

import java.time.Instant
import java.time.ZoneOffset
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
            snapshot = snapshot(
                game(
                    appId = 10,
                    name = "Portal",
                    minutes = 600,
                    recentMinutes = 45,
                    lastPlayedAt = Instant.parse("2026-07-22T18:00:00Z").epochSecond
                )
            ),
            localDate = "2026-07-24",
            recordedAt = Instant.parse("2026-07-24T12:00:00Z").toEpochMilli(),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(45, history.days.single().totalMinutes)
        assertEquals("2026-07-22", history.days.single().date)
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

    @Test
    fun currentLibraryMetadataEnrichesIconsInExistingHeatmapDays() {
        val previous = SteamPlayActivityHistory(
            accountId = 1L,
            baseline = listOf(SteamPlaytimeBaseline(10, "Old name", 600)),
            days = listOf(
                SteamPlayActivityDay(
                    date = "2026-07-24",
                    games = listOf(SteamPlayActivityGame(10, "Old name", 45))
                )
            )
        )
        val currentGame = game(10, "Portal", 600).copy(
            iconHash = "icon-hash",
            headerImageUrl = "https://cdn.example/header.jpg"
        )

        val history = updateSteamPlayActivity(
            previous = previous,
            snapshot = snapshot(currentGame),
            localDate = "2026-07-25",
            recordedAt = 2L
        )

        val activity = history.days.single().games.single()
        assertEquals("Portal", activity.gameName)
        assertEquals("icon-hash", activity.iconHash)
        assertEquals("https://cdn.example/header.jpg", activity.headerImageUrl)
    }

    @Test
    fun elapsedSnapshotsUseEachGamesLastPlayedDate() {
        val previous = SteamPlayActivityHistory(
            accountId = 1L,
            baseline = listOf(
                SteamPlaytimeBaseline(10, "Portal", 600),
                SteamPlaytimeBaseline(20, "Half-Life", 300)
            ),
            updatedAt = Instant.parse("2026-08-10T12:00:00Z").toEpochMilli()
        )

        val history = updateSteamPlayActivity(
            previous = previous,
            snapshot = snapshot(
                game(
                    appId = 10,
                    name = "Portal",
                    minutes = 630,
                    lastPlayedAt = Instant.parse("2026-08-11T21:30:00Z").epochSecond
                ),
                game(
                    appId = 20,
                    name = "Half-Life",
                    minutes = 345,
                    lastPlayedAt = Instant.parse("2026-08-12T08:15:00Z").epochSecond
                )
            ),
            localDate = "2026-08-13",
            recordedAt = Instant.parse("2026-08-13T09:00:00Z").toEpochMilli(),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(listOf("2026-08-12", "2026-08-11"), history.days.map { it.date })
        assertEquals(45, history.days.first { it.date == "2026-08-12" }.totalMinutes)
        assertEquals(30, history.days.first { it.date == "2026-08-11" }.totalMinutes)
    }

    @Test
    fun staleLastPlayedTimestampFallsBackToSnapshotDate() {
        val previous = SteamPlayActivityHistory(
            accountId = 1L,
            baseline = listOf(SteamPlaytimeBaseline(10, "Portal", 600)),
            updatedAt = Instant.parse("2026-08-12T12:00:00Z").toEpochMilli()
        )

        val history = updateSteamPlayActivity(
            previous = previous,
            snapshot = snapshot(
                game(
                    appId = 10,
                    name = "Portal",
                    minutes = 630,
                    lastPlayedAt = Instant.parse("2026-08-11T21:30:00Z").epochSecond
                )
            ),
            localDate = "2026-08-13",
            recordedAt = Instant.parse("2026-08-13T09:00:00Z").toEpochMilli(),
            zoneId = ZoneOffset.UTC
        )

        assertEquals("2026-08-13", history.days.single().date)
        assertEquals(30, history.days.single().totalMinutes)
    }

    @Test
    fun legacyRefreshDayMovesBackToLastPlayedDateOnce() {
        val previous = SteamPlayActivityHistory(
            accountId = 1L,
            baseline = listOf(SteamPlaytimeBaseline(10, "Portal", 630)),
            days = listOf(
                SteamPlayActivityDay(
                    date = "2026-08-13",
                    games = listOf(SteamPlayActivityGame(10, "Portal", 30))
                )
            ),
            updatedAt = Instant.parse("2026-08-13T09:00:00Z").toEpochMilli()
        )

        val history = updateSteamPlayActivity(
            previous = previous,
            snapshot = snapshot(
                game(
                    appId = 10,
                    name = "Portal",
                    minutes = 630,
                    lastPlayedAt = Instant.parse("2026-08-11T21:30:00Z").epochSecond
                )
            ),
            localDate = "2026-08-14",
            recordedAt = Instant.parse("2026-08-14T09:00:00Z").toEpochMilli(),
            zoneId = ZoneOffset.UTC
        )

        assertEquals("2026-08-11", history.days.single().date)
        assertEquals(30, history.days.single().totalMinutes)
        assertEquals(2, history.dateAttributionVersion)
    }

    private fun game(
        appId: Int,
        name: String,
        minutes: Int,
        recentMinutes: Int = 0,
        lastPlayedAt: Long = 0L
    ) = SteamGame(
        appId = appId,
        name = name,
        playtimeForeverMinutes = minutes,
        playtimeRecentMinutes = recentMinutes,
        lastPlayedAt = lastPlayedAt
    )

    private fun snapshot(vararg games: SteamGame) = SteamLibrarySnapshot(
        accountId = 1L,
        games = games.toList(),
        fetchedAt = 0L
    )
}
