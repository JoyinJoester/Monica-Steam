package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreResponsiveLayoutGuardTest {

    @Test
    fun featuredHeroUsesTheCompleteSteamHeaderAspectRatio() {
        val hero = storeSource()
            .substringAfter("private fun StoreFeaturedHero(")
            .substringBefore("@Composable\nprivate fun StoreHeroSkeleton")

        assertTrue(hero.contains("aspectRatio(460f / 215f)"))
        assertTrue(hero.contains("contentScale = ContentScale.Fit"))
        assertFalse(hero.contains("alpha = 0.22f"))
        assertFalse(hero.contains("Brush.verticalGradient"))
    }

    @Test
    fun searchResultPriceUsesTheFullCardWidth() {
        val searchCard = storeSource()
            .substringAfter("private fun SearchResultCard(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(searchCard.contains("SteamStoreLayoutTokens.SearchCardPadding"))
        assertTrue(searchCard.contains("PriceRow("))
        assertTrue(searchCard.contains("modifier = Modifier.fillMaxWidth()"))
    }

    @Test
    fun repeatedStoreCardsUseCompactStableLayoutTokens() {
        val store = storeSource()
        val tokens = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreLayoutTokens.kt"
        ).readText()

        assertTrue(tokens.contains("GameCardWidth = 196.dp"))
        assertTrue(tokens.contains("GameCardHeight = 248.dp"))
        assertTrue(tokens.contains("SearchImageWidth = 104.dp"))
        assertTrue(store.contains("SteamStoreLayoutTokens.GameCardWidth"))
        assertTrue(store.contains("SteamStoreLayoutTokens.GameCardHeight"))
        assertTrue(store.contains("SteamStoreLayoutTokens.SearchImageWidth"))
    }

    @Test
    fun priceTokensWrapBetweenItemsInsteadOfInsideCurrencyText() {
        val priceRow = storeSource()
            .substringAfter("private fun PriceRow(")
            .substringBefore("@Composable private fun CachedNotice")

        assertTrue(priceRow.contains("FlowRow("))
        assertTrue(priceRow.contains("softWrap = false"))
        assertTrue(priceRow.contains("maxLines = 1"))
    }

    private fun storeSource(): String = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
    ).readText()

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
