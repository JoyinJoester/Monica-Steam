package takagi.ru.monica.steam.store.purchase.data

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.store.bundle.data.SteamStoreBundleParser
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundle
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.data.SteamStoreFamilyViewException
import takagi.ru.monica.steam.store.related.data.SteamStoreRelatedContentService

internal data class SteamStorePurchasePage(
    val visiblePackageIds: Set<Int> = emptySet(),
    val tags: List<String> = emptyList(),
    val bundles: List<SteamStoreBundle> = emptyList(),
    val discounts: Map<Int, SteamPackageDiscount> = emptyMap()
)

internal data class SteamPackageDiscount(val priceCents: Int, val percent: Int, val endEpochSeconds: Long)

internal object SteamStorePurchasePageParser {
    fun parse(html: String, appId: Int? = null): SteamStorePurchasePage {
        val document = Jsoup.parse(html)
        val visiblePackageIds = document
            .select(
                ".game_area_purchase_game_wrapper:not([data-ds-bundleid]) " +
                    "input[name=subid]"
            )
            .mapNotNull { it.attr("value").toIntOrNull()?.takeIf { id -> id > 0 } }
            .toSet()
        return SteamStorePurchasePage(
            visiblePackageIds = visiblePackageIds,
            tags = document
                .select(".glance_tags.popular_tags a.app_tag, .popular_tags a.app_tag")
                .map { it.text().trim() }
                .filter(String::isNotBlank)
                .distinct(),
            bundles = SteamStoreBundleParser.parse(html),
            discounts = if (appId == null) emptyMap() else parseDiscounts(document, appId)
        )
    }
    private fun parseDiscounts(document: org.jsoup.nodes.Document, appId: Int): Map<Int, SteamPackageDiscount> {
        val candidates = mutableMapOf<Int, MutableSet<SteamPackageDiscount>>()
        fun option(value: JsonElement?) {
            val obj = value as? JsonObject ?: return
            if (obj["bundleid"]?.jsonPrimitive?.intOrNull?.let { it > 0 } == true) return
            val id = obj["packageid"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return
            val price = obj["final_price_in_cents"]?.jsonPrimitive?.intOrNull?.takeIf { it >= 0 } ?: return
            val percent = obj["discount_pct"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..100 } ?: return
            val end = obj["discount_end_date"]?.jsonPrimitive?.longOrNull
                ?.takeIf { it in 1..253402300799L } ?: return
            candidates.getOrPut(id) { mutableSetOf() }.add(SteamPackageDiscount(price, percent, end))
        }
        fun visit(node: JsonElement, depth: Int) {
            if (depth > 24) return
            when (node) {
                is JsonObject -> {
                    (node["rgStoreItems"] as? JsonArray)?.forEach { value ->
                        val item = value as? JsonObject ?: return@forEach
                        if (item["appid"]?.jsonPrimitive?.intOrNull == appId) {
                            option(item["best_purchase_option"])
                            (item["purchase_options"] as? JsonArray)?.forEach(::option)
                        }
                    }
                    node.values.filter { it is JsonObject || it is JsonArray }.forEach { visit(it, depth + 1) }
                }
                is JsonArray -> node.forEach { visit(it, depth + 1) }
                else -> Unit
            }
        }
        document.select("[data-props]").forEach { element ->
            val raw = element.attr("data-props")
            if (raw.length <= 2_000_000) runCatching { visit(Json.parseToJsonElement(raw), 0) }
        }
        // Conflicting metadata cannot provide a trustworthy countdown.
        return candidates.mapNotNull { (id, values) -> values.singleOrNull()?.let { id to it } }.toMap()
    }
}

internal class SteamStorePurchasePageService(
    private val client: OkHttpClient,
    private val relatedContentService: SteamStoreRelatedContentService
) {
    fun fetch(
        appId: Int,
        countryCode: String?,
        language: String,
        steamLoginSecure: String?,
        accessToken: String?
    ): SteamStorePurchasePage = runCatching {
        val request = buildSteamStoreRequest(
            path = "/app/$appId/",
            query = mapOf("l" to language),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        ).newBuilder().header("Accept", "text/html").build()
        val page = client.newCall(request).execute().use { response ->
            if (response.code == 403 && steamLoginSecure?.isNotBlank() == true) {
                throw SteamStoreFamilyViewException()
            }
            if (!response.isSuccessful) return@use SteamStorePurchasePage()
            SteamStorePurchasePageParser.parse(response.body?.string().orEmpty(), appId)
        }
        val metadata = relatedContentService.fetch(
            appIds = page.bundles.flatMap { bundle -> bundle.items.map { it.appId } },
            countryCode = countryCode.orEmpty(),
            language = language,
            accessToken = accessToken
        ).associateBy { it.appId }
        page.copy(
            bundles = page.bundles.map { bundle ->
                val items = bundle.items.map { item ->
                    val app = metadata[item.appId]
                    item.copy(
                        name = app?.name?.takeIf(String::isNotBlank) ?: item.name,
                        imageUrl = app?.headerImageUrl.orEmpty()
                    )
                }
                bundle.copy(
                    imageUrl = items.firstNotNullOfOrNull {
                        it.imageUrl.takeIf(String::isNotBlank)
                    }.orEmpty(),
                    items = items
                )
            }
        )
    }.onFailure { error ->
        SteamDiagLogger.append(
            "store_purchase_page fetch_failed app_id=$appId type=${error.javaClass.simpleName}"
        )
    }.getOrElse { error ->
        if (error is SteamStoreFamilyViewException) throw error
        SteamStorePurchasePage()
    }
}
