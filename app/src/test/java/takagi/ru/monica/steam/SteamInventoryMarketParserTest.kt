package takagi.ru.monica.steam

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.market.SteamInventoryItem
import takagi.ru.monica.steam.market.SteamMarketService
import takagi.ru.monica.steam.market.allocateSteamInventorySale
import takagi.ru.monica.steam.market.findNewSteamMarketConfirmations
import takagi.ru.monica.steam.market.mergeSteamInventoryStacks
import takagi.ru.monica.steam.market.removeSteamInventoryAmount
import takagi.ru.monica.steam.network.SteamConfirmation

class SteamInventoryMarketParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun overviewParsesGamesContextsAndWallet() {
        val html = """
            <script>
            var g_rgAppContextData = {
              "730":{"name":"Counter-Strike 2","icon":"https://cdn.example/cs2.png","rgContexts":{"2":{"name":"Backpack","asset_count":12}}},
              "440":{"name":"Team Fortress 2","icon":"https://cdn.example/tf2.png","rgContexts":{"2":{"name":"Items","asset_count":3}}},
              "570":{"name":"Dota 2","icon":"https://cdn.example/dota.png","rgContexts":[]}
            };
            var g_rgWalletInfo = {
              "wallet_currency":"23",
              "wallet_fee_percent":"0.05",
              "wallet_publisher_fee_percent_default":"0.10",
              "wallet_market_minimum":"7",
              "wallet_currency_increment":"1"
            };
            </script>
        """.trimIndent()

        val overview = SteamInventoryService.parseOverviewHtml(html)

