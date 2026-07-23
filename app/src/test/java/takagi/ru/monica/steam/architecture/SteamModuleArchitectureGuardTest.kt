package takagi.ru.monica.steam.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamModuleArchitectureGuardTest {
    @Test
    fun steamRootContainsOnlyModuleDirectories() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam")

        assertTrue(root.isDirectory)
        assertTrue(root.listFiles().orEmpty().none { it.extension == "kt" })
    }

    @Test
    fun documentedFeatureAndSharedModulesExist() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam")
        val expectedExistingModules = setOf(
            "alerts",
            "backup",
            "core",
            "data",
            "diagnostics",
            "friends",
            "health",
            "io",
            "library",
            "market",
            "navigation",
            "network",
            "notifications",
            "organization",
            "profile",
            "quickaccess",
            "security",
            "store",
            "trade"
        )
        val actual = root.listFiles().orEmpty()
            .filter(File::isDirectory)
            .map(File::getName)
            .toSet()

        assertTrue(actual.containsAll(expectedExistingModules))
    }

    @Test
    fun legacySteamUiCannotReceiveAdditionalFiles() {
        val legacyUi = projectFile("app/src/main/java/takagi/ru/monica/steam/ui")
        val temporaryAllowlist = setOf(
            "SteamBackupScreen.kt",
            "SteamBatchSellSheet.kt",
            "SteamHealthScreen.kt",
            "SteamInventoryMarketContent.kt",
            "SteamLibraryScreen.kt",
            "SteamLoginNotificationHelper.kt",
            "SteamMiniProfileBackgroundLayer.kt",
            "SteamMiniProfileCrop.kt",
            "SteamOrganizationComponents.kt",
            "SteamQrAccountPreference.kt",
            "SteamQrScannerScreen.kt",
            "SteamScreen.kt",
            "SteamSearchFilters.kt",
            "SteamTradeOffersContent.kt",
            "SteamUiScalePreferences.kt",
            "SteamUiScaleProvider.kt",
            "SteamViewModel.kt"
        )
        val actual = legacyUi.listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .map(File::getName)
            .toSet()

        assertEquals(temporaryAllowlist, actual)
    }

    @Test
    fun applicationHostDoesNotDependOnFeatureInternals() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertFalse(activity.contains(".friends.data."))
        assertFalse(activity.contains(".friends.domain."))
        assertFalse(activity.contains(".friends.presentation."))
        assertTrue(activity.contains(".friends.ui.SteamFriendsScreen"))
    }

    @Test
    fun architectureDocumentTracksTheLegacyAllowlist() {
        val document = projectFile("docs/architecture/STEAM_MODULES.md").readText()

        assertTrue(document.contains("## Feature Modules"))
        assertTrue(document.contains("## Shared Modules"))
        assertTrue(document.contains("## Dependency Rules"))
        assertTrue(document.contains("SteamScreen.kt"))
        assertTrue(document.contains("SteamViewModel.kt"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
