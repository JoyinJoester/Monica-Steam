package takagi.ru.monica.steam.market

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient

class SteamMarketService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetchPriceOverview(
        appId: Int,
        marketHashName: String,
        currency: Int
    ): SteamMarketPrice? {
        val payload = api.communityGetJson(
            path = "/market/priceoverview/",
            query = mapOf(
                "appid" to appId.toString(),
                "currency" to currency.toString(),
                "market_hash_name" to marketHashName
            )
        )
        return parsePriceOverview(payload)
    }

    fun fetchPriceHistory(
        account: SteamAccount,
        appId: Int,
        marketHashName: String,
        points: Int = 60
    ): List<SteamMarketHistoryPoint> {
        val sessionId = SteamInventoryService.newSessionId()
        val payload = api.communityGetJson(
            path = "/market/pricehistory/",
            query = mapOf(
                "appid" to appId.toString(),
                "market_hash_name" to marketHashName
            ),
            cookies = SteamInventoryService.marketCookies(account, sessionId)
        )
        if (!payload.boolValue("success")) return emptyList()
        val values = (payload["prices"] as? JsonArray).orEmpty().mapNotNull { rawPoint ->
            val point = rawPoint as? JsonArray ?: return@mapNotNull null
            val price = (point.getOrNull(1) as? JsonPrimitive)
                ?.contentOrNull
                ?.toDoubleOrNull()
                ?: return@mapNotNull null
            SteamMarketHistoryPoint(
                label = (point.getOrNull(0) as? JsonPrimitive)?.contentOrNull.orEmpty(),
                price = price,
                volume = (point.getOrNull(2) as? JsonPrimitive)
                    ?.contentOrNull
                    ?.replace(",", "")
                    ?.toIntOrNull()
            )
        }
        return values.takeLast(points.coerceAtLeast(1))
    }

    fun sell(
        account: SteamAccount,
        item: SteamInventoryItem,
        priceReceive: Int,
        assetId: String = item.assetId,
        amount: Int = 1
    ): SteamMarketSellResult {
        val sessionId = SteamInventoryService.newSessionId()
        val payload = api.communityPostJson(
            path = "/market/sellitem/",
            form = mapOf(
                "sessionid" to listOf(sessionId),
                "appid" to listOf(item.appId.toString()),
                "contextid" to listOf(item.contextId),
                "assetid" to listOf(assetId),
                "amount" to listOf(amount.coerceAtLeast(1).toString()),
                "price" to listOf(priceReceive.toString())
            ),
            cookies = SteamInventoryService.marketCookies(account, sessionId),
            referer = "https://steamcommunity.com/profiles/${account.steamId}/inventory/"
        )
        return SteamMarketSellResult(
            success = payload.boolValue("success"),
            requiresConfirmation = payload.boolValue("requires_confirmation") ||
                payload.boolValue("needs_mobile_confirmation"),
            message = payload.stringValue("message").takeIf { it.isNotBlank() }
        )
    }

    fun fetchMyListings(
        account: SteamAccount,
        language: String,
        start: Int = 0,
        count: Int = 100
    ): SteamMarketListingsPage {
        val sessionId = SteamInventoryService.newSessionId()
        val payload = api.communityGetJson(
            path = "/market/mylistings/",
            query = mapOf(
                "norender" to "1",
                "start" to start.coerceAtLeast(0).toString(),
                "count" to count.coerceIn(1, 100).toString(),
                "l" to language
            ),
            cookies = SteamInventoryService.marketCookies(account, sessionId)
        )
        return parseListingsPage(payload, start = start, count = count)
    }

    fun cancelListing(account: SteamAccount, listingId: String): Boolean {
        val sessionId = SteamInventoryService.newSessionId()
        return runCatching {
            val payload = api.communityPostJson(
                path = "/market/removelisting/$listingId",
                form = mapOf("sessionid" to listOf(sessionId)),
                cookies = SteamInventoryService.marketCookies(account, sessionId),
                referer = "https://steamcommunity.com/market/"
            )
            isCancelSuccess(payload)
        }.getOrDefault(false)
    }

    companion object {
        private const val ITEM_IMAGE_BASE =
            "https://community.fastly.steamstatic.com/economy/image/"

        fun parsePriceOverview(payload: JsonObject): SteamMarketPrice? {
            if (!payload.boolValue("success")) return null
            val volume = payload.stringValue("volume")
                .replace(",", "")
                .toIntOrNull()
            return SteamMarketPrice(
                lowestPrice = payload.stringValue("lowest_price").takeIf { it.isNotBlank() },
                medianPrice = payload.stringValue("median_price").takeIf { it.isNotBlank() },
                volume = volume
            )
        }

        fun parseListingsPage(
            payload: JsonObject,
            start: Int,
            count: Int
        ): SteamMarketListingsPage {
            val total = payload.intValue("num_active_listings")
            val rawListings = (payload["listings"] as? JsonArray).orEmpty()
            val listings = rawListings.mapNotNull { raw ->
                val listing = raw as? JsonObject ?: return@mapNotNull null
                val asset = listing["asset"] as? JsonObject ?: JsonObject(emptyMap())
                SteamMarketListing(
                    listingId = listing.stringValue("listingid"),
                    appId = asset.intValue("appid"),
                    contextId = asset.stringValue("contextid"),
                    assetId = asset.stringValue("id").ifBlank { asset.stringValue("assetid") },
                    marketHashName = asset.stringValue("market_hash_name"),
                    name = asset.stringValue("name").ifBlank { asset.stringValue("market_name") },
                    iconUrl = asset.stringValue("icon_url").let { rawIcon ->
                        when {
                            rawIcon.isBlank() -> ""
                            rawIcon.startsWith("https://") -> rawIcon
                            else -> ITEM_IMAGE_BASE + rawIcon.removePrefix("/")
                        }
                    },
                    sellerReceives = listing.intValue("price"),
                    fee = listing.intValue("fee"),
                    createdAt = listing.stringValue("time_created").toLongOrNull()
                        ?: listing.intValue("time_created").toLong(),
                    active = listing.boolValue("active")
                )
            }.filter { it.active }
            val nextStart = start.coerceAtLeast(0) + rawListings.size
            return SteamMarketListingsPage(
                items = listings,
                totalActive = total,
                nextStart = nextStart,
                hasMore = if (total > 0) {
                    rawListings.isNotEmpty() && nextStart < total
                } else {
                    rawListings.size >= count.coerceAtLeast(1)
                }
            )
        }

        fun isCancelSuccess(payload: JsonObject): Boolean {
            if (payload.boolValue("needauth") || payload.boolValue("needsauth")) return false
            val success = payload["success"] as? JsonPrimitive ?: return true
            return success.booleanOrNull == true
        }
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
