package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLibraryIntegrationGuardTest {
    @Test
    fun accountDataUsesPlainCompactCardsAndGoogleSansFlex() {
        val catalog = projectFile("gradle/libs.versions.toml").readText()
        val type = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/theme/Type.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val accountDetail = screen
            .substringAfter("private fun SteamAccountDetail(")
            .substringBefore("private fun rememberSteamMiniProfileDecor(")

        assertTrue(catalog.contains("material3Expressive = \"1.5.0-alpha18\""))
        assertTrue(type.contains("GoogleSansFlexFontFamily"))
        assertTrue(type.contains("R.font.google_sans_flex_regular"))
        assertTrue(type.contains("R.font.google_sans_flex_metric"))
        assertTrue(type.contains("R.font.google_sans_flex_display"))
        assertFalse(accountDetail.contains("MaterialShapes."))
        assertFalse(accountDetail.contains(".clip(shape)"))
        assertTrue(accountDetail.contains("shape = RoundedCornerShape(18.dp)"))
        assertTrue(accountDetail.contains("shape = RoundedCornerShape(16.dp)"))
        val valueCard = accountDetail
            .substringAfter("private fun SteamAccountValueCard(")
            .substringBefore("private fun rememberSteamMiniProfileDecor(")
        assertTrue(valueCard.contains("fontSize = accountValueTextSize(value)"))
        assertTrue(valueCard.contains("maxLines = 2"))
        assertTrue(valueCard.contains("softWrap = true"))
        assertFalse(valueCard.contains("TextOverflow.Ellipsis"))
        assertTrue(accountDetail.contains("GoogleSansFlexFontFamily"))
        assertTrue(projectFile("licenses/GoogleSansFlex-OFL.txt").isFile)
    }

    @Test
    fun dockProgressIsReportedOnlyFromLibraryOverview() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val loadingEffect = screen
            .substringAfter("LaunchedEffect(state.loadingLibrary, libraryDestination)")
            .substringBefore("AnimatedContent(")

        assertTrue(
            loadingEffect.contains(
                "state.loadingLibrary && libraryDestination == " +
                    "SteamLibraryDestination.Overview"
            )
        )
    }

    @Test
    fun cacheIsEncryptedAndFailuresDoNotReplaceLastSuccess() {
        val repository = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamLibraryCacheRepository.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt"
        ).readText()
        val database = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamDatabase.kt"
        ).readText()

        assertTrue(repository.contains("securityManager.encryptDataLegacyCompat("))
        assertTrue(repository.contains("SteamLibrarySnapshot.serializer()"))
        assertTrue(repository.contains("SteamGameAchievements.serializer()"))
        val failureBranch = viewModel
            .substringAfter("is SteamLibraryResult.Failure -> {")
            .substringBefore("fun openGame")
        assertFalse(failureBranch.contains("cacheRepository.saveLibrary"))
        assertTrue(database.contains("version = 5"))
        assertTrue(database.contains("migration4To5()"))
        assertTrue(database.contains("ON DELETE CASCADE"))
    }

    @Test
    fun productionNavigationContainsLibraryAndAchievementStates() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val libraryViewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt"
        ).readText()

        assertTrue(activity.contains("MonicaSteamPage.LIBRARY"))
        assertTrue(activity.contains("SteamLibraryScreen("))
        assertTrue(screen.contains("SteamAchievementFilter.COMPLETED -> achievements?.completed"))
        assertTrue(screen.contains("SteamAchievementFilter.INCOMPLETE -> achievements?.incomplete"))
        assertTrue(screen.contains("steam_library_estimated_value"))
        assertTrue(screen.contains("priceCoverage"))
        assertTrue(screen.contains("ExpressiveTopBar("))
        assertTrue(screen.contains("ModalBottomSheet("))
        assertFalse(screen.contains("HorizontalPager("))
        assertFalse(screen.contains("OutlinedTextField("))
        assertTrue(screen.contains("Icons.Default.SwitchAccount"))
        val overview = screen
            .substringAfter("private fun SteamLibraryOverview(")
            .substringBefore("private fun SteamAccountHeroSwitcher(")
        val hero = screen
            .substringAfter("private fun SteamAccountHeroSwitcher(")
            .substringBefore("private fun SteamAccountSwitcherSheet(")
        val switcher = screen
            .substringAfter("private fun SteamAccountSwitcherSheet(")
            .substringBefore("private fun SteamAccountHeroCard(")
        assertFalse(overview.contains("LibrarySyncStatus("))
        assertTrue(overview.contains("SteamLibraryFilterSplitButton("))
        assertFalse(overview.contains("FilterChip("))
        assertTrue(hero.contains("onOpenAccountDetails"))
        assertTrue(switcher.contains("containerColor = MaterialTheme.colorScheme.background"))
        assertTrue(switcher.contains("tonalElevation = 0.dp"))
        val detail = screen
            .substringAfter("private fun SteamGameDetail(")
            .substringBefore("private fun SteamGameDetailHero(")
        assertTrue(detail.contains("SteamGameDetailHero("))
        assertTrue(detail.contains("SingleChoiceSegmentedButtonRow("))
        assertFalse(detail.contains("steam_library_achievement_progress"))
        val detailHero = screen
            .substringAfter("private fun SteamGameDetailHero(")
            .substringBefore("private fun SteamGameDetailMetric(")
        assertTrue(detailHero.contains("steam_library_achievement_progress"))
        assertTrue(detailHero.contains("contentScale = ContentScale.Fit"))
        assertFalse(detailHero.contains("contentScale = ContentScale.Crop"))
        val accountDetail = screen
            .substringAfter("private fun SteamAccountDetail(")
            .substringBefore("private fun SteamAccountDetailHero(")
        assertTrue(accountDetail.contains("steam_library_inventory_count"))
        assertTrue(accountDetail.contains("steam_library_estimated_value"))
        assertTrue(accountDetail.contains("steam_library_price_coverage"))
        assertTrue(screen.contains("formatAccountHeroPrice("))
        assertTrue(screen.contains("private fun SteamRegionalPriceSheet("))
        assertFalse(detail.contains("AchievementHeading("))
        assertTrue(screen.contains("SplitButtonLayout("))
        val gameRow = screen
            .substringAfter("private fun SteamGameLibraryRow(")
            .substringBefore("private fun SteamGameBanner(")
        assertTrue(gameRow.contains("game.name"))
        assertTrue(gameRow.contains("formatGameHours(game.playtimeForeverMinutes)"))
        assertFalse(gameRow.contains("game.price"))
        assertFalse(gameRow.contains("game.playtimeRecentMinutes"))
        assertFalse(gameRow.contains("steam_library_unplayed_badge"))
        val regionalSheet = screen
            .substringAfter("private fun SteamRegionalPriceSheet(")
            .substringBefore("private fun SteamRegionalPriceRow(")
        assertTrue(regionalSheet.contains("sortedRegionalPricesForDisplay"))
        assertFalse(regionalSheet.contains("SteamPriceHistoryPanel("))
        val regionalRow = screen
            .substringAfter("private fun SteamRegionalPriceRow(")
            .substringBefore("private fun regionalCountryName(")
        assertTrue(regionalRow.contains("cnyFinalPriceMinor"))
        assertTrue(regionalRow.contains("cnyOriginalPriceMinor"))
        assertTrue(regionalRow.contains("originalPriceMinor"))
        assertTrue(libraryViewModel.contains("SteamCurrencyExchangeService"))
        assertFalse(screen.contains("SteamPriceHistoryPanel"))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
