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
    val fields: List<SteamNotificationDetailField> = emptyList(),
    val appIds: List<Int> = emptyList(),
    val actorSteamId: String? = null,
    val inventoryReference: SteamNotificationInventoryReference? = null
)

data class SteamNotificationInventoryReference(
    val appId: Int,
    val contextId: String,
    val assetId: String
)

object SteamNotificationDetailParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        bodyData: String,
        title: String,
        summary: String,
        kind: SteamNotificationKind = SteamNotificationKind.UNKNOWN
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
        val actorSteamId = if (kind == SteamNotificationKind.FRIEND_INVITE) {
            flattened.firstOrNull { field -> field.key.leafKey() in ACTOR_ID_KEYS }
                ?.value
                ?.toSteamId64()
        } else {
            null
        }
        val inventoryReference = if (kind == SteamNotificationKind.ITEM) {
            val appId = flattened.firstOrNull { it.key.leafKey() == "appid" }
                ?.value?.toIntOrNull() ?: 0
            val contextId = flattened.firstOrNull { it.key.leafKey() == "contextid" }
                ?.value.orEmpty()
            val assetId = flattened.firstOrNull { it.key.leafKey() == "assetid" }
                ?.value.orEmpty()
            SteamNotificationInventoryReference(appId, contextId, assetId)
                .takeIf { it.appId > 0 && it.contextId.isNotBlank() && it.assetId.isNotBlank() }
        } else {
            null
        }
        val appIds = flattened
            .asSequence()
            .filter { field -> field.key.leafKey() in APP_ID_KEYS }
            .filter { kind != SteamNotificationKind.ITEM }
            .flatMap { field -> APP_ID_PATTERN.findAll(field.value).map { it.value.toIntOrNull() } }
            .filterNotNull()
            .filter { it > 0 }
            .distinct()
            .take(MAX_APP_IDS)
            .toList()
        val fields = flattened
            .asSequence()
            .filter { field ->
                val leafKey = field.key.leafKey()
                leafKey !in TITLE_KEYS &&
                    leafKey !in MESSAGE_KEYS &&
                    leafKey !in TECHNICAL_KEYS &&
                    !(kind == SteamNotificationKind.FRIEND_INVITE && leafKey in ACTOR_ID_KEYS) &&
                    !(kind == SteamNotificationKind.ITEM && leafKey in ITEM_REFERENCE_KEYS)
            }
            .filter { field -> field.value.isDistinctText(title, summary, message.orEmpty()) }
            .map { field ->
                if (kind == SteamNotificationKind.FRIEND_INVITE && field.key.leafKey() == "state") {
                    field.copy(
                        key = "friend_invite_state",
                        value = field.value.toFriendInviteState()
                    )
                } else {
                    field
                }
            }
            .distinctBy { field -> field.key.lowercase() to field.value }
            .take(MAX_DETAIL_FIELDS)
            .toList()

        return SteamNotificationDetails(
            message = message,
            fields = fields,
            appIds = appIds,
            actorSteamId = actorSteamId,
            inventoryReference = inventoryReference
        )
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

    private fun String.toSteamId64(): String? {
        val value = trim().toLongOrNull() ?: return null
        return when {
            value >= STEAM_ID64_BASE -> value.toString()
            value > 0L -> (STEAM_ID64_BASE + value).toString()
            else -> null
        }
    }

    private fun String.toFriendInviteState(): String = when (toIntOrNull()) {
        1, 2 -> "pending"
        3 -> "accepted"
        4 -> "ignored"
        else -> this
    }

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
    private val APP_ID_KEYS = setOf("appid", "app_id", "appids", "app_ids")
    private val ACTOR_ID_KEYS = setOf("requestor_id", "requestorid", "steamid", "steam_id")
    private val ITEM_REFERENCE_KEYS = setOf("appid", "contextid", "assetid")
    private val TECHNICAL_KEYS = APP_ID_KEYS + setOf(
        "count",
        "quantity",
        "item_count",
        "packageid",
        "package_id",
        "bundleid",
        "bundle_id"
    )
    private val APP_ID_PATTERN = Regex("\\d+")
    private const val MAX_APP_IDS = 12
    private const val MAX_DETAIL_FIELDS = 24
    private const val STEAM_ID64_BASE = 76561197960265728L
}
