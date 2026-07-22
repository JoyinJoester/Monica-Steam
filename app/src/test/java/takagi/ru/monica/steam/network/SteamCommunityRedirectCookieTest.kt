package takagi.ru.monica.steam.network

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCommunityRedirectCookieTest {
    @Test
    fun eligibilityCookieIsAddedWithoutDroppingAccountCookies() {
        val cookieJar = linkedMapOf(
            "steamLoginSecure" to "account-session",
            "sessionid" to "original-session"
        )
        val headers = Headers.Builder()
            .add(
                "Set-Cookie",
                "webTradeEligibility=%7B%22allowed%22%3A1%7D; Max-Age=30; Path=/; HttpOnly"
            )
            .add("Set-Cookie", "sessionid=server-session; Path=/; Secure")
            .build()

        val changed = SteamApiClient().absorbCommunityCookies(
            requestUrl = "https://steamcommunity.com/market/eligibilitycheck/".toHttpUrl(),
            headers = headers,
            cookieJar = cookieJar,
            nowMillis = 1_000L
        )

        assertEquals("account-session", cookieJar["steamLoginSecure"])
        assertEquals("server-session", cookieJar["sessionid"])
        assertTrue(cookieJar.containsKey("webTradeEligibility"))
        assertEquals(setOf("webTradeEligibility", "sessionid"), changed)
    }
}
