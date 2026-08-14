package takagi.ru.monica.steam.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSettingsChildPagesTest {
    @Test
    fun sharedSettingsSupportsCompactHomeAndDedicatedChildModes() {
        val mode = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreenMode.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()

        listOf(
            "COMPACT_HOME",
            "DATA_MANAGEMENT",
            "APPEARANCE",
            "ADDITIONAL"
        ).forEach { expected -> assertTrue(mode.contains(expected)) }
        assertTrue(screen.contains("screenMode: SettingsScreenMode = SettingsScreenMode.FULL"))
        assertTrue(screen.contains("val showDataManagementEntry = isCompactHomeMode"))
        assertTrue(screen.contains("val dataManagementEntryMatches = matchesSettingsItem("))
        assertTrue(screen.contains("val showAppearanceEntry = isCompactHomeMode"))
        assertTrue(screen.contains("val appearanceEntryMatches = matchesSettingsItem("))
        assertTrue(screen.contains("if (showAdditionalDetails)"))
        assertTrue(screen.contains("compactHomeSections: List<SettingsNavigationSection>"))
        assertTrue(screen.contains("visibleCompactHomeSections.forEach"))
        assertTrue(screen.contains("settings_data_management_entry_title"))
        assertTrue(screen.contains("settings_appearance_entry_title"))
    }

    @Test
    fun steamSettingsHomeRoutesCategoriesToIndependentLists() {
        val navigation = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()

        assertTrue(navigation.contains("mode = SettingsScreenMode.COMPACT_HOME"))
        assertTrue(navigation.contains("SteamSettingsChild.DATA_MANAGEMENT"))
        assertTrue(navigation.contains("mode = SettingsScreenMode.DATA_MANAGEMENT"))
        assertTrue(navigation.contains("SteamSettingsChild.APPEARANCE"))
        assertTrue(navigation.contains("mode = SettingsScreenMode.APPEARANCE"))
        assertTrue(navigation.contains("SteamSettingsChild.STEAM_FEATURES"))
        assertTrue(navigation.contains("SteamSettingsChild.NAVIGATION"))
        assertFalse(navigation.contains("SteamSettingsChild.APP_SUPPORT"))
        assertTrue(navigation.contains("SteamSettingsChild.NETWORK_OPTIMIZATION_ADVANCED"))
        assertTrue(navigation.contains("SteamSettingsAdditionalGroup.STEAM_EXPERIENCE"))
        assertTrue(navigation.contains("SteamSettingsAdditionalGroup.NAVIGATION"))
        assertTrue(navigation.contains("onOpenNotifications = { child = SteamSettingsChild.NOTIFICATIONS }"))
        assertTrue(navigation.contains("onNavigateBack = { child = null }"))
        assertTrue(navigation.contains("SteamNetworkOptimizationAutoScreen("))
        assertTrue(
            navigation.contains(
                "onNavigateBack = { child = SteamSettingsChild.NETWORK_OPTIMIZATION }"
            )
        )
    }

    @Test
    fun dataAndFeatureChildPagesReuseExistingSteamEntries() {
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()

        assertTrue(host.contains("onNavigateToDataManagement = onOpenDataManagement"))
        assertTrue(host.contains("onNavigateToAppearance = onOpenAppearance"))
        assertTrue(host.contains("onNavigateToAdditionalSettings = onOpenSteamFeatures"))
        assertTrue(host.contains("onNavigateToSteamBackup = onOpenMaFileTransfer"))
        assertTrue(host.contains("onNavigateToWebDavBackup = onOpenWebDavBackup"))
        assertTrue(host.contains("onNavigateToMdbx = onOpenMdbx"))
        assertTrue(host.contains("SteamStoreHintSettingsEntry(onClick = onOpenStoreHints)"))
        assertTrue(host.contains("homeHeaderContent ="))
        assertTrue(host.contains("SteamNetworkOptimizationPullCard("))
        assertTrue(host.contains("onHomeHeaderPullTriggered = onOpenNetworkOptimization"))
        assertTrue(host.contains("SteamSettingsAdditionalGroup.NAVIGATION"))
        assertTrue(host.contains("showLanguage = inlineAppSupportItems"))
        assertTrue(host.contains("showAppSupportItemsOnCompactHome = inlineAppSupportItems"))
        assertTrue(host.contains("showBottomNavigation = false"))
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
