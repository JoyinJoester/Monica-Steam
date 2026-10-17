package takagi.ru.monica.steam.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.InterfaceScale
import takagi.ru.monica.steam.foundation.ui.calculateSteamUiDensity

class SteamUiScaleSettingsTest {
    @Test
    fun launcherAppliesPersistedDensityWhilePreservingSystemFontScale() {
        val gradle = projectFile("app/build.gradle").readText()
        val launcher = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val providerFile = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamUiScaleProvider.kt"
        )
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamUiScalePreferences.kt"
        ).readText()

        assertTrue(gradle.contains("exclude 'takagi/ru/monica/MainActivity.kt'"))
        assertTrue(preferences.contains("preferencesDataStore("))
        assertTrue(preferences.contains("name = \"monica_steam_ui_scale\""))
        assertTrue(preferences.contains("Flow<Int>"))
        assertTrue(preferences.contains("InterfaceScale.normalizePercent("))
        assertTrue(Regex("""setSteamUiScaledContent\s+(?:\w+@)?\s*\{""").containsMatchIn(launcher))
        assertFalse(launcher.contains("ProvideSteamContentDensity"))
        assertTrue(providerFile.exists())
        val provider = providerFile.readText()
        assertTrue(provider.contains("ComponentActivity.setSteamUiScaledContent"))
        assertTrue(provider.contains("ProvideSteamUiScale(content)"))
        assertTrue(provider.contains("SteamUiScalePreferences"))
        assertTrue(provider.contains("CompositionLocalProvider("))
        assertTrue(provider.contains("InterfaceScale.DEFAULT_PERCENT"))
        assertTrue(provider.contains("ProvideMonicaInterfaceScale(scalePercent)"))
        assertFalse(provider.contains("coerceAtMost(1f)"))
        val sharedProvider = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/scale/InterfaceScaleProvider.kt"
        ).readText()
        assertTrue(sharedProvider.contains("LocalDensity provides appDensity"))
        assertTrue(sharedProvider.contains("InterfaceScale.calculateDensity("))
        assertTrue(sharedProvider.contains("fontScale = baseDensity.fontScale"))
    }

    @Test
    fun nativeAppearanceSectionProvidesMonicaAndroidScaleSliderAndDefaultReset() {
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val contentFile = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/InterfaceScaleSettingsContent.kt"
        )
        val legacyContentFile = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SteamUiScaleSettingsContent.kt"
        )

        assertTrue(settings.contains("additionalAppearanceContent"))
        assertTrue(host.contains("InterfaceScaleSettingsItem("))
        assertTrue(host.contains("InterfaceScaleSelectionSheet("))
        assertTrue(host.contains("uiScalePreferences.updateScale("))
        assertTrue(contentFile.exists())
        assertFalse(legacyContentFile.exists())
        val content = contentFile.readText()
        assertTrue(content.contains("Slider("))
        assertTrue(content.contains("onValueChangeFinished"))
        assertTrue(content.contains("InterfaceScale.calculateEffectiveDpi("))
        assertTrue(content.contains("LocalDensity provides systemDensity"))
        assertTrue(content.contains("stateDescription"))
        assertTrue(content.contains("InterfaceScale.DEFAULT_PERCENT"))
        assertTrue(content.contains("InterfaceScale.MIN_PERCENT.toFloat()"))
        assertTrue(content.contains("InterfaceScale.MAX_PERCENT.toFloat()"))
    }

    @Test
    fun popupMenusKeepTheSameRootScaledDensityAsPages() {
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamUiScaleProvider.kt"
        ).readText()
        val menu = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordTopActionsMenu.kt"
        ).readText()

        assertTrue(provider.contains("LocalSteamUiChromeDensity provides appDensity"))
        assertTrue(menu.contains("LocalSteamUiChromeDensity.current ?: LocalDensity.current"))
        assertTrue(menu.contains("LocalDensity provides menuDensity"))
        assertTrue(menu.contains("CompositionLocalProvider("))
    }

    @Test
    fun continuousScaleValuesAreSanitizedAndAppliedPredictably() {
        assertEquals(InterfaceScale.DEFAULT_PERCENT, InterfaceScale.normalizePercent(null))
        assertEquals(InterfaceScale.MIN_PERCENT, InterfaceScale.normalizePercent(20))
        assertEquals(93, InterfaceScale.normalizePercent(93))
        assertEquals(InterfaceScale.MAX_PERCENT, InterfaceScale.normalizePercent(180))
        assertEquals(
            2.4f,
            calculateSteamUiDensity(3f, InterfaceScale.MIN_PERCENT),
            0.0001f
        )
        assertEquals(
            3.6f,
            calculateSteamUiDensity(3f, InterfaceScale.MAX_PERCENT),
            0.0001f
        )
        assertEquals(528, InterfaceScale.calculateEffectiveDpi(440, 120))
    }

    @Test
    fun scaleStringsDescribeDpiAndSystemIsolation() {
        val english = projectFile("app/src/main/res/values/strings.xml").readText()
        val chinese = projectFile("app/src/main/res/values-zh/strings.xml").readText()

        listOf(english, chinese).forEach { strings ->
            assertTrue(strings.contains("name=\"interface_scale_title\""))
            assertTrue(strings.contains("name=\"interface_scale_current\""))
            assertTrue(strings.contains("name=\"interface_scale_description\""))
            assertTrue(strings.contains("name=\"interface_scale_reset\""))
            assertTrue(strings.contains("DPI"))
            assertFalse(strings.contains("name=\"steam_ui_scale_compact\""))
        }
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
