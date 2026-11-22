package takagi.ru.monica.steam.friends.chat.background.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationDecision
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPolicy
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

internal data class SteamChatNotificationTarget(val enabled: Boolean, val handle: SteamAccountSessionHandle?)

/** Connection state stays inside the process. Only a validated incoming message can notify. */
internal class SteamChatNotificationMonitor(
    context: Context,
    private val targets: Flow<SteamChatNotificationTarget>,
    private val events: (SteamAccountSessionHandle) -> Flow<SteamChatRealtimeEvent>,
    private val observeAccount: (SteamAccountSessionHandle) -> Unit
) {
    private val preferences = SteamChatBackgroundPreferences(context)
    private val notificationPublisher = SteamChatNotificationPublisher(context)

    suspend fun run() {
        targets.collectLatest { target ->
            val handle = target.handle
            if (target.enabled && handle != null) monitor(handle)
        }
    }

    private suspend fun monitor(handle: SteamAccountSessionHandle) {
        observeAccount(handle)
        while (currentCoroutineContext().isActive) {
            try {
                events(handle).collect { event ->
                    when (event) {
                        is SteamChatRealtimeEvent.ConnectionChanged,
                        is SteamChatRealtimeEvent.ReactionChanged,
                        is SteamChatRealtimeEvent.Typing -> Unit
                        is SteamChatRealtimeEvent.Message -> processMessage(handle, event)
                        is SteamChatRealtimeEvent.Acknowledged -> notificationPublisher.cancelConversation(handle.stableKey, event.partnerSteamId)
                        is SteamChatRealtimeEvent.ConversationLeft -> notificationPublisher.cancelConversation(handle.stableKey, event.partnerSteamId)
                    }
                }
                delay(RESTART_DELAY_MILLIS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SteamDiagLogger.append("chat_notification_monitor failed account=${handle.stableKey.hashCode()} type=${error::class.java.simpleName}")
                delay(RESTART_DELAY_MILLIS)
            }
        }
    }

    private suspend fun processMessage(handle: SteamAccountSessionHandle, event: SteamChatRealtimeEvent.Message) {
        if (event.message.senderSteamId == handle.account.steamId) {
            notificationPublisher.cancelConversation(handle.stableKey, event.message.partnerSteamId)
            return
        }
        val decision = SteamChatNotificationPolicy.evaluate(handle.stableKey, handle.account.steamId, event.message)
        if (decision !is SteamChatNotificationDecision.Notify) return
        if (!preferences.claimNotification(decision.identity)) return
        var published = false
        try {
            currentCoroutineContext().ensureActive()
            published = notificationPublisher.publishIncomingMessage(handle, event.message, decision.preview)
        } finally {
            if (!published) withContext(NonCancellable) {
                preferences.releaseNotification(decision.identity)
            }
        }
        if (!published) SteamDiagLogger.append("chat_notification_notify failed account=${handle.stableKey.hashCode()} partner=${event.message.partnerSteamId.hashCode()}")
    }

    private companion object { const val RESTART_DELAY_MILLIS = 5_000L }
}
