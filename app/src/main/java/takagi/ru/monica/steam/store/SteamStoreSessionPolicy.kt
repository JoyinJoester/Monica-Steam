package takagi.ru.monica.steam.store

import java.net.URI
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

object SteamStoreSessionPolicy {
    fun cookies(steamLoginSecure: String?, sessionId: String): List<String> = buildList {
        add("sessionid=${encode(sessionId)}; Domain=.steampowered.com; Path=/; Secure; SameSite=None")
        add("birthtime=0; Domain=.steampowered.com; Path=/; Secure")
        add("lastagecheckage=1-January-1980; Domain=.steampowered.com; Path=/; Secure")
        steamLoginSecure?.takeIf { it.isNotBlank() }?.let { value ->
            add(
                "steamLoginSecure=${encode(value)}; Domain=.steampowered.com; " +
                    "Path=/; Secure; HttpOnly; SameSite=None"
            )
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name()
    ).replace("+", "%20")
}
