package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.data.*
import takagi.ru.monica.steam.store.domain.*

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun cacheKeysKeepWishlistAccountsSeparate() {
        assertEquals("v2_account_7_wishlist.json", steamWishlistCacheName(7L))
        assertEquals("v2_account_8_wishlist.json", steamWishlistCacheName(8L))
        assertFalse(steamWishlistCacheName(7L) == steamWishlistCacheName(8L))
    }

    @Test
    fun wishlistCanUseAccessTokenEmbeddedInSecureCookie() {
        assertEquals(
            "token-value",
            effectiveSteamStoreAccessToken(
                accessToken = null,
                steamLoginSecure = "76561198000000000||token-value"
            )
        )
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
