package takagi.ru.monica.steam.store

import org.junit.Assert.assertFalse
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
}
