package takagi.ru.monica.steam.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamUiScaleSettingsTest {
    @Test
    fun rootAppliesPersistedDensityWhilePreservingSystemFontScale() {
        val main = projectFile(
            "app/src/main/java/takagi/ru/monica/MainActivity.kt"
        ).readText()
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamUiScalePreferences.kt"
        ).readText()

        assertTrue(preferences.contains("preferencesDataStore("))
        assertTrue(preferences.contains("name = \"monica_steam_ui_scale\""))
        assertTrue(preferences.contains("85, 90, 100, 110"))
        assertTrue(main.contains("SteamUiScalePreferences"))
        assertTrue(main.contains("CompositionLocalProvider(LocalDensity provides appDensity)"))
        assertTrue(main.contains("calculateSteamUiDensity("))
        assertTrue(main.contains("fontScale = baseDensity.fontScale"))
    }

    @Test
    fun nativeAppearanceSectionProvidesScaleSheetAndDefaultReset() {
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val content = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SteamUiScaleSettingsContent.kt"
        ).readText()

        assertTrue(settings.contains("additionalAppearanceContent"))
        assertTrue(host.contains("SteamUiScaleSettingsItem("))
        assertTrue(host.contains("SteamUiScaleSelectionSheet("))
        assertTrue(host.contains("uiScalePreferences.updateScale("))
        assertTrue(content.contains("SteamUiScaleOption.DEFAULT"))
        assertTrue(content.contains("heightIn(min = 48.dp)"))
    }

    @Test
    fun supportedScaleValuesAreSanitizedAndAppliedPredictably() {
        assertEquals(
            listOf(85, 90, 100, 110),
            SteamUiScaleOption.supportedPercentages
        )
        assertEquals(SteamUiScaleOption.COMPACT, SteamUiScaleOption.fromPercent(85))
        assertEquals(SteamUiScaleOption.DEFAULT, SteamUiScaleOption.fromPercent(null))
        assertEquals(SteamUiScaleOption.DEFAULT, SteamUiScaleOption.fromPercent(75))
        assertEquals(
            2.55f,
            calculateSteamUiDensity(3f, SteamUiScaleOption.COMPACT),
            0.0001f
        )
        assertEquals(
            3.3f,
            calculateSteamUiDensity(3f, SteamUiScaleOption.LARGE),
            0.0001f
        )
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
