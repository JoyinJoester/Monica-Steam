package takagi.ru.monica.steam.market

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamInventoryLazyKeyTest {
    @Test
    fun duplicateInventoryRowsStillReceiveUniqueComposeKeys() {
        val game = SteamInventoryGame(730, "2", "CS2", "默认", "", 4)
        val item = SteamInventoryItem(
            appId = 730,
            contextId = "2",
            assetId = "asset",
            classId = "class",
            instanceId = "0",
            amount = 1,
            marketHashName = "item",
            name = "Item",
            type = "",
            iconUrl = "",
            marketable = true,
            tradable = true,
            commodity = false,
            publisherFeePercent = null
        )
        val stack = SteamInventoryItemStack(item, listOf("asset"))
        val listing = SteamMarketListing(
            listingId = "listing",
            appId = 730,
            contextId = "2",
            assetId = "asset",
            marketHashName = "item",
            name = "Item",
            iconUrl = "",
            sellerReceives = 100,
            fee = 15,
            createdAt = 0L,
            active = true
        )

        val gameKeys = listOf(game, game).mapIndexed(::steamInventoryGameLazyKey)
        val stackKeys = listOf(stack, stack).mapIndexed(::steamInventoryStackLazyKey)
        val listingKeys = listOf(listing, listing).mapIndexed(::steamMarketListingLazyKey)

        assertEquals(2, gameKeys.toSet().size)
        assertEquals(2, stackKeys.toSet().size)
        assertEquals(2, listingKeys.toSet().size)
    }
}
