package takagi.ru.monica.steam.space.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

/** No server-supplied navigation URL or credentials are forwarded to an external surface. */
object SteamSpaceLinks {
    const val HOME = "https://steamcommunity.com/my/home/"

    fun isAllowedSourceUrl(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        if (!url.isHttps || url.host != "steamcommunity.com" || url.port != 443 ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null
        ) return false
        if (url.encodedPath == "/my/home/") return url.query == null
        if (url.encodedPath == "/sharedfiles/filedetails/") {
            return url.queryParameterNames == setOf("id") &&
                url.queryParameterValues("id").size == 1 && validId(url.queryParameter("id"))
        }
        return url.query == null && SOURCE_PATH.matches(url.encodedPath)
    }

    internal fun image(raw: String?): String? = raw?.takeIf {
        it.length <= 2_048 && SteamRemoteImageCache.isAllowedSteamImageUrl(it) &&
            it.toHttpUrlOrNull()?.let { url ->
                url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null
            } == true
    }

    internal fun achievementIcon(appId: Int, raw: String?): String? {
        image(raw)?.let { valid ->
            val url = valid.toHttpUrlOrNull() ?: return null
            // Older image routes return an HTML app shell with HTTP 200. Repair cached URLs too.
            val oldPrefix = "/steamcommunity/public/images/apps/"
            return if (url.encodedPath.startsWith(oldPrefix)) {
                image("https://shared.akamai.steamstatic.com/community_assets/images/apps/" +
                    url.encodedPath.removePrefix(oldPrefix))
            } else valid
        }
        // Current definitions supply filenames relative to community_assets.
        if (appId <= 0 || raw == null || !ACHIEVEMENT_IMAGE_FILE.matches(raw) || ".." in raw) return null
        return image("https://shared.akamai.steamstatic.com/community_assets/images/apps/$appId/$raw")
    }

    internal fun profile(actor: String): String = checked("https://steamcommunity.com/profiles/$actor/")
    internal fun status(actor: String, post: Long): String =
        checked("https://steamcommunity.com/profiles/$actor/status/$post/")
    internal fun publishedFile(id: String): String =
        checked("https://steamcommunity.com/sharedfiles/filedetails/?id=$id")
    internal fun review(actor: String, appId: Int): String =
        checked("https://steamcommunity.com/profiles/$actor/recommended/$appId/")
    internal fun announcement(actor: String, id: String): String =
        checked("https://steamcommunity.com/gid/$actor/announcements/detail/$id/")

    private fun checked(url: String): String = url.also { require(isAllowedSourceUrl(it)) }
    private fun validId(id: String?): Boolean = id != null && ID.matches(id) && id.toULongOrNull()?.let { it > 0uL } == true
    private val ID = Regex("[1-9][0-9]{0,19}")
    private val ACHIEVEMENT_IMAGE_FILE = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")
    private val SOURCE_PATH = Regex(
        "/(?:profiles/[1-9][0-9]{16}/(?:status/[1-9][0-9]{0,9}/|recommended/[1-9][0-9]{0,9}/)?|" +
            "gid/[1-9][0-9]{16}/announcements/detail/[1-9][0-9]{0,19}/)"
    )
}
