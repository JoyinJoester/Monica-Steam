package takagi.ru.monica.steam.workshop

import kotlinx.serialization.json.*
import org.jsoup.Jsoup

/** Reads JSON hydration data, never executes Steam or workshop-author JavaScript. */
internal object SteamWorkshopParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun browse(html: String, appId: Int, page: Int): WorkshopBatch {
        val document = Jsoup.parse(html)
        // Steam now embeds hydration in an inert JSON block. Keep support for
        // cached/older pages without evaluating either format's bootstrap script.
        val context = document.selectFirst("script#valve-ssr-data[type=application/json]")?.let {
            json.parseToJsonElement(it.data()).jsonObject["renderContext"] as? JsonObject
                ?: throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
        } ?: run {
            val script = document.select("script").asSequence().map { it.data() }
                .firstOrNull { it.contains("window.SSR.renderContext=JSON.parse(") }
                ?: throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
            val encoded = jsonStringAfter(script, "window.SSR.renderContext=JSON.parse(")
            json.parseToJsonElement(encoded).jsonObject
        }
        val queries = json.parseToJsonElement(context.text("queryData")).jsonObject.array("queries")
        fun query(prefix: String): JsonObject? = queries.mapNotNull { it as? JsonObject }
            .firstOrNull { row ->
                val key = row.array("queryKey")
                (key.firstOrNull() as? JsonPrimitive)?.contentOrNull?.startsWith(prefix) == true &&
                    ((key.getOrNull(1) as? JsonObject)?.int("appid") == appId ||
                        (key.getOrNull(1) as? JsonPrimitive)?.intOrNull == appId)
            }?.obj("state")?.get("data") as? JsonObject
        val data = query("workshop_browse") ?: run {
            if (document.text().contains("too many requests", true)) throw WorkshopException(WorkshopFailure.RATE_LIMIT)
            throw WorkshopException(WorkshopFailure.UNAVAILABLE)
        }
        if (data.int("eresult") != 1 || data.int("current_page") != page || data["results"] !is JsonArray) {
            throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
        }
        val authors = data.array("creator_player_link_details").mapNotNull { it as? JsonObject }
            .associate { it.obj("public_data").text("steamid") to it.obj("public_data").text("persona_name") }
        val items = data.array("results").map { item(it.jsonObject, appId, authors) }.distinctBy { it.id }
        val tags = query("declared_tags")?.array("readytouse_tags").orEmpty()
            .flatMap { group ->
                group.jsonObject.array("tags").mapNotNull { value ->
                    val tag = value.jsonObject
                    if (tag.bool("admin_only") || tag.text("name").isBlank()) null
                    else WorkshopTag(tag.text("name"), tag.text("display_name").ifBlank { tag.text("name") }, group.jsonObject.text("name"))
                }
            }.distinctBy { it.value }
        return WorkshopBatch(items, page, page < data.int("total_pages"), data.int("total_count"), tags)
    }

    fun item(value: JsonObject, appId: Int, authors: Map<String, String> = emptyMap()): WorkshopItem {
        val id = value.text("publishedfileid")
        if (id.toULongOrNull() == null || id == "0" || value.int("consumer_appid") != appId || value.text("title").isBlank()) {
            throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
        }
        return WorkshopItem(
            id = id, appId = appId, title = value.text("title"), preview = value.text("preview_url"),
            author = authors[value.text("creator")] ?: value.text("creator"),
            description = value.text("file_description").ifBlank { value.text("short_description") },
            tags = value.array("tags").map { it.jsonObject.text("display_name").ifBlank { it.jsonObject.text("tag") } },
            subscriptions = value.long("subscriptions"), updated = value.long("time_updated"),
            created = value.long("time_created"),
            size = value.long("file_size"), fileType = value.int("file_type"),
            canSubscribe = value.bool("can_subscribe"), banned = value.bool("banned"), incompatible = value.bool("incompatible"),
            children = value.array("children").map { it.jsonObject.text("publishedfileid") }.filter { it.toULongOrNull() != null },
            childCount = value.int("num_children"),
            previews = value.array("previews").mapNotNull {
                it.jsonObject.takeIf { p -> p.int("preview_type") == 0 }?.text("url")?.takeIf(String::isNotBlank)
            }
        )
    }

    /** Handles escaped quotes and JSON terminators inside author text. */
    internal fun jsonStringAfter(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        if (markerIndex < 0) throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
        val start = markerIndex + marker.length
        if (source.getOrNull(start) != '"') throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
        var escaped = false
        for (index in start + 1 until source.length) {
            val c = source[index]
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == '"') return json.parseToJsonElement(source.substring(start, index + 1)).jsonPrimitive.content
        }
        throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
    }
}
internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.int(key: String): Int = text(key).toIntOrNull() ?: 0
internal fun JsonObject.long(key: String): Long = text(key).toLongOrNull() ?: 0
internal fun JsonObject.bool(key: String): Boolean = text(key) == "true" || text(key) == "1"
internal fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
