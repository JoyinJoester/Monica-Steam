package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStandaloneNavigationGuardTest {
    @Test
    fun standaloneUsesConfigurableFiveTabNavigation() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()
        val fixedBar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamFixedBottomBar.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val libraryScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()

        assertTrue(activity.contains("SteamFixedBottomBar("))
        assertFalse(activity.contains("SteamStandaloneDock("))
        assertTrue(activity.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(activity.contains(".align(Alignment.BottomCenter)"))
        assertFalse(activity.contains("bottomBar ="))
        assertTrue(fixedBar.contains("SteamDockTab.completeFixedOrder(order)"))
        assertTrue(fixedBar.contains("selected = selected == tab"))
        assertTrue(fixedBar.contains("onClick = { onSelected(tab) }"))
        assertTrue(activity.contains("zIndex(1f)"))
        assertFalse(activity.contains("SteamAccountPickerSheet("))
        assertFalse(activity.contains("SteamAvatarImage("))
        assertFalse(activity.contains("onScan = { navigateTo(MonicaSteamPage.SCANNER) }"))
        assertFalse(activity.contains("NavigationBarItem"))
        assertTrue(activity.contains("SteamDockPreferences"))
        assertFalse(activity.contains("LinearProgressIndicator"))
        assertFalse(activity.contains("showProgress"))
        assertTrue(libraryScreen.contains("LocalSteamDockContentClearance.current"))
        assertTrue(dock.contains("listOf(STORE, LIBRARY, CHAT)"))
        assertTrue(settings.contains("SteamDockOrderScreen("))
        assertTrue(settings.contains("rememberReorderableLazyListState"))
        val dockOrderScreen = settings
            .substringAfter("internal fun SteamDockOrderScreen(")
            .substringBefore("private fun SteamSettingsSection(")
        val dragCallback = dockOrderScreen
            .substringAfter("rememberReorderableLazyListState(listState)")
            .substringBefore("LaunchedEffect(reorderableState.isAnyItemDragging)")
        val reorderableList = dockOrderScreen.substringAfter("LazyColumn(")
        assertTrue(dockOrderScreen.contains("reorderFixedDockOrder(localOrder, from.index, to.index)"))
        assertTrue(dockOrderScreen.contains("LaunchedEffect(reorderableState.isAnyItemDragging)"))
        assertTrue(dockOrderScreen.contains("showSwitch = false"))
        assertFalse(dockOrderScreen.contains("enabledTabs"))
        assertFalse(dragCallback.contains("onOrderChange("))
        assertFalse(reorderableList.substringBefore("items(localOrder").contains("steam_dock_order_hint"))
        assertTrue(steamScreen.contains("Modifier.steamWindowTopPadding()"))

        val menu = steamScreen
            .substringAfter("private fun SteamTopActionsMenu(")
            .substringBefore("private fun SteamStorageSourceMenu(")
        assertFalse(menu.contains("R.string.steam_library_title"))
        assertFalse(menu.contains("R.string.steam_store_title"))
        assertFalse(menu.contains("R.string.nav_settings"))

        assertFalse(steamScreen.contains("SteamOrganization"))
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