        assertEquals(listOf(12, 3), overview.games.map { it.itemCount })
        assertEquals("Counter-Strike 2", overview.games.first().name)
        assertEquals(23, overview.wallet.currency)
        assertEquals(7, overview.wallet.marketMinimum)
    }

    @Test
    fun inventoryPageMergesAssetsWithDescriptions() {
        val payload = json.parseToJsonElement(
            """
            {
              "assets":[{"appid":"730","contextid":"2","assetid":"100","classid":"10","instanceid":"0","amount":"2"}],
              "descriptions":[{"classid":"10","instanceid":"0","market_hash_name":"AK-47 | Redline","name":"AK-47","type":"Rifle","icon_url":"icon-path","marketable":1,"tradable":1,"commodity":0,"market_fee":"0.12"}],
              "more_items":1,
              "last_assetid":"100",
              "total_inventory_count":80,
              "success":1
            }
            """.trimIndent()
        ).jsonObject

        val page = SteamInventoryService.parseInventoryPage(payload)

        assertEquals(1, page.items.size)
        assertEquals(2, page.items.first().amount)
        assertEquals("AK-47 | Redline", page.items.first().marketHashName)
        assertEquals(0.12, page.items.first().publisherFeePercent ?: 0.0, 0.0001)
        assertTrue(page.items.first().marketable)
        assertTrue(page.hasMore)
        assertEquals("100", page.lastAssetId)
    }

    @Test
    fun marketListingsKeepSteamSellerReceiveAndAddFeeForBuyerPrice() {
        val listingsPayload = json.parseToJsonElement(
            """
            {
              "success":true,
              "num_active_listings":1,
              "listings":[{
                "listingid":"555",
                "time_created":1700000000,
                "price":579,
                "fee":85,
                "active":1,
                "asset":{"appid":730,"contextid":"2","id":"100","market_hash_name":"AK-47 | Redline","name":"AK-47","icon_url":"icon-path"}
              }]
            }
            """.trimIndent()
        ).jsonObject
        val pricePayload = json.parseToJsonElement(
            """{"success":true,"lowest_price":"¥ 1.17","median_price":"¥ 1.20","volume":"1,234"}"""
        ).jsonObject

        val listings = SteamMarketService.parseListingsPage(listingsPayload, start = 0, count = 100)
        val price = SteamMarketService.parsePriceOverview(pricePayload)

        assertEquals(1, listings.items.size)
        assertEquals(579, listings.items.first().sellerReceives)
        assertEquals(85, listings.items.first().fee)
        assertEquals(664, listings.items.first().buyerPrice)
        assertFalse(listings.hasMore)
        assertEquals(1234, price?.volume)
        assertEquals("¥ 1.17", price?.lowestPrice)
    }

    @Test
    fun cancellationRequiresPositiveOrEmptySuccessResponse() {
        assertTrue(SteamMarketService.isCancelSuccess(json.parseToJsonElement("{}").jsonObject))
        assertTrue(SteamMarketService.isCancelSuccess(json.parseToJsonElement("{\"success\":true}").jsonObject))
        assertFalse(SteamMarketService.isCancelSuccess(json.parseToJsonElement("{\"success\":false}").jsonObject))
        assertFalse(SteamMarketService.isCancelSuccess(json.parseToJsonElement("{\"needauth\":true}").jsonObject))
        assertNull(SteamMarketService.parsePriceOverview(json.parseToJsonElement("{\"success\":false}").jsonObject))
    }

    @Test
    fun marketListingsKeepPagingWhenSteamReturnsAShortPage() {
        val payload = json.parseToJsonElement(
            """
            {
              "success":true,
              "num_active_listings":3,
              "listings":[{
                "listingid":"555",
                "price":117,
                "fee":17,
                "active":1,
                "asset":{"appid":730,"contextid":"2","id":"100","name":"AK-47"}
              }]
            }
            """.trimIndent()
        ).jsonObject

        val page = SteamMarketService.parseListingsPage(payload, start = 0, count = 100)

        assertEquals(1, page.nextStart)
        assertTrue(page.hasMore)
    }

    @Test
    fun stackingKeepsCommodityAmountWithoutExpandingAssetIds() {
        val item = SteamInventoryItem(
            appId = 753,
            contextId = "6",
            assetId = "asset-1",
            classId = "class-1",
            instanceId = "0",
            amount = 3,
            marketHashName = "Trading Card",
            name = "Trading Card",
            type = "Card",
            iconUrl = "",
            marketable = true,
            tradable = true,
            commodity = true,
            publisherFeePercent = null
        )

        val stacks = mergeSteamInventoryStacks(emptyList(), listOf(item))

        assertEquals(3, stacks.single().count)
        assertEquals(listOf("asset-1"), stacks.single().assetIds)
        assertEquals(3, stacks.single().assetAmounts.getValue("asset-1"))
    }

    @Test
    fun largeCommodityAmountUsesBoundedAssetMetadata() {
        val payload = json.parseToJsonElement(
            """
            {
              "success":1,
              "assets":[{
                "appid":753,
                "contextid":"6",
                "assetid":"asset-large",
                "classid":"class-large",
                "instanceid":"0",
                "amount":"100000"
              }],
              "descriptions":[{
                "classid":"class-large",
                "instanceid":"0",
                "market_hash_name":"Commodity",
                "name":"Commodity",
                "type":"Item",
                "marketable":1,
                "tradable":1,
                "commodity":1
              }],
              "total_inventory_count":100000
            }
            """.trimIndent()
        ).jsonObject

        val page = SteamInventoryService.parseInventoryPage(payload)
        val stack = mergeSteamInventoryStacks(emptyList(), page.items).single()

        assertEquals(100_000, page.items.single().amount)
        assertEquals(100_000, stack.count)
        assertEquals(1, stack.assetIds.size)
        assertEquals(1, stack.assetAmounts.size)
    }

    @Test
    fun commoditySaleUsesOneAssetRequestWithRequestedAmount() {
        val item = SteamInventoryItem(
            appId = 753,
            contextId = "6",
            assetId = "asset-stack",
            classId = "class-stack",
            instanceId = "0",
            amount = 100,
            marketHashName = "Commodity",
            name = "Commodity",
            type = "Item",
            iconUrl = "",
            marketable = true,
            tradable = true,
            commodity = true,
            publisherFeePercent = null
        )
        val stack = mergeSteamInventoryStacks(emptyList(), listOf(item)).single()

        val allocations = allocateSteamInventorySale(stack, requestedQuantity = 40)
        val remaining = removeSteamInventoryAmount(stack, removedQuantity = 40)

        assertEquals(1, allocations.size)
        assertEquals("asset-stack", allocations.single().assetId)
        assertEquals(40, allocations.single().amount)
        assertEquals(60, remaining?.count)
        assertEquals(1, remaining?.assetIds?.size)
    }

    @Test
    fun autoConfirmOnlyReturnsNewMarketListings() {
        val old = confirmation(id = "old", type = "3")
        val freshMarket = confirmation(id = "new", type = "MarketSell")
        val freshTrade = confirmation(id = "trade", type = "2")

        val selected = findNewSteamMarketConfirmations(
            preExistingIds = setOf(old.id),
            latest = listOf(old, freshMarket, freshTrade)
        )

        assertEquals(listOf(freshMarket), selected)
    }

    private fun confirmation(id: String, type: String): SteamConfirmation {
        return SteamConfirmation(
            id = id,
            nonce = "nonce-$id",
            type = type,
            headline = "headline",
            summary = "summary",
            imageUrl = "",
            creationTime = 0L
        )
    }
}
