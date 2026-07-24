package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFloatingDockGuardTest {
    @Test
    fun dockUsesThreeItemToolbarWithIndependentTokenAction() {
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
        assertTrue(settings.contains("listOf(LIBRARY, STORE, SETTINGS)"))
        assertTrue(dock.contains("filterNot { it == SteamDockTab.TOKEN }"))
        assertTrue(dock.contains("floatingActionButton ="))
        assertTrue(dock.contains("FloatingActionButton("))
        assertTrue(dock.contains("onSelected(SteamDockTab.TOKEN)"))
        assertFalse(dock.contains("SteamAvatarImage"))
        assertFalse(dock.contains("SteamAccountPickerSheet"))
        assertFalse(dock.contains("Icons.Default.QrCodeScanner"))
        assertTrue(dock.contains("WindowInsets.navigationBars"))
        assertFalse(dock.contains("offset(x = 8.dp)"))
        assertTrue(dock.contains("showProgress"))
        assertFalse(activity.contains("onScan = { navigateTo(MonicaSteamPage.SCANNER) }"))
        assertFalse(dock.contains("Column(modifier = Modifier.fillMaxWidth())"))
        assertFalse(settings.substringAfter("enum class SteamDockTab").substringBefore(";").contains("SCANNER"))
        assertTrue(notices.contains("Essentials"))
        assertTrue(notices.contains("Copyright (c) 2025 Sameera Sandakelum"))
    }

    @Test
    fun dockPagesKeepFloatingActionsAboveTheToolbar() {
        val toolbar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamEssentialsFloatingToolbar.kt"
        ).readText()
        val token = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(toolbar.contains("SteamDockFabClearance"))
        assertTrue(token.contains("padding(bottom = SteamDockFabClearance)"))
        assertTrue(store.contains("padding(bottom = SteamDockFabClearance)"))
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
