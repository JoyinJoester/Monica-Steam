package takagi.ru.monica.steam.friends.chat.background.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.chat.data.SteamFriendChatRealtimeService
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

/** Owns notification listening only for this app process; never starts an Android service. */
object SteamChatNotificationRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        retireLegacyConnection(appContext)
        if (SteamChatBackgroundPreferences(appContext).settings.first().enabled) start(appContext)
        else stop(appContext)
    }

    @Synchronized
    fun start(context: Context): Boolean = try {
        val appContext = context.applicationContext
        retireLegacyConnection(appContext)
        if (monitorJob?.isActive != true) {
            val preferences = SteamChatBackgroundPreferences(appContext)
            val sourceRepository = SteamAccountSourceRepository.get(appContext)
            val targets = preferences.settings.combine(sourceRepository.state) { settings, state ->
                val selected = state.accounts.firstOrNull { it.id == state.selectedAccountId }
                SteamChatNotificationTarget(settings.enabled, selected?.let {
                    sourceRepository.sessionHandleForSource(it, state.storageSource)
                })
            }.distinctUntilChangedBy { it.enabled to it.handle?.stableKey }
            val monitor = SteamChatNotificationMonitor(
                appContext, targets,
                events = { handle ->
                    SteamFriendChatRealtimeService(sessionResolver = SteamAccountSessionResolver { _, forceRefresh ->
                        sourceRepository.sessionManager.resolve(handle, forceRefresh).account
                    }).events(handle.account)
                },
                observeAccount = { SteamVoiceCallRuntime.get(appContext).observeAccount(it.account) }
            )
            monitorJob = scope.launch { monitor.run() }
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SteamDiagLogger.append("chat_notification_start failed type=${error::class.java.simpleName}")
        false
    }

    @Synchronized
    fun stop(context: Context) {
        monitorJob?.cancel()
        monitorJob = null
        retireLegacyConnection(context.applicationContext)
    }

    private fun retireLegacyConnection(context: Context) {
        // An upgrade must remove an old running component as well as its notification.
        context.stopService(Intent().setClassName(context.packageName,
            "takagi.ru.monica.steam.friends.chat.background.data.SteamChatBackgroundService"))
        SteamChatNotificationPublisher(context).retireLegacyConnectionNotifications()
    }
}
