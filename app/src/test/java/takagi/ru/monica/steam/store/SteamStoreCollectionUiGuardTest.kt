package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreCollectionUiGuardTest {
    @Test
    fun collectionScreenUsesM3TabsWishlistAndConditionalCheckoutBar() {
        val collection = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/SteamNativeCartScreen.kt"
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
    fun storeMovesCartFromTopPillToFabAndAddsWishlistDetailAction() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/SteamStoreScreen.kt"
        ).readText()
        val home = store
            .substringAfter("SteamStoreDestination.Home -> Scaffold(")
            .substringBefore("if (showAccounts)")
        val topBar = home
            .substringAfter("topBar = {")
            .substringBefore("floatingActionButton =")
        val detail = store
            .substringAfter("private fun SteamStoreDetailContent(")
            .substringBefore("@Composable private fun DetailTextSection")

        assertTrue(home.contains("ExtendedFloatingActionButton("))
        assertTrue(home.contains("onClick = viewModel::openCart"))
        assertFalse(topBar.contains("viewModel::openCart"))
        assertFalse(topBar.contains("BadgedBox("))
        assertTrue(detail.contains("onToggleWishlist"))
        assertTrue(detail.contains("Icons.Default.Favorite"))
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
