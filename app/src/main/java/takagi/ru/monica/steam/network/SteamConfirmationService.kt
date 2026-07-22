package takagi.ru.monica.steam.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.steam.core.SteamTotp
import takagi.ru.monica.steam.data.SteamAccount

data class SteamConfirmation(
    val id: String,
    val nonce: String,
    val type: String,
    val headline: String,
    val summary: String,
    val imageUrl: String,
    val creationTime: Long
)

data class SteamBatchResult(
    val ok: Int,
    val failed: Int
)

class SteamConfirmationService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(account: SteamAccount, nowSeconds: Long = System.currentTimeMillis() / 1000L): List<SteamConfirmation> {
        require(account.canUseConfirmations) { "Steam account has no identity secret or access token" }
        val query = baseQuery(account, nowSeconds, "list") + ("tag" to "list")
        val payload = api.communityGetJson(
            path = "/mobileconf/getlist",
            query = query,
            cookies = cookies(account)
        )
        if (payload.bool("success") != true) {
            return emptyList()
        }
        val confs = payload["conf"] as? JsonArray ?: return emptyList()
        return confs.mapNotNull { (it as? JsonObject)?.toConfirmation() }
    }

    fun respond(
        account: SteamAccount,
        confirmation: SteamConfirmation,
        accept: Boolean,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        require(account.canUseConfirmations) { "Steam account has no identity secret, real SteamID, or access token" }
        val op = if (accept) "allow" else "cancel"
        val form = (baseQuery(account, nowSeconds, op) + mapOf(
            "tag" to op,
            "op" to op,
            "cid" to confirmation.id,
            "ck" to confirmation.nonce
        )).mapValues { listOf(it.value) }
        val payload = api.communityPostJson(
            path = "/mobileconf/ajaxop",
            form = form,
            cookies = cookies(account)
        )
        return payload.bool("success") == true
    }

    fun respondMultiple(
        account: SteamAccount,
        confirmations: List<SteamConfirmation>,
        accept: Boolean,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): SteamBatchResult {
        if (confirmations.isEmpty()) return SteamBatchResult(ok = 0, failed = 0)
        require(account.canUseConfirmations) { "Steam account has no identity secret, real SteamID, or access token" }
        val op = if (accept) "allow" else "cancel"
        val form = baseQuery(account, nowSeconds, op)
            .mapValues { listOf(it.value) }
            .toMutableMap()
        form["tag"] = listOf(op)
        form["op"] = listOf(op)
        form["cid[]"] = confirmations.map { it.id }
        form["ck[]"] = confirmations.map { it.nonce }

        runCatching {
            val payload = api.communityPostJson(
                path = "/mobileconf/multiajaxop",
                form = form,
                cookies = cookies(account)
            )
            if (payload.bool("success") == true) {
                return SteamBatchResult(ok = confirmations.size, failed = 0)
            }
        }

        var ok = 0
        var failed = 0
        confirmations.forEach { confirmation ->
            if (runCatching { respond(account, confirmation, accept, nowSeconds) }.getOrDefault(false)) {
                ok++
            } else {
                failed++
            }
        }
        return SteamBatchResult(ok = ok, failed = failed)
    }

    private fun baseQuery(account: SteamAccount, nowSeconds: Long, tag: String): Map<String, String> {
        val identitySecret = requireNotNull(account.identitySecret) { "identity secret required" }
        return mapOf(
            "p" to account.deviceId,
            "a" to account.steamId,
            "k" to SteamTotp.generateConfirmationHash(identitySecret, nowSeconds, tag),
            "t" to nowSeconds.toString(),
            "m" to "react"
        )
    }

    private fun cookies(account: SteamAccount): Map<String, String> {
        val token = account.accessToken.orEmpty()
        return mapOf(
            "steamLoginSecure" to "${account.steamId}||$token",
            "mobileClient" to "android",
            "mobileClientVersion" to "777777 3.6.4"
        )
    }

    private fun JsonObject.toConfirmation(): SteamConfirmation? {
        val id = stringAny("id", "confid") ?: return null
        val nonce = stringAny("nonce", "key") ?: return null
        val summaryText = when (val summary = this["summary"]) {
            is JsonArray -> summary.joinToString("\n") { it.textValue() }
            else -> summary.textValue()
        }
        return SteamConfirmation(
            id = id,
            nonce = nonce,
            type = stringAny("type", "conf_type") ?: "",
            headline = stringAny("headline", "creator") ?: "",
            summary = summaryText,
            imageUrl = imageUrl(),
            creationTime = longAny("creation_time", "time") ?: 0L
        )
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.booleanOrNull ?: when (primitive.contentOrNull?.lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
    }

    private fun JsonObject.stringAny(vararg keys: String): String? {
        keys.forEach { key ->
            val value = this[key].textValue()
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun JsonObject.longAny(vararg keys: String): Long? {
        keys.forEach { key ->
            val primitive = this[key]?.jsonPrimitive ?: return@forEach
            primitive.longOrNull?.let { return it }
            primitive.contentOrNull?.toLongOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.imageUrl(): String {
        val directUrl = stringAny(
            "icon",
            "icon_url",
            "image",
            "image_url",
            "imageUrl",
            "creator_avatar"
        )
        if (!directUrl.isNullOrBlank()) {
            return directUrl
        }

        val details = this["details"] as? JsonObject ?: return ""
        return details.stringAny(
            "icon",
            "icon_url",
            "image",
            "image_url",
            "imageUrl",
            "asset_icon",
            "asset_icon_url"
        ).orEmpty()
    }

    private fun JsonElement?.textValue(): String {
        return (this as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
