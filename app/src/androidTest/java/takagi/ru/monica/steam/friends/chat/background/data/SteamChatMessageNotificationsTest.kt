package takagi.ru.monica.steam.friends.chat.background.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.FileOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationDecision
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPolicy
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin

/** Real NotificationManager records, isolated from saved accounts and network access. */
class SteamChatMessageNotificationsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val publisher = SteamChatNotificationPublisher(context)
    private val handle = SteamAccountSessionHandle(
        SteamAccount(1, "76561198000000001", "Fixture account", "Fixture account", "", "",
            null, null, null, null, null, null, "{}", false, 0, 0, 0),
        SteamAccountSessionOrigin(SteamStorageSource.Local)
    )
    private val firstPartner = "76561198000000002"
    private val secondPartner = "76561198000000003"
    // A repeated instrumentation run must not reuse messages already claimed in DataStore.
    private val fixtureTimestamp = System.currentTimeMillis() / 1000

    @Before fun prepare() {
        check(context.packageName == "takagi.ru.monica.steamapp.messagenotifications")
        manager.cancelAll()
        await { manager.activeNotifications.isEmpty() }
        assertTrue(publisher.canPostMessageNotifications())
    }

    @After fun clean() {
        manager.cancelAll()
        await { manager.activeNotifications.isEmpty() }
    }

    @Test fun consecutiveMessagesKeepSeparateContentAndCanBeRemovedIndividually() {
        post(handle, message(1, "First independent message"))
        post(handle, message(2, "Second independent message"))
        await { messages().size == 2 }
        val active = messages()
        assertEquals(2, active.map { it.tag }.distinct().size)
        val contents = active.map { sbn ->
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)!!
            assertEquals(1, style.messages.size)
            style.messages.single().text.toString()
        }.toSet()
        assertEquals(setOf("First independent message", "Second independent message"), contents)
        val first = active.first { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == "First independent message" }
        manager.cancel(first.tag, first.id)
        await { messages().size == 1 }
        assertEquals("Second independent message", messages().single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun replayUsesPersistentDedupAndDoesNotResurrectDismissedMessage() = runBlocking {
        val message = message(3, "Replay fixture", timestamp = System.currentTimeMillis() / 1000)
        val decision = decision(handle, message)
        val preferences = SteamChatBackgroundPreferences(context)
        assertTrue(preferences.claimNotification(decision.identity))
        assertTrue(publisher.publishIncomingMessage(handle, message, decision.preview))
        await { messages().size == 1 }
        val active = messages().single()
        // A direct replay also has the same Android address; it cannot add a child.
        assertTrue(publisher.publishIncomingMessage(handle, message, decision.preview))
        await { messages().size == 1 }
        manager.cancel(active.tag, active.id)
        await { messages().isEmpty() }
        assertFalse(SteamChatBackgroundPreferences(context).claimNotification(decision.identity))
        assertTrue(messages().isEmpty())
    }

    @Test fun recreatedPublisherCancelsOnlyTheExactSourceAndConversationIncludingLegacy() {
        val mdbx = handle.copy(origin = SteamAccountSessionOrigin(SteamStorageSource.Mdbx(9), "fixture-entry"))
        post(handle, message(1, "Local one"))
        post(handle, message(2, "Local two"))
        post(handle, message(1, "Other friend", secondPartner))
        post(mdbx, message(1, "Other storage"))
        val legacy = steamChatNotificationAddress(handle.stableKey, firstPartner)
        manager.notify(legacy.tag, legacy.id, simpleNotification("Legacy"))
        await { manager.activeNotifications.size == 5 }
        SteamChatNotificationPublisher(context).cancelConversation(handle.stableKey, firstPartner)
        await { manager.activeNotifications.size == 2 }
        assertEquals(setOf("Other friend", "Other storage"), messages().map {
            it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        }.toSet())
        assertNotEquals(messages()[0].notification.contentIntent, messages()[1].notification.contentIntent)
    }

    @Test fun eachChildKeepsPrivatePublicVersionAndImmutableConversationRoute() {
        post(handle, message(1, "Private fixture body"))
        await { messages().size == 1 }
        val notification = messages().single().notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals(Notification.CATEGORY_MESSAGE, notification.category)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(notification.contentIntent.isImmutable)
        assertEquals(SteamChatNotificationContract.openConversationPendingIntent(context, handle, firstPartner), notification.contentIntent)
        assertNotEquals(SteamChatNotificationContract.openConversationPendingIntent(context, handle, secondPartner), notification.contentIntent)
        assertNotNull(notification.publicVersion)
        val publicText = notification.publicVersion.extras.toString()
        assertFalse(publicText.contains("Private fixture body"))
        assertFalse(publicText.contains(firstPartner))
        assertFalse(publicText.contains("Fixture account"))
    }

    @Test fun oldestMessagesAreBoundedWithoutRemovingOtherNotifications() {
        manager.notify("unrelated-fixture", 7, simpleNotification("Unrelated"))
        for (ordinal in 1..SteamChatNotificationPublisher.MAX_ACTIVE_MESSAGES + 2) {
            post(handle, message(ordinal, "Bounded $ordinal"))
            await { messages().any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == "Bounded $ordinal" } }
            SystemClock.sleep(230)
        }
        assertEquals(SteamChatNotificationPublisher.MAX_ACTIVE_MESSAGES, messages().size)
        val text = messages().map { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() }.toSet()
        assertFalse(text.contains("Bounded 1"))
        assertFalse(text.contains("Bounded 2"))
        assertTrue(text.contains("Bounded 42"))
        assertFalse(manager.activeNotifications.any { it.id == SteamChatNotificationPublisher.LEGACY_SERVICE_NOTIFICATION_ID })
        assertTrue(manager.activeNotifications.any { it.tag == "unrelated-fixture" })
    }

    @Test fun systemShadeShowsIndependentMessageNotifications() {
        post(handle, message(1, "First independent message"))
        post(handle, message(2, "Second independent message"))
        await { messages().size == 2 }
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        SystemClock.sleep(1200)
        val output = File(context.filesDir, "notification-shade.png")
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        FileOutputStream(output).use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
    }

    @Test fun upgradeRemovesBothConnectionChannelsAndKeepsMessagesAndChannelSettings() {
        post(handle, message(71, "Existing friend message"))
        await { messages().size == 1 }
        val before = manager.getNotificationChannel("steam_chat_messages")
        for (id in SteamChatNotificationPublisher.LEGACY_SERVICE_CHANNEL_IDS) {
            manager.createNotificationChannel(NotificationChannel(id, "Old connection fixture", NotificationManager.IMPORTANCE_LOW))
        }
        manager.notify(SteamChatNotificationPublisher.LEGACY_SERVICE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, SteamChatNotificationPublisher.LEGACY_SERVICE_CHANNEL_IDS.first())
                .setSmallIcon(R.drawable.ic_steam_chat_notification).setContentText("Connecting fixture").setOngoing(true).build())
        await { manager.activeNotifications.size == 2 }
        // Boot cannot restore the retired keepalive or touch existing notifications.
        SteamChatNotificationUpgradeReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(2, manager.activeNotifications.size)
        SteamChatNotificationUpgradeReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        await { manager.activeNotifications.size == 1 }
        SteamChatNotificationPublisher.LEGACY_SERVICE_CHANNEL_IDS.forEach { assertNull(manager.getNotificationChannel(it)) }
        val after = manager.getNotificationChannel("steam_chat_messages")
        assertEquals(before.importance, after.importance)
        assertEquals(before.sound, after.sound)
        assertEquals(before.shouldVibrate(), after.shouldVibrate())
        assertEquals("Existing friend message", messages().single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun installedPackageHasNoChatKeepaliveServiceOrBootReceiver() {
        val info = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.GET_RECEIVERS)
        assertFalse(info.services.orEmpty().any { it.name.endsWith("SteamChatBackgroundService") })
        assertFalse(info.receivers.orEmpty().any { it.name.endsWith("SteamChatBackgroundReceiver") })
        assertTrue(info.receivers.orEmpty().any { it.name.endsWith("SteamChatNotificationUpgradeReceiver") })
        assertTrue(info.services.orEmpty().any { it.name.endsWith("SteamVoiceCallService") })
    }

    @Test fun waitingConnectionReconnectAndTypingStaySilentAndOnlyIncomingMessageNotifies() = runBlocking {
        val targets = MutableStateFlow(SteamChatNotificationTarget(true, null))
        val events = MutableSharedFlow<SteamChatRealtimeEvent>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch { SteamChatNotificationMonitor(context, targets, { events }, {}).run() }
        try {
            SystemClock.sleep(150)
            assertTrue(manager.activeNotifications.isEmpty())
            assertEquals(0, events.subscriptionCount.value)
            targets.value = SteamChatNotificationTarget(true, handle)
            await { events.subscriptionCount.value == 1 }
            events.emit(SteamChatRealtimeEvent.ConnectionChanged(false))
            events.emit(SteamChatRealtimeEvent.ConnectionChanged(true))
            events.emit(SteamChatRealtimeEvent.ConnectionChanged(false))
            events.emit(SteamChatRealtimeEvent.Typing(firstPartner, false))
            // The following message also serves as a barrier for earlier silent events.
            val incoming = message(81, "Only friend messages notify")
            events.emit(SteamChatRealtimeEvent.Message(incoming))
            await { messages().size == 1 }
            assertEquals(1, manager.activeNotifications.size)
            assertEquals("Only friend messages notify", messages().single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            val active = messages().single()
            manager.cancel(active.tag, active.id)
            await { messages().isEmpty() }
            events.emit(SteamChatRealtimeEvent.Message(incoming))
            events.emit(SteamChatRealtimeEvent.ConnectionChanged(true))
            assertTrue(manager.activeNotifications.isEmpty())
            events.emit(SteamChatRealtimeEvent.Message(message(82, "My outgoing message").copy(senderSteamId = handle.account.steamId)))
            events.emit(SteamChatRealtimeEvent.ConnectionChanged(true))
            assertTrue(manager.activeNotifications.isEmpty())
        } finally {
            job.cancelAndJoin()
            scope.cancel()
        }
    }

    @Test fun disableAndSourceSwitchCancelOldListenerAndKeepNotificationsSourceBound() = runBlocking {
        val other = handle.copy(origin = SteamAccountSessionOrigin(SteamStorageSource.Mdbx(99), "notification-fixture"))
        val targets = MutableStateFlow(SteamChatNotificationTarget(true, handle))
        val localEvents = MutableSharedFlow<SteamChatRealtimeEvent>()
        val otherEvents = MutableSharedFlow<SteamChatRealtimeEvent>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch { SteamChatNotificationMonitor(context, targets,
            { if (it.stableKey == handle.stableKey) localEvents else otherEvents }, {}).run() }
        try {
            await { localEvents.subscriptionCount.value == 1 }
            localEvents.emit(SteamChatRealtimeEvent.Message(message(91, "Local message")))
            await { messages().size == 1 }
            targets.value = SteamChatNotificationTarget(true, other)
            await { localEvents.subscriptionCount.value == 0 && otherEvents.subscriptionCount.value == 1 }
            localEvents.emit(SteamChatRealtimeEvent.Message(message(92, "Stale source")))
            otherEvents.emit(SteamChatRealtimeEvent.Message(message(91, "Other source message")))
            await { messages().size == 2 }
            assertEquals(setOf("Local message", "Other source message"), messages().map { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() }.toSet())
            otherEvents.emit(SteamChatRealtimeEvent.Acknowledged(firstPartner, 1_800_000_000))
            await { messages().size == 1 }
            assertEquals("Local message", messages().single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            targets.value = SteamChatNotificationTarget(false, other)
            await { otherEvents.subscriptionCount.value == 0 }
            otherEvents.emit(SteamChatRealtimeEvent.Message(message(93, "Disabled message")))
            assertEquals(1, messages().size)
        } finally {
            job.cancelAndJoin()
            scope.cancel()
        }
    }

    @Test fun reconnectAfterGatewayFailureDoesNotPostAConnectionNotification() = runBlocking {
        val attempts = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch {
            SteamChatNotificationMonitor(context, MutableStateFlow(SteamChatNotificationTarget(true, handle)), {
                flow {
                    if (attempts.incrementAndGet() == 1) throw IllegalStateException("Synthetic gateway failure")
                    emit(SteamChatRealtimeEvent.ConnectionChanged(true))
                    emit(SteamChatRealtimeEvent.Message(message(101, "Message after reconnect")))
                    kotlinx.coroutines.awaitCancellation()
                }
            }, {}).run()
        }
        try {
            await { attempts.get() == 1 }
            assertTrue(manager.activeNotifications.isEmpty())
            val deadline = SystemClock.uptimeMillis() + 9000
            while (messages().isEmpty() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
            assertEquals(2, attempts.get())
            assertEquals(1, manager.activeNotifications.size)
            assertEquals("Message after reconnect", messages().single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        } finally {
            job.cancelAndJoin()
            scope.cancel()
        }
    }

    private fun message(ordinal: Int, body: String, partner: String = firstPartner, timestamp: Long = fixtureTimestamp) =
        SteamChatMessage(partner, partner, timestamp, ordinal, body)

    private fun decision(target: SteamAccountSessionHandle, message: SteamChatMessage) =
        SteamChatNotificationPolicy.evaluate(target.stableKey, target.account.steamId, message) as SteamChatNotificationDecision.Notify

    private fun post(target: SteamAccountSessionHandle, message: SteamChatMessage) {
        assertTrue(publisher.publishIncomingMessage(target, message, decision(target, message).preview))
    }

    private fun messages() = manager.activeNotifications.filter { it.tag?.startsWith(MESSAGE_NOTIFICATION_TAG_PREFIX) == true }

    private fun simpleNotification(text: String) = NotificationCompat.Builder(context, "steam_chat_messages")
        .setSmallIcon(R.drawable.ic_steam_chat_notification).setContentTitle(text).setContentText(text).build()

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
        assertTrue("NotificationManager did not reach the expected state", condition())
    }
}
