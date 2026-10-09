package takagi.ru.monica.steam.web.domain

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SteamWebNavigationPolicy {
    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase().orEmpty()
        host == "s.team" ||
            host == "steampowered.com" ||
            host.endsWith(".steampowered.com") ||
            host == "steamcommunity.com" ||
            host.endsWith(".steamcommunity.com")
    }.getOrDefault(false)

    fun isSafeExternal(url: String): Boolean = runCatching {
        when (URI(url).scheme?.lowercase()) {
            "http", "https", "steam", "mailto" -> true
            else -> false
        }
    }.getOrDefault(false)
}

data class SteamWebCookieWrite(
    val url: String,
    val value: String
)

enum class SteamWebSessionProblem {
    TEMPORARY_SESSION,
    AUTHENTICATED_SESSION_REQUIRED,
    INVALID_SESSION,
    EXPECTED_ACCOUNT_REQUIRED,
    IDENTITY_MISMATCH,
}

data class SteamWebSessionDecision(
    val canLoad: Boolean,
    val installAuthenticatedCookie: Boolean,
    val cookieSteamId: String? = null,
    val problem: SteamWebSessionProblem? = null,
)

object SteamWebAccountSessionPolicy {
    fun decide(
        expectedSteamId: String?,
        steamLoginSecure: String?,
        requireAuthenticatedSession: Boolean,
        temporarySession: Boolean = false,
    ): SteamWebSessionDecision {
        if (temporarySession) return SteamWebSessionDecision(
            canLoad = false, installAuthenticatedCookie = false,
            problem = SteamWebSessionProblem.TEMPORARY_SESSION,
        )
        val expectedId = expectedSteamId?.trim().orEmpty()
        val loginSecure = steamLoginSecure?.trim().orEmpty()
        if (loginSecure.isEmpty()) {
            return if (requireAuthenticatedSession) {
                SteamWebSessionDecision(
                    canLoad = false,
                    installAuthenticatedCookie = false,
                    problem = SteamWebSessionProblem.AUTHENTICATED_SESSION_REQUIRED,
                )
            } else {
                SteamWebSessionDecision(
                    canLoad = true,
                    installAuthenticatedCookie = false,
                )
            }
        }

        val normalized = normalizeSteamCookieValue(loginSecure)
        val separatorIndex = normalized.indexOf("||")
        if (separatorIndex <= 0 || separatorIndex + 2 >= normalized.length) {
            return SteamWebSessionDecision(
                canLoad = false,
                installAuthenticatedCookie = false,
                problem = SteamWebSessionProblem.INVALID_SESSION,
            )
        }
        val cookieSteamId = normalized.substring(0, separatorIndex)
        val sessionToken = normalized.substring(separatorIndex + 2)
        if (cookieSteamId.isBlank() || sessionToken.isBlank()) {
            return SteamWebSessionDecision(
                canLoad = false,
                installAuthenticatedCookie = false,
                problem = SteamWebSessionProblem.INVALID_SESSION,
            )
        }
        if (expectedId.isEmpty()) {
            return SteamWebSessionDecision(
                canLoad = false,
                installAuthenticatedCookie = false,
                cookieSteamId = cookieSteamId,
                problem = SteamWebSessionProblem.EXPECTED_ACCOUNT_REQUIRED,
            )
        }
        if (cookieSteamId != expectedId) {
            return SteamWebSessionDecision(
                canLoad = false,
                installAuthenticatedCookie = false,
                cookieSteamId = cookieSteamId,
                problem = SteamWebSessionProblem.IDENTITY_MISMATCH,
            )
        }
        return SteamWebSessionDecision(
            canLoad = true,
            installAuthenticatedCookie = true,
            cookieSteamId = cookieSteamId,
        )
    }
}

enum class SteamWebClientMode {
    DEFAULT,
    COMMUNITY_DESKTOP
}

data class SteamWebDisplayPolicy(
    val useWideViewPort: Boolean,
    val loadWithOverviewMode: Boolean,
    val textZoomPercent: Int,
)

