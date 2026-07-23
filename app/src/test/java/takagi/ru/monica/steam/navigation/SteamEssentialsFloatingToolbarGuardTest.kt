package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamEssentialsFloatingToolbarGuardTest {
    @Test
    fun toolbarKeepsEssentialsConditionalRenderingAndSpringLabelMotion() {
        val toolbar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamEssentialsFloatingToolbar.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val settingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()

        assertTrue(toolbar.contains("if (itemWidth > 0.dp || isSelected)"))
        assertTrue(toolbar.contains("targetValue = if (expanded || isSelected) 48.dp else 0.dp"))
        assertTrue(toolbar.contains("targetValue = if (isSelected && !shouldHideLabel) 80.dp else 0.dp"))
        assertTrue(toolbar.contains("Spring.DampingRatioMediumBouncy"))
        assertTrue(toolbar.contains("windowInsetsPadding(WindowInsets.navigationBars)"))
        assertTrue(toolbar.contains("HorizontalFloatingToolbar("))
        assertTrue(activity.contains("SteamEssentialsFloatingToolbar("))
        assertTrue(activity.contains("selectedIndex = tabs.indexOf(selected)"))
        assertTrue(activity.contains("zIndex(1f)"))
        assertTrue(settingsHost.contains("SettingsScreen("))
        assertTrue(settingsHost.contains("contentBottomPadding = 112.dp"))
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
