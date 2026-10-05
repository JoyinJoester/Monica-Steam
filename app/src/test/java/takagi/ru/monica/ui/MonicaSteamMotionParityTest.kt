package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level guard for the navigation contract shared with Monica Android.
 *
 * The standalone app intentionally keeps its Steam-specific screens, but the
 * secondary navigation retains EasyNotes motion; Dock roots slide independently.
 */
class MonicaSteamMotionParityTest {
    @Test
    fun activityUsesSlidingDockSwitchAndEasyNotesForSecondaryPages() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertFalse("Do not infer direction from an arbitrary page depth", source.contains("steamPageDepth"))
        assertFalse("Do not keep a second copy of Monica's transition parameters", source.contains("steamPageContentTransform"))
        assertFalse(source.contains("CubicBezierEasing"))
        assertFalse(source.contains("slideInHorizontally"))
        assertFalse(source.contains("slideOutHorizontally"))
        assertTrue(source.contains("steamDockPageTransition("))
        assertTrue(source.contains("reduceAnimations = settings.reduceAnimations"))
        assertTrue(source.contains("initialState.isDockPage(dockStyle)"))
        assertTrue(source.contains("targetState.isDockPage(dockStyle)"))
        assertTrue(source.contains("easyNotesScreenEnter(settings.reduceAnimations)"))
        assertTrue(source.contains("easyNotesScreenExit(settings.reduceAnimations)"))
        assertTrue(source.contains("var pageHistory by rememberSaveable"))
        assertTrue(source.contains("fun navigateTo(page: MonicaSteamPage)"))
        assertTrue(source.contains("fun navigateBack()"))
    }

    @Test
    fun settingsLibraryAndStoreUseTheSameEasyNotesPageTransition() {
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
        assertTrue(settings.contains("easyNotesScreenEnter(settings.reduceAnimations)"))
        assertTrue(settings.contains("easyNotesScreenExit(settings.reduceAnimations)"))
        listOf(library, store).forEach { source ->
            assertTrue(source.contains("easyNotesScreenEnter(reduceAnimations)"))
            assertTrue(source.contains("easyNotesScreenExit(reduceAnimations)"))
        }
        assertTrue(settings.contains("targetState = child"))
    }

    @Test
    fun navigationAnimatesDockToDockAndBackToParent() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        assertTrue(source.contains("initialState.isDockPage(dockStyle)"))
        assertTrue(source.contains("targetState.isDockPage(dockStyle)"))
        assertTrue(source.contains("steamDockPageTransition("))
        assertTrue(source.contains("easyNotesScreenEnter(settings.reduceAnimations)"))
        assertTrue(source.contains("easyNotesScreenExit(settings.reduceAnimations)"))
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
