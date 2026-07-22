package takagi.ru.monica.steam.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.market.SteamInventoryItem
import takagi.ru.monica.steam.market.SteamInventoryItemStack
import takagi.ru.monica.steam.market.SteamMarketHistoryPoint
import takagi.ru.monica.steam.market.SteamMarketListing
import takagi.ru.monica.steam.market.SteamMarketPrice
import takagi.ru.monica.steam.market.SteamWalletInfo

class SteamMarketAnalyticsTest {
    @Test
    fun valuationCountsOnlyMarketableItemsWithUsablePrices() {
        val priced = stack("priced", quantity = 2, marketable = true)
        val missing = stack("missing", quantity = 1, marketable = true)
        val private = stack("private", quantity = 3, marketable = false)
        val result = SteamMarketAnalytics.inventoryValuation(
            stacks = listOf(priced, missing, private),
            prices = mapOf(priced.item.stackKey to SteamMarketPrice("$1.15", null, null)),
            wallet = SteamWalletInfo.Fallback
        )

        assertEquals(230L, result.buyerValueMinor)
        assertEquals(2, result.pricedItems)
        assertEquals(3, result.marketableItems)
        assertEquals(66, result.coveragePercent)
        assertTrue(result.sellerReceiveMinor in 1 until result.buyerValueMinor)
    }

    @Test
    fun listingAndHistorySummariesExposeFeesTrendAndVolume() {
        val listings = listOf(
            listing("1", receive = 100, fee = 15),
            listing("2", receive = 200, fee = 30)
        )
        val listingSummary = SteamMarketAnalytics.listings(listings)
        val history = SteamMarketAnalytics.history(
            listOf(
                SteamMarketHistoryPoint("a", 1.0, 2),
                SteamMarketHistoryPoint("b", 1.5, 3)
            )
        )

        assertEquals(345L, listingSummary.buyerValueMinor)
        assertEquals(45L, listingSummary.feesMinor)
        assertEquals(SteamPriceTrendDirection.UP, history.direction)
        assertEquals(5L, history.volume)
    }

    @Test
    fun csvUsesWhitelistedColumnsAndEscapesSpreadsheetBreakingContent() {
        val stack = stack("item, \"quoted\"\nname", quantity = 1, marketable = true)
        val csv = SteamMarketCsv.inventory(listOf(stack), emptyMap())

        assertTrue(csv.startsWith(SteamMarketCsv.inventoryHeaders.joinToString(",")))
        assertTrue(csv.contains("\"item, \"\"quoted\"\" name\""))
        assertFalse(csv.contains("shared_secret"))
        assertFalse(csv.contains("access_token"))
    }

    private fun stack(name: String, quantity: Int, marketable: Boolean): SteamInventoryItemStack {
        val item = SteamInventoryItem(
            appId = 730,
            contextId = "2",
            assetId = "asset-$name",
            classId = "class-$name",
            instanceId = "0",
            amount = quantity,
            marketHashName = name,
            name = name,
            type = "item",
            iconUrl = "",
            marketable = marketable,
            tradable = marketable,
            commodity = false,
            publisherFeePercent = null
        )
        return SteamInventoryItemStack(item, List(quantity) { "asset-$it" })
    }

    private fun listing(id: String, receive: Int, fee: Int): SteamMarketListing {
        return SteamMarketListing(
            listingId = id,
            appId = 730,
            contextId = "2",
            assetId = "asset-$id",
            marketHashName = "item-$id",
            name = "item-$id",
            iconUrl = "",
            sellerReceives = receive,
            fee = fee,
            createdAt = 0L,
            active = true
        )
    }
}
