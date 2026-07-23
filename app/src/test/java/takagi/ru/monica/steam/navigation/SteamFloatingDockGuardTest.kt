package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFloatingDockGuardTest {
    @Test
    fun dockReusesEssentialsFloatingToolbarWithFixedScannerFab() {
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

        assertTrue(activity.contains("HorizontalFloatingToolbar"))
        assertTrue(activity.contains("ExperimentalMaterial3ExpressiveApi"))
        assertTrue(activity.contains("animateDpAsState"))
        assertTrue(dock.contains("SteamDockTab.sanitizeOrder(order).forEachIndexed"))
        assertTrue(dock.contains("floatingActionButton ="))
        assertTrue(dock.contains("FloatingActionButton("))
        assertTrue(dock.contains("Icons.Default.QrCodeScanner"))
        assertTrue(dock.contains("WindowInsets.navigationBars"))
        assertTrue(dock.contains("isSelected && !shouldHideLabel"))
        assertTrue(dock.contains("showProgress"))
        assertTrue(activity.contains("onScan = { navigateTo(MonicaSteamPage.SCANNER) }"))
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
