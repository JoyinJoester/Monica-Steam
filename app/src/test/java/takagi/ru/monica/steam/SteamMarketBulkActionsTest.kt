package takagi.ru.monica.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import takagi.ru.monica.steam.market.SteamMarketListing
import takagi.ru.monica.steam.market.removeCancelledSteamMarketListings

class SteamMarketBulkActionsTest {
    @Test
    fun removesOnlyListingsConfirmedAsCancelled() {
        val listings = listOf(listing("a"), listing("b"), listing("c"))

        val remaining = removeCancelledSteamMarketListings(
            existing = listings,
            cancelledListingIds = setOf("b", "missing")
        )

        assertEquals(listOf("a", "c"), remaining.map { it.listingId })
    }

    @Test
    fun emptyCancellationSetPreservesTheExistingListInstance() {
        val listings = listOf(listing("a"))

        assertSame(
            listings,
            removeCancelledSteamMarketListings(listings, emptySet())
        )
    }

    private fun listing(id: String) = SteamMarketListing(
        listingId = id,
        appId = 730,
        contextId = "2",
        assetId = "asset_$id",
        marketHashName = "item_$id",
        name = "Item $id",
        iconUrl = "",
        sellerReceives = 100,
        fee = 17,
        createdAt = 0L,
        active = true
    )
}
