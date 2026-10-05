package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStorePullRefreshGuardTest {
    @Test
    fun homeUsesRealRefreshStateExpressiveIndicatorAndSettingsMenu() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText() + projectFile("app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreHomePage.kt").readText() + projectFile("app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreNavigationDrawer.kt").readText()
        val controls = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamPageRefreshControls.kt"
        ).readText()

        assertTrue(store.contains("SteamStoreNavigationDrawer("))
        assertTrue(store.contains("onClick = actions.settings"))
        assertTrue(store.contains("SteamExpressivePullToRefresh("))
        assertTrue(store.contains("val refreshing = if (searching) state.searching else if (catalog) state.loadingCatalog else state.loadingHome"))
        assertTrue(store.contains("SteamExpressivePullToRefresh(refreshing, actions.refresh"))
        assertTrue(store.contains("viewModel.loadHome(force = true)"))
        assertTrue(controls.contains("PullToRefreshDefaults.LoadingIndicator("))
        assertTrue(controls.contains("state.isAnimating"))
        assertTrue(controls.contains(".offset { IntOffset(0,"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
