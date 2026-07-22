package takagi.ru.monica.steam.network

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class SteamApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun steamApiGetJson(
        path: String,
        query: Map<String, String> = emptyMap(),
        accessToken: String? = null
    ): JsonObject {
        return getJsonFromHost(
            host = "https://api.steampowered.com",
            path = path,
            query = query,
            accessToken = accessToken
        )
    }

    fun steamStoreGetJson(
        appId: Int,
        currency: String,
        language: String
    ): JsonObject = steamStoreGetJson(listOf(appId), currency, language)

    fun steamStoreGetJson(
        appIds: List<Int>,
        currency: String,
        language: String
    ): JsonObject {
        require(appIds.isNotEmpty() && appIds.size <= 40) { "Store price batch must contain 1..40 apps" }
        return getJsonFromHost(
            host = "https://store.steampowered.com",
            path = "/api/appdetails",
            query = mapOf(
                "appids" to appIds.joinToString(","),
                "cc" to currency,
                "l" to language
            )
        )
    }

    private val communityClient = client.newBuilder()
        .dns(SteamCommunityDns.create(client))
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val host = chain.request().url.host.lowercase()
            require(host == "steamcommunity.com" || host.endsWith(".steamcommunity.com")) {
                "Steam community redirect blocked: $host"
            }
            chain.proceed(chain.request())
        }
        .build()

    fun callProtobuf(
        iface: String,
        method: String,
        request: SteamProtoWriter,
        accessToken: String? = null,
        useGet: Boolean = false,
        version: Int = 1
    ): ByteArray {
        val baseUrl = "https://api.steampowered.com/$iface/$method/v$version/"
        val encoded = Base64.getEncoder().encodeToString(request.toByteArray())
        val httpRequest = if (useGet) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("input_protobuf_encoded", encoded)
                .apply {
                    if (!accessToken.isNullOrBlank()) addQueryParameter("access_token", accessToken)
                }
                .build()
            Request.Builder().url(url).get()
        } else {
            val body = FormBody.Builder()
                .add("input_protobuf_encoded", encoded)
                .build()
            val url = baseUrl.toHttpUrl().newBuilder()
                .apply {
                    if (!accessToken.isNullOrBlank()) addQueryParameter("access_token", accessToken)
                }
                .build()
            Request.Builder().url(url).post(body)
        }.header("User-Agent", "okhttp/4.9.2")
            .header("Accept", "application/json, text/plain, */*")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val eResult = response.header("x-eresult")?.toIntOrNull()
            if (!response.isSuccessful || (eResult != null && eResult != 1)) {
                val message = response.header("x-error_message")
                    ?: "Steam API failed: $iface/$method (${response.code}, eresult=${eResult ?: "none"})"
                throw SteamApiException(message, eResult ?: response.code)
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    fun communityGetJson(
        path: String,
        query: Map<String, String>,
        cookies: Map<String, String> = emptyMap(),
        referer: String? = null
    ): JsonObject {
        val url = "https://steamcommunity.com$path".toHttpUrl().newBuilder()
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .headers(defaultCommunityHeaders(cookies, referer))
            .build()
        return executeJson(request)
    }

    private fun getJsonFromHost(
        host: String,
        path: String,
        query: Map<String, String>,
        accessToken: String? = null
    ): JsonObject {
        val url = "$host$path".toHttpUrl().newBuilder()
            .apply {
                query.forEach { (key, value) -> addQueryParameter(key, value) }
                if (!accessToken.isNullOrBlank()) addQueryParameter("access_token", accessToken)
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Monica-Steam/1.0")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SteamApiException("Steam API request failed: ${response.code}", response.code)
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank() || !body.trimStart().startsWith("{")) {
                throw SteamApiException("Steam API returned an invalid JSON response")
            }
            return json.parseToJsonElement(body).jsonObject
        }
    }

    fun communityGetText(
        path: String,
        query: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        referer: String? = null
    ): String {
        val url = "https://steamcommunity.com$path".toHttpUrl().newBuilder()
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        return executeTextWithRedirects(
            initialUrl = url,
            initialCookies = cookies,
            initialReferer = referer
        )
    }

    fun communityPostJson(
        path: String,
        form: Map<String, List<String>>,
        cookies: Map<String, String> = emptyMap(),
        referer: String? = null
    ): JsonObject {
        val body = FormBody.Builder().apply {
            form.forEach { (key, values) ->
                values.forEach { value -> add(key, value) }
            }
        }.build()
        val request = Request.Builder()
            .url("https://steamcommunity.com$path")
            .post(body)
            .headers(defaultCommunityHeaders(cookies, referer))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JsonObject {
        communityClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw communityFailure(response)
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank() || !body.trimStart().startsWith("{")) {
                return JsonObject(emptyMap())
            }
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun executeTextWithRedirects(
        initialUrl: HttpUrl,
        initialCookies: Map<String, String>,
        initialReferer: String?
    ): String {
        val cookieJar = LinkedHashMap(initialCookies)
        var currentUrl = initialUrl
        var currentReferer = initialReferer
        var redirectCount = 0

        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .get()
                .headers(
                    defaultCommunityHeaders(
                        cookies = cookieJar,
                        referer = currentReferer,
                        acceptJson = false
                    )
                )
                .build()
            communityClient.newCall(request).execute().use { response ->
                absorbCommunityCookies(
                    requestUrl = response.request.url,
                    headers = response.headers,
                    cookieJar = cookieJar
                )
                if (response.isSuccessful) {
                    return response.body?.string().orEmpty()
                }
                if (!response.isRedirect) {
                    throw communityFailure(response)
                }
                if (redirectCount >= MAX_COMMUNITY_TEXT_REDIRECTS) {
                    throw SteamApiException("Steam community redirect limit exceeded")
                }
                val location = response.header("Location").orEmpty()
                val target = response.request.url.resolve(location)
                    ?: throw SteamApiException("Steam community redirect missing target")
                if (!isAllowedCommunityUrl(target)) {
                    throw SteamApiException("Steam community redirect blocked: $target")
                }
                currentReferer = response.request.url.toString()
                currentUrl = target
                redirectCount++
            }
        }
    }

    internal fun absorbCommunityCookies(
        requestUrl: HttpUrl,
        headers: Headers,
        cookieJar: MutableMap<String, String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Set<String> {
        val changedNames = linkedSetOf<String>()
        Cookie.parseAll(requestUrl, headers).forEach { cookie ->
            if (cookie.expiresAt <= nowMillis) {
                cookieJar.remove(cookie.name)
            } else {
                cookieJar[cookie.name] = cookie.value
            }
            changedNames += cookie.name
        }
        return changedNames
    }

    private fun defaultCommunityHeaders(
        cookies: Map<String, String>,
        referer: String?,
        acceptJson: Boolean = true
    ): okhttp3.Headers {
        if (referer != null) {
            val refererHost = referer.toHttpUrl().host.lowercase()
            require(refererHost == "steamcommunity.com" || refererHost.endsWith(".steamcommunity.com")) {
                "Invalid Steam community referer"
            }
        }
        return okhttp3.Headers.Builder()
            .add("User-Agent", "okhttp/4.9.2")
            .add(
                "Accept",
                if (acceptJson) "application/json, text/plain, */*" else "text/html, */*"
            )
            .add("X-Requested-With", "com.valvesoftware.android.steam.community")
            .apply {
                if (referer != null) add("Referer", referer)
                if (cookies.isNotEmpty()) {
                    add("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            .build()
    }

    private fun communityFailure(response: Response): SteamApiException {
        if (response.isRedirect) {
            val location = response.header("Location").orEmpty()
            val target = response.request.url.resolve(location)
            val targetText = target?.toString() ?: location.ifBlank { "unknown" }
            return if (target?.encodedPath?.startsWith("/login/") == true) {
                SteamApiException("Steam community session expired")
            } else {
                SteamApiException("Steam community redirect blocked: $targetText")
            }
        }
        return SteamApiException("Steam community request failed: ${response.code}")
    }

    private fun isAllowedCommunityUrl(url: HttpUrl): Boolean {
        if (url.scheme != "https") return false
        val host = url.host.lowercase()
        return host == "steamcommunity.com" || host.endsWith(".steamcommunity.com")
    }

    private companion object {
        const val MAX_COMMUNITY_TEXT_REDIRECTS = 6
    }
}

class SteamApiException(
    message: String,
    val eResult: Int? = null
) : Exception(message)
