package takagi.ru.monica.steam.friends.chat.info.data

import android.content.Context
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

/**
 * Resolves the profile background a friend is currently wearing on Steam.
 *
 * Steam only exposes the equipped background inside the community profile HTML, so the
 * page is read once (with the signed-in account's cookies) and the resolved URL is kept
 * for half a day. Every failure degrades to `null`; callers fall back to a blurred avatar
 * instead of showing an empty slab.
 */
internal class SteamProfileBackgroundStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun backgroundUrl(account: SteamAccount?, steamId: String): String? =
        withContext(Dispatchers.IO) {
            if (!STEAM_ID64.matches(steamId)) return@withContext null
            val urlKey = "$steamId.url"
            val timeKey = "$steamId.at"
            val cachedAt = preferences.getLong(timeKey, 0L)
            if (cachedAt > 0L && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return@withContext preferences.getString(urlKey, null)?.takeIf(String::isNotBlank)
            }
            val previous = preferences.getString(urlKey, null)?.takeIf(String::isNotBlank)
            val fetched = runCatching { fetch(account, steamId) }.getOrNull()
            preferences.edit()
                .putString(urlKey, fetched.orEmpty())
                .putLong(timeKey, System.currentTimeMillis())
                .apply()
            // A transient network failure keeps whatever the last successful read found.
            fetched ?: previous
        }

    private fun fetch(account: SteamAccount?, steamId: String): String? {
        val html = SteamApiClient().communityGetText(
            path = "/profiles/$steamId/",
            query = mapOf("l" to communityLanguage()),
            cookies = account?.let(::cookies).orEmpty(),
            referer = "https://steamcommunity.com/profiles/$steamId/"
        )
        return parseSteamProfileBackgroundUrl(html)
    }

    private fun cookies(account: SteamAccount): Map<String, String> {
        val loginSecure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: "${account.steamId}||${account.accessToken.orEmpty()}"
        val session = ByteArray(12).also(SECURE_RANDOM::nextBytes)
        return mapOf(
            "steamLoginSecure" to loginSecure,
            "sessionid" to session.joinToString("") { "%02x".format(it.toInt() and 0xff) },
            "mobileClient" to "android",
            "mobileClientVersion" to "777777 3.6.4"
        )
    }

    private fun communityLanguage(): String = when (Locale.getDefault().toLanguageTag().lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "schinese"
        "zh-tw", "zh-hk", "zh-hant" -> "tchinese"
        "ja", "ja-jp" -> "japanese"
        "ru", "ru-ru" -> "russian"
        else -> "english"
    }

    companion object {
        private const val PREFERENCES_NAME = "steam_profile_background"
        private const val CACHE_TTL_MS = 12L * 60L * 60L * 1000L
        private val STEAM_ID64 = Regex("7656119\\d{10}")
        private val SECURE_RANDOM = SecureRandom()
    }
}

/**
 * Steam marks the equipped background either as an inline `background-image` on the profile
 * container, or — for animated ones — as the poster frame of a `<video>` inside
 * `.profile_animated_background`. Both shapes are scraped here, then filtered by the CDN
 * allow-list so a crafted profile cannot make the app fetch an arbitrary host.
 */
internal fun parseSteamProfileBackgroundUrl(html: String): String? {
    if (html.isBlank()) return null
    val document = runCatching { Jsoup.parse(html) }.getOrNull()
    val sources = buildList {
        document?.select("[style*=background-image]")?.forEach { add(it.attr("style")) }
        document?.selectFirst(".profile_animated_background video[poster]")?.attr("poster")?.let(::add)
        document?.selectFirst("video.profile_background_video")?.attr("poster")?.let(::add)
        document?.selectFirst("img.profile_background_image")?.attr("src")?.let(::add)
        add(html)
    }
    for (source in sources) {
        val raw = STYLE_URL.find(source)?.groupValues?.getOrNull(1)
            ?: JSON_URL.find(source)?.groupValues?.getOrNull(1)
            ?: source.takeIf { it.startsWith("https://") }
            ?: continue
        val url = raw.replace("&amp;", "&").trim()
        if (SteamRemoteImageCache.isAllowedSteamImageUrl(url)) return url
    }
    return null
}

private val STYLE_URL = Regex("""url\(\s*['"]?(https?://[^'")\s]+)""", RegexOption.IGNORE_CASE)
private val JSON_URL = Regex(""""background_url"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
