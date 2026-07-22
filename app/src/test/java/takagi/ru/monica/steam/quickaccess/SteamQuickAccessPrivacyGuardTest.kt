package takagi.ru.monica.steam.quickaccess

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamQuickAccessPrivacyGuardTest {
    @Test
    fun manifestRegistersTwoConfigurableHomeWidgetsAndNoQuickSettingsTile() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val statsInfo = projectFile(
            "app/src/main/res/xml/steam_account_stats_widget_info.xml"
        ).readText()
        val recentInfo = projectFile(
            "app/src/main/res/xml/steam_recent_games_widget_info.xml"
        ).readText()

        assertTrue(manifest.contains("SteamAccountStatsWidgetProvider"))
        assertTrue(manifest.contains("SteamRecentGamesWidgetProvider"))
        assertTrue(manifest.contains("SteamWidgetConfigureActivity"))
        assertFalse(manifest.contains("QS_TILE"))
        assertFalse(manifest.contains("SteamQuickSettingsTileService"))
        assertTrue(statsInfo.contains("android:configure"))
        assertTrue(recentInfo.contains("android:configure"))
        assertTrue(recentInfo.contains("android:resizeMode=\"horizontal|vertical\""))
        assertTrue(recentInfo.contains("android:minResizeHeight"))
    }

    @Test
    fun widgetDataIsCacheFirstAndContainsRequestedAccountFields() {
        val data = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/quickaccess/SteamWidgetData.kt"
        ).readText()
        val renderer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/quickaccess/SteamWidgetRenderer.kt"
        ).readText()
        val decorRepository = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/SteamMiniProfileDecorRepository.kt"
        ).readText()

        assertTrue(data.contains("SteamLibraryCacheRepository"))
        assertTrue(data.contains("inventoryItemCount"))
        assertTrue(data.contains("estimatedReplacementValueMinor"))
        assertTrue(data.contains("SteamMiniProfileDecorRepository"))
        assertTrue(renderer.contains("steam_account_stats_avatar"))
        assertTrue(renderer.contains("steam_account_stats_playtime"))
        assertTrue(renderer.contains("steam_account_stats_inventory"))
        assertTrue(renderer.contains("steam_account_stats_value"))
        assertTrue(renderer.contains("isCurrentlyPlaying"))
        assertTrue(decorRepository.contains("CURRENT_GAME_MAX_STALE_MILLIS"))
        assertTrue(decorRepository.contains("currentGameName = null"))
    }

    @Test
    fun settingsExposeBothWidgetPinRequestsButNoTileRequest() {
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val installer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/quickaccess/SteamQuickAccessInstaller.kt"
        ).readText()

        assertTrue(host.contains("requestPinAccountWidget"))
        assertTrue(host.contains("requestPinRecentGamesWidget"))
        assertFalse(host.contains("requestAddTile"))
        assertFalse(installer.contains("requestAddTileService"))
        assertTrue(installer.contains("android.appwidget.AppWidgetManager"))
    }

    @Test
    fun recentWidgetUsesHeightToChooseOneOrTwoRows() {
        assertFalse(SteamRecentGamesWidgetProvider.shouldShowTwoGames(60))
        assertTrue(SteamRecentGamesWidgetProvider.shouldShowTwoGames(160))
    }

    @Test
    fun formattersKeepWidgetNumbersCompactAndLocaleStable() {
        assertTrue(SteamWidgetDataLoader.formatPlaytime(120).contains("2 h"))
        assertTrue(SteamWidgetDataLoader.formatGamePlaytime(90).contains("1 h"))
        assertTrue(SteamWidgetDataLoader.formatValue(1299, "CNY").startsWith("¥"))
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
