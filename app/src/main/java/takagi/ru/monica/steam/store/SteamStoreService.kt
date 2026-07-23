package takagi.ru.monica.steam.store

import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import java.util.UUID

class SteamStoreService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val api: SteamApiClient = SteamApiClient(client)
) {
    private val countryBySession = ConcurrentHashMap<String, String>()

    fun featured(
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese"
    ): SteamStoreHome {
        val body = get(
            path = "/api/featuredcategories",
            query = mapOf("l" to language),
            steamLoginSecure = steamLoginSecure,
            countryCode = accountCountryOrFail(steamLoginSecure, accessToken)
        )
        return SteamStoreParser.parseFeatured(body)
    }

    fun search(
        queryText: String,
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese"
    ): List<SteamStoreItem> {
        if (queryText.isBlank()) return emptyList()
        val body = get(
            path = "/api/storesearch/",
            query = mapOf("term" to queryText.trim(), "l" to language),
            steamLoginSecure = steamLoginSecure,
            countryCode = accountCountryOrFail(steamLoginSecure, accessToken)
        )
        return SteamStoreParser.parseSearch(body)
    }

    fun detail(
        appId: Int,
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese"
    ): SteamStoreDetail {
        val body = get(
            path = "/api/appdetails",
            query = mapOf(
                "appids" to appId.toString(),
                "l" to language
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = accountCountryOrFail(steamLoginSecure, accessToken)
        )
        return SteamStoreParser.parseDetail(appId, body)
            ?: throw IllegalStateException("Steam 商店没有返回该商品详情")
    }

    fun wishlist(
        steamId: String,
        steamLoginSecure: String?,
        accessToken: String? = null,
        language: String = "schinese"
    ): List<SteamWishlistItem> {
        val session = steamLoginSecure?.takeIf(String::isNotBlank)
            ?: throw SteamStoreWishlistSessionException()
        val country = accountCountryOrFail(session, accessToken)
        val items = mutableListOf<SteamWishlistItem>()
        val seen = mutableSetOf<Int>()
        repeat(MAX_WISHLIST_PAGES) { page ->
            val payload = executeText(
                buildSteamWishlistRequest(
                    steamId = steamId,
                    page = page,
                    steamLoginSecure = session,
                    countryCode = country,
                    language = language
                )
            )
            if (isSteamWishlistLoginResponse(payload)) {
                throw SteamStoreWishlistSessionException()
            }
            val pageItems = SteamStoreParser.parseWishlist(payload)
            val newItems = pageItems.filter { seen.add(it.appId) }
            if (newItems.isEmpty()) return items
            items += newItems
            if (pageItems.size < WISHLIST_PAGE_SIZE) return items
        }
        return items
    }

    fun setWishlist(
        appId: Int,
        add: Boolean,
        steamLoginSecure: String?,
        accessToken: String? = null
    ) {
        val session = steamLoginSecure?.takeIf(String::isNotBlank)
            ?: throw SteamStoreWishlistSessionException()
        val country = accountCountryOrFail(session, accessToken)
        val sessionId = UUID.randomUUID().toString().replace("-", "").take(24)
        val payload = executeText(
            buildSteamWishlistMutationRequest(
                appId = appId,
                add = add,
                steamLoginSecure = session,
                sessionId = sessionId,
                countryCode = country
            )
        )
        if (isSteamWishlistLoginResponse(payload)) {
            throw SteamStoreWishlistSessionException()
        }
        if (!SteamStoreParser.parseWishlistMutationSuccess(payload)) {
            throw IllegalStateException("Steam 没有确认愿望单修改")
        }
    }

    private fun get(
        path: String,
        query: Map<String, String>,
        steamLoginSecure: String?,
        countryCode: String? = null
    ): String {
        val request = buildSteamStoreRequest(path, query, steamLoginSecure, countryCode)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.isRedirect) {
                    throw SteamStoreSessionException("Steam 商店会话被重定向，请刷新后重试")
                }
                throw IllegalStateException("Steam 商店请求失败：${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Steam 商店返回空数据")
        }
    }

    private fun executeText(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.isRedirect || response.code == 401 || response.code == 403) {
                    throw SteamStoreWishlistSessionException()
                }
                throw IllegalStateException("Steam 愿望单请求失败：${response.code}")
            }
            return response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 愿望单返回空数据")
        }
    }

    private fun resolveCountryCode(
        steamLoginSecure: String?,
        accessToken: String?
    ): String? {
        val session = steamLoginSecure?.takeIf(String::isNotBlank)
        val accountToken = effectiveSteamStoreAccessToken(accessToken, session)
        val credential = accountToken ?: session ?: return null
        val key = credential.hashCode().toString()
        countryBySession[key]?.let { return it }
        val protobufCountry = accountToken?.let { token ->
            val attempt = runCatching {
                parseSteamStoreAccountCountry(
                    api.callProtobuf(
                        iface = "IStoreService",
                        method = "GetDiscoveryQueueSettings",
                        request = SteamProtoWriter(),
                        accessToken = token,
                        useGet = true
                    )
                )
            }
            attempt.onFailure { error ->
                SteamDiagLogger.append(
                    "store_region account_api_failed type=${error.javaClass.simpleName} " +
                        "result=${(error as? SteamApiException)?.eResult ?: "none"}"
                )
            }
            attempt.getOrNull()?.also { country ->
                SteamDiagLogger.append("store_region resolved source=account_api country=$country")
            }
        }
        val country = protobufCountry ?: session?.let {
            runCatching {
            val request = buildSteamStoreRequest(
                path = "/account/",
                query = emptyMap(),
                    steamLoginSecure = it,
                countryCode = null
            )
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                SteamStoreRegionParser.parseCountryCode(response.body?.string().orEmpty())
            }
            }.getOrNull()
        }
        country?.let { countryBySession[key] = it }
        if (country == null) {
            SteamDiagLogger.append(
                "store_region unresolved access_token_present=${accountToken != null} " +
                    "secure_cookie_present=${session != null}"
            )
        }
        return country
    }

    private fun accountCountryOrFail(
        steamLoginSecure: String?,
        accessToken: String?
    ): String? {
        val session = steamLoginSecure?.takeIf(String::isNotBlank)
        val accountToken = effectiveSteamStoreAccessToken(accessToken, session)
        if (session == null && accountToken == null) return null
        return resolveCountryCode(session, accountToken)
            ?: throw SteamStoreAccountRegionException()
    }

    private companion object {
        const val MAX_WISHLIST_PAGES = 20
        const val WISHLIST_PAGE_SIZE = 100
    }
}

