package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.data.*
import takagi.ru.monica.steam.store.presentation.*

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreDetailRegionalPriceGuardTest {
    @Test
    fun detailUsesCompleteHeaderAndAccessibleRegionalPriceEntry() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val detail = store
            .substringAfter("private fun SteamStoreDetailContent(")
            .substringBefore("@Composable private fun DetailTextSection")

        assertTrue(detail.contains("contentScale = ContentScale.Fit"))
        assertTrue(detail.contains("onOpenRegionalPrices"))
        assertTrue(detail.contains("heightIn(min = 48.dp)"))
        assertTrue(detail.contains("SteamStoreRegionalPriceSheet("))
        assertTrue(detail.contains("sortedRegionalPricesForDisplay("))
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
            listOf("CN", "US", "JP", "KR", "HK", "TW", "UA", "IN", "ID"),
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
