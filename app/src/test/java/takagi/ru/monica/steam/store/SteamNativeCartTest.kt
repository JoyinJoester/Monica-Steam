package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamNativeCartTest {
    @Test
    fun checkoutUsesDistinctAvailablePackagesAndTotalUsesCurrentPrices() {
        val items = listOf(
            SteamCartItem(1, 100, "A", finalPriceCents = 1200),
            SteamCartItem(2, 100, "B", finalPriceCents = 800),
            SteamCartItem(3, null, "C", finalPriceCents = null)
        )
        assertEquals(listOf(100), steamCartCheckoutPackageIds(items))
        assertEquals(2000, steamCartTotalCents(items))
    }

    @Test
    fun duplicateCartAndWishlistRowsReceiveUniqueKeys() {
        val cart = SteamCartItem(730, 1, "Counter-Strike 2")
        val wishlist = SteamWishlistItem(730, "Counter-Strike 2")

        val cartKeys = listOf(cart, cart).mapIndexed(::steamCartLazyKey)
        val wishlistKeys = listOf(wishlist, wishlist).mapIndexed(::steamWishlistLazyKey)

        assertEquals(2, cartKeys.distinct().size)
        assertEquals(2, wishlistKeys.distinct().size)
    }
}
