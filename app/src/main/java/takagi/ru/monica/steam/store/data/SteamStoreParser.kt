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
import org.jsoup.Jsoup
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreBaseGame
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreDemo
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption
import takagi.ru.monica.steam.store.requirements.domain.SteamStoreSystemRequirements

object SteamStoreParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseFeatured(payload: String, countryCode: String? = null): SteamStoreHome {
        val root = json.parseToJsonElement(payload).jsonObject
        return SteamStoreHome(
            specials = categoryItems(root["specials"], countryCode),
            topSellers = categoryItems(root["top_sellers"], countryCode),
            newReleases = categoryItems(root["new_releases"], countryCode),
            comingSoon = categoryItems(root["coming_soon"], countryCode)
        )
    }

    fun parseDiscoveryEvents(payload: String): List<SteamStoreEvent> {
        val events = linkedMapOf<String, SteamStoreEvent>()
        val document = Jsoup.parse(payload, STEAM_STORE_BASE)
        document.select(".home_page_takeunder, .home_area_spotlight").forEach { container ->
            val link = container.selectFirst("a[href*=/sale/]") ?: return@forEach
            val canonicalUrl = link.absUrl("href").substringBefore('?')
            if (!canonicalUrl.startsWith(STEAM_SALE_BASE)) return@forEach
            val current = events[canonicalUrl]
            val title = link.attr("aria-label").ifBlank {
                container.selectFirst("img[alt]")?.attr("alt").orEmpty()
            }.trim()
            val image = eventImage(container)
            val badge = container.selectFirst(".home_capsule_banner")?.text().orEmpty().trim()
            val candidate = SteamStoreEvent(
                title = title,
                url = canonicalUrl,
                imageUrl = image,
                badge = badge
            )
            events[canonicalUrl] = SteamStoreEvent(
                title = current?.title?.takeIf(String::isNotBlank)
                    ?: candidate.title.takeIf(String::isNotBlank)
                    ?: eventTitleFromUrl(canonicalUrl),
                url = canonicalUrl,
                imageUrl = candidate.imageUrl.takeIf(String::isNotBlank)
                    ?: current?.imageUrl.orEmpty(),
                badge = candidate.badge.takeIf(String::isNotBlank) ?: current?.badge.orEmpty()
            )
        }
        return events.values.filter { it.url.startsWith(STEAM_SALE_BASE) }.take(MAX_EVENTS)
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
        val pcRequirements = data.obj("pc_requirements")
        val packageOptions = data.array("package_groups")
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .flatMap { it.array("subs").asSequence() }
            .mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val packageId = item.int("packageid")?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                val optionText = stripHtml(item.string("option_text").orEmpty())
                if (isCommercialLicense(optionText)) return@mapNotNull null
                SteamStorePackageOption(
                    packageId = packageId,
                    title = stripPackagePrice(optionText),
                    description = stripHtml(item.string("option_description").orEmpty()),
                    priceCents = item.int("price_in_cents_with_discount")
                        ?: item.int("price_in_cents"),
                    discountPercent = item.string("percent_savings_text")
                        ?.filter(Char::isDigit)
                        ?.toIntOrNull()
                        ?.coerceIn(0, 100)
                        ?: 0,
                    isFreeLicense = item.bool("is_free_license") == true,
                    canGetFreeLicense = item.bool("can_get_free_license") == true
                )
            }
            .distinctBy(SteamStorePackageOption::packageId)
            .toList()
        val fullGame = data.obj("fullgame")?.let { game ->
            val fullGameAppId = game.int("appid")?.takeIf { it > 0 } ?: return@let null
            SteamStoreBaseGame(
                appId = fullGameAppId,
                name = game.string("name").orEmpty()
            )
        }
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
            packageId = packageOptions.firstOrNull()?.packageId,
            packageOptions = packageOptions,
            demos = data.array("demos").mapNotNull { element ->
                val demo = element as? JsonObject ?: return@mapNotNull null
                val demoAppId = demo.int("appid")?.takeIf { it > 0 } ?: return@mapNotNull null
                SteamStoreDemo(
                    appId = demoAppId,
                    description = stripHtml(demo.string("description").orEmpty())
                )
            }.distinctBy(SteamStoreDemo::appId),
            dlcAppIds = data.array("dlc").mapNotNull { element ->
                element.jsonPrimitive.intOrNull?.takeIf { it > 0 }
            }.distinct(),
            fullGame = fullGame,
            categories = data.array("categories").mapNotNull {
                (it as? JsonObject)?.string("description")
            },
            supportedLanguages = stripHtml(data.string("supported_languages").orEmpty()),
            controllerSupport = data.string("controller_support").orEmpty(),
            systemRequirements = SteamStoreSystemRequirements(
                minimum = stripRequirementHtml(pcRequirements?.string("minimum").orEmpty()),
                recommended = stripRequirementHtml(
                    pcRequirements?.string("recommended").orEmpty()
                )
            ),
            website = data.string("website").orEmpty(),
            recommendationCount = data.obj("recommendations")?.int("total"),
            achievementCount = data.obj("achievements")?.int("total")
        )
    }

    private fun categoryItems(element: JsonElement?, countryCode: String? = null): List<SteamStoreItem> {
        val category = element as? JsonObject ?: return emptyList()
        return category.array("items").mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            val appId = item.int("id") ?: return@mapNotNull null
            if (appId <= 0 || item.int("type")?.let { it != 0 } == true) return@mapNotNull null
            SteamStoreItem(
                appId = appId,
                name = item.string("name").orEmpty(),
                imageUrl = item.string("large_capsule_image")
                    ?: item.string("small_capsule_image").orEmpty(),
                headerImageUrl = item.string("header_image").orEmpty(),
                currency = item.string("currency") ?: steamStoreCurrencyForCountry(countryCode),
                initialPriceCents = item.int("original_price"),
                // Unpriced hardware/preorders can carry a zero final_price.
                finalPriceCents = item.int("final_price")?.takeUnless {
                    it == 0 && item.int("original_price") == null && item.bool("is_free") != true
                },
                discountPercent = item.int("discount_percent") ?: 0,
                windows = item.bool("windows_available") == true,
                mac = item.bool("mac_available") == true,
                linux = item.bool("linux_available") == true
            )
        }.distinctBy(SteamStoreItem::appId)
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

    private fun stripRequirementHtml(value: String): String = stripHtml(value)
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")

    private fun isCommercialLicense(title: String): Boolean =
        COMMERCIAL_LICENSE.containsMatchIn(title)

    private fun stripPackagePrice(title: String): String = title
        .replace(PACKAGE_PRICE_SUFFIX, "")
        .replace(PACKAGE_FREE_SUFFIX, "")
        .trim()

    private fun eventImage(container: org.jsoup.nodes.Element): String {
        val candidates = listOf(
            container.selectFirst("img[data-image-url]")?.attr("abs:data-image-url"),
            container.selectFirst("img[src]")?.attr("abs:src"),
            STYLE_IMAGE.find(container.attr("style"))?.groupValues?.getOrNull(1)
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }

    private fun eventTitleFromUrl(url: String): String = url.substringAfterLast('/')
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

    private val STYLE_IMAGE = Regex("url\\(['\"]?([^'\")]+)", RegexOption.IGNORE_CASE)
    private val COMMERCIAL_LICENSE = Regex("\\bcommercial\\s+licen[cs]e\\b", RegexOption.IGNORE_CASE)
    private val PACKAGE_PRICE_SUFFIX = Regex(
        "\\s+-\\s+(?:[A-Z]{2,4}\\s*)?(?:\\p{Sc}\\s*)?\\d[\\d.,\\s]*$",
        RegexOption.IGNORE_CASE
    )
    private val PACKAGE_FREE_SUFFIX = Regex("\\s+-\\s+(?:free|免费|免費)$", RegexOption.IGNORE_CASE)
    private const val MAX_EVENTS = 12
    private const val STEAM_STORE_BASE = "https://store.steampowered.com"
    private const val STEAM_SALE_BASE = "https://store.steampowered.com/sale/"

    private fun calculateDiscount(initial: Int?, final: Int?): Int {
        if (initial == null || final == null || initial <= 0 || final >= initial) return 0
        return ((initial - final) * 100 / initial).coerceIn(0, 100)
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
