package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreRegionSearchResult
import takagi.ru.monica.steam.store.data.mergeSteamStoreSearchResults
import takagi.ru.monica.steam.store.data.steamStoreDetailFallbackCountries
import takagi.ru.monica.steam.store.domain.SteamStoreItem

class SteamStoreGlobalSearchTest {
    @Test
    fun accountCatalogWinsWhileLockedRegionalResultsRemainVisible() {
        val results = mergeSteamStoreSearchResults(
            query = "Portal",
            accountCountryCode = "CN",
            accountRegionResponded = true,
            regionalResults = listOf(
                SteamStoreRegionSearchResult(
                    countryCode = "CN",
                    items = listOf(storeItem(620, "Portal 2", "CNY", 4_200))
                ),
                SteamStoreRegionSearchResult(
                    countryCode = "US",
                    items = listOf(
                        storeItem(620, "Portal 2", "USD", 999),
                        storeItem(400, "Portal", "USD", 999)
                    )
                )
            )
        )

        assertEquals(listOf(400, 620), results.map(SteamStoreItem::appId))
        assertFalse(results.first().availableInAccountRegion ?: true)
        assertEquals("US", results.first().priceCountryCode)
        assertTrue(results.last().availableInAccountRegion == true)
        assertEquals("CN", results.last().priceCountryCode)
        assertEquals("CNY", results.last().currency)
        assertEquals(4_200, results.last().finalPriceCents)
    }

    @Test
    fun availabilityRemainsUnknownWhenAccountCatalogRequestFails() {
        val results = mergeSteamStoreSearchResults(
            query = "Region Game",
            accountCountryCode = "CN",
            accountRegionResponded = false,
            regionalResults = listOf(
                SteamStoreRegionSearchResult(
                    countryCode = "JP",
                    items = listOf(storeItem(10, "Region Game", "JPY", 1_000))
                )
            )
        )

        assertNull(results.single().availableInAccountRegion)
        assertEquals("JP", results.single().priceCountryCode)
    }

    @Test
    fun lockedDetailTriesDiscoveryRegionBeforeOtherCatalogs() {
        assertEquals(
            listOf("JP", "US", "KR", "DE", "RU"),
            steamStoreDetailFallbackCountries(
                accountCountryCode = "CN",
                discoveryCountryCode = "JP"
            )
        )
    }

    @Test
    fun storeUiLabelsLockedResultsAndKeepsRegionalPriceEntry() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val searchCard = source
            .substringAfter("private fun SearchResultCard(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")
        val detail = source
            .substringAfter("private fun SteamStoreDetailContent(")
            .substringBefore("@Composable\nprivate fun SteamStorePurchaseActions(")
        val purchaseActions = source
            .substringAfter("private fun SteamStorePurchaseActions(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun SteamStoreRegionalPriceSheet(")

        assertTrue(searchCard.contains("steam_store_unavailable_account_region"))
        assertTrue(searchCard.contains("steam_store_reference_region_price"))
        assertTrue(detail.contains("steam_store_unavailable_account_region"))
        assertTrue(detail.contains("onOpenRegionalPrices"))
        assertTrue(purchaseActions.contains("purchaseAvailable"))
        assertTrue(purchaseActions.contains("enabled = purchaseAvailable || inCart"))
    }

    private fun storeItem(
        appId: Int,
        name: String,
        currency: String,
        finalPriceCents: Int
    ): SteamStoreItem = SteamStoreItem(
        appId = appId,
        name = name,
        currency = currency,
        initialPriceCents = finalPriceCents,
        finalPriceCents = finalPriceCents
    )

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
