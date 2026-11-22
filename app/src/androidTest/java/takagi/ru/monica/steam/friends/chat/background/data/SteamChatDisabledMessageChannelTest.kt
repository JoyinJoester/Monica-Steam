package takagi.ru.monica.steam.friends.chat.background.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationDecision
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPolicy
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin

/** Run separately after the normal suite, then uninstall this isolated fixture package. */
class SteamChatDisabledMessageChannelTest {
    @Test fun disabledMessageChannelStaysDisabledAndFailedPostReleasesClaim() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "takagi.ru.monica.steamapp.messagenotifications")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val publisher = SteamChatNotificationPublisher(context)
        assertTrue(publisher.canPostMessageNotifications())
        manager.createNotificationChannel(NotificationChannel("steam_chat_messages", "Disabled fixture", NotificationManager.IMPORTANCE_NONE))
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("steam_chat_messages").importance)
        // Cleanup must retain a disabled message channel, even when it removes old channels.
        publisher.retireLegacyConnectionNotifications()
        assertFalse(publisher.canPostMessageNotifications())
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("steam_chat_messages").importance)
        val handle = SteamAccountSessionHandle(
            SteamAccount(1, "76561198000000001", "Fixture", "Fixture", "", "", null, null,
                null, null, null, null, "{}", false, 0, 0, 0),
            SteamAccountSessionOrigin(SteamStorageSource.Local))
        val message = SteamChatMessage("76561198000000002", "76561198000000002", 1_800_000_000, 201, "Disabled channel message")
        val decision = SteamChatNotificationPolicy.evaluate(handle.stableKey, handle.account.steamId, message) as SteamChatNotificationDecision.Notify
        val events = MutableSharedFlow<SteamChatRealtimeEvent>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch { SteamChatNotificationMonitor(context,
            MutableStateFlow(SteamChatNotificationTarget(true, handle)), { events }, {}).run() }
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (events.subscriptionCount.value == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
            assertEquals(1, events.subscriptionCount.value)
            events.emit(SteamChatRealtimeEvent.Message(message))
            events.emit(SteamChatRealtimeEvent.ConnectionChanged(true))
            assertTrue(manager.activeNotifications.isEmpty())
            val preferences = SteamChatBackgroundPreferences(context)
            assertTrue("A rejected notification must remain eligible for replay", preferences.claimNotification(decision.identity))
            preferences.releaseNotification(decision.identity)
            assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("steam_chat_messages").importance)
        } finally {
            job.cancelAndJoin()
            scope.cancel()
            manager.cancelAll()
        }
    }
}
