package takagi.ru.monica.steam.alerts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAlertIntegrationGuardTest {
    @Test
    fun schedulerUsesInexactNonWakeupAlarmWithoutHeavyBackgroundFrameworks() {
        val scheduler = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/alerts/data/SteamAlertScheduler.kt"
        ).readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(scheduler.contains("setInexactRepeating("))
        assertTrue(scheduler.contains("AlarmManager.ELAPSED_REALTIME"))
        assertFalse(scheduler.contains("setExact"))
        assertFalse(scheduler.contains("ELAPSED_REALTIME_WAKEUP"))
        assertFalse(scheduler.contains("WorkManager"))
        assertTrue(manifest.contains(".steam.alerts.data.SteamAlertReceiver"))
        assertTrue(manifest.contains("android:exported=\"false\""))
    }

    @Test
    fun notificationSurfaceUsesFixedPrivateTextAndExplicitAppIntent() {
        val notifier = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/alerts/data/SteamAlertNotifier.kt"
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
            "app/src/main/java/takagi/ru/monica/steam/alerts/data/SteamAlertPreferences.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/settings/ui/SteamNotificationSettingsScreen.kt"
        ).readText()

        assertTrue(preferences.contains("preferencesDataStore"))
        assertTrue(settings.contains("alertPreferences.setEnabled"))
        assertTrue(settings.contains("setNotificationsEnabled"))
        assertTrue(settings.contains("setConfirmationsEnabled"))
        assertTrue(settings.contains("setSessionEnabled"))
        assertTrue(settings.contains("setDevicesEnabled"))
        assertTrue(settings.contains("setWishlistDiscountsEnabled"))
        assertTrue(settings.contains("setLoginRequestsEnabled"))
        assertTrue(settings.contains("SteamChatNotificationRuntime"))
        assertTrue(settings.contains("SteamAlertScheduler.sync(context)"))
    }

    @Test
    fun receiverUsesSourceAwareHandlesInsteadOfWritingRoomDirectly() {
        val receiver = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/alerts/data/SteamAlertReceiver.kt"
        ).readText()
        val sources = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountSourceRepository.kt"
        ).readText()

        assertTrue(receiver.contains("SteamAlertAccountSessionProvider"))
        assertTrue(receiver.contains("sourceRepository.loadAllSessionHandles()"))
        assertTrue(receiver.contains("sourceRepository.sessionManager.resolve(handle)"))
        assertFalse(receiver.contains("SteamSessionRefreshService"))
        assertFalse(receiver.contains("SteamAccountRepository("))
        assertTrue(sources.contains("suspend fun loadAllSessionHandles()"))
        assertTrue(sources.contains("SteamStorageSource.Mdbx(database.id)"))
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
