package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.data.*
import takagi.ru.monica.steam.store.presentation.*

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreDetailRegionalPriceGuardTest {
    @Test
    fun detailUsesCompleteHeaderAndAccessibleRegionalPriceEntry() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val detail = store
            .substringAfter("internal fun SteamStoreDetailContent(")
            .substringBefore("@Composable private fun DetailTextSection")

        val header = projectFile("app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreDetailHeader.kt").readText()
        val prices = projectFile("app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreRegionalPrices.kt").readText()
        assertTrue(header.contains("contentScale = ContentScale.Fit"))
        assertTrue(detail.contains("SteamStoreDetailHeader(detail, loading, onOpenRegionalPrices"))
        assertTrue(detail.contains("SteamStoreRegionalPriceSheet("))
        assertTrue(detail.contains("historyCountryCode = detail.accountCountryCode ?: detail.priceCountryCode"))
        assertTrue(prices.contains("sortedStoreRegionalPrices(prices, current)"))
        assertTrue(prices.contains("SteamDbPriceSection("))
        assertTrue(prices.contains("compact = true"))
        assertFalse(prices.contains("ItadHistoryLowSection("))
        assertTrue(prices.contains("SteamStoreRegionalPriceHeader("))

    }

    @Test
    fun viewModelReusesRegionalPriceServicesAndAccountCache() {
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/data/SteamStoreCache.kt"
        ).readText()

        assertTrue(viewModel.contains("SteamGameLibraryService"))
        assertTrue(viewModel.contains("SteamCurrencyExchangeService"))
        assertTrue(viewModel.contains("fetchRegionalPrices("))
        assertTrue(viewModel.contains("applyCnyConversions("))
        assertTrue(viewModel.contains("REGIONAL_PRICE_COUNTRY_CODES"))
        assertTrue(viewModel.contains("cache.readRegionalPrices("))
        assertTrue(viewModel.contains("cache.writeRegionalPrices("))
        assertTrue(cache.contains("steamRegionalPriceCacheName("))
    }

    @Test
    fun regionalPriceCacheSeparatesAccountsAndGames() {
        assertEquals(
            "v2_account_42_regional_prices_730.json",
            steamRegionalPriceCacheName(accountId = 42L, appId = 730)
        )
        assertEquals(
            "v2_guest_regional_prices_570.json",
            steamRegionalPriceCacheName(accountId = null, appId = 570)
        )
        assertEquals(
            listOf(
                "CN", "US", "JP", "KR", "HK", "TW", "DE", "GB", "BR", "RU",
                "UA", "IN", "ID", "PK"
            ),
            SteamStoreViewModel.REGIONAL_PRICE_COUNTRY_CODES
        )
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
