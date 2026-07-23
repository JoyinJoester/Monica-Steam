package takagi.ru.monica.steam.store.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.steam.store.domain.*

object SteamStoreParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseFeatured(payload: String): SteamStoreHome {
        val root = json.parseToJsonElement(payload).jsonObject
        return SteamStoreHome(
            specials = categoryItems(root["specials"]),
            topSellers = categoryItems(root["top_sellers"]),
            newReleases = categoryItems(root["new_releases"]),
            comingSoon = categoryItems(root["coming_soon"])
        )
    }

    fun parseSearch(payload: String): List<SteamStoreItem> {
        val root = json.parseToJsonElement(payload).jsonObject
        return root.array("items").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val appId = item.int("id") ?: return@mapNotNull null
            val price = item.obj("price")
            val platforms = item.obj("platforms")
            SteamStoreItem(
                appId = appId,
                name = item.string("name").orEmpty(),
                imageUrl = item.string("tiny_image").orEmpty(),
                headerImageUrl = item.string("tiny_image").orEmpty(),
                currency = price?.string("currency") ?: "CNY",
                initialPriceCents = price?.int("initial"),
                finalPriceCents = price?.int("final"),
                discountPercent = calculateDiscount(price?.int("initial"), price?.int("final")),
                windows = platforms?.bool("windows") == true,
                mac = platforms?.bool("mac") == true,
                linux = platforms?.bool("linux") == true,
                metascore = item.string("metascore")?.toIntOrNull()
            )
        }
    }

    fun parseDetail(appId: Int, payload: String): SteamStoreDetail? {
        val root = json.parseToJsonElement(payload).jsonObject
        val wrapper = root[appId.toString()] as? JsonObject ?: return null
        if (wrapper.bool("success") != true) return null
        val data = wrapper.obj("data") ?: return null
        val price = data.obj("price_overview")
        val platforms = data.obj("platforms")
        val packageId = data.array("package_groups")
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .flatMap { it.array("subs").asSequence() }
            .mapNotNull { (it as? JsonObject)?.int("packageid") }
            .firstOrNull()
        return SteamStoreDetail(
            appId = data.int("steam_appid") ?: appId,
            name = data.string("name").orEmpty(),
            type = data.string("type") ?: "game",
            shortDescription = stripHtml(data.string("short_description").orEmpty()),
            about = stripHtml(data.string("about_the_game").orEmpty()),
            headerImageUrl = data.string("header_image").orEmpty(),
            backgroundImageUrl = data.string("background_raw")
                ?: data.string("background").orEmpty(),
            screenshots = data.array("screenshots").mapNotNull {
                (it as? JsonObject)?.string("path_full")
            },
            developers = data.stringArray("developers"),
            publishers = data.stringArray("publishers"),
            genres = data.array("genres").mapNotNull {
                (it as? JsonObject)?.string("description")
            },
            releaseDate = data.obj("release_date")?.string("date").orEmpty(),
            currency = price?.string("currency") ?: "CNY",
            initialPriceCents = price?.int("initial"),
            finalPriceCents = price?.int("final"),
            discountPercent = price?.int("discount_percent") ?: 0,
            isFree = data.bool("is_free") == true,
            windows = platforms?.bool("windows") == true,
            mac = platforms?.bool("mac") == true,
            linux = platforms?.bool("linux") == true,
            packageId = packageId
        )
    }

    fun parseWishlist(payload: String): List<SteamWishlistItem> {
        val root = json.parseToJsonElement(payload) as? JsonObject ?: return emptyList()
        return root.mapNotNull { (appIdKey, element) ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val appId = item.int("appid") ?: appIdKey.toIntOrNull() ?: return@mapNotNull null
            val sub = item.array("subs").firstOrNull() as? JsonObject
            val discountBlock = sub?.string("discount_block").orEmpty()
            SteamWishlistItem(
                appId = appId,
                name = item.string("name").orEmpty(),
                imageUrl = item.string("capsule")
                    ?: item.string("capsule_image")
                    ?: item.string("header_image").orEmpty(),
                packageId = sub?.int("id") ?: sub?.int("packageid"),
                discountPercent = sub?.int("discount_pct")
                    ?: item.int("discount_percent")
                    ?: 0,
                formattedInitialPrice = extractWishlistPrice(
                    discountBlock,
                    "discount_original_price"
                ),
                formattedFinalPrice = extractWishlistPrice(
                    discountBlock,
                    "discount_final_price"
                ),
                priority = item.int("priority") ?: 0,
                addedAtEpochSeconds = item.long("added")
                    ?: item.long("date_added")
                    ?: 0L
            )
        }.sortedWith(
            compareBy<SteamWishlistItem> { it.priority }
                .thenByDescending { it.addedAtEpochSeconds }
        )
    }

    fun parseWishlistMutationSuccess(payload: String): Boolean {
        val root = json.parseToJsonElement(payload) as? JsonObject ?: return false
        return root.bool("success") == true || root.int("success") == 1
    }

    private fun categoryItems(element: JsonElement?): List<SteamStoreItem> {
        val category = element as? JsonObject ?: return emptyList()
        return category.array("items").mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            val appId = item.int("id") ?: return@mapNotNull null
            SteamStoreItem(
                appId = appId,
                name = item.string("name").orEmpty(),
                imageUrl = item.string("large_capsule_image")
                    ?: item.string("small_capsule_image").orEmpty(),
                headerImageUrl = item.string("header_image").orEmpty(),
                currency = item.string("currency") ?: "CNY",
                initialPriceCents = item.int("original_price"),
                finalPriceCents = item.int("final_price"),
                discountPercent = item.int("discount_percent") ?: 0,
                windows = item.bool("windows_available") == true,
                mac = item.bool("mac_available") == true,
                linux = item.bool("linux_available") == true
            )
        }
    }

    private fun stripHtml(value: String): String = value
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</?(p|div|li|ul|ol|h[1-6])[^>]*>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun calculateDiscount(initial: Int?, final: Int?): Int {
        if (initial == null || final == null || initial <= 0 || final >= initial) return 0
        return ((initial - final) * 100 / initial).coerceIn(0, 100)
    }

    private fun extractWishlistPrice(block: String, className: String): String {
        if (block.isBlank()) return ""
        val match = Regex(
            """class\s*=\s*[\"'][^\"']*\b${Regex.escape(className)}\b[^\"']*[\"'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(block) ?: return ""
        return match.groupValues[1]
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&yen;", "¥")
            .replace("&euro;", "€")
            .replace("&pound;", "£")
            .replace("&amp;", "&")
            .trim()
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())

    private fun JsonObject.stringArray(key: String): List<String> =
        array(key).mapNotNull { it.jsonPrimitive.contentOrNull }
}
