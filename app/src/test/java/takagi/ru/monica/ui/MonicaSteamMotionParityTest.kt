package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level guard for the navigation contract shared with Monica Android.
 *
 * The standalone app intentionally keeps its Steam-specific screens, but the
 * navigation motion must not grow a second set of timing and direction rules.
 */
class MonicaSteamMotionParityTest {
    @Test
    fun activityUsesInstantDockSwitchAndEasyNotesForSecondaryPages() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertFalse("Do not infer direction from an arbitrary page depth", source.contains("steamPageDepth"))
        assertFalse("Do not keep a second copy of Monica's transition parameters", source.contains("steamPageContentTransform"))
        assertFalse(source.contains("CubicBezierEasing"))
        assertFalse(source.contains("slideInHorizontally"))
        assertFalse(source.contains("slideOutHorizontally"))
        assertTrue(source.contains("EnterTransition.None"))
        assertTrue(source.contains("ExitTransition.None"))
        assertTrue(source.contains("initialState.isDockPage() && targetState.isDockPage()"))
        assertTrue(source.contains("easyNotesScreenEnter().togetherWith(easyNotesScreenExit())"))
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
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamLibraryScreen.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/SteamStoreScreen.kt"
        ).readText()

        assertTrue(settings.contains("targetState = child"))
        assertTrue(library.contains("targetState = libraryDestination"))
        assertTrue(store.contains("targetState = storeDestination"))
        listOf(settings, library, store).forEach { source ->
            assertTrue(source.contains("easyNotesScreenEnter().togetherWith(easyNotesScreenExit())"))
        }
        assertTrue(settings.contains("targetState = child"))
    }

    @Test
    fun navigationDoesNotAnimateDockToDockButDoesAnimateBackToParent() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        assertTrue(source.contains("initialState.isDockPage() && targetState.isDockPage()"))
        assertTrue(source.contains("EnterTransition.None togetherWith ExitTransition.None"))
        assertTrue(source.contains("easyNotesScreenEnter().togetherWith(easyNotesScreenExit())"))
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
