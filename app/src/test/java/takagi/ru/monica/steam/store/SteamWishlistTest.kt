package takagi.ru.monica.steam.store

import okio.Buffer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamWishlistTest {
    @Test
    fun storeClientDoesNotAutomaticallyFollowSessionRedirectLoops() {
        val field = SteamStoreService::class.java.getDeclaredField("client").apply {
            isAccessible = true
        }
        val client = field.get(SteamStoreService()) as OkHttpClient

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun parsesOfficialWishlistDataWithLocalizedPrices() {
        val payload = """
            {
              "620": {
                "name": "Portal 2",
                "capsule": "https://cdn.example/620.jpg",
                "priority": 1,
                "added": 1700000000,
                "subs": [{
                  "id": 1234,
                  "discount_pct": 50,
                  "price": 2100,
                  "discount_block": "<div class=\"discount_original_price\">¥ 42.00</div><div class=\"discount_final_price\">¥ 21.00</div>"
                }]
              }
            }
        """.trimIndent()

        val item = SteamStoreParser.parseWishlist(payload).single()

        assertEquals(620, item.appId)
        assertEquals("Portal 2", item.name)
        assertEquals("https://cdn.example/620.jpg", item.imageUrl)
        assertEquals(1234, item.packageId)
        assertEquals(50, item.discountPercent)
        assertEquals("¥ 42.00", item.formattedInitialPrice)
        assertEquals("¥ 21.00", item.formattedFinalPrice)
        assertEquals(1700000000L, item.addedAtEpochSeconds)
    }

    @Test
    fun parsesAnEmptyOfficialWishlist() {
        assertTrue(SteamStoreParser.parseWishlist("[]").isEmpty())
        assertTrue(SteamStoreParser.parseWishlist("{}").isEmpty())
    }

    @Test
    fun cacheKeysKeepWishlistAccountsSeparate() {
        assertEquals("v2_account_7_wishlist.json", steamWishlistCacheName(7L))
        assertEquals("v2_account_8_wishlist.json", steamWishlistCacheName(8L))
        assertFalse(steamWishlistCacheName(7L) == steamWishlistCacheName(8L))
    }

    @Test
    fun officialWishlistRequestsCarryAccountSessionAndMutationSessionId() {
        val read = buildSteamWishlistRequest(
            steamId = "76561198000000000",
            page = 2,
            steamLoginSecure = "76561198000000000||token",
            countryCode = "CN"
        )
        assertEquals("store.steampowered.com", read.url.host)
        assertEquals("2", read.url.queryParameter("p"))
        assertEquals("CN", read.url.queryParameter("cc"))
        assertTrue(read.header("Cookie").orEmpty().contains("steamLoginSecure="))

        val mutation = buildSteamWishlistMutationRequest(
            appId = 620,
            add = true,
            steamLoginSecure = "76561198000000000||token",
            sessionId = "0123456789abcdef01234567",
            countryCode = "CN"
        )
        val body = Buffer().also { mutation.body?.writeTo(it) }.readUtf8()
        assertEquals("POST", mutation.method)
        assertEquals("/api/addtowishlist", mutation.url.encodedPath)
        assertTrue(mutation.header("Cookie").orEmpty().contains("sessionid=0123456789abcdef01234567"))
        assertTrue(body.contains("sessionid=0123456789abcdef01234567"))
        assertTrue(body.contains("appid=620"))
    }

    @Test
    fun loginMarkupIsRejectedInsteadOfBeingCachedAsWishlistData() {
        assertTrue(isSteamWishlistLoginResponse("<!DOCTYPE html><title>Welcome to Steam</title>"))
        assertFalse(isSteamWishlistLoginResponse("{\"620\":{\"name\":\"Portal 2\"}}"))
    }

    @Test
    fun expiredWishlistSessionRefreshesAndRetriesOnce() = runTest {
        var refreshCount = 0
        val requestedSessions = mutableListOf<String?>()

        val result = executeSteamStoreAccountRetry(
            initialCredentials = SteamStoreAccountCredentials("old-access", "old-session"),
            forceRefreshCredentials = {
                refreshCount++
                SteamStoreAccountCredentials("new-access", "new-session")
            },
            request = { credentials ->
                requestedSessions += credentials.steamLoginSecure
                if (credentials.steamLoginSecure == "old-session") {
                    throw SteamStoreWishlistSessionException()
                }
                "loaded"
            }
        )

        assertEquals("loaded", result)
        assertEquals(listOf("old-session", "new-session"), requestedSessions)
        assertEquals(1, refreshCount)
    }
}
