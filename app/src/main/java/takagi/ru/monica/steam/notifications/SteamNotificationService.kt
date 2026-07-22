package takagi.ru.monica.steam.notifications

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient

class SteamNotificationService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(
        account: SteamAccount,
        fetchedAt: Long = System.currentTimeMillis()
    ): SteamNotificationSnapshot {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val payload = api.steamApiGetJson(
            path = "/ISteamNotificationService/GetSteamNotifications/v1/",
            query = linkedMapOf(
                "include_hidden" to "false",
                "include_confirmation_count" to "true",
                "include_pinned_counts" to "true",
                "include_read" to "true",
                "count_only" to "false"
            ),
            accessToken = accessToken
        )
        return SteamNotificationParser.parse(payload, fetchedAt)
    }
}

object SteamNotificationParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, fetchedAt: Long = System.currentTimeMillis()): SteamNotificationSnapshot {
        val payload = json.parseToJsonElement(raw).jsonObject
        return parse(payload, fetchedAt)
    }

    fun parse(payload: JsonObject, fetchedAt: Long = System.currentTimeMillis()): SteamNotificationSnapshot {
        val response = payload.obj("response") ?: payload
        val notifications = response.array("notifications")
            .mapNotNull { it as? JsonObject }
            .mapNotNull(::parseNotification)
            .filterNot(SteamNotification::hidden)
            .sortedByDescending(SteamNotification::timestamp)
        return SteamNotificationSnapshot(
            notifications = notifications,
            confirmationCount = response.int("confirmation_count"),
            pendingGiftCount = response.int("pending_gift_count"),
            pendingFriendCount = response.int("pending_friend_count"),
            unreadCount = response.int("unread_count"),
            pendingFamilyInviteCount = response.int("pending_family_invite_count"),
            fetchedAt = fetchedAt
        )
    }

    private fun parseNotification(raw: JsonObject): SteamNotification? {
        val id = raw.string("notification_id")
        if (id.isBlank()) return null
        val type = raw.int("notification_type")
        val kind = SteamNotificationKind.fromType(type)
        val bodyData = raw.string("body_data")
        val body = bodyData.takeIf { it.trimStart().startsWith("{") }
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val title = body?.firstText(
            "title",
            "app_name",
            "game_name",
            "item_name",
            "package_name",
            "display_name",
            "name"
        ).orEmpty().ifBlank { defaultTitle(kind) }
        val summary = body?.firstText(
            "gifter_name",
            "sender",
            "persona_name",
            "body",
            "message",
            "text",
            "comment"
        ).orEmpty().ifBlank {
            bodyData.takeUnless { body != null }.orEmpty()
        }
        val relatedId = body?.firstText(
            "giftid",
            "gift_id",
            "tradeofferid",
            "trade_offer_id",
            "appid",
            "app_id",
            "familyid",
            "family_id"
        )?.takeIf(String::isNotBlank)
        return SteamNotification(
            id = id,
            type = type,
            kind = kind,
            title = title,
            summary = summary,
            relatedId = relatedId,
            bodyData = bodyData,
            read = raw.bool("read"),
            timestamp = raw.long("timestamp"),
            hidden = raw.bool("hidden"),
            expiry = raw.long("expiry"),
            viewed = raw.long("viewed")
        )
    }

    private fun defaultTitle(kind: SteamNotificationKind): String = when (kind) {
        SteamNotificationKind.GIFT -> "Steam gift"
        SteamNotificationKind.COMMENT -> "New comment"
        SteamNotificationKind.ITEM -> "New item"
        SteamNotificationKind.FRIEND_INVITE -> "Friend invitation"
        SteamNotificationKind.SALE -> "Steam sale"
        SteamNotificationKind.PRELOAD -> "Preload available"
        SteamNotificationKind.WISHLIST -> "Wishlist update"
        SteamNotificationKind.TRADE_OFFER -> "Trade offer"
        SteamNotificationKind.GENERAL -> "Steam notification"
        SteamNotificationKind.HELP_REQUEST -> "Steam Support"
        SteamNotificationKind.ASYNC_GAME -> "Game update"
        SteamNotificationKind.CHAT_MESSAGE -> "Chat message"
        SteamNotificationKind.MODERATOR_MESSAGE -> "Moderator message"
        SteamNotificationKind.FAMILY -> "Steam Family"
        SteamNotificationKind.PARENTAL -> "Parental controls"
        SteamNotificationKind.GAME_INVITE -> "Game invitation"
        SteamNotificationKind.TRADE_REVERSED -> "Trade update"
        SteamNotificationKind.UNKNOWN -> "Steam notification"
    }

    private fun JsonObject.firstText(vararg keys: String): String? {
        keys.forEach { key ->
            string(key).takeIf(String::isNotBlank)?.let { return it }
        }
        return null
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: return ""
        return primitive.contentOrNull.orEmpty()
    }

    private fun JsonObject.int(key: String): Int {
        val primitive = this[key] as? JsonPrimitive ?: return 0
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: 0
    }

    private fun JsonObject.long(key: String): Long {
        val primitive = this[key] as? JsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
    }

    private fun JsonObject.bool(key: String): Boolean {
        val primitive = this[key] as? JsonPrimitive ?: return false
        return primitive.booleanOrNull ?: when (primitive.contentOrNull?.lowercase()) {
            "1", "true" -> true
            else -> false
        }
    }
}
