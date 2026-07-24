package takagi.ru.monica.steam.notifications.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SteamNotificationDetailField(
    val key: String,
    val value: String
)

data class SteamNotificationDetails(
    val message: String? = null,
    val fields: List<SteamNotificationDetailField> = emptyList()
)

object SteamNotificationDetailParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        bodyData: String,
        title: String,
        summary: String
    ): SteamNotificationDetails {
        val rawBody = bodyData.trim()
        if (rawBody.isBlank()) return SteamNotificationDetails()

        val element = runCatching { json.parseToJsonElement(rawBody) }.getOrNull()
            ?: return SteamNotificationDetails(
                message = rawBody.takeIf {
                    !it.looksLikeStructuredData() && it.isDistinctText(title, summary)
                }
            )
        if (element is JsonPrimitive) {
            val value = element.contentOrNull.orEmpty().trim()
            return SteamNotificationDetails(
                message = value.takeIf { it.isDistinctText(title, summary) }
            )
        }

        val flattened = buildList { flatten(element, path = "", output = this) }
        val message = flattened
            .firstOrNull { field -> field.key.leafKey() in MESSAGE_KEYS }
            ?.value
            ?.takeIf { it.isDistinctText(title, summary) }
        val fields = flattened
            .asSequence()
            .filter { field ->
                val leafKey = field.key.leafKey()
                leafKey !in TITLE_KEYS && leafKey !in MESSAGE_KEYS
            }
            .filter { field -> field.value.isDistinctText(title, summary, message.orEmpty()) }
            .distinctBy { field -> field.key.lowercase() to field.value }
            .take(MAX_DETAIL_FIELDS)
            .toList()

        return SteamNotificationDetails(message = message, fields = fields)
    }

    private fun flatten(
        element: JsonElement,
        path: String,
        output: MutableList<SteamNotificationDetailField>
    ) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                flatten(value, path.childPath(key), output)
            }

            is JsonArray -> {
                val primitiveValues = element.mapNotNull { child ->
                    (child as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
                }
                if (primitiveValues.size == element.size && primitiveValues.isNotEmpty()) {
                    output += SteamNotificationDetailField(path, primitiveValues.joinToString())
                } else {
                    element.forEachIndexed { index, child ->
                        flatten(child, "$path[$index]", output)
                    }
                }
            }

            is JsonPrimitive -> element.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { value -> output += SteamNotificationDetailField(path, value) }
        }
    }

    private fun String.childPath(child: String): String =
        if (isBlank()) child else "$this.$child"

    private fun String.leafKey(): String =
        substringAfterLast('.').substringBefore('[').lowercase()

    private fun String.isDistinctText(vararg existing: String): Boolean {
        val candidate = trim()
        return candidate.isNotBlank() && existing.none { value ->
            value.isNotBlank() && candidate.equals(value.trim(), ignoreCase = true)
        }
    }

    private fun String.looksLikeStructuredData(): Boolean =
        startsWith('{') || startsWith('[')

    private val TITLE_KEYS = setOf(
        "title",
        "app_name",
        "game_name",
        "item_name",
        "package_name",
        "display_name",
        "name"
    )
    private val MESSAGE_KEYS = setOf(
        "body",
        "message",
        "text",
        "comment",
        "description",
        "detail",
        "notification_body",
        "notification_text"
    )
    private const val MAX_DETAIL_FIELDS = 24
}
