package takagi.ru.monica.steam.friends.chat.background

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatBackgroundIntegrationGuardTest {
    @Test
    fun manifestRemovesChatKeepaliveAndOnlyCleansUpOnUpgrade() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("SteamChatBackgroundService"))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"))
        assertFalse(manifest.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
        assertTrue(manifest.contains("SteamChatNotificationUpgradeReceiver"))
        val receiver = manifest.substringAfter("SteamChatNotificationUpgradeReceiver").substringBefore("</receiver>")
        assertTrue(receiver.contains("MY_PACKAGE_REPLACED"))
        assertFalse(receiver.contains("BOOT_COMPLETED"))
        assertTrue(manifest.contains("microphone|mediaPlayback"))
    }

    @Test
    fun runtimeUsesSelectedSourceAndMonitorKeepsPolicyAndPersistentDedup() {
        val runtime = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationRuntime.kt").readText()
        val monitor = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationMonitor.kt").readText()
        val sources = projectFile("app/src/main/java/takagi/ru/monica/steam/data/SteamAccountSourceRepository.kt").readText()
        assertTrue(runtime.contains("combine(sourceRepository.state)"))
        assertTrue(runtime.contains("sessionHandleForSource"))
        assertTrue(runtime.contains("SteamFriendChatRealtimeService"))
        assertFalse(runtime.contains("loadAllSessionHandles"))
        assertTrue(monitor.contains("collectLatest"))
        assertTrue(monitor.contains("SteamChatNotificationPolicy.evaluate"))
        assertTrue(monitor.contains("preferences.claimNotification"))
        assertTrue(sources.contains("current.storageSource != source"))
        assertTrue(sources.contains("entryId = record.entryId"))
    }

    @Test
    fun settingsAreOptInAndReuseTheSharedMonicaSettingsSurface() {
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/notifications/settings/ui/SteamNotificationSettingsScreen.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val settingsRouter = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatBackgroundPreferences.kt"
        ).readText()

        assertTrue(settings.contains("SettingsSection"))
        assertTrue(settings.contains("SettingsItemWithSwitch"))
        assertTrue(settings.contains("POST_NOTIFICATIONS"))
        assertTrue(settings.contains("SteamChatNotificationRuntime.start"))
        assertTrue(host.contains("additionalSettingsContent"))
        assertTrue(settingsRouter.contains("SteamSettingsChild.NOTIFICATIONS -> SteamNotificationSettingsScreen("))
        assertTrue(preferences.contains("enabled = values[KEY_ENABLED] ?: false"))
        assertTrue(preferences.contains("preferencesDataStore"))
    }

    @Test
    fun messageNotificationsArePrivateDeduplicatedAndOpenTheExactConversation() {
        val publisher = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationPublisher.kt"
        ).readText()
        val contract = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationContract.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val facade = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/SteamChatBackground.kt"
        ).readText()

        assertTrue(publisher.contains("VISIBILITY_PRIVATE"))
        assertTrue(publisher.contains("setPublicVersion(publicVersion)"))
        assertTrue(publisher.contains("openConversationPendingIntent"))
        assertTrue(publisher.contains("MESSAGE_CHANNEL_ID"))
        assertTrue(publisher.contains("retireLegacyConnectionNotifications"))
        assertTrue(publisher.contains("cancelConversation"))
        assertTrue(publisher.contains("steamChatMessageNotificationAddress"))
        assertTrue(publisher.contains("activeMessageNotifications"))
        assertTrue(publisher.contains("notificationManager.notify(address.tag, address.id"))
        assertTrue(contract.contains("handle.origin.entryId"))
        assertTrue(contract.contains("EXTRA_PARTNER_STEAM_ID"))
        assertTrue(activity.contains("override fun onNewIntent"))
        assertTrue(activity.contains("SteamChatBackground.consumeNotification"))
        assertTrue(activity.contains("SteamChatBackground.activateNotificationTarget"))
        assertFalse(activity.contains("friends.chat.background.data."))
        assertFalse(activity.contains("friends.chat.background.domain."))
        assertTrue(facade.contains("activateChatNotificationTarget"))
        assertTrue(activity.contains("currentPage = MonicaSteamPage.CHAT"))
    }

    @Test
    fun applicationRestoresOptInProcessListenerWithoutStartingAService() {
        val application = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamApplication.kt"
        ).readText()
        val facade = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/SteamChatBackground.kt"
        ).readText()
        val controller = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/background/data/SteamChatNotificationRuntime.kt"
        ).readText()

        assertTrue(application.contains("SteamChatBackground.syncNotifications"))
        assertFalse(application.contains("friends.chat.background.data."))
        assertTrue(facade.contains("SteamChatNotificationRuntime.sync"))
        assertTrue(controller.contains("settings.first().enabled"))
        assertFalse(controller.contains("startForegroundService"))
        assertFalse(controller.contains("startService("))
        assertTrue(controller.contains("scope.launch { monitor.run() }"))
        assertTrue(controller.contains("stopService"))
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
