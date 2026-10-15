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

    private fun shared(id: Int, lastPlayedAt: Long, minutes: Int = 60) = SteamGame(
        id, "Shared $id", minutes, 0,
        ownership = SteamGameOwnership.FAMILY_SHARED,
        lastPlayedAt = lastPlayedAt
    )
}
