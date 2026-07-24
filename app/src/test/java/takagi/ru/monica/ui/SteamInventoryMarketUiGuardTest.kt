package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamInventoryMarketUiGuardTest {
    @Test
    fun inventoryAndMarketRegisterThroughSteamSectionMenu() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(source.contains("INVENTORY("))
        assertTrue(source.contains("MARKET("))
        assertTrue(source.contains("R.string.steam_section_inventory"))
        assertTrue(source.contains("R.string.steam_section_market"))
        assertTrue(source.contains("SteamSection.entries.forEach"))
        assertTrue(source.contains("SteamInventoryContent("))
        assertTrue(source.contains("SteamMarketListingsContent("))
    }

    @Test
    fun inventoryAndListingsOpenDirectlyIntoTheirContent() {
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/inventory/ui/SteamInventoryMarketContent.kt"
        ).readText()

        assertTrue(uiSource.contains("SteamConfirmationAccountCard("))
        assertFalse(uiSource.contains("SteamInventoryAnalyticsCard("))
        assertFalse(uiSource.contains("SteamListingAnalyticsCard("))
        assertFalse(uiSource.contains("R.string.steam_analytics_inventory_value"))
        assertFalse(uiSource.contains("R.string.steam_analytics_active_listings"))
    }

    @Test
    fun marketUiUsesNativeComponentsAndSafeAutoConfirmation() {
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/inventory/ui/SteamInventoryMarketContent.kt"
        ).readText()
        val stateSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        assertTrue(uiSource.contains("LazyVerticalGrid("))
        assertTrue(uiSource.contains("SteamSellItemSheet("))
        assertTrue(uiSource.contains("SteamMarketPriceTrend("))
        assertTrue(uiSource.contains("onRequestCancelListings"))
        assertTrue(uiSource.contains("hasSteamCommunitySession"))
        assertFalse(uiSource.contains("WebView"))
        assertTrue(stateSource.contains("preExistingMarketIds"))
        assertTrue(stateSource.contains("findNewSteamMarketConfirmations("))
        assertTrue(stateSource.contains("confirmationService.respondMultiple("))
    }

    @Test
    fun marketWritesCarrySessionAndSteamReferer() {
        val serviceSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/market/SteamMarketService.kt"
        ).readText()
        val apiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamApiClient.kt"
        ).readText()

        assertTrue(serviceSource.contains("/market/sellitem/"))
        assertTrue(serviceSource.contains("\"sessionid\" to listOf(sessionId)"))
        assertTrue(serviceSource.contains("referer = \"https://steamcommunity.com/profiles/"))
        assertTrue(serviceSource.contains("/market/removelisting/"))
        assertTrue(apiSource.contains("Steam community redirect blocked"))
    }

    @Test
    fun inventoryCardsClipPressStateAndSupportBatchSelection() {
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/inventory/ui/SteamInventoryMarketContent.kt"
        ).readText()
        val screenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(uiSource.contains(".clip(shape)"))
        assertTrue(uiSource.contains(".combinedClickable("))
        assertTrue(uiSource.contains("onLongClick = onLongClick"))
        assertTrue(uiSource.contains("selectedStackKeys"))
        assertTrue(uiSource.contains("steam_inventory_batch_sell"))
        assertTrue(screenSource.contains("selectedInventoryStackKeys"))
        assertTrue(screenSource.contains("SteamBatchSellSheet("))
    }

    @Test
    fun marketListingsClipPressStateAndSupportBatchCancellation() {
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/inventory/ui/SteamInventoryMarketContent.kt"
        ).readText()
        val stateSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        val listingCard = uiSource
            .substringAfter("private fun SteamMarketListingCard(")
            .substringBefore("private fun SteamMarketInfoBlock(")

        assertTrue(listingCard.contains(".clip(shape)"))
        assertTrue(listingCard.contains(".combinedClickable("))
        assertTrue(listingCard.contains("onLongClick = onLongClick"))
        assertTrue(listingCard.contains("this.selected = selected"))
        assertTrue(uiSource.contains("selectedListingIds"))
        assertTrue(uiSource.contains("steam_market_batch_cancel"))
        assertTrue(stateSource.contains("fun cancelMarketListings("))
        assertTrue(stateSource.contains("distinctBy { it.listingId }"))
        assertTrue(stateSource.contains("removeCancelledSteamMarketListings("))
    }

    @Test
    fun sharedSteamAccountSwitcherClipsItsPressStateToRoundedCorners() {
        val screenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamAccountUi.kt"
        ).readText()
        val accountCard = screenSource
            .substringAfter("internal fun SteamConfirmationAccountCard(")
            .substringBefore("private fun SteamAuthorizedDevicesSection(")

        assertTrue(accountCard.contains("val shape = RoundedCornerShape(12.dp)"))
        assertTrue(accountCard.contains(".clip(shape)"))
        assertTrue(accountCard.indexOf(".clip(shape)") < accountCard.indexOf(".clickable("))
        assertTrue(accountCard.contains("shape = shape"))
    }

    @Test
    fun priceChartSupportsTapAndHorizontalDragReadout() {
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/inventory/ui/SteamInventoryMarketContent.kt"
        ).readText()

        assertTrue(uiSource.contains("detectTapGestures"))
        assertTrue(uiSource.contains("detectHorizontalDragGestures"))
        assertTrue(uiSource.contains("selectedIndex"))
        assertTrue(uiSource.contains("selected.label"))
        assertTrue(uiSource.contains("drawCircle("))
    }

    @Test
    fun batchListingPreservesPerStackPartialResultsAndOneConfirmationPass() {
        val stateSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()
        val sheetSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/market/ui/SteamBatchSellSheet.kt"
        ).readText()
        val pricingSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/market/SteamBatchPricing.kt"
        ).readText()

        assertTrue(stateSource.contains("fun sellInventoryBatch("))
        assertTrue(stateSource.contains("listedByStackKey"))
        assertTrue(stateSource.contains("preExistingMarketIds"))
        assertTrue(stateSource.contains("findNewSteamMarketConfirmations("))
        assertTrue(sheetSource.contains("SteamBatchPricing.resolveEntries("))
        assertTrue(sheetSource.contains("SteamBatchPriceMode.RECENT_HIGH"))
        assertTrue(sheetSource.contains("SteamBatchPriceMode.RECENT_LOW"))
        assertTrue(pricingSource.contains("quantity = 1"))
        assertTrue(sheetSource.contains("itemReceiveOverrides"))
        assertTrue(sheetSource.contains("steam_market_batch_items_title"))
        assertTrue(sheetSource.contains("steam_market_batch_item_price_edit"))
        assertTrue(sheetSource.contains("LazyColumn("))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
