package takagi.ru.monica.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.market.SteamBatchPriceMode
import takagi.ru.monica.steam.market.SteamBatchPricing
import takagi.ru.monica.steam.market.SteamInventoryItem
import takagi.ru.monica.steam.market.SteamInventoryItemStack
import takagi.ru.monica.steam.market.SteamMarketHistoryPoint
import takagi.ru.monica.steam.market.SteamMarketPrice
import takagi.ru.monica.steam.market.SteamMarketQuote
import takagi.ru.monica.steam.market.SteamWalletInfo

class SteamBatchPricingTest {
    private val wallet = SteamWalletInfo(
        currency = 23,
        steamFeePercent = 0.05,
        publisherFeePercent = 0.10,
        marketMinimum = 7,
        currencyIncrement = 1
    )
    private val quote = SteamMarketQuote(
        price = SteamMarketPrice(
            lowestPrice = "¥ 1.17",
            medianPrice = "¥ 2.67",
            volume = 12
        ),
        history = listOf(
            SteamMarketHistoryPoint("Jul 1", 3.83, 2),
            SteamMarketHistoryPoint("Jul 2", 10.83, 1)
        )
    )

    @Test
    fun parsesLocalizedPricesIntoMinorUnits() {
        assertEquals(117, SteamBatchPricing.parseLocalizedPriceMinorUnits("¥ 1.17"))
        assertEquals(117, SteamBatchPricing.parseLocalizedPriceMinorUnits("€ 1,17"))
        assertEquals(123456, SteamBatchPricing.parseLocalizedPriceMinorUnits("$1,234.56 USD"))
        assertEquals(700, SteamBatchPricing.parseLocalizedPriceMinorUnits("₹ 7"))
        assertNull(SteamBatchPricing.parseLocalizedPriceMinorUnits("--"))
    }

    @Test
    fun resolvesPresetBuyerPricesBackToSellerReceive() {
        assertEquals(
            100,
            SteamBatchPricing.resolveSellerReceive(
                SteamBatchPriceMode.LOWEST_LISTING,
                quote,
                wallet,
                publisherFeePercent = null
            )
        )
        assertEquals(
            233,
            SteamBatchPricing.resolveSellerReceive(
                SteamBatchPriceMode.MEDIAN,
                quote,
                wallet,
                publisherFeePercent = null
            )
        )
    }

    @Test
    fun supportsHistoryExtremesAndManualReceive() {
        val recentHigh = SteamBatchPricing.resolveSellerReceive(
            SteamBatchPriceMode.RECENT_HIGH,
            quote,
            wallet,
            publisherFeePercent = null
        )
        val recentLow = SteamBatchPricing.resolveSellerReceive(
            SteamBatchPriceMode.RECENT_LOW,
            quote,
            wallet,
            publisherFeePercent = null
        )

        assertEquals(1083, takagi.ru.monica.steam.market.SteamMarketFees(wallet).buyerPays(recentHigh!!))
        assertEquals(383, takagi.ru.monica.steam.market.SteamMarketFees(wallet).buyerPays(recentLow!!))
        assertEquals(
            321,
            SteamBatchPricing.resolveSellerReceive(
                SteamBatchPriceMode.MANUAL,
                quote = null,
                wallet = wallet,
                publisherFeePercent = null,
                manualReceive = 321
            )
        )
    }

    @Test
    fun perItemReceiveOverrideWinsWithoutChangingOtherItems() {
        val first = stack("first")
        val second = stack("second")

        val entries = SteamBatchPricing.resolveEntries(
            stacks = listOf(first, second),
            mode = SteamBatchPriceMode.LOWEST_LISTING,
            quotes = mapOf(
                first.item.stackKey to quote,
                second.item.stackKey to quote
            ),
            wallet = wallet,
            itemReceiveOverrides = mapOf(first.item.stackKey to 456)
        )

        assertEquals(456, entries.first { it.stack == first }.priceReceive)
        assertEquals(100, entries.first { it.stack == second }.priceReceive)
        assertEquals(listOf(1, 1), entries.map { it.quantity })
    }

    @Test
    fun itemOverrideCanSupplyPriceWhenPresetIsMissing() {
        val stack = stack("missing")

        val entries = SteamBatchPricing.resolveEntries(
            stacks = listOf(stack),
            mode = SteamBatchPriceMode.LOWEST_LISTING,
            quotes = emptyMap(),
            wallet = wallet,
            itemReceiveOverrides = mapOf(stack.item.stackKey to 88)
        )

        assertEquals(88, entries.single().priceReceive)
    }

    private fun stack(id: String): SteamInventoryItemStack {
        return SteamInventoryItemStack(
            item = SteamInventoryItem(
                appId = 730,
                contextId = "2",
                assetId = "asset_$id",
                classId = "class_$id",
                instanceId = "0",
                amount = 1,
                marketHashName = "market_$id",
                name = "Item $id",
                type = "",
                iconUrl = "",
                marketable = true,
                tradable = true,
                commodity = true,
                publisherFeePercent = null
            ),
            assetIds = listOf("asset_$id")
        )
    }
}
