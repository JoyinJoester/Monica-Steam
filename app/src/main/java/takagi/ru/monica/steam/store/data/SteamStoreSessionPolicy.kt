package takagi.ru.monica.steam.store.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SteamStoreNavigationPolicy {
    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase().orEmpty()
        host == "steampowered.com" || host.endsWith(".steampowered.com") ||
            host == "steamcommunity.com" || host.endsWith(".steamcommunity.com")
    }.getOrDefault(false)
}

data class SteamWebCookieWrite(
    val url: String,
    val value: String
)

object SteamStoreSessionPolicy {
    fun cookies(steamLoginSecure: String?, sessionId: String): List<String> =
        domainCookies(
            domain = ".steampowered.com",
            steamLoginSecure = steamLoginSecure,
            sessionId = sessionId,
            includeAgeGate = true
        )

    fun cookieWrites(steamLoginSecure: String?, sessionId: String): List<SteamWebCookieWrite> {
        return buildList {
            domainCookies(
                domain = ".steampowered.com",
                steamLoginSecure = steamLoginSecure,
                sessionId = sessionId,
                includeAgeGate = true
            ).forEach { value ->
                add(SteamWebCookieWrite("https://store.steampowered.com", value))
            }
            domainCookies(
                domain = ".steamcommunity.com",
                steamLoginSecure = steamLoginSecure,
                sessionId = sessionId,
                includeAgeGate = false
            ).forEach { value ->
                add(SteamWebCookieWrite("https://steamcommunity.com", value))
            }
        }
    }

    private fun domainCookies(
        domain: String,
        steamLoginSecure: String?,
        sessionId: String,
        includeAgeGate: Boolean
    ): List<String> = buildList {
        add("sessionid=${encode(sessionId)}; Domain=$domain; Path=/; Secure; SameSite=None")
        if (includeAgeGate) {
            add("birthtime=0; Domain=$domain; Path=/; Secure")
            add("lastagecheckage=1-January-1980; Domain=$domain; Path=/; Secure")
        } else {
            add("mobileClient=android; Domain=$domain; Path=/; Secure")
            add("mobileClientVersion=777777%203.6.4; Domain=$domain; Path=/; Secure")
        }
        steamLoginSecure?.takeIf { it.isNotBlank() }?.let { value ->
            add(
                "steamLoginSecure=${encode(normalizeEncodedValue(value))}; Domain=$domain; " +
                    "Path=/; Secure; HttpOnly; SameSite=None"
            )
        }
    }

    private fun normalizeEncodedValue(value: String): String {
        if (!Regex("%[0-9a-fA-F]{2}").containsMatchIn(value)) return value
        return runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun encode(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name()
    ).replace("+", "%20")
}
