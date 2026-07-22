package takagi.ru.monica.steam.trade

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamTradeOfferService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(
        account: SteamAccount,
        language: String = "english",
        activeOnly: Boolean = true
    ): SteamTradeOffersSnapshot {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val response = api.callProtobuf(
            iface = "IEconService",
            method = "GetTradeOffers",
            request = SteamProtoWriter().apply {
                writeBool(1, true)
                writeBool(2, true)
                writeBool(3, true)
                writeString(4, language)
                writeBool(6, activeOnly)
                writeBool(7, false)
            },
            accessToken = accessToken,
            useGet = true
        )
        return parseProtoSnapshot(response)
    }

    fun respond(
        account: SteamAccount,
        offer: SteamTradeOffer,
        action: SteamTradeOfferAction
    ): SteamTradeOfferActionResult {
        require(account.hasRealSteamId) { "real Steam ID required" }
        require(
            !account.accessToken.isNullOrBlank() || !account.steamLoginSecure.isNullOrBlank()
        ) { "Steam community session required" }
        when (action) {
            SteamTradeOfferAction.ACCEPT -> require(offer.direction == SteamTradeOfferDirection.RECEIVED)
            SteamTradeOfferAction.DECLINE -> require(offer.direction == SteamTradeOfferDirection.RECEIVED)
            SteamTradeOfferAction.CANCEL -> require(offer.direction == SteamTradeOfferDirection.SENT)
        }

        val sessionId = SteamInventoryService.newSessionId()
        val path = when (action) {
            SteamTradeOfferAction.ACCEPT -> "/tradeoffer/${offer.id}/accept"
            SteamTradeOfferAction.DECLINE -> "/tradeoffer/${offer.id}/decline"
            SteamTradeOfferAction.CANCEL -> "/tradeoffer/${offer.id}/cancel"
        }
        val form = linkedMapOf("sessionid" to listOf(sessionId))
        if (action == SteamTradeOfferAction.ACCEPT) {
            form["serverid"] = listOf("1")
            form["tradeofferid"] = listOf(offer.id)
            form["partner"] = listOf(offer.partnerSteamId)
            form["captcha"] = listOf("")
        }
        val payload = api.communityPostJson(
            path = path,
            form = form,
            cookies = SteamInventoryService.marketCookies(account, sessionId),
            referer = "https://steamcommunity.com/tradeoffer/${offer.id}/"
        )
        return parseActionResult(payload, action)
    }

    companion object {
        private const val STEAM_ID64_OFFSET = 76561197960265728L
        private const val ITEM_IMAGE_BASE =
            "https://community.fastly.steamstatic.com/economy/image/"

        fun parseSnapshot(payload: JsonObject): SteamTradeOffersSnapshot {
            val response = payload.obj("response") ?: payload
            val descriptions = response.array("descriptions")
                .mapNotNull { it as? JsonObject }
                .associateBy(::descriptionKey)
            return SteamTradeOffersSnapshot(
                received = parseOffers(
                    response.array("trade_offers_received"),
                    SteamTradeOfferDirection.RECEIVED,
                    descriptions
                ),
                sent = parseOffers(
                    response.array("trade_offers_sent"),
                    SteamTradeOfferDirection.SENT,
                    descriptions
                )
            )
        }

        fun parseProtoSnapshot(bytes: ByteArray): SteamTradeOffersSnapshot {
            val fields = SteamProtoReader(bytes).parseAll()
            val descriptions = fields
                .filter { it.number == 3 }
                .mapNotNull { it.bytes?.let(::parseProtoDescription) }
                .associateBy { descriptionKey(it) }
            val sent = fields
                .filter { it.number == 1 }
                .mapNotNull { field ->
                    field.bytes?.let {
                        parseProtoOffer(it, SteamTradeOfferDirection.SENT, descriptions)
                    }
                }
            val received = fields
                .filter { it.number == 2 }
                .mapNotNull { field ->
                    field.bytes?.let {
                        parseProtoOffer(it, SteamTradeOfferDirection.RECEIVED, descriptions)
                    }
                }
            return SteamTradeOffersSnapshot(
                received = received.sortedByDescending { maxOf(it.updatedAt, it.createdAt) },
                sent = sent.sortedByDescending { maxOf(it.updatedAt, it.createdAt) }
            )
        }

        fun parseActionResult(
            payload: JsonObject,
            action: SteamTradeOfferAction
        ): SteamTradeOfferActionResult {
            val tradeId = payload.string("tradeid").takeIf(String::isNotBlank)
            val mobile = payload.bool("needs_mobile_confirmation") ||
                payload.bool("needs_mobileconf")
            val email = payload.bool("needs_email_confirmation")
            val explicitSuccess = payload.boolOrNull("success")
            val success = explicitSuccess == true || tradeId != null || mobile || email ||
                (explicitSuccess == null && action != SteamTradeOfferAction.ACCEPT &&
                    payload.string("strError").isBlank())
            return SteamTradeOfferActionResult(
                success = success,
                requiresMobileConfirmation = mobile,
                requiresEmailConfirmation = email,
                tradeId = tradeId,
                message = payload.string("strError")
                    .ifBlank { payload.string("message") }
                    .takeIf(String::isNotBlank)
            )
        }

        private fun parseOffers(
            array: JsonArray,
            direction: SteamTradeOfferDirection,
            descriptions: Map<String, JsonObject>
        ): List<SteamTradeOffer> {
            return array.mapNotNull { raw ->
                val offer = raw as? JsonObject ?: return@mapNotNull null
                val id = offer.string("tradeofferid")
                if (id.isBlank()) return@mapNotNull null
                val partnerAccountId = offer.long("accountid_other")
                val stateCode = offer.int("trade_offer_state")
                SteamTradeOffer(
                    id = id,
                    direction = direction,
                    partnerAccountId = partnerAccountId,
                    partnerSteamId = (partnerAccountId + STEAM_ID64_OFFSET).toString(),
                    message = offer.string("message"),
                    state = SteamTradeOfferState.fromCode(stateCode),
                    rawStateCode = stateCode,
                    itemsToGive = parseItems(offer.array("items_to_give"), descriptions),
                    itemsToReceive = parseItems(offer.array("items_to_receive"), descriptions),
                    createdAt = offer.long("time_created"),
                    updatedAt = offer.long("time_updated"),
                    expirationTime = offer.long("expiration_time"),
                    escrowEndDate = offer.long("escrow_end_date"),
                    confirmationMethod = offer.int("confirmation_method")
                )
            }.sortedByDescending { maxOf(it.updatedAt, it.createdAt) }
        }

        private fun parseItems(
            array: JsonArray,
            descriptions: Map<String, JsonObject>
        ): List<SteamTradeOfferItem> {
            return array.mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                val description = descriptions[descriptionKey(item)]
                val rawIcon = description?.string("icon_url").orEmpty()
                SteamTradeOfferItem(
                    appId = item.int("appid"),
                    contextId = item.string("contextid"),
                    assetId = item.string("assetid"),
                    classId = item.string("classid"),
                    instanceId = item.string("instanceid"),
                    amount = item.int("amount").coerceAtLeast(1),
                    name = description?.string("name")
                        ?.ifBlank { description.string("market_name") }
                        .orEmpty(),
                    type = description?.string("type").orEmpty(),
                    iconUrl = when {
                        rawIcon.isBlank() -> ""
                        rawIcon.startsWith("https://") -> rawIcon
                        else -> ITEM_IMAGE_BASE + rawIcon.removePrefix("/")
                    },
                    tradable = description?.bool("tradable") ?: false,
                    marketable = description?.bool("marketable") ?: false,
                    missing = item.bool("missing")
                )
            }
        }

        private fun parseProtoOffer(
            bytes: ByteArray,
            direction: SteamTradeOfferDirection,
            descriptions: Map<String, ProtoDescription>
        ): SteamTradeOffer? {
            val fields = SteamProtoReader(bytes).parseAll()
            val byNumber = fields.groupBy(SteamProtoField::number)
            val id = byNumber.first(1)?.asUnsignedString().orEmpty()
            if (id.isBlank() || id == "0") return null
            val partnerAccountId = byNumber.first(2)?.asLong ?: 0L
            val stateCode = byNumber.first(5)?.asInt ?: 0
            return SteamTradeOffer(
                id = id,
                direction = direction,
                partnerAccountId = partnerAccountId,
                partnerSteamId = (partnerAccountId + STEAM_ID64_OFFSET).toString(),
                message = byNumber.first(3)?.asString.orEmpty(),
                state = SteamTradeOfferState.fromCode(stateCode),
                rawStateCode = stateCode,
                itemsToGive = byNumber[6].orEmpty().mapNotNull { field ->
                    field.bytes?.let { parseProtoAsset(it, descriptions) }
                },
                itemsToReceive = byNumber[7].orEmpty().mapNotNull { field ->
                    field.bytes?.let { parseProtoAsset(it, descriptions) }
                },
                createdAt = byNumber.first(9)?.asLong ?: 0L,
                updatedAt = byNumber.first(10)?.asLong ?: 0L,
                expirationTime = byNumber.first(4)?.asLong ?: 0L,
                escrowEndDate = byNumber.first(13)?.asLong ?: 0L,
                confirmationMethod = byNumber.first(14)?.asInt ?: 0
            )
        }

        private fun parseProtoAsset(
            bytes: ByteArray,
            descriptions: Map<String, ProtoDescription>
        ): SteamTradeOfferItem {
            val fields = SteamProtoReader(bytes).parse()
            val appId = fields[1]?.asInt ?: 0
            val classId = fields[4]?.asUnsignedString().orEmpty()
            val instanceId = fields[5]?.asUnsignedString().orEmpty()
            val description = descriptions["${appId}_${classId}_${instanceId}"]
                ?: descriptions["${appId}_${classId}_0"]
            return SteamTradeOfferItem(
                appId = appId,
                contextId = fields[2]?.asUnsignedString().orEmpty(),
                assetId = fields[3]?.asUnsignedString().orEmpty(),
                classId = classId,
                instanceId = instanceId,
                amount = (fields[7]?.asLong ?: 1L).coerceAtLeast(1L).toInt(),
                name = description?.name.orEmpty(),
                type = description?.type.orEmpty(),
                iconUrl = description?.iconUrl.orEmpty(),
                tradable = description?.tradable ?: false,
                marketable = description?.marketable ?: false,
                missing = fields[8]?.asBool ?: false
            )
        }

        private fun parseProtoDescription(bytes: ByteArray): ProtoDescription {
            val fields = SteamProtoReader(bytes).parse()
            val appId = fields[1]?.asInt ?: 0
            val classId = fields[2]?.asUnsignedString().orEmpty()
            val instanceId = fields[3]?.asUnsignedString().orEmpty()
            val rawIcon = fields[6]?.asString.orEmpty()
            return ProtoDescription(
                appId = appId,
                classId = classId,
                instanceId = instanceId,
                name = fields[14]?.asString.orEmpty().ifBlank {
                    fields[17]?.asString.orEmpty()
                },
                type = fields[16]?.asString.orEmpty(),
                iconUrl = when {
                    rawIcon.isBlank() -> ""
                    rawIcon.startsWith("https://") -> rawIcon
                    else -> ITEM_IMAGE_BASE + rawIcon.removePrefix("/")
                },
                tradable = fields[9]?.asBool ?: false,
                marketable = fields[25]?.asBool ?: false
            )
        }

        private fun descriptionKey(description: ProtoDescription): String {
            return "${description.appId}_${description.classId}_${description.instanceId}"
        }

        private fun Map<Int, List<SteamProtoField>>.first(number: Int): SteamProtoField? {
            return this[number]?.firstOrNull()
        }

        private fun SteamProtoField.asUnsignedString(): String {
            return java.lang.Long.toUnsignedString(asLong)
        }

        private data class ProtoDescription(
            val appId: Int,
            val classId: String,
            val instanceId: String,
            val name: String,
            val type: String,
            val iconUrl: String,
            val tradable: Boolean,
            val marketable: Boolean
        )

        private fun descriptionKey(value: JsonObject): String {
            return "${value.int("appid")}_${value.string("classid")}_${value.string("instanceid")}"
        }

        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonObject.array(key: String): JsonArray =
            this[key] as? JsonArray ?: JsonArray(emptyList())

        private fun JsonObject.string(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

        private fun JsonObject.int(key: String): Int {
            val primitive = this[key] as? JsonPrimitive ?: return 0
            return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: 0
        }

        private fun JsonObject.long(key: String): Long {
            val primitive = this[key] as? JsonPrimitive ?: return 0L
            return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
        }

        private fun JsonObject.bool(key: String): Boolean = boolOrNull(key) == true

        private fun JsonObject.boolOrNull(key: String): Boolean? {
            val primitive = this[key] as? JsonPrimitive ?: return null
            return primitive.booleanOrNull ?: when (primitive.contentOrNull?.lowercase()) {
                "1", "true" -> true
                "0", "false" -> false
                else -> null
            }
        }
    }
}