internal fun buildSteamWishlistRequest(
    steamId: String,
    page: Int,
    steamLoginSecure: String,
    countryCode: String?,
    language: String = "schinese"
): Request = buildSteamStoreRequest(
    path = "/wishlist/profiles/$steamId/wishlistdata/",
    query = mapOf("p" to page.coerceAtLeast(0).toString(), "l" to language),
    steamLoginSecure = steamLoginSecure,
    countryCode = countryCode
)

internal fun buildSteamWishlistMutationRequest(
    appId: Int,
    add: Boolean,
    steamLoginSecure: String,
    sessionId: String,
    countryCode: String?
): Request {
    val body = FormBody.Builder()
        .add("sessionid", sessionId)
        .add("appid", appId.toString())
        .build()
    val base = buildSteamStoreRequest(
        path = if (add) "/api/addtowishlist" else "/api/removefromwishlist",
        query = emptyMap(),
        steamLoginSecure = null,
        countryCode = countryCode
    )
    return base.newBuilder()
        .header(
            "Cookie",
            "steamLoginSecure=${encodeSteamCookieValue(steamLoginSecure)}; " +
                "sessionid=${encodeSteamCookieValue(sessionId)}"
        )
        .header("X-Requested-With", "XMLHttpRequest")
        .post(body)
        .build()
}

internal fun isSteamWishlistLoginResponse(payload: String): Boolean {
    val trimmed = payload.trimStart()
    return trimmed.startsWith("<") ||
        payload.contains("Welcome to Steam", ignoreCase = true) ||
        payload.contains("login.steampowered.com", ignoreCase = true)
}

internal fun effectiveSteamStoreAccessToken(
    accessToken: String?,
    steamLoginSecure: String?
): String? = accessToken?.trim()?.takeIf(String::isNotBlank)
    ?: steamLoginSecure
        ?.substringAfter("||", missingDelimiterValue = "")
        ?.trim()
        ?.takeIf(String::isNotBlank)

internal fun parseSteamStoreAccountCountry(response: ByteArray): String? =
    SteamProtoReader(response).parse()[1]
        ?.asString
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.matches(Regex("[A-Z]{2}")) }

internal fun buildSteamStoreRequest(
    path: String,
    query: Map<String, String>,
    steamLoginSecure: String?,
    countryCode: String? = null
): Request {
    require(path.startsWith("/"))
    val url = "https://store.steampowered.com$path".toHttpUrl().newBuilder()
        .apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
            countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }?.let {
                addQueryParameter("cc", it)
            }
        }
        .build()
    return Request.Builder()
        .url(url)
        .header("User-Agent", "Monica-Steam/1.0")
        .header("Accept", "application/json")
        .apply {
            steamLoginSecure?.takeIf(String::isNotBlank)?.let { value ->
                header("Cookie", "steamLoginSecure=${encodeSteamCookieValue(value)}")
            }
        }
        .get()
        .build()
}

internal fun encodeSteamCookieValue(value: String): String = URLEncoder.encode(
    value,
    StandardCharsets.UTF_8.name()
).replace("+", "%20")

internal object SteamStoreRegionParser {
    private val countryPatterns = listOf(
        Regex("\\\"wallet_country\\\"\\s*:\\s*\\\"([A-Za-z]{2})\\\""),
        Regex("wallet_country\\s*[=:]\\s*['\\\"]([A-Za-z]{2})['\\\"]"),
        Regex("\\\"country_code\\\"\\s*:\\s*\\\"([A-Za-z]{2})\\\"")
    )

    fun parseCountryCode(html: String): String? = countryPatterns.asSequence()
        .mapNotNull { it.find(html)?.groupValues?.getOrNull(1) }
        .map { it.uppercase() }
        .firstOrNull { it.length == 2 }
}
