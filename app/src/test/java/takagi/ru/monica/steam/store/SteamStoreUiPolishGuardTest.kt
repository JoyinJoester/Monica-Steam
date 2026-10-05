package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreUiPolishGuardTest {
    @Test
    fun storeUsesMonicaExpressiveHierarchyAndImmersiveDetail() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserScreen.kt"
        ).readText()
        val actionBar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserActionBar.kt"
        ).readText()

        val header = projectFile("app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreDetailHeader.kt").readText()
        assertTrue(store.contains("SteamStoreDetailTopBar("))
        assertTrue(store.contains("SteamStoreDetailHeader("))
        assertFalse(store.contains("height(390.dp)"))
        assertTrue(header.contains("coerceAtMost(184.dp)"))
        assertTrue(store.contains("heightIn(min = 48.dp)"))
        assertFalse(store.contains("OutlinedTextField("))

        assertTrue(actionBar.contains("SelectionActionBar("))
        assertTrue(actionBar.contains("showSelectionControls = false"))
        assertTrue(web.contains("navigationBarsPadding()"))
        assertTrue(actionBar.contains("Icons.Default.Refresh"))
        assertTrue(actionBar.contains("Icons.Default.Share"))
        assertFalse(web.contains("heightIn(min = 64.dp)"))
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
