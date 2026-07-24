package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.data.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreSessionPolicyTest {
    @Test
    fun allowsOnlyOfficialSteamHttpsNavigation() {
        assertTrue(SteamStoreNavigationPolicy.isAllowed("https://store.steampowered.com/cart/"))
        assertTrue(SteamStoreNavigationPolicy.isAllowed("https://checkout.steampowered.com/"))
        assertTrue(SteamStoreNavigationPolicy.isAllowed("https://steamcommunity.com/login/home/"))
        assertFalse(SteamStoreNavigationPolicy.isAllowed("http://store.steampowered.com/cart/"))
        assertFalse(SteamStoreNavigationPolicy.isAllowed("https://store.steampowered.com.evil.example/"))
        assertFalse(SteamStoreNavigationPolicy.isAllowed("javascript:alert(1)"))
    }

    @Test
    fun buildsEncodedSecureLoginCookie() {
        val cookies = SteamStoreSessionPolicy.cookies(
            steamLoginSecure = "76561198000000000||token/value+with spaces",
            sessionId = "abcdef0123456789abcdef01"
        )
        val secure = cookies.single { it.startsWith("steamLoginSecure=") }
        assertTrue(secure.contains("%7C%7C"))
        assertTrue(secure.contains("Secure"))
        assertTrue(secure.contains("HttpOnly"))
        assertFalse(secure.contains(" with spaces"))
        assertTrue(cookies.any { it.startsWith("sessionid=abcdef") })
    }

    @Test
    fun writesSessionCookiesToStoreAndCommunityDomains() {
        val writes = SteamStoreSessionPolicy.cookieWrites(
            steamLoginSecure = "76561198000000000||token",
            sessionId = "abcdef0123456789abcdef01"
        )

        assertTrue(writes.any { it.url == "https://store.steampowered.com" })
        assertTrue(writes.any { it.url == "https://steamcommunity.com" })
        assertTrue(writes.any {
            it.url == "https://steamcommunity.com" &&
                it.value.contains("Domain=.steamcommunity.com") &&
                it.value.startsWith("steamLoginSecure=")
        })
    }

    @Test
    fun communityDesktopModeUsesBrowserUaWithoutLegacyMobileCookies() {
        val defaultUserAgent =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8 Build/AP3A; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/138.0.7204.157 Mobile Safari/537.36"

        val userAgent = SteamWebClientPolicy.userAgent(
            mode = SteamWebClientMode.COMMUNITY_DESKTOP,
            defaultUserAgent = defaultUserAgent
        )
        val writes = SteamStoreSessionPolicy.cookieWrites(
            steamLoginSecure = "76561198000000000||token",
            sessionId = "abcdef0123456789abcdef01",
            clientMode = SteamWebClientMode.COMMUNITY_DESKTOP
        )

        assertTrue(userAgent.contains("Windows NT 10.0; Win64; x64"))
        assertTrue(userAgent.contains("Chrome/138.0.7204.157"))
        assertFalse(userAgent.contains("; wv"))
        assertFalse(userAgent.contains(" Mobile "))
        val legacyMobileCookies = writes.filter {
            it.value.startsWith("mobileClient=") ||
                it.value.startsWith("mobileClientVersion=")
        }
        assertEquals(2, legacyMobileCookies.size)
        assertTrue(legacyMobileCookies.all { it.value.contains("Max-Age=0") })
        assertFalse(legacyMobileCookies.any { it.value.contains("=android") })
        assertFalse(legacyMobileCookies.any { it.value.contains("777777") })
        assertTrue(writes.any { it.value.startsWith("steamLoginSecure=") })
    }

    @Test
    fun keepsPreviouslyEncodedSteamLoginSecureAtSingleEncodingLevel() {
        val raw = SteamStoreSessionPolicy.cookies(
            steamLoginSecure = "76561198000000000%7C%7Ctoken%2Fvalue",
            sessionId = "abcdef0123456789abcdef01"
        ).single { it.startsWith("steamLoginSecure=") }

        assertTrue(raw.contains("%7C%7C"))
        assertTrue(raw.contains("%2F"))
        assertFalse(raw.contains("%257C%257C"))
        assertEquals(1, "%7C%7C".toRegex().findAll(raw).count())
    }
}
