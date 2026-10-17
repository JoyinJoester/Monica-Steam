package takagi.ru.monica.steam.library

import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamLibraryPlaytimeServiceTest {
    @Test
    fun clientActivityUsesSelectedTokenAndOnlyEnrichesSharedLicenseIds() {
        val requests = mutableListOf<Request>()
        val service = service(requests) { request ->
            assertTrue(request.url.encodedPath.contains("ClientGetLastPlayedTimes"))
            val fields = SteamProtoReader(Base64.getDecoder().decode(
                request.url.queryParameter("input_protobuf_encoded")
            )).parseAll()
            assertEquals(0L, fields.single().asLong)
            assertEquals("selected-token", request.url.queryParameter("access_token"))
            200 to clientResponse(20 to 135, 10 to 999, 900 to 888)
        }
        val result = service.fetch(account(), setOf(20))
        assertEquals(setOf(20), result.items.keys)
        assertEquals(135, result.items.getValue(20).recentMinutes)
        assertTrue(result.unavailableAppIds.isEmpty())
        assertEquals(listOf("GET"), requests.map { it.method })
    }

    @Test
    fun missingClientRecordsUseAllRecentGamesForSelectedPlayerWithoutSummingSources() {
        val requests = mutableListOf<Request>()
        val recentGames = (20..32).joinToString(",") {
            """{"appid":$it,"playtime_2weeks":$it,"playtime_forever":1000}"""
        }
        val service = service(requests) { request ->
            if (request.url.encodedPath.contains("ClientGetLastPlayedTimes")) {
                200 to clientResponse(20 to 135)
            } else {
                assertTrue(request.url.encodedPath.contains("GetRecentlyPlayedGames"))
                assertEquals(account().steamId, request.url.queryParameter("steamid"))
                assertEquals("selected-token", request.url.queryParameter("access_token"))
                assertEquals("0", request.url.queryParameter("count"))
                200 to """{"response":{"total_count":13,"games":[$recentGames]}}""".toByteArray()
            }
        }
        val result = service.fetch(account(), setOf(20, 32, 33))
        assertEquals(setOf(20, 32), result.items.keys)
        assertEquals(135, result.items.getValue(20).recentMinutes)
        assertEquals(32, result.items.getValue(32).recentMinutes)
        assertTrue(result.unavailableAppIds.isEmpty())
        assertEquals(2, requests.size)
    }

    @Test
    fun emptyMalformedAndRejectedClientResponsesUseRecentEndpoint() {
        for ((code, body) in listOf(
            200 to ByteArray(0), 403 to ByteArray(0), 200 to byteArrayOf(10, 127)
        )) {
            val service = service { request ->
                if (request.url.encodedPath.contains("ClientGetLastPlayedTimes")) code to body
                else 200 to """{"response":{"total_count":1,"games":[
                    {"appid":20,"playtime_2weeks":75,"playtime_forever":600}
                ]}}""".toByteArray()
            }
            val result = service.fetch(account(), setOf(20))
            assertEquals(75, result.items.getValue(20).recentMinutes)
            assertEquals(600, result.items.getValue(20).totalMinutes)
            assertEquals(null, result.items.getValue(20).lastPlayedAt)
            assertTrue(result.unavailableAppIds.isEmpty())
        }
    }

    @Test
    fun failedSupplementKeepsSuccessfulClientRecords() {
        val service = service { request ->
            if (request.url.encodedPath.contains("ClientGetLastPlayedTimes")) {
                200 to clientResponse(20 to 135)
            } else 503 to ByteArray(0)
        }
        val result = service.fetch(account(), setOf(20, 21))
        assertEquals(135, result.items.getValue(20).recentMinutes)
        assertEquals(setOf(21), result.unavailableAppIds)
    }

    @Test
    fun explicitEmptyRecentResponseIsSuccessful() {
        val service = service { request ->
            if (request.url.encodedPath.contains("ClientGetLastPlayedTimes")) 200 to ByteArray(0)
            else 200 to """{"response":{"total_count":0}}""".toByteArray()
        }
        val result = service.fetch(account(), setOf(20))
        assertTrue(result.items.isEmpty())
        assertTrue(result.unavailableAppIds.isEmpty())
    }

    @Test
    fun invalidOrIncompleteRecentResponsesAreUnavailableInsteadOfZeroMinutes() {
        for (payload in listOf(
            "{}",
            """{"response":{}}""",
            """{"response":{"total_count":1}}""",
            """{"response":{"total_count":2,"games":[{"appid":20,"playtime_2weeks":30}]}}""",
            """{"response":{"total_count":1,"games":[{"appid":20}]}}""",
            """{"response":{"total_count":1,"games":[{"appid":20,"playtime_2weeks":-1}]}}""",
            """{"response":{"total_count":1,"games":[{"appid":20,"playtime_2weeks":2147483648}]}}""",
            """{"response":{"total_count":2,"games":[{"appid":20,"playtime_2weeks":30},{"appid":20,"playtime_2weeks":30}]}}"""
        )) {
            val service = service { request ->
                if (request.url.encodedPath.contains("ClientGetLastPlayedTimes")) 200 to ByteArray(0)
                else 200 to payload.toByteArray()
            }
            assertEquals(setOf(20), service.fetch(account(), setOf(20)).unavailableAppIds)
        }
    }

    @Test
    fun clientParserPreservesZeroMinutesAndUnsignedTimestamps() {
        val payload = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 20L)
                writeVarint(2, 4_000_000_000L)
                writeVarint(4, 600L)
            })
        }.toByteArray()
        val activity = SteamLibraryPlaytimeService.parseClientActivity(payload).getValue(20)
        assertEquals(0, activity.recentMinutes)
        assertEquals(600, activity.totalMinutes)
        assertEquals(4_000_000_000L, activity.lastPlayedAt)
        for (minutes in listOf(-1L, Int.MAX_VALUE.toLong() + 1)) {
            val invalid = SteamProtoWriter().apply {
                writeMessage(1, SteamProtoWriter().apply {
                    writeVarint(1, 20L)
                    writeVarint(3, minutes)
                })
            }.toByteArray()
            assertTrue(runCatching { SteamLibraryPlaytimeService.parseClientActivity(invalid) }.isFailure)
        }
    }

    @Test
    fun noSharedGamesOrMissingSessionDoesNotMakeRequests() {
        val requests = mutableListOf<Request>()
        val service = service(requests) { error("No request expected") }
        assertTrue(service.fetch(account(), emptySet()).items.isEmpty())
        assertEquals(setOf(20), service.fetch(account().copy(accessToken = null), setOf(20)).unavailableAppIds)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun partialRefreshKeepsCachedMinutesOnlyForUnavailableGamesOfSameAccount() {
        val cached = snapshot(20 to 10, 21 to 22)
        val fresh = snapshot(20 to 135, 21 to 0).copy(recentPlaytimeUnavailableAppIds = setOf(21))
        val merged = mergeLibraryDashboardSnapshot(fresh, cached, inventoryFailure)
        assertEquals(listOf(135, 22), merged.games.map { it.playtimeRecentMinutes })
        assertEquals(157L, merged.recentPlaytimeMinutes)
        val restored = Json.decodeFromString<SteamLibrarySnapshot>(Json.encodeToString(merged))
        assertEquals(merged, restored)
        assertEquals(listOf(135, 0), mergeLibraryDashboardSnapshot(
            fresh, cached.copy(accountId = 2), inventoryFailure
        ).games.map { it.playtimeRecentMinutes })
        val owned = fresh.copy(games = fresh.games.map { it.copy(ownership = SteamGameOwnership.OWNED) })
        assertEquals(listOf(135, 0), mergeLibraryDashboardSnapshot(
            owned, cached, inventoryFailure
        ).games.map { it.playtimeRecentMinutes })
    }

    @Test
    fun successfulZeroMinutesClearsPreviousRecentActivity() {
        val merged = mergeLibraryDashboardSnapshot(snapshot(20 to 0), snapshot(20 to 135), inventoryFailure)
        assertEquals(0L, merged.recentPlaytimeMinutes)
        assertFalse(merged.games.single().wasRecentlyPlayed(1_800_000_000L))
        val oldSnapshot = Json.decodeFromString<SteamLibrarySnapshot>(
            """{"accountId":1,"games":[],"fetchedAt":1}"""
        )
        assertTrue(oldSnapshot.recentPlaytimeUnavailableAppIds.isEmpty())
    }

    private fun clientResponse(vararg records: Pair<Int, Int>) = SteamProtoWriter().apply {
        records.forEach { (id, recent) ->
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, id.toLong())
                writeVarint(2, 1_799_999_940L)
                writeVarint(3, recent.toLong())
                writeVarint(4, 7_335L)
            })
        }
    }.toByteArray()

    private fun service(
        requests: MutableList<Request> = mutableListOf(),
        response: (Request) -> Pair<Int, ByteArray>
    ): SteamLibraryPlaytimeService = SteamLibraryPlaytimeService(SteamApiClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val (code, body) = response(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test response")
                .body(body.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
    ))

    private fun snapshot(vararg minutes: Pair<Int, Int>) = SteamLibrarySnapshot(
        accountId = 1,
        games = minutes.map { (id, recent) ->
            SteamGame(id, "Shared $id", 7_335, recent, ownership = SteamGameOwnership.FAMILY_SHARED)
        },
        fetchedAt = 1_800_000_000_000L
    )

    private val inventoryFailure = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)

    private fun account() = SteamAccount(
        id = 1, steamId = "76561198000000001", accountName = "selected", displayName = "Selected",
        deviceId = "android:test", sharedSecret = "test", identitySecret = null, revocationCode = null,
        tokenGid = null, accessToken = "selected-token", refreshToken = null, steamLoginSecure = null,
        rawSteamGuardJson = "{}", selected = true, sortOrder = 0, createdAt = 0, updatedAt = 0
    )
}
