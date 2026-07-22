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
}
