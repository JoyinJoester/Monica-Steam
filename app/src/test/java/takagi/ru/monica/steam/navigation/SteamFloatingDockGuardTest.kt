package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFloatingDockGuardTest {
    @Test
    fun dockReusesEssentialsFloatingToolbarWithoutCrowdedAccountAction() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val dock = activity
            .substringAfter("private fun SteamStandaloneDock(")
            .substringBefore("private fun SteamDockTab.icon()")
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()
        val notices = projectFile("THIRD_PARTY_NOTICES.md").readText()
        assertTrue(activity.contains("SteamEssentialsFloatingToolbar("))
        assertTrue(activity.contains("ExperimentalMaterial3ExpressiveApi"))
        assertTrue(activity.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(activity.contains("modifier = Modifier.align(Alignment.BottomCenter)"))
        assertTrue(activity.contains("selectedIndex = tabs.indexOf(selected)"))
        assertTrue(activity.contains("zIndex(1f)"))
        assertFalse(activity.contains("bottomBar ="))
        assertTrue(settings.contains("listOf(TOKEN, LIBRARY, STORE, SETTINGS)"))
        assertFalse(dock.contains("floatingActionButton"))
        assertFalse(dock.contains("SteamAvatarImage"))
        assertFalse(dock.contains("SteamAccountPickerSheet"))
        assertFalse(dock.contains("Icons.Default.QrCodeScanner"))
        assertTrue(dock.contains("WindowInsets.navigationBars"))
        assertTrue(dock.contains("showProgress"))
        assertFalse(activity.contains("onScan = { navigateTo(MonicaSteamPage.SCANNER) }"))
        assertFalse(dock.contains("Column(modifier = Modifier.fillMaxWidth())"))
        assertFalse(settings.substringAfter("enum class SteamDockTab").substringBefore(";").contains("SCANNER"))
        assertTrue(notices.contains("Essentials"))
        assertTrue(notices.contains("Copyright (c) 2025 Sameera Sandakelum"))
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
