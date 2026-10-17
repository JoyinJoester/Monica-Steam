package takagi.ru.monica.steam.library

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.family.SteamFamilyLibraryService
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryFilterSelection
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryOwnershipFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlayStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibrarySortOrder
import takagi.ru.monica.steam.library.filter.domain.countSteamLibraryGames
import takagi.ru.monica.steam.library.filter.domain.filterSteamLibraryGames
import takagi.ru.monica.steam.library.ui.SteamLibraryGameFilter
import takagi.ru.monica.steam.library.ui.SteamLibraryGameSectionType
import takagi.ru.monica.steam.library.ui.buildSteamLibrarySections
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamFamilyRecentActivityTest {
    private val now = 1_800_000_000L
    private val fortnight = 14L * 24L * 60L * 60L
    private val games = listOf(
        SteamGame(1, "Owned recent", 600, 90),
        shared(2, now - 60),
        shared(3, now - fortnight),
        shared(4, now - fortnight - 1),
        shared(5, 0, minutes = 0),
        shared(6, now + 1),
        shared(7, now, minutes = 0),
        shared(8, now - fortnight - 1, minutes = 0)
    )

    @Test
    fun screenshotRecentMinutesSortAcrossOwnershipInDashboardSearchAndFilters() {
        // Minutes match the displayed hours; synthetic dates deliberately favor the wrong order.
        val input = listOf(
            SteamGame(1, "Balatro", 3330, 318, lastPlayedAt = now - 60),
            shared(2, now - 120, 66).copy(name = "Danganronpa", playtimeRecentMinutes = 1),
            SteamGame(3, "Bloons TD 6", 10080, 1146, lastPlayedAt = now - 180),
            shared(4, now - 240, 114).copy(name = "Baldur's Gate 3", playtimeRecentMinutes = 114),
            shared(5, now - 300, 762).copy(name = "Cyberpunk 2077", playtimeRecentMinutes = 582),
            shared(6, now - 360, 294).copy(name = "Hades", playtimeRecentMinutes = 60)
        )
        val expected = listOf(3, 5, 1, 4, 6, 2)
        for (ordered in listOf(input, input.reversed(), input.drop(2) + input.take(2))) {
            for (selection in listOf(
                SteamLibraryFilterSelection(),
                SteamLibraryFilterSelection(playStatus = SteamLibraryPlayStatusFilter.RECENT),
                SteamLibraryFilterSelection(sortOrder = SteamLibrarySortOrder.RECENT_PLAYTIME)
            )) {
                assertEquals(expected, buildSteamLibrarySections(ordered, "", selection, now)
                    .first().games.map { it.appId })
                assertEquals(listOf(1, 4, 6, 2), filterSteamLibraryGames(ordered, "a", selection, now)
                    .map { it.appId })
            }
            for (filter in listOf(SteamLibraryGameFilter.ALL, SteamLibraryGameFilter.RECENT)) {
                assertEquals(expected, buildSteamLibrarySections(ordered, "", filter, now)
                    .first().games.map { it.appId })
            }
        }
    }

    @Test
    fun refreshedAndCachedSharedMinutesReorderTheDashboard() {
        val cached = SteamLibrarySnapshot(1, listOf(
            SteamGame(10, "Owned", 600, 90, lastPlayedAt = now),
            shared(20, now - 60)
        ), now * 1000)
        fun recentIds(snapshot: SteamLibrarySnapshot) = buildSteamLibrarySections(
            snapshot.games, "", SteamLibraryFilterSelection(), now
        ).first().games.map { it.appId }

        assertEquals(listOf(10, 20), recentIds(cached))
        val fresh = cached.copy(games = listOf(
            cached.games.first(), cached.games.last().copy(playtimeRecentMinutes = 180)
        ))
        val inventoryFailure = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
        val merged = mergeLibraryDashboardSnapshot(fresh, cached, inventoryFailure)
        assertEquals(listOf(20, 10), recentIds(merged))
        val restored = Json.decodeFromString<SteamLibrarySnapshot>(Json.encodeToString(merged))
        assertEquals(listOf(20, 10), recentIds(restored))
        val unavailable = cached.copy(recentPlaytimeUnavailableAppIds = setOf(20))
        assertEquals(listOf(20, 10), recentIds(
            mergeLibraryDashboardSnapshot(unavailable, restored, inventoryFailure)
        ))
    }

    @Test
    fun bothDashboardPathsIncludeSharedActivityInExactlyOneSection() {
        val current = buildSteamLibrarySections(games, "", SteamLibraryFilterSelection(), now)
        val legacy = buildSteamLibrarySections(games, "", SteamLibraryGameFilter.ALL, now)
        assertEquals(current, legacy)
        assertEquals(listOf(1, 7, 2, 3), current[0].games.map(SteamGame::appId))
        assertEquals(setOf(4, 6, 8), current[1].games.map(SteamGame::appId).toSet())
        assertEquals(listOf(5), current[2].games.map(SteamGame::appId))
        assertEquals(games.size, current.sumOf { it.games.size })
        assertEquals(games.size, current.flatMap { it.games }.distinctBy { it.appId }.size)
    }

    @Test
    fun recentDashboardsMixOwnershipByRecentMinutesRegardlessOfInputOrder() {
        val input = interleavedGames()
        for (ordered in listOf(input, input.reversed(), input.drop(2) + input.take(2))) {
            val current = buildSteamLibrarySections(ordered, "", SteamLibraryFilterSelection(), now)
            val legacy = buildSteamLibrarySections(ordered, "", SteamLibraryGameFilter.ALL, now)
            assertEquals(listOf(10, 11, 12, 13), current.first().games.map(SteamGame::appId))
            assertEquals(current, legacy)
        }
    }

    @Test
    fun recentFiltersAndSearchKeepRecentMinutesOrderAcrossOwnership() {
        val input = interleavedGames()
        val selection = SteamLibraryFilterSelection(playStatus = SteamLibraryPlayStatusFilter.RECENT)
        val expected = listOf(10, 11, 12, 13)
        assertEquals(expected, filterSteamLibraryGames(input, "game", selection, now).map { it.appId })
        assertEquals(expected, filterSteamLibraryGames(
            input, "game", SteamLibraryFilterSelection(), now
        ).map { it.appId })
        assertEquals(expected, buildSteamLibrarySections(input, "game", selection, now)
            .single().games.map { it.appId })
        assertEquals(expected, buildSteamLibrarySections(input, "game", SteamLibraryGameFilter.RECENT, now)
            .single().games.map { it.appId })
    }

    @Test
    fun missingAndInvalidDatesDoNotOverrideReportedRecentMinutes() {
        val input = listOf(
            SteamGame(10, "Missing", 500, 50),
            SteamGame(11, "Future", 500, 60, lastPlayedAt = Long.MAX_VALUE),
            SteamGame(12, "Negative", 500, 40, lastPlayedAt = -1),
            SteamGame(13, "Owned", 10, 1, lastPlayedAt = now - 60),
            shared(14, now)
        )
        val selection = SteamLibraryFilterSelection(playStatus = SteamLibraryPlayStatusFilter.RECENT)
        val expected = listOf(11, 10, 12, 13, 14)
        assertEquals(expected, buildSteamLibrarySections(input, "", SteamLibraryGameFilter.ALL, now)
            .first().games.map { it.appId })
        assertEquals(expected, filterSteamLibraryGames(input, "", selection, now).map { it.appId })
        assertEquals(151, input.sumOf { it.playtimeRecentMinutes })
        assertEquals(0L, input.first().lastPlayedAt)
    }

    @Test
    fun equalDatesHaveStableOrderWithoutAnOwnershipPreference() {
        val input = listOf(
            SteamGame(12, "Same", 60, 0, lastPlayedAt = now),
            shared(11, now).copy(name = "Same"),
            SteamGame(10, "Alpha", 60, 0, lastPlayedAt = now)
        )
        for (ordered in listOf(input, input.reversed())) {
            val current = buildSteamLibrarySections(ordered, "", SteamLibraryFilterSelection(), now)
            val legacy = buildSteamLibrarySections(ordered, "", SteamLibraryGameFilter.ALL, now)
            assertEquals(listOf(10, 11, 12), current.first().games.map { it.appId })
            assertEquals(current, legacy)
        }
    }

    @Test
    fun explicitPlaytimeAndNameSortsKeepTheirSelectedOrder() {
        val selection = SteamLibraryFilterSelection(playStatus = SteamLibraryPlayStatusFilter.RECENT)
        val expectedOrders = mapOf(
            SteamLibrarySortOrder.RECENT_PLAYTIME to listOf(10, 11, 12, 13),
            SteamLibrarySortOrder.TOTAL_PLAYTIME to listOf(10, 12, 13, 11),
            SteamLibrarySortOrder.NAME_ASCENDING to listOf(10, 11, 12, 13),
            SteamLibrarySortOrder.NAME_DESCENDING to listOf(13, 12, 11, 10)
        )
        for ((sortOrder, expected) in expectedOrders) {
            val results = buildSteamLibrarySections(
                interleavedGames(), "", selection.copy(sortOrder = sortOrder), now
            )
            assertEquals(expected, results.single().games.map { it.appId })
        }
    }

    @Test
    fun recentFilterSearchOwnershipAndPreviewCountAgree() {
        val selection = SteamLibraryFilterSelection(
            ownership = SteamLibraryOwnershipFilter.FAMILY_SHARED,
            playStatus = SteamLibraryPlayStatusFilter.RECENT
        )
        val results = filterSteamLibraryGames(games, "Shared", selection, now)
        assertEquals(setOf(2, 3, 7), results.map(SteamGame::appId).toSet())
        assertEquals(3, countSteamLibraryGames(games, "Shared", selection, now))
        assertEquals(results, buildSteamLibrarySections(games, "Shared", selection, now).single().games)
        val legacy = buildSteamLibrarySections(games, "Shared", SteamLibraryGameFilter.RECENT, now)
        assertEquals(results.map { it.appId }.toSet(), legacy.single().games.map { it.appId }.toSet())
        assertTrue(results.all { it.playtimeRecentMinutes == 0 })
    }

    @Test
    fun dateOnlyActivityDoesNotAlsoAppearAsUnplayed() {
        val selection = SteamLibraryFilterSelection(playStatus = SteamLibraryPlayStatusFilter.UNPLAYED)
        assertEquals(listOf(5), filterSteamLibraryGames(games, "", selection, now).map { it.appId })
        val played = filterSteamLibraryGames(
            games, "", selection.copy(playStatus = SteamLibraryPlayStatusFilter.PLAYED), now
        )
        assertEquals(setOf(1, 2, 3, 4, 6, 7, 8), played.map { it.appId }.toSet())
        assertFalse(shared(9, Long.MAX_VALUE).wasRecentlyPlayed(now))
        assertFalse(shared(9, -1).wasRecentlyPlayed(now))
    }

    @Test
    fun cachedActivitySurvivesSerializationAndFailedFamilyRefresh() {
        val snapshot = SteamLibrarySnapshot(1, games, now * 1000)
        val restored = Json.decodeFromString<SteamLibrarySnapshot>(Json.encodeToString(snapshot))
        val merged = mergeLibraryDashboardSnapshot(
            fresh = snapshot.copy(
                games = listOf(games.first()), familyShareFailure = SteamLibraryFailureReason.NETWORK
            ),
            cached = restored,
            inventoryResult = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
        )
        val recent = buildSteamLibrarySections(merged.games, "", SteamLibraryFilterSelection(), now)
            .single { it.type == SteamLibraryGameSectionType.RECENT }
        assertEquals(listOf(1, 7, 2, 3), recent.games.map { it.appId })
        assertEquals(90L, merged.recentPlaytimeMinutes)
        // The same cached date naturally expires; it is never converted to synthetic minutes.
        assertFalse(restored.sharedGames.first().wasRecentlyPlayed(now + fortnight))
    }

    @Test
    fun parserPreservesSecondsWithoutNarrowingToSignedInt() {
        for ((raw, expected) in listOf(4_000_000_000L to 4_000_000_000L, -1L to 0L, 0L to 0L)) {
            val payload = SteamProtoWriter().apply {
                writeMessage(1, SteamProtoWriter().apply {
                    writeVarint(1, 20)
                    writeVarint(12, raw)
                    writeVarint(13, 120)
                })
            }.toByteArray()
            val parsed = SteamFamilyLibraryService.parseSharedLibraryApps(payload).single()
            assertEquals(expected, parsed.lastPlayedAt)
            assertEquals(120, parsed.playtimeForeverMinutes)
            assertEquals(0, parsed.playtimeRecentMinutes)
        }
    }

    private fun interleavedGames() = listOf(
        SteamGame(10, "A game", 1_000, 200, lastPlayedAt = now - 300),
        SteamGame(11, "B game", 10, 2, lastPlayedAt = now - 60),
        shared(12, now - 30).copy(name = "C game"),
        shared(13, now - 120).copy(name = "D game")
    )

    private fun shared(id: Int, lastPlayedAt: Long, minutes: Int = 60) = SteamGame(
        id, "Shared $id", minutes, 0,
        ownership = SteamGameOwnership.FAMILY_SHARED,
        lastPlayedAt = lastPlayedAt
    )
}
