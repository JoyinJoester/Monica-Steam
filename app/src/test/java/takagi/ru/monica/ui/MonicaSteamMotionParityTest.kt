package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level integration guard; native frame behavior lives in SteamAppGlassUiTest.
 *
 * The standalone app intentionally keeps its Steam-specific screens, but the
 * Steam pages share one timing policy and keep their existing navigation callbacks.
 */
class MonicaSteamMotionParityTest {
    @Test
    fun activitySharesMotionForDockAndSecondaryPages() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertFalse("Do not infer direction from an arbitrary page depth", source.contains("steamPageDepth"))
        assertFalse("Do not keep a second copy of Monica's transition parameters", source.contains("steamPageContentTransform"))
        assertFalse(source.contains("CubicBezierEasing"))
        assertFalse(source.contains("slideInHorizontally"))
        assertFalse(source.contains("slideOutHorizontally"))
        assertTrue(source.contains("SteamTabPageHost("))
        val host = projectFile("app/src/main/java/takagi/ru/monica/steam/navigation/SteamTabPageHost.kt").readText()
        assertTrue(host.contains("contentKey = { it }"))
        assertTrue(host.contains("for (page in pages) key(page)"))
        assertFalse(source.contains("MONICA_STEAM_DOCK_CONTENT_KEY"))
        assertTrue(source.contains("initialPage.isDockPage(dockStyle)"))
        assertTrue(source.contains("targetPage.isDockPage(dockStyle)"))
        assertTrue(source.contains("steamPageTransition(settings.reduceAnimations,"))

        assertTrue(source.contains("var pageHistory by rememberSaveable"))
        assertTrue(source.contains("fun navigateTo(page: MonicaSteamPage)"))
        assertTrue(source.contains("fun navigateBack()"))
    }

    @Test
    fun settingsLibraryAndStoreUseTheSamePageTransition() {
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(settings.contains("targetState = child"))
        assertTrue(library.contains("targetState = libraryDestination"))
        assertTrue(store.contains("targetState = storeDestination"))
        assertTrue(settings.contains("steamPageTransition(settings.reduceAnimations,"))

        listOf(library, store).forEach { source ->
            assertTrue(source.contains("steamPageTransition(reduceAnimations,"))

        }
        assertTrue(settings.contains("targetState = child"))
    }

    @Test
    fun navigationUsesConfiguredDockOrderAndDistinctPageKeys() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        assertTrue(source.contains("initialPage.isDockPage(dockStyle)"))
        assertTrue(source.contains("targetPage.isDockPage(dockStyle)"))
        assertTrue(source.contains("activeDockOrder.indexOf(targetPage.toDockTab())"))
        assertTrue(source.contains("steamPageTransition(settings.reduceAnimations,"))

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
