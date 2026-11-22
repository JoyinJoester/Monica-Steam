package takagi.ru.monica.steam.space.data

import java.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamSpaceAppNamesTest {
    private val account = SteamAccount(7, "76561198000000001", "fixture", "Fixture", "test", "",
        null, null, null, "synthetic-token", null, null, "{}", true, 0, 0, 0)

    @Test fun officialHttpBatchSuppliesActualNamesBeforeSlowMediaAndNeverCallsCmForMetadata() {
        var requests = 0
        var now = 0L
        val api = SteamApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests++
            assertEquals("GET", request.method)
            assertEquals("api.steampowered.com", request.url.host)
            assertEquals("/IStoreBrowseService/GetItems/v1/", request.url.encodedPath)
            assertEquals("synthetic-token", request.url.queryParameter("access_token"))
            val fields = SteamProtoReader(Base64.getDecoder().decode(request.url.queryParameter("input_protobuf_encoded"))).parseAll()
            assertEquals(listOf(1091500), fields.filter { it.number == 1 }.map { SteamProtoReader(it.bytes!!).parse().getValue(1).asInt })
            assertEquals("schinese", SteamProtoReader(fields.single { it.number == 2 }.bytes!!).parse().getValue(1).asString)
            val data = SteamProtoReader(fields.single { it.number == 3 }.bytes!!).parse()
            assertEquals(1L, data.getValue(10).asLong)
            assertEquals(0L, data.getValue(19).asLong)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(items(app(1091500, "Cyberpunk 2077"), app(999, "Unrelated")).toResponseBody()).build()
        }.build())
        val cm = object : SteamCmGateway {
            override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray = when (method) {
                SteamSpaceService.NEWS_METHOD -> SteamProtoWriter().apply {
                    for (kind in listOf(2L, 13L)) writeMessage(1, SteamProtoWriter().apply {
                        writeVarint(1, kind); writeVarint(2, 1800000000); writeFixed64(3, account.steamId.toLong())
                        writeFixed64(5, 1091500); if (kind == 13L) writeFixed64(11, 123)
                    })
                }.toByteArray()
                SteamSpaceService.FILE_METHOD -> { now = 21_000_000_000L; ByteArray(0) }
                else -> error("Metadata must use HTTP, not CM: $method")
            }
            override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray) = error("Unrelated CM message")
        }
        val page = SteamSpaceService(cm, { now }, { _, _ -> emptyMap() }, SteamSpaceAppNames(api)::load)
            .fetchPage(account, "schinese", null)
        assertEquals(1, requests)
        assertTrue(page.events.all { it.appNames == mapOf(1091500 to "Cyberpunk 2077") })
    }

    @Test fun failedWrongTypeOrConflictingIdentitiesNeverSupplyAGameName() {
        val response = items(
            app(620, "Portal 2"), app(621, "Package", type = 1), app(622, "Failed", success = 9),
            app(623, "Conflicting", redundant = 624), app(625, "", redundant = 625)
        )
        assertEquals(mapOf(620 to "Portal 2"), SteamSpaceParser.appNames(response))
    }

    @Test fun authenticationFailureReachesOwnerInsteadOfBecomingAnEmptyNameMap() {
        val api = SteamApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                .body(ByteArray(0).toResponseBody()).build()
        }.build())
        val failure = assertThrows(SteamApiException::class.java) { SteamSpaceAppNames(api).load(account, listOf(620), "english") }
        assertEquals(401, failure.httpStatusCode)
    }

    @Test fun emptyBatchStillResolvesScreenshotIdsFromPublicStoreAndCachesOnlySuccessfulNames() {
        val calls = mutableListOf<String>()
        var expectedLanguage = "schinese"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            calls += request.url.host
            val body = if (request.url.host == "api.steampowered.com") ByteArray(0) else {
                assertNull(request.url.queryParameter("access_token"))
                assertNull(request.header("Cookie"))
                assertEquals("basic", request.url.queryParameter("filters"))
                assertEquals(expectedLanguage, request.url.queryParameter("l"))
                val id = request.url.queryParameter("appids")!!.toInt()
                val name = if (id == 815370) "绿色地狱" else "赛博朋克 2077"
                """{"$id":{"success":true,"data":{"steam_appid":$id,"name":"$name"}}}""".toByteArray()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val lookup = SteamSpaceAppNames(SteamApiClient(client), client)
        val expected = mapOf(815370 to "绿色地狱", 1091500 to "赛博朋克 2077")
        assertEquals(expected, lookup.load(account, expected.keys.toList(), "schinese"))
        assertEquals(listOf("api.steampowered.com", "store.steampowered.com", "store.steampowered.com"), calls)
        assertEquals(expected, lookup.load(account.copy(id = 8), expected.keys.toList(), "schinese"))
        assertEquals(3, calls.size) // Only public names are shared; no private account snapshot.
        expectedLanguage = "english"
        lookup.load(account, expected.keys.toList(), "english")
        assertEquals(6, calls.size)
    }

    @Test fun failedBatchUsesBoundedFallbackAndWrongPublicIdentityRemainsUnknown() {
        var publicCalls = 0
        var matching = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request()
            val api = r.url.host == "api.steampowered.com"
            val id = r.url.queryParameter("appids")?.toInt() ?: 0
            if (!api) publicCalls++
            val body = if (api) "" else """{"$id":{"success":true,"data":{"steam_appid":${if (matching) id else id+1},"name":"Confirmed"}}}"""
            Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(if (api) 503 else 200).message("fixture")
                .body(body.toResponseBody()).build()
        }.build()
        val lookup = SteamSpaceAppNames(SteamApiClient(client), client)
        assertTrue(lookup.load(account, (1..12).toList(), "english").isEmpty())
        assertEquals(SteamSpaceAppNames.MAX_PUBLIC_LOOKUPS, publicCalls)
        matching = true
        assertEquals(mapOf(1 to "Confirmed"), lookup.load(account, listOf(1), "english"))
        assertEquals(9, publicCalls) // Unknown names were not cached as confirmed absences.
    }

    @Test fun publicMetadataRequiresExplicitSuccessRequestedIdAndBoundedText() {
        assertNull(SteamSpaceAppNames.parsePublicName(1, """{"1":{"success":false,"data":{"steam_appid":1,"name":"Hidden"}}}"""))
        assertNull(SteamSpaceAppNames.parsePublicName(1, """{"2":{"success":true,"data":{"steam_appid":2,"name":"Other"}}}"""))
        assertNull(SteamSpaceAppNames.parsePublicName(1, """{"1":{"success":true,"data":{"steam_appid":1,"name":25}}}"""))
        assertNull(SteamSpaceAppNames.parsePublicName(1, """{"1":{"success":true,"data":{"steam_appid":1,"name":"${"x".repeat(513)}"}}}"""))
        assertEquals("Portal", SteamSpaceAppNames.parsePublicName(1, """{"1":{"success":true,"data":{"steam_appid":1,"name":" Portal "}}}"""))
    }

    private fun items(vararg apps: SteamProtoWriter) = SteamProtoWriter().apply {
        apps.forEach { writeMessage(1, it) }
    }.toByteArray()
    private fun app(id: Int, name: String, type: Long = 0, success: Long = 1, redundant: Int = 0) = SteamProtoWriter().apply {
        writeVarint(1, type); writeVarint(2, id.toLong()); writeVarint(3, success)
        writeString(6, name); writeVarint(9, redundant.toLong())
    }
}
