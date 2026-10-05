package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreDiscoveryUiGuardTest {
    @Test
    fun homeUsesModularM3BrowseMenuEventsAndPointsShop() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val discovery = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreDiscoveryContent.kt"
        ).readText()
        val menu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreNavigationDrawer.kt"
        ).readText()

        assertTrue(screen.contains("SteamStoreHomePage("))
        assertTrue(screen.contains("viewModel.selectBrowseFilter(filter)"))
        assertTrue(screen.contains("viewModel::openPointsShop"))
        assertTrue(discovery.contains("SteamStoreEventSection("))
        assertTrue(discovery.contains("LazyRow("))
        assertTrue(discovery.contains("MaterialTheme.colorScheme"))
        assertTrue(menu.contains("ModalNavigationDrawer("))
        assertTrue(menu.contains("actions.points"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
