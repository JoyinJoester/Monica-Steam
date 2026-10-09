package takagi.ru.monica.steam.navigation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SteamAdaptiveLayoutTest {
    @Test
    fun portraitPhoneKeepsCompactLayout() {
        val layout = resolveSteamAdaptiveLayout(widthDp = 393, heightDp = 873)

        assertEquals(SteamAdaptiveLayoutMode.COMPACT, layout.mode)
        assertFalse(layout.useNavigationRail)
        assertFalse(layout.useTwoPaneLayout)
    }

    @Test
    fun landscapePhoneUsesRailAndTwoPanes() {
        val layout = resolveSteamAdaptiveLayout(widthDp = 873, heightDp = 393)

        assertEquals(SteamAdaptiveLayoutMode.LANDSCAPE, layout.mode)
        assertTrue(layout.useNavigationRail)
        assertTrue(layout.useTwoPaneLayout)
        assertEquals(320, layout.preferredListPaneWidthDp)
    }

    @Test
    fun tabletUsesExpandedLayoutInEitherOrientation() {
        val portrait = resolveSteamAdaptiveLayout(widthDp = 800, heightDp = 1280)
        val landscape = resolveSteamAdaptiveLayout(widthDp = 1280, heightDp = 800)

        assertEquals(SteamAdaptiveLayoutMode.EXPANDED, portrait.mode)
        assertEquals(SteamAdaptiveLayoutMode.EXPANDED, landscape.mode)
        assertEquals(400, landscape.preferredListPaneWidthDp)
    }

    @Test
    fun narrowMultiWindowFallsBackToCompactLayout() {
        val layout = resolveSteamAdaptiveLayout(widthDp = 520, heightDp = 390)

        assertEquals(SteamAdaptiveLayoutMode.COMPACT, layout.mode)
        assertFalse(layout.useNavigationRail)
    }

    @Test
    fun coreScreensReadTheSharedAdaptivePolicy() {
        listOf(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt",
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreHomePage.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt",
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).forEach { path ->
            assertTrue(File(projectRoot(), path).readText().contains("rememberSteamAdaptiveLayout()"))
        }
    }

    private fun projectRoot(): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return directory
    }
}
