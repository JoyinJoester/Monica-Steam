package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreBrowseMenuUiGuardTest {
    @Test
    fun homeUsesAnEdgeDrawerWithGroupedActions() {
        val menu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreNavigationDrawer.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreHomePage.kt"
        ).readText()

        assertTrue(menu.contains("ModalNavigationDrawer("))
        assertTrue(menu.contains("ModalDrawerSheet("))
        assertTrue(menu.contains("actions.points"))
        assertTrue(menu.contains("actions.activateProduct"))
        assertTrue(menu.contains("close(action.onClick)"))
        assertTrue(screen.contains("SteamStoreNavigationDrawer("))

    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
