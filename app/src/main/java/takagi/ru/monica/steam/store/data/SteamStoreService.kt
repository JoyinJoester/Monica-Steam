package takagi.ru.monica.steam.store.data

import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import java.util.UUID
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SteamStoreRegionSearchResult(
    val countryCode: String?,
    val items: List<SteamStoreItem>
)

private data class SteamStoreSearchTarget(
    val countryCode: String?,
    val steamLoginSecure: String?
)

internal val STEAM_STORE_DISCOVERY_COUNTRY_CODES =
    listOf("US", "CN", "JP", "KR", "DE", "RU")

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

    suspend fun search(
        queryText: String,
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese"
    ): List<SteamStoreItem> {
        if (queryText.isBlank()) return emptyList()
        val query = queryText.trim()
        val accountCountry = accountCountryOrFail(steamLoginSecure, accessToken)
        val targets = buildList {
            add(
                SteamStoreSearchTarget(
                    countryCode = accountCountry,
                    steamLoginSecure = steamLoginSecure
                )
            )
            STEAM_STORE_DISCOVERY_COUNTRY_CODES
                .filterNot { it.equals(accountCountry, ignoreCase = true) }
                .forEach { countryCode ->
                    add(SteamStoreSearchTarget(countryCode = countryCode, steamLoginSecure = null))
                }
        }.distinctBy { it.countryCode?.uppercase().orEmpty() }
        val attempts = coroutineScope {
            targets.map { target ->
                async {
                    try {
                        Result.success(
                            SteamStoreRegionSearchResult(
                                countryCode = target.countryCode,
                                items = SteamStoreParser.parseSearch(
                                    getAsync(
                                        path = "/api/storesearch/",
                                        query = mapOf("term" to query, "l" to language),
                                        steamLoginSecure = target.steamLoginSecure,
                                        countryCode = target.countryCode
                                    )
                                )
                            )
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        SteamDiagLogger.append(
                            "store_search catalog_failed country=${target.countryCode ?: "account_default"} " +
                                "type=${error.javaClass.simpleName}"
                        )
                        Result.failure(error)
                    }
                }
            }.awaitAll()
        }
        val regionalResults = attempts.mapNotNull(Result<SteamStoreRegionSearchResult>::getOrNull)
        if (regionalResults.isEmpty()) {
            throw attempts.firstNotNullOfOrNull(Result<SteamStoreRegionSearchResult>::exceptionOrNull)
                ?: IllegalStateException("Steam 商店搜索没有返回数据")
        }
        val accountRegionResponded = accountCountry != null && regionalResults.any {
            it.countryCode.equals(accountCountry, ignoreCase = true)
        }
        return mergeSteamStoreSearchResults(
            query = query,
            accountCountryCode = accountCountry,
            accountRegionResponded = accountRegionResponded,
            regionalResults = regionalResults
        )
    }

    fun detail(
        appId: Int,
        steamLoginSecure: String? = null,
        accessToken: String? = null,
        language: String = "schinese",
        discoveryCountryCode: String? = null
    ): SteamStoreDetail {
        val accountCountry = accountCountryOrFail(steamLoginSecure, accessToken)
        requestDetail(
            appId = appId,
            language = language,
            steamLoginSecure = steamLoginSecure,
            countryCode = accountCountry
        )?.let { detail ->
            return detail.copy(
                availableInAccountRegion = accountCountry?.let { true },
                accountCountryCode = accountCountry,
                priceCountryCode = accountCountry
            )
        }
        steamStoreDetailFallbackCountries(
            accountCountryCode = accountCountry,
            discoveryCountryCode = discoveryCountryCode
        ).forEach { countryCode ->
            val detail = runCatching {
                requestDetail(
                    appId = appId,
                    language = language,
                    steamLoginSecure = null,
                    countryCode = countryCode
                )
            }.onFailure { error ->
                SteamDiagLogger.append(
                    "store_detail fallback_failed app_id=$appId country=$countryCode " +
                        "type=${error.javaClass.simpleName}"
                )
            }.getOrNull()
            if (detail != null) {
                return detail.copy(
                    availableInAccountRegion = accountCountry?.let { false },
                    accountCountryCode = accountCountry,
                    priceCountryCode = countryCode
                )
            }
        }
        throw IllegalStateException("Steam 商店没有返回该商品详情")
    }

    private fun requestDetail(
        appId: Int,
        language: String,
        steamLoginSecure: String?,
        countryCode: String?
    ): SteamStoreDetail? {
        val body = get(
            path = "/api/appdetails",
            query = mapOf(
                "appids" to appId.toString(),
                "l" to language
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        return SteamStoreParser.parseDetail(appId, body)
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

    private suspend fun getAsync(
        path: String,
        query: Map<String, String>,
        steamLoginSecure: String?,
        countryCode: String?
    ): String = suspendCancellableCoroutine { continuation ->
        val request = buildSteamStoreRequest(path, query, steamLoginSecure, countryCode)
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) {
                            if (response.isRedirect) {
                                throw SteamStoreSessionException(
                                    "Steam 商店会话被重定向，请刷新后重试"
                                )
                            }
                            throw IllegalStateException("Steam 商店请求失败：${response.code}")
                        }
                        response.body?.string()?.takeIf(String::isNotBlank)
                            ?: throw IllegalStateException("Steam 商店返回空数据")
                    }
                    result.onSuccess { body ->
                        if (continuation.isActive) continuation.resume(body)
                    }.onFailure { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
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

internal fun mergeSteamStoreSearchResults(
    query: String,
    accountCountryCode: String?,
    accountRegionResponded: Boolean,
    regionalResults: List<SteamStoreRegionSearchResult>
): List<SteamStoreItem> {
    val accountCountry = accountCountryCode?.trim()?.uppercase()
    val orderedResults = regionalResults.sortedBy { result ->
        if (accountCountry != null && result.countryCode.equals(accountCountry, true)) 0 else 1
    }
    val accountAppIds = orderedResults
        .firstOrNull { it.countryCode.equals(accountCountry, ignoreCase = true) }
        ?.items
        ?.mapTo(mutableSetOf(), SteamStoreItem::appId)
        .orEmpty()
    val merged = linkedMapOf<Int, SteamStoreItem>()
    orderedResults.forEach { regionalResult ->
        val priceCountry = regionalResult.countryCode?.trim()?.uppercase()
        val accountResult = accountCountry != null && priceCountry == accountCountry
        regionalResult.items.forEach { item ->
            val annotated = item.copy(
                availableInAccountRegion = when {
                    accountCountry == null || !accountRegionResponded -> null
                    item.appId in accountAppIds -> true
                    else -> false
                },
                accountCountryCode = accountCountry,
                priceCountryCode = priceCountry
            )
            if (accountResult) {
                merged[item.appId] = annotated
            } else {
                merged.putIfAbsent(item.appId, annotated)
            }
        }
    }
    return merged.values.sortedWith(
        compareBy<SteamStoreItem> { steamStoreSearchRelevance(query, it.name) }
            .thenBy { if (it.availableInAccountRegion == false) 1 else 0 }
            .thenBy { it.name.lowercase() }
    ).take(MAX_GLOBAL_SEARCH_RESULTS)
}

internal fun steamStoreDetailFallbackCountries(
    accountCountryCode: String?,
    discoveryCountryCode: String?
): List<String> {
    val accountCountry = accountCountryCode?.trim()?.uppercase()
    return buildList {
        discoveryCountryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }?.let(::add)
        addAll(STEAM_STORE_DISCOVERY_COUNTRY_CODES)
    }.filterNot { it == accountCountry }.distinct()
}

private fun steamStoreSearchRelevance(query: String, name: String): Int {
    val normalizedQuery = query.trim().lowercase()
    val normalizedName = name.trim().lowercase()
    return when {
        normalizedName == normalizedQuery -> 0
        normalizedName.startsWith(normalizedQuery) -> 1
        normalizedName.contains(normalizedQuery) -> 2
        normalizedQuery.split(Regex("\\s+")).all(normalizedName::contains) -> 3
        else -> 4
    }
}

private const val MAX_GLOBAL_SEARCH_RESULTS = 48

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
