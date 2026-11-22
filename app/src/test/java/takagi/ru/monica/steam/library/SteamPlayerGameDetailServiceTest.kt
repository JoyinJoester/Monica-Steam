package takagi.ru.monica.steam.library

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.*

class SteamPlayerGameDetailServiceTest {
    private val account = SteamAccount(1, "76561198000000001", "player", "Player", "android:test", "test",
        null, null, null, "test-token", null, null, "{}", true, 0, 0, 0)
    private fun service(handler: (Request) -> ByteArray) = SteamGameLibraryService(SteamApiClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("test-token", request.url.queryParameter("access_token"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(handler(request).toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()))
    @Test fun ownedGameReturnsExactPlayerTimeWithoutPricesOrFamilyEnumeration() {
        var calls = 0
        val service = service { request ->
            calls++; assertTrue(request.url.encodedPath.contains("GetOwnedGames"))
            SteamProtoWriter().apply {
                writeVarint(1, 2)
                listOf(620 to 1740, 570 to 9999).forEach { (app, total) ->
                    writeMessage(2, SteamProtoWriter().apply {
                        writeVarint(1, app.toLong()); writeString(2, "Game $app")
                        writeVarint(3, 1026); writeVarint(4, total.toLong()); writeVarint(11, 1794902400)
                    })
                }
            }.toByteArray()
        }
        val game = (service.fetchPlayerGame(account, 620, "english") as SteamLibraryResult.Success).value!!
        assertEquals(620, game.appId); assertEquals(1740, game.playtimeForeverMinutes)
        assertEquals(1026, game.playtimeRecentMinutes); assertEquals(1794902400L, game.lastPlayedAt)
        assertEquals(1, calls)
    }
    @Test fun unownedSharedGameUsesSelectedPlayersActivity() {
        val paths = mutableListOf<String>()
        val service = service { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.contains("GetOwnedGames")) SteamProtoWriter().apply { writeVarint(1, 0) }.toByteArray()
            else {
                assertTrue(request.url.encodedPath.contains("ClientGetLastPlayedTimes"))
                SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
                    writeVarint(1, 620); writeVarint(2, 1794902400); writeVarint(3, 45); writeVarint(4, 125)
                }) }.toByteArray()
            }
        }
        val game = (service.fetchPlayerGame(account, 620, "english") as SteamLibraryResult.Success).value!!
        assertEquals(125, game.playtimeForeverMinutes); assertEquals(45, game.playtimeRecentMinutes)
        assertEquals(2, paths.size)
    }
    @Test fun missingSharedTotalIsUnavailableRatherThanZero() {
        val service = service { request ->
            if (request.url.encodedPath.contains("GetOwnedGames")) SteamProtoWriter().apply { writeVarint(1, 0) }.toByteArray()
            else SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 620); writeVarint(3, 45)
            }) }.toByteArray()
        }
        assertNull((service.fetchPlayerGame(account, 620, "english") as SteamLibraryResult.Success).value)
    }
    @Test fun unsignedOrInvalidAccountNeverRequestsActivity() {
        val service = service { error("No authenticated request expected") }
        assertEquals(SteamLibraryFailureReason.SESSION_REQUIRED,
            (service.fetchPlayerGame(account.copy(accessToken = null), 620, "english") as SteamLibraryResult.Failure).reason)
    }
}
