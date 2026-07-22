package takagi.ru.monica.steam.analytics

import java.util.Locale
import takagi.ru.monica.steam.market.SteamBatchPricing
import takagi.ru.monica.steam.market.SteamInventoryItemStack
import takagi.ru.monica.steam.market.SteamMarketFees
import takagi.ru.monica.steam.market.SteamMarketHistoryPoint
import takagi.ru.monica.steam.market.SteamMarketListing
import takagi.ru.monica.steam.market.SteamMarketPrice
import takagi.ru.monica.steam.market.SteamWalletInfo

data class SteamInventoryValuation(
    val buyerValueMinor: Long,
    val sellerReceiveMinor: Long,
    val pricedItems: Int,
    val marketableItems: Int,
    val pricedStacks: Int,
    val marketableStacks: Int
) {
    val coveragePercent: Int
        get() = if (marketableItems == 0) 0 else (pricedItems * 100 / marketableItems)
}

data class SteamListingAnalysis(
    val listingCount: Int,
    val buyerValueMinor: Long,
    val sellerReceiveMinor: Long,
    val feesMinor: Long,
    val invalidPriceCount: Int
)

enum class SteamPriceTrendDirection {
    UP,
    DOWN,
    FLAT,
    UNKNOWN
}

data class SteamPriceHistorySummary(
    val minimum: Double?,
    val maximum: Double?,
    val average: Double?,
    val latest: Double?,
    val changePercent: Double?,
    val volume: Long,
    val direction: SteamPriceTrendDirection
)

object SteamMarketAnalytics {
    fun inventoryValuation(
        stacks: List<SteamInventoryItemStack>,
        prices: Map<String, SteamMarketPrice>,
        wallet: SteamWalletInfo
    ): SteamInventoryValuation {
        var buyerValue = 0L
        var sellerReceive = 0L
        var pricedItems = 0
        var marketableItems = 0
        var pricedStacks = 0
        var marketableStacks = 0
        val fees = SteamMarketFees(wallet)
        stacks.filter { it.item.marketable }.forEach { stack ->
            val quantity = stack.count.coerceAtLeast(1)
            marketableItems += quantity
            marketableStacks++
            val buyerPrice = SteamBatchPricing.parseLocalizedPriceMinorUnits(
                prices[stack.item.stackKey]?.lowestPrice
            ) ?: return@forEach
            val receive = fees.receiveFromTotal(buyerPrice, stack.item.publisherFeePercent)
            buyerValue += buyerPrice.toLong() * quantity
            sellerReceive += receive.toLong() * quantity
            pricedItems += quantity
            pricedStacks++
        }
        return SteamInventoryValuation(
            buyerValueMinor = buyerValue,
            sellerReceiveMinor = sellerReceive,
            pricedItems = pricedItems,
            marketableItems = marketableItems,
            pricedStacks = pricedStacks,
            marketableStacks = marketableStacks
        )
    }

    fun listings(listings: List<SteamMarketListing>): SteamListingAnalysis {
        return SteamListingAnalysis(
            listingCount = listings.size,
            buyerValueMinor = listings.sumOf { it.buyerPrice.toLong().coerceAtLeast(0L) },
            sellerReceiveMinor = listings.sumOf { it.sellerReceives.toLong().coerceAtLeast(0L) },
            feesMinor = listings.sumOf { it.fee.toLong().coerceAtLeast(0L) },
            invalidPriceCount = listings.count {
                it.sellerReceives <= 0 || it.fee < 0 || it.buyerPrice < it.sellerReceives
            }
        )
    }

    fun history(points: List<SteamMarketHistoryPoint>): SteamPriceHistorySummary {
        val prices = points.map { it.price }.filter { it.isFinite() && it >= 0.0 }
        val first = prices.firstOrNull()
        val latest = prices.lastOrNull()
        val change = if (first != null && latest != null && first > 0.0) {
            (latest - first) / first * 100.0
        } else {
            null
        }
        val direction = when {
            change == null -> SteamPriceTrendDirection.UNKNOWN
            change > 0.5 -> SteamPriceTrendDirection.UP
            change < -0.5 -> SteamPriceTrendDirection.DOWN
            else -> SteamPriceTrendDirection.FLAT
        }
        return SteamPriceHistorySummary(
            minimum = prices.minOrNull(),
            maximum = prices.maxOrNull(),
            average = prices.takeIf { it.isNotEmpty() }?.average(),
            latest = latest,
            changePercent = change,
            volume = points.sumOf { it.volume?.toLong()?.coerceAtLeast(0L) ?: 0L },
            direction = direction
        )
    }
}

object SteamMarketCsv {
    val inventoryHeaders = listOf(
        "app_id", "context_id", "asset_id", "name", "market_hash_name",
        "quantity", "marketable", "tradable", "lowest_price"
    )
    val listingHeaders = listOf(
        "listing_id", "app_id", "context_id", "asset_id", "name",
        "market_hash_name", "buyer_price_minor", "seller_receive_minor", "fee_minor"
    )

    fun inventory(
        stacks: List<SteamInventoryItemStack>,
        prices: Map<String, SteamMarketPrice>
    ): String {
        val rows = stacks.map { stack ->
            val item = stack.item
            listOf(
                item.appId,
                item.contextId,
                item.assetId,
                item.name,
                item.marketHashName,
                stack.count,
                item.marketable,
                item.tradable,
                prices[item.stackKey]?.lowestPrice.orEmpty()
            )
        }
        return encode(inventoryHeaders, rows)
    }

    fun listings(listings: List<SteamMarketListing>): String {
        val rows = listings.map { listing ->
            listOf(
                listing.listingId,
                listing.appId,
                listing.contextId,
                listing.assetId,
                listing.name,
                listing.marketHashName,
                listing.buyerPrice,
                listing.sellerReceives,
                listing.fee
            )
        }
        return encode(listingHeaders, rows)
    }

    private fun encode(headers: List<String>, rows: List<List<Any?>>): String {
        return buildString {
            append(headers.joinToString(",", transform = ::escape))
            append("\r\n")
            rows.forEach { row ->
                append(row.joinToString(",") { escape(it?.toString().orEmpty()) })
                append("\r\n")
            }
        }
    }

    private fun escape(value: String): String {
        val normalized = value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
        return if (normalized.any { it == ',' || it == '"' }) {
            "\"${normalized.replace("\"", "\"\"")}\""
        } else {
            normalized
        }
    }
}
