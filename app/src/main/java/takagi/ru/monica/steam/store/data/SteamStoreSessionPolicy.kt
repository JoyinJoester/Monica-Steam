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

enum class SteamWebClientMode {
    DEFAULT,
    COMMUNITY_DESKTOP
}

object SteamWebClientPolicy {
    private val chromeVersionPattern = Regex("Chrome/[0-9.]+")

    fun userAgent(mode: SteamWebClientMode, defaultUserAgent: String): String = when (mode) {
        SteamWebClientMode.DEFAULT -> defaultUserAgent
        SteamWebClientMode.COMMUNITY_DESKTOP -> {
            val chromeVersion = chromeVersionPattern.find(defaultUserAgent)?.value
                ?: "Chrome/120.0.0.0"
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) $chromeVersion Safari/537.36"
        }
    }

    fun usesDesktopLayout(mode: SteamWebClientMode): Boolean =
        mode == SteamWebClientMode.COMMUNITY_DESKTOP
}

object SteamStoreSessionPolicy {
    fun cookies(steamLoginSecure: String?, sessionId: String): List<String> =
        domainCookies(
            domain = ".steampowered.com",
            steamLoginSecure = steamLoginSecure,
            sessionId = sessionId,
            includeAgeGate = true
        )

    fun cookieWrites(
        steamLoginSecure: String?,
        sessionId: String,
        clientMode: SteamWebClientMode = SteamWebClientMode.DEFAULT
    ): List<SteamWebCookieWrite> {
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
                includeAgeGate = false,
                includeMobileClient = clientMode != SteamWebClientMode.COMMUNITY_DESKTOP
            ).forEach { value ->
                add(SteamWebCookieWrite("https://steamcommunity.com", value))
            }
            if (clientMode == SteamWebClientMode.COMMUNITY_DESKTOP) {
                add(
                    SteamWebCookieWrite(
                        "https://steamcommunity.com",
                        "mobileClient=; Domain=.steamcommunity.com; Path=/; Max-Age=0; Secure"
                    )
                )
                add(
                    SteamWebCookieWrite(
                        "https://steamcommunity.com",
                        "mobileClientVersion=; Domain=.steamcommunity.com; Path=/; Max-Age=0; Secure"
                    )
                )
            }
        }
    }

    private fun domainCookies(
        domain: String,
        steamLoginSecure: String?,
        sessionId: String,
        includeAgeGate: Boolean,
        includeMobileClient: Boolean = !includeAgeGate
    ): List<String> = buildList {
        add("sessionid=${encode(sessionId)}; Domain=$domain; Path=/; Secure; SameSite=None")
        if (includeAgeGate) {
            add("birthtime=0; Domain=$domain; Path=/; Secure")
            add("lastagecheckage=1-January-1980; Domain=$domain; Path=/; Secure")
        } else if (includeMobileClient) {
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