object SteamWebClientPolicy {
    private val chromeVersionPattern = Regex("Chrome/[0-9.]+")
    private val webViewMarkerPattern = Regex("\\s*;\\s*wv(?=\\s|\\))")
    private val webViewVersionPattern = Regex("\\s+Version/4\\.0(?=\\s)")
    private const val defaultTextZoomPercent = 100

    fun userAgent(mode: SteamWebClientMode, defaultUserAgent: String): String = when (mode) {
        SteamWebClientMode.DEFAULT -> normalizeMobileUserAgent(defaultUserAgent)
        SteamWebClientMode.COMMUNITY_DESKTOP -> {
            val chromeVersion = chromeVersionPattern.find(defaultUserAgent)?.value
                ?: "Chrome/120.0.0.0"
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) $chromeVersion Safari/537.36"
        }
    }

    fun displayPolicy(mode: SteamWebClientMode): SteamWebDisplayPolicy = when (mode) {
        SteamWebClientMode.DEFAULT -> SteamWebDisplayPolicy(
            useWideViewPort = true,
            loadWithOverviewMode = false,
            textZoomPercent = defaultTextZoomPercent,
        )

        SteamWebClientMode.COMMUNITY_DESKTOP -> SteamWebDisplayPolicy(
            useWideViewPort = true,
            loadWithOverviewMode = true,
            textZoomPercent = defaultTextZoomPercent,
        )
    }

    private fun normalizeMobileUserAgent(defaultUserAgent: String): String {
        val source = defaultUserAgent.trim().ifEmpty {
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        return source
            .replace(webViewMarkerPattern, "")
            .replace(webViewVersionPattern, "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}

object SteamWebSessionCookiePolicy {
    fun cookies(
        steamLoginSecure: String?,
        sessionId: String,
        steamParentalCookie: String? = null,
    ): List<String> =
        domainCookies(
            domain = ".steampowered.com",
            steamLoginSecure = steamLoginSecure,
            sessionId = sessionId,
            includeAgeGate = true,
            steamParentalCookie = steamParentalCookie,
        )

    fun cookieWrites(
        steamLoginSecure: String?,
        sessionId: String,
        clientMode: SteamWebClientMode = SteamWebClientMode.DEFAULT,
        steamParentalCookie: String? = null,
    ): List<SteamWebCookieWrite> = buildList {
        domainCookies(
            domain = ".steampowered.com",
            steamLoginSecure = steamLoginSecure,
            sessionId = sessionId,
            includeAgeGate = true,
            steamParentalCookie = steamParentalCookie,
        ).forEach { value ->
            add(SteamWebCookieWrite("https://store.steampowered.com", value))
        }
        domainCookies(
            domain = ".steamcommunity.com",
            steamLoginSecure = steamLoginSecure,
            sessionId = sessionId,
            includeAgeGate = false,
            includeMobileClient = clientMode != SteamWebClientMode.COMMUNITY_DESKTOP,
            steamParentalCookie = steamParentalCookie,
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

    private fun domainCookies(
        domain: String,
        steamLoginSecure: String?,
        sessionId: String,
        includeAgeGate: Boolean,
        includeMobileClient: Boolean = !includeAgeGate,
        steamParentalCookie: String? = null,
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
                "steamLoginSecure=${encode(normalizeSteamCookieValue(value))}; Domain=$domain; " +
                    "Path=/; Secure; HttpOnly; SameSite=None"
            )
        }
        steamParentalCookie
            ?.takeIf { it.startsWith("steamparental=") }
            ?.let { cookie ->
                add("$cookie; Domain=$domain; Path=/; Secure; HttpOnly; SameSite=None")
            }
    }

    private fun encode(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name()
    ).replace("+", "%20")
}

internal fun normalizeSteamCookieValue(value: String): String {
    if (!Regex("%[0-9a-fA-F]{2}").containsMatchIn(value)) return value
    return runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
