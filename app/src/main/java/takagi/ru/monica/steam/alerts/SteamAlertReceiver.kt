package takagi.ru.monica.steam.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.network.SteamAuthorizedDeviceService
import takagi.ru.monica.steam.network.SteamConfirmationService
import takagi.ru.monica.steam.network.SteamSessionRefreshService
import takagi.ru.monica.steam.notifications.SteamNotificationService

class SteamAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                checkAlerts(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkAlerts(context: Context) {
        val preferences = SteamAlertPreferences(context)
        val settings = preferences.settings.first()
        if (!settings.enabled) return

        val database = SteamDatabase.getDatabase(context)
        val repository = SteamAccountRepository(
            database.steamAccountDao(),
            SecurityManager(context)
        )
        val accounts = repository.observeAccounts().first()
        val sessionService = SteamSessionRefreshService()
        val confirmationService = SteamConfirmationService()
        val deviceService = SteamAuthorizedDeviceService()
        val notificationService = SteamNotificationService()
        val usableAccounts = mutableListOf<SteamAccount>()
        var sessionIssues = 0

        accounts.forEach { account ->
            val refreshed = if (
                settings.sessionEnabled &&
                sessionService.shouldRefresh(account)
            ) {
                sessionService.refreshIfNeeded(account)?.also { result ->
                    repository.updateSessionTokens(
                        id = account.id,
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                        steamLoginSecure = "${account.steamId}||${result.accessToken}"
                    )
                }?.let { result ->
                    account.copy(
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken ?: account.refreshToken,
                        steamLoginSecure = "${account.steamId}||${result.accessToken}"
                    )
                }
            } else {
                account
            }
            if (refreshed == null) {
                sessionIssues++
            } else if (refreshed.accessToken.isNullOrBlank() && refreshed.refreshToken.isNullOrBlank()) {
                sessionIssues++
            } else {
                usableAccounts += refreshed
            }
        }

        var unreadNotifications = 0
        if (settings.notificationsEnabled) {
            usableAccounts.filter { it.hasRealSteamId && !it.accessToken.isNullOrBlank() }
                .forEach { account ->
                    runCatching { notificationService.fetch(account) }
                        .onSuccess { snapshot ->
                            unreadNotifications += maxOf(
                                snapshot.unreadCount,
                                snapshot.pendingGiftCount +
                                    snapshot.pendingFriendCount +
                                    snapshot.pendingFamilyInviteCount
                            )
                        }
                }
        }

        var pendingConfirmations = 0
        if (settings.confirmationsEnabled) {
            usableAccounts.filter {
                it.canUseConfirmations && !it.accessToken.isNullOrBlank()
            }.forEach { account ->
                pendingConfirmations += runCatching {
                    confirmationService.fetch(account).size
                }.getOrDefault(0)
            }
        }

        var deviceCount = 0
        var deviceChecksSucceeded = true
        if (settings.devicesEnabled) {
            usableAccounts.filter { it.hasRealSteamId && !it.accessToken.isNullOrBlank() }
                .forEach { account ->
                    runCatching { deviceService.fetch(account).size }
                        .onSuccess { deviceCount += it }
                        .onFailure { deviceChecksSucceeded = false }
                }
        }

        var stalePriceCaches = 0
        if (settings.pricesEnabled) {
            val now = System.currentTimeMillis()
            accounts.forEach { account ->
                val cache = database.steamLibraryCacheDao().getLibrary(account.id)
                if (cache != null && now - cache.fetchedAt >= SteamAlertPolicy.PRICE_STALE_MS) {
                    stalePriceCaches++
                }
            }
        }

        val decision = SteamAlertPolicy.evaluate(
            settings = settings,
            observation = SteamAlertObservation(
                unreadNotifications = unreadNotifications,
                pendingConfirmations = pendingConfirmations,
                sessionIssues = sessionIssues,
                authorizedDeviceCount = deviceCount.takeIf {
                    settings.devicesEnabled && deviceChecksSucceeded
                },
                stalePriceCaches = stalePriceCaches
            )
        )
        val now = System.currentTimeMillis()
        val shouldNotify = SteamAlertPolicy.shouldNotify(settings, decision, now)
        if (shouldNotify) {
            SteamAlertNotifier.show(context, decision.kinds)
        }
        preferences.recordDecision(decision, now.takeIf { shouldNotify })
    }
}
