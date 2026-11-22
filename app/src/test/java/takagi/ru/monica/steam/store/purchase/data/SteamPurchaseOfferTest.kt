package takagi.ru.monica.steam.store.purchase.data

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.purchase.domain.*

class SteamPurchaseOfferTest {
    private fun page(app: Int, option: String) = """<div data-props='{"rgPayloads":[{"rgStoreItems":[{"appid":$app,"best_purchase_option":$option}]}]}'></div>"""
    private fun option(end: String = "1800000000", id: Int = 10, bundle: String = "null") =
        """{"packageid":$id,"bundleid":$bundle,"final_price_in_cents":"2100","discount_pct":50,"discount_end_date":$end}"""
    @Test fun countdownOnlyComesFromMatchingGameAndPackage() {
        val result = SteamStorePurchasePageParser.parse(page(620, option()) + page(400, option(id=20)), 620)
        assertEquals(setOf(10), result.discounts.keys)
        assertEquals(1800000000L, result.discounts[10]!!.endEpochSeconds)
    }
    @Test fun encodedJsonAttributesAreDecoded() {
        val valid = "<div data-props=\"" + """{"rgStoreItems":[{"appid":620,"best_purchase_option":${option()}}]}""".replace("\"", "&quot;") + "\"></div>"
        assertEquals(1, SteamStorePurchasePageParser.parse(valid, 620).discounts.size)
    }
    @Test fun unknownMalformedBundleAndConflictingDatesAreRejected() {
        for (value in listOf("null", "0", "-1", "\"soon\"", "99999999999999999"))
            assertTrue(SteamStorePurchasePageParser.parse(page(620, option(value)),620).discounts.isEmpty())
        assertTrue(SteamStorePurchasePageParser.parse(page(620, option(bundle="99")),620).discounts.isEmpty())
        assertTrue(SteamStorePurchasePageParser.parse(page(620,option())+page(620,option("1800000001")),620).discounts.isEmpty())
        assertTrue(SteamStorePurchasePageParser.parse("<div data-props='broken'>",620).discounts.isEmpty())
    }
    @Test fun displayedPriceCannotTakeExpiryFromFreePassOrDifferentEdition() {
        val detail = SteamStoreDetail(620,"Portal",finalPriceCents=2100,discountPercent=50,
            packageOptions=listOf(SteamStorePackageOption(10,priceCents=0,discountEndEpochSeconds=1800000000),
                SteamStorePackageOption(20,priceCents=3000,discountPercent=50,discountEndEpochSeconds=1800000000)))
        assertNull(steamPurchaseOffer(detail).endEpochSeconds)
        val matched = detail.copy(packageOptions=detail.packageOptions + SteamStorePackageOption(30,
            priceCents=2100,discountPercent=50,discountEndEpochSeconds=1800000000))
        assertEquals(1800000000L,steamPurchaseOffer(matched).endEpochSeconds)
        assertTrue(steamPurchaseOffer(matched).expired(1800000000))
    }
    @Test fun remainingTimeUpdatesAcrossBoundariesAndNeverShowsZeroMinutes() {
        assertNull(steamDiscountRemaining(null,100))
        assertNull(steamDiscountRemaining(100,100))
        assertNull(steamDiscountRemaining(99,100))
        assertEquals(SteamDiscountRemaining(SteamDiscountRemaining.Unit.MINUTES,1),steamDiscountRemaining(101,100))
        assertEquals(SteamDiscountRemaining(SteamDiscountRemaining.Unit.HOURS,1),steamDiscountRemaining(3700,100))
        assertEquals(SteamDiscountRemaining(SteamDiscountRemaining.Unit.DAYS,1),steamDiscountRemaining(86500,100))
    }
}
