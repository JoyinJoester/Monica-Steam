package takagi.ru.monica.steam.store.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.store.domain.SteamReviewPage
import takagi.ru.monica.steam.store.domain.SteamStoreReviews

class SteamStoreReviewService(
    private val client: OkHttpClient
) {
    fun fetch(appId: Int, language: String = "schinese"): SteamStoreReviews? {
        val page = fetchPart(appId, "page") { fetchPage(appId, language = language) }
        val recent = fetchPart(appId, "recent") {
            SteamStoreReviewParser.parseRecent(
                get(
                    pathSegments = listOf("appreviewhistogram", appId.toString()),
                    query = mapOf(
                        "l" to language,
                        "review_score_preference" to "0"
                    )
                )
            )
        }
        return if (page == null && recent == null) {
            null
        } else {
            SteamStoreReviews(
                overall = page?.summary,
                recent = recent,
                items = page?.items.orEmpty(),
                nextCursor = page?.nextCursor
            )
        }
    }

    fun fetchPage(
        appId: Int,
        cursor: String = "*",
        language: String = "schinese",
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): SteamReviewPage = SteamStoreReviewParser.parsePage(
        get(
            pathSegments = listOf("appreviews", appId.toString()),
            query = mapOf(
                "json" to "1",
                "language" to language,
                "purchase_type" to "all",
                "review_type" to "all",
                "filter" to "recent",
                "day_range" to "30",
                "num_per_page" to pageSize.coerceIn(1, MAX_PAGE_SIZE).toString(),
                "cursor" to cursor
            )
        )
    )

    private fun <T> fetchPart(
        appId: Int,
        part: String,
        request: () -> T?
    ): T? = runCatching(request).onFailure { error ->
        SteamDiagLogger.append(
            "store_reviews $part failed app_id=$appId type=${error.javaClass.simpleName}"
        )
    }.getOrNull()

    private fun get(pathSegments: List<String>, query: Map<String, String>): String {
        val url = STORE_BASE_URL.toHttpUrl().newBuilder().apply {
            pathSegments.forEach { addPathSegment(it) }
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Monica-Steam/Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam 评价请求失败：${response.code}")
            }
            return response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 评价返回空数据")
        }
    }

    private companion object {
        const val STORE_BASE_URL = "https://store.steampowered.com/"
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
