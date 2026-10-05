package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreCollectionUiGuardTest {
    @Test
    fun cartAndWishlistListsReserveSpaceAboveFloatingDock() {
        val collection = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamNativeCartScreen.kt"
        ).readText()
        val tokens = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreLayoutTokens.kt"
        ).readText()

        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertTrue(tokens.contains("CollectionBottomSpacing = 16.dp"))
        assertTrue(activity.contains("LocalSteamDockContentClearance provides"))
        assertTrue(collection.contains("LocalSteamDockContentClearance.current"))
        assertTrue(collection.contains("steamDockActionClearance"))
        assertEquals(
            1,
            Regex("bottom = SteamStoreLayoutTokens\\.CollectionBottomSpacing")
                .findAll(collection)
                .count()
        )
        assertTrue(collection.contains("bottom = bottomContentPadding"))
    }

    @Test
    fun collectionScreenUsesM3TabsWishlistAndConditionalCheckoutBar() {
        val collection = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamNativeCartScreen.kt"
        ).readText()

        assertTrue(collection.contains("SingleChoiceSegmentedButtonRow("))
        assertTrue(collection.contains("SegmentedButton("))
        assertTrue(collection.contains("SteamStoreCollectionTab.WISHLIST"))
        assertTrue(collection.contains("SteamWishlistItem"))
        assertTrue(collection.contains("AnimatedContent("))
        assertTrue(collection.contains("private fun SteamCheckoutBar("))
        assertTrue(
            collection.contains(
                "selectedTab == SteamStoreCollectionTab.CART && cartItems.isNotEmpty()"
            )
        )
        assertFalse(collection.contains("Text(\"购物车\")"))
        assertFalse(collection.contains("Text(\"合计\")"))
    }

    @Test
    fun storeDetailUsesOnePrimaryPurchaseActionAndSemanticErrorFeedback() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val detail = store
            .substringAfter("private fun SteamStoreDetailContent(")
            .substringBefore("private fun SteamStorePurchaseActions(")

        assertTrue(store.contains("private fun SteamStorePurchaseActions("))
        assertTrue(detail.contains("SteamStorePurchaseActions("))

        val actions = store
            .substringAfter("private fun SteamStorePurchaseActions(")
            .substringBefore("private fun SteamStoreRegionalPriceSheet(")
        val splitButton = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/gift/ui/" +
                "SteamStoreGiftPurchaseSplitButton.kt"
        ).readText()

        assertTrue(actions.contains("SteamStoreGiftPurchaseSplitButton("))
        assertTrue(splitButton.contains("SplitButtonLayout("))
        assertTrue(Regex("(?m)^\\s*OutlinedButton\\(").containsMatchIn(actions))
        assertTrue(actions.contains("FilledTonalButton("))
        assertFalse(actions.contains("FilledTonalIconButton("))
        assertTrue(Regex("fillMaxWidth\\(\\)").findAll(actions).count() >= 2)
        assertTrue(
            Regex("heightIn\\(min = 52\\.dp\\)").findAll(actions).count() >= 2
        )
        assertTrue(
            Regex("RoundedCornerShape\\(18\\.dp\\)").findAll(actions).count() >= 2
        )
        assertTrue(actions.contains("MaterialTheme.colorScheme.errorContainer"))
        assertTrue(actions.contains("MaterialTheme.colorScheme.onErrorContainer"))
        assertTrue(actions.contains("Icons.Default.ErrorOutline"))
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
