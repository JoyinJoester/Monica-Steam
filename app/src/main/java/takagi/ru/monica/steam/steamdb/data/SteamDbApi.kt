package takagi.ru.monica.steam.steamdb.data

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.*
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.steamdb.domain.*

internal interface SteamDbGateway {
    fun info(appId: Int): SteamDbResult<SteamDbAppInfo>
    fun players(appId: Int): SteamDbResult<Long>
    fun rating(appId: Int): SteamDbResult<SteamDbRating>
    fun price(appId: Int, region: String): SteamDbResult<SteamDbLowestPrice>
}

/** Public data only. This client never sends Steam sessions, cookies or account credentials. */
internal class SteamDbApi(
    private val client: OkHttpClient = SteamHttpClientProvider.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build(),
    private val extension: HttpUrl = "https://extension.steamdb.info/api/".toHttpUrl(),
    private val steam: HttpUrl = "https://api.steampowered.com/".toHttpUrl(),
    private val store: HttpUrl = "https://store.steampowered.com/".toHttpUrl(),
    private val clock: () -> Long = System::currentTimeMillis
) : SteamDbGateway {
    private val retryAt = AtomicLong()

    override fun info(appId: Int) = get(extension.newBuilder().addPathSegment("ExtensionApp")
        .addPathSegment("").addQueryParameter("appid", "$appId").build(), true) { root ->
        val data = root.extensionData()
        SteamDbAppInfo(data.count("cp"), data.count("mdp"), data.count("mp"), data.count("f"), data.timestamp("u"))
            .takeIf { listOf(it.currentPlayers, it.peakToday, it.peakAll, it.followers, it.updatedAt).any { v -> v != null } }
    }

    override fun price(appId: Int, region: String) = get(extension.newBuilder().addPathSegment("ExtensionAppPrice")
        .addPathSegment("").addQueryParameter("appid", "$appId").addQueryParameter("currency", region).build(), true) { root ->
        val data = root.extensionData()
        data.text("p")?.let { price ->
            SteamDbLowestPrice(price, data.count("d")?.takeIf { it in 1..100 }?.toInt(),
                data.text("l"), data.count("c"), data.timestamp("t"))
        }
    }

    override fun players(appId: Int) = get(steam.newBuilder()
        .addPathSegments("ISteamUserStats/GetNumberOfCurrentPlayers/v1/")
        .addQueryParameter("appid", "$appId").build(), false) { root ->
        val response = root["response"] as? JsonObject
        response?.takeIf { it.count("result") == 1L }?.count("player_count")
    }

    override fun rating(appId: Int) = get(store.newBuilder().addPathSegment("appreviews").addPathSegment("$appId")
        .addQueryParameter("json", "1").addQueryParameter("language", "all")
        .addQueryParameter("purchase_type", "steam").addQueryParameter("review_type", "all")
        .addQueryParameter("filter", "all").addQueryParameter("num_per_page", "1")
        .addQueryParameter("filter_offtopic_activity", "1").build(), false) { root ->
        if (root.count("success") != 1L) return@get null
        val summary = root["query_summary"] as? JsonObject ?: return@get null
        val positive = summary.count("total_positive") ?: return@get null
        val negative = summary.count("total_negative") ?: return@get null
        if (positive > Long.MAX_VALUE - negative) return@get null
        SteamDbRating(positive, negative)
    }

    private fun <T> get(url: HttpUrl, steamDb: Boolean, parse: (JsonObject) -> T?): SteamDbResult<T> {
        if (steamDb && retryAt.get() > clock()) return SteamDbResult.Failed(SteamDbFailure.RATE_LIMITED, retryAt.get())
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (steamDb) {
                // The extension endpoint requires its browser protocol headers (Millennium reference).
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36")
                header("Origin", "chrome-extension://kdbmhfkmnlmbkgbabkdealhhbfhlmmon")
                header("X-Requested-With", "SteamDB")
                header("sec-ch-ua", "\"Chromium\";v=\"154\", \"Google Chrome\";v=\"154\", \"Not A(Brand\";v=\"99\"")
                header("sec-ch-ua-mobile", "?0")
                header("sec-ch-ua-platform", "\"Windows\"")
                header("sec-fetch-dest", "empty")
                header("sec-fetch-mode", "cors")
                header("sec-fetch-site", "cross-site")
                header("Accept-Language", "en-US,en;q=0.9")
                header("priority", "u=1, i")
            } else header("User-Agent", "Monica-Steam/Android")
        }.build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val until = clock() + retryDelayMillis(response.header("Retry-After"), clock())
                    if (steamDb) retryAt.updateAndGet { maxOf(it, until) }
                    return SteamDbResult.Failed(SteamDbFailure.RATE_LIMITED, until)
                }
                if (!response.isSuccessful) return SteamDbResult.Failed(SteamDbFailure.SERVICE, statusCode = response.code)
                val body = response.body ?: return SteamDbResult.Failed(SteamDbFailure.NO_DATA)
                val source = body.source()
                if (source.request(MAX_BODY + 1)) return SteamDbResult.Failed(SteamDbFailure.SERVICE)
                val root = Json.parseToJsonElement(source.readUtf8()) as? JsonObject
                    ?: return SteamDbResult.Failed(SteamDbFailure.SERVICE)
                parse(root)?.let { SteamDbResult.Ready(it, clock()) }
                    ?: SteamDbResult.Failed(SteamDbFailure.NO_DATA)
            }
        } catch (_: IOException) {
            SteamDbResult.Failed(SteamDbFailure.NETWORK)
        } catch (_: IllegalArgumentException) {
            SteamDbResult.Failed(SteamDbFailure.SERVICE)
        }
    }

    private fun JsonObject.extensionData(): JsonObject {
        if ((get("success") as? JsonPrimitive)?.booleanOrNull != true) return JsonObject(emptyMap())
        return get("data") as? JsonObject ?: JsonObject(emptyMap())
    }
    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
        ?.trim()?.takeIf { it.isNotBlank() && it.length <= 256 }
    private fun JsonObject.count(key: String): Long? = text(key)?.replace(",", "")?.toLongOrNull()?.takeIf { it >= 0 }
    private fun JsonObject.timestamp(key: String): Long? = count(key)?.takeIf { it in 1..253_402_300_799L }

    companion object {
        private const val MAX_BODY = 256L * 1024
        internal fun retryDelayMillis(value: String?, now: Long): Long {
            value?.trim()?.toLongOrNull()?.let { return it.coerceIn(1, 86_400) * 1_000 }
            return runCatching {
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now
            }.getOrDefault(60_000L).coerceIn(1_000, 86_400_000)
        }
    }
}
