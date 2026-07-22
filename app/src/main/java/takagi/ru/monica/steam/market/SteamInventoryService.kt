package takagi.ru.monica.steam.market

import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient

class SteamInventoryService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetchOverview(account: SteamAccount): SteamInventoryOverview {
        requireMarketSession(account)
        val html = api.communityGetText(
            path = "/profiles/${account.steamId}/inventory/",
            query = emptyMap(),
            cookies = marketCookies(account, newSessionId())
        )
        return parseOverviewHtml(html)
    }

    fun fetchItems(
        account: SteamAccount,
        appId: Int,
        contextId: String,
        language: String,
        startAssetId: String? = null,
        count: Int = 75
    ): SteamInventoryPage {
        requireMarketSession(account)
        val query = linkedMapOf(
            "l" to language,
            "count" to count.coerceIn(1, 2000).toString()
        )
        startAssetId?.takeIf { it.isNotBlank() }?.let { query["start_assetid"] = it }
        val payload = api.communityGetJson(
            path = "/inventory/${account.steamId}/$appId/$contextId",
            query = query,
            cookies = marketCookies(account, newSessionId())
        )
        return parseInventoryPage(payload)
    }

    companion object {
        private const val ITEM_IMAGE_BASE =
            "https://community.fastly.steamstatic.com/economy/image/"
        private val parser = Json { ignoreUnknownKeys = true }
        private val random = SecureRandom()

        fun parseOverviewHtml(html: String): SteamInventoryOverview {
            val apps = extractJsonObject(html, "g_rgAppContextData")
            val walletObject = extractJsonObject(html, "g_rgWalletInfo")
            val wallet = walletObject?.let(::parseWallet) ?: SteamWalletInfo.Fallback
            val games = buildList {
                apps?.forEach { (appIdText, rawApp) ->
                    val app = rawApp as? JsonObject ?: return@forEach
                    val contexts = app["rgContexts"] as? JsonObject ?: return@forEach
                    contexts.forEach { (contextId, rawContext) ->
                        val context = rawContext as? JsonObject ?: return@forEach
                        val count = context.intValue("asset_count")
                        if (count <= 0) return@forEach
                        add(
                            SteamInventoryGame(
                                appId = appIdText.toIntOrNull() ?: 0,
                                contextId = contextId,
                                name = app.stringValue("name"),
                                contextName = context.stringValue("name"),
                                iconUrl = app.stringValue("icon"),
                                itemCount = count
                            )
                        )
                    }
                }
            }.sortedByDescending { it.itemCount }
            return SteamInventoryOverview(games = games, wallet = wallet)
        }

        fun parseInventoryPage(payload: JsonObject): SteamInventoryPage {
            val descriptions = (payload["descriptions"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .associateBy { "${it.stringValue("classid")}_${it.stringValue("instanceid")}" }
            val items = (payload["assets"] as? JsonArray).orEmpty().mapNotNull { rawAsset ->
                val asset = rawAsset as? JsonObject ?: return@mapNotNull null
                val key = "${asset.stringValue("classid")}_${asset.stringValue("instanceid")}"
                val description = descriptions[key] ?: return@mapNotNull null
                SteamInventoryItem(
                    appId = asset.intValue("appid"),
                    contextId = asset.stringValue("contextid"),
                    assetId = asset.stringValue("assetid"),
                    classId = asset.stringValue("classid"),
                    instanceId = asset.stringValue("instanceid"),
                    amount = asset.intValue("amount").coerceAtLeast(1),
                    marketHashName = description.stringValue("market_hash_name"),
                    name = description.stringValue("name").ifBlank {
                        description.stringValue("market_name")
                    },
                    type = description.stringValue("type"),
                    iconUrl = itemImageUrl(description.stringValue("icon_url")),
                    marketable = description.boolValue("marketable"),
                    tradable = description.boolValue("tradable"),
                    commodity = description.boolValue("commodity"),
                    publisherFeePercent = description.doubleValueOrNull("market_fee")
                )
            }
            return SteamInventoryPage(
                items = items,
                lastAssetId = payload.stringValue("last_assetid").takeIf { it.isNotBlank() },
                hasMore = payload.boolValue("more_items"),
                totalCount = payload.intValue("total_inventory_count")
            )
        }

        internal fun marketCookies(account: SteamAccount, sessionId: String): Map<String, String> {
            val loginSecure = account.steamLoginSecure?.takeIf { it.isNotBlank() }
                ?: "${account.steamId}||${account.accessToken.orEmpty()}"
            return mapOf(
                "steamLoginSecure" to loginSecure,
                "sessionid" to sessionId,
                "mobileClient" to "android",
                "mobileClientVersion" to "777777 3.6.4"
            )
        }

        internal fun newSessionId(): String {
            val bytes = ByteArray(12)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        private fun requireMarketSession(account: SteamAccount) {
            require(account.hasRealSteamId) { "real Steam ID required" }
            require(
                !account.accessToken.isNullOrBlank() || !account.steamLoginSecure.isNullOrBlank()
            ) { "Steam community session required" }
        }

        private fun parseWallet(payload: JsonObject): SteamWalletInfo {
            return SteamWalletInfo(
                currency = payload.intValue("wallet_currency").takeIf { it > 0 } ?: 1,
                steamFeePercent = payload.doubleValueOrNull("wallet_fee_percent") ?: 0.05,
                publisherFeePercent = payload.doubleValueOrNull(
                    "wallet_publisher_fee_percent_default"
                ) ?: 0.10,
                marketMinimum = payload.intValue("wallet_market_minimum").coerceAtLeast(1),
                currencyIncrement = payload.intValue("wallet_currency_increment").coerceAtLeast(1)
            )
        }

        private fun extractJsonObject(html: String, variableName: String): JsonObject? {
            val match = Regex("${Regex.escape(variableName)}\\s*=\\s*").find(html) ?: return null
            var index = match.range.last + 1
            if (index >= html.length || html[index] != '{') return null
            val start = index
            var depth = 0
            var quote: Char? = null
            while (index < html.length) {
                val char = html[index]
                if (quote != null) {
                    if (char == '\\') {
                        index++
                    } else if (char == quote) {
                        quote = null
                    }
                } else {
                    when (char) {
                        '\'', '"' -> quote = char
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val raw = html.substring(start, index + 1)
                                return runCatching {
                                    parser.parseToJsonElement(raw).jsonObject
                                }.getOrNull()
                            }
                        }
                    }
                }
                index++
            }
            return null
        }

        private fun itemImageUrl(raw: String): String {
            if (raw.isBlank()) return ""
            if (raw.startsWith("https://")) return raw
            return ITEM_IMAGE_BASE + raw.removePrefix("/")
        }
    }
}

internal fun JsonObject.stringValue(key: String): String {
    return (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

internal fun JsonObject.intValue(key: String): Int {
    val primitive = this[key] as? JsonPrimitive ?: return 0
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: 0
}

internal fun JsonObject.doubleValueOrNull(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
}

internal fun JsonObject.boolValue(key: String): Boolean {
    val primitive = this[key] as? JsonPrimitive ?: return false
    return primitive.booleanOrNull ?: when (primitive.contentOrNull?.lowercase()) {
        "1", "true" -> true
        else -> false
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
