package takagi.ru.monica.steam.store

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreParser
import takagi.ru.monica.steam.store.data.SteamStoreService

class SteamHomeGameRankingTest {
    private fun fixture(name: String) = javaClass.getResource("/store-ranking/$name")!!.readText()
    @Test fun featuredDeduplicatesAndDoesNotCallUnpricedHardwareFree() {
        val home = SteamStoreParser.parseFeatured(fixture("featured-in.json"), "IN")
        assertEquals(home.topSellers.size, home.topSellers.map { it.appId }.distinct().size)
        val hardware = home.topSellers.first { it.appId == 4165910 }
        assertNull(hardware.finalPriceCents)
        assertFalse(hardware.isFree)
        assertEquals(16000, home.specials.first { it.appId == 1304930 }.finalPriceCents)
    }
    @Test fun homeUsesGameOnlyRankingAndPreservesRegion() {
        var requestedGames = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/api/featuredcategories" -> fixture("featured-in.json")
                "/search/results/" -> {
                    assertEquals("998", request.url.queryParameter("category1"))
                    assertEquals("topsellers", request.url.queryParameter("filter"))
                    requestedGames = true
                    fixture("topsellers-in.json")
                }
                else -> "<html></html>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val home = SteamStoreService(client = client).featured()
        assertTrue(requestedGames)
        assertTrue(home.topSellers.size >= 6)
        assertFalse(home.topSellers.any { it.appId == 4165910 || it.appId == 4165890 })
        assertTrue(home.topSellers.any { it.isFree })
        assertEquals(home.topSellers.size, home.topSellers.map { it.appId }.distinct().size)
    }
    @Test fun gameRankingFailureDoesNotFallBackToHardware() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.encodedPath == "/api/featuredcategories") fixture("featured-in.json") else "<html></html>"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody("text/plain".toMediaType())).build()
        }.build()
        val home = SteamStoreService(client = client).featured()
        assertTrue(home.topSellers.isEmpty())
        assertTrue(home.specials.isNotEmpty())
    }
}
