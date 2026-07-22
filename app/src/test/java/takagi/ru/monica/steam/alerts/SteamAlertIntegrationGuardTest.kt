package takagi.ru.monica.steam.alerts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAlertIntegrationGuardTest {
    @Test
    fun schedulerUsesInexactNonWakeupAlarmWithoutHeavyBackgroundFrameworks() {
        val scheduler = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/alerts/SteamAlertScheduler.kt"
        ).readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(scheduler.contains("setInexactRepeating("))
        assertTrue(scheduler.contains("AlarmManager.ELAPSED_REALTIME"))
        assertFalse(scheduler.contains("setExact"))
        assertFalse(scheduler.contains("ELAPSED_REALTIME_WAKEUP"))
        assertFalse(scheduler.contains("WorkManager"))
        assertTrue(manifest.contains(".steam.alerts.SteamAlertReceiver"))
        assertTrue(manifest.contains("android:exported=\"false\""))
    }

    @Test
    fun notificationSurfaceUsesFixedPrivateTextAndExplicitAppIntent() {
        val notifier = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/alerts/SteamAlertNotifier.kt"
        ).readText()

        assertTrue(notifier.contains("VISIBILITY_PRIVATE"))
        assertTrue(notifier.contains("setPublicVersion(publicVersion)"))
        assertTrue(notifier.contains("SteamQuickAccessContract.pendingIntent"))
        assertTrue(notifier.contains("steam_alert_notification_text"))
        listOf(
            "sharedSecret",
            "identitySecret",
            "recoveryCode",
            "accountName",
            "steamId",
            "buyerValue",
            "device.description"
        ).forEach { forbidden ->
            assertFalse("Notifier contains $forbidden", notifier.contains(forbidden))
        }
    }

    @Test
    fun settingsUseDataStoreAndExposeAllAlertControls() {
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/alerts/SteamAlertPreferences.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()

        assertTrue(preferences.contains("preferencesDataStore"))
        assertTrue(settings.contains("alertPreferences.setEnabled"))
        assertTrue(settings.contains("setConfirmationsEnabled"))
        assertTrue(settings.contains("setSessionEnabled"))
        assertTrue(settings.contains("setDevicesEnabled"))
        assertTrue(settings.contains("setPricesEnabled"))
        assertTrue(settings.contains("SteamAlertScheduler.sync(context)"))
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
