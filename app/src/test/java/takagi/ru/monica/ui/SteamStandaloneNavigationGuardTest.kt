package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStandaloneNavigationGuardTest {
    @Test
    fun standaloneUsesThreeSortableTabsAndIndependentTokenAction() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(activity.contains("SteamEssentialsFloatingToolbar("))
        assertTrue(activity.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(activity.contains("modifier = Modifier.align(Alignment.BottomCenter)"))
        assertFalse(activity.contains("bottomBar ="))
        assertTrue(activity.contains("selectedIndex = tabs.indexOf(selected)"))
        assertTrue(activity.contains("floatingActionButton ="))
        assertTrue(activity.contains("onSelected(SteamDockTab.TOKEN)"))
        assertTrue(activity.contains("zIndex(1f)"))
        assertFalse(activity.contains("SteamAccountPickerSheet("))
        assertFalse(activity.contains("SteamAvatarImage("))
        assertFalse(activity.contains("onScan = { navigateTo(MonicaSteamPage.SCANNER) }"))
        assertFalse(activity.contains("NavigationBarItem"))
        assertTrue(activity.contains("SteamDockPreferences"))
        assertTrue(activity.contains("LinearProgressIndicator"))
        assertTrue(activity.contains("showProgress = currentPage == MonicaSteamPage.LIBRARY"))
        assertTrue(activity.contains("if (showProgress)"))
        assertTrue(dock.contains("listOf(LIBRARY, STORE, SETTINGS)"))
        assertTrue(settings.contains("SteamDockOrderScreen("))
        assertTrue(settings.contains("rememberReorderableLazyListState"))
        val dockOrderScreen = settings
            .substringAfter("private fun SteamDockOrderScreen(")
            .substringBefore("private fun SteamSettingsSection(")
        val dragCallback = dockOrderScreen
            .substringAfter("rememberReorderableLazyListState(listState)")
            .substringBefore("LaunchedEffect(reorderableState.isAnyItemDragging)")
        val reorderableList = dockOrderScreen.substringAfter("LazyColumn(")
        assertTrue(dockOrderScreen.contains("reorderDockOrder(localOrder, from.index, to.index)"))
        assertTrue(dockOrderScreen.contains("LaunchedEffect(reorderableState.isAnyItemDragging)"))
        assertFalse(dragCallback.contains("onOrderChange("))
        assertFalse(reorderableList.substringBefore("items(localOrder").contains("steam_dock_order_hint"))
        assertTrue(steamScreen.contains("Modifier.statusBarsPadding()"))

        val menu = steamScreen
            .substringAfter("private fun SteamTopActionsMenu(")
            .substringBefore("private fun SteamStorageSourceMenu(")
        assertFalse(menu.contains("R.string.steam_library_title"))
        assertFalse(menu.contains("R.string.steam_store_title"))
        assertFalse(menu.contains("R.string.nav_settings"))

        val codeContent = steamScreen
            .substringAfter("private fun SteamCodeContent(")
            .substringBefore("private fun SteamAccountDetailContent(")
        assertFalse(codeContent.contains("SteamOrganizationFilterBar("))
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
