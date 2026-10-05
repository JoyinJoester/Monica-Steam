package takagi.ru.monica.steam.store

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.ui.formatStoreRegionalPrice

class SteamStoreRegionalPricesTest {
    @Test fun comparisonAmountsDropOnlyInsignificantDecimalZeros() {
        assertEquals("¥298", formatStoreRegionalPrice("CNY", 29800))
        assertEquals("¥49.5", formatStoreRegionalPrice("CNY", 4950))
        assertEquals("¥240.53", formatStoreRegionalPrice("CNY", 24053))
        assertEquals("¥1000", formatStoreRegionalPrice("CNY", 100000))
    }
    @Test fun localPricesShowOneCurrencyIdentifierAndKeepSteamMinorUnits() {
        assertEquals("HKD 82.50", storeLocalPriceLabel("HKD", 8250, Locale.US))
        assertEquals("JPY 1,980", storeLocalPriceLabel("JPY", 198000, Locale.US))
    }
    @Test fun regionFlagsUseChinaForHongKongAndTaiwan() {
        assertEquals("🇨🇳", storeRegionFlag("CN"))
        assertEquals("🇨🇳", storeRegionFlag("hk"))
        assertEquals("🇨🇳", storeRegionFlag(" TW "))
        assertEquals("🇺🇸", storeRegionFlag("US"))
        assertEquals("", storeRegionFlag("invalid"))
    }
    private fun price(country: String, cny: Long? = 100, available: Boolean = true) =
        SteamRegionalPrice(country, "USD", 100, isAvailable = available, cnyFinalPriceMinor = cny)

    @Test fun accountRegionPrecedesChinaAndCheaperCountries() {
        val rows = listOf(price("CN", 200), price("US", 500), price("PK", 100))
        assertEquals(listOf("US", "PK", "CN"), sortedStoreRegionalPrices(rows, " us ").map { it.countryCode })
    }

    @Test fun unavailableAccountRegionStaysFirstAndMissingConversionsSortLast() {
        val rows = listOf(price("DE", available = false), price("JP", null), price("CN", 300), price("PH", available = false), price("PK", 100))
        assertEquals(listOf("PH", "PK", "CN", "JP", "DE"), sortedStoreRegionalPrices(rows, "PH").map { it.countryCode })
    }

    @Test fun absentAccountRegionDoesNotPromoteChina() {
        assertEquals(listOf("PK", "CN"), sortedStoreRegionalPrices(listOf(price("CN", 200), price("PK", 100)), null).map { it.countryCode })
    }

    @Test fun queryIncludesNewAccountRegionOnceAndNormalizesCountries() {
        assertEquals(listOf("PH", "CN", "US"), storeRegionalPriceCountries(" ph ", listOf("cn", "US", "PH", "", "invalid")))
        assertNull(normalizedStoreCountry("中国"))
        assertEquals(listOf("CN"), storeRegionalPriceCountries(null, listOf("cn")))
    }

    @Test fun cacheWithoutAccountRegionMustRefreshButUnavailableCountsAsFetched() {
        assertFalse(storePricesContainCurrentRegion(listOf(price("CN")), "PH"))
        assertTrue(storePricesContainCurrentRegion(listOf(price("ph", available = false)), " PH "))
        assertTrue(storePricesContainCurrentRegion(emptyList(), null))
    }
}
