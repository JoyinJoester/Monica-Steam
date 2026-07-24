package takagi.ru.monica.steam.notifications.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.notifications.domain.SteamNotification
import takagi.ru.monica.steam.notifications.domain.SteamNotificationAppContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationDetailParser
import takagi.ru.monica.steam.notifications.domain.SteamNotificationSnapshot
import takagi.ru.monica.steam.store.data.SteamStoreCache
import takagi.ru.monica.steam.store.data.SteamStoreService
import takagi.ru.monica.steam.store.domain.SteamStoreDetail

class SteamNotificationContentService(
    private val storeService: SteamStoreService,
    private val storeCache: SteamStoreCache
) {
    suspend fun enrich(
        account: SteamAccount,
        snapshot: SteamNotificationSnapshot,
        cachedSnapshot: SteamNotificationSnapshot? = null
    ): SteamNotificationSnapshot = coroutineScope {
        val appIdsByNotification = snapshot.notifications.associate { notification ->
            notification.id to notificationAppIds(notification)
        }
        val cachedContent = cachedSnapshot?.notifications.orEmpty()
            .flatMap(SteamNotification::appContent)
            .associateBy(SteamNotificationAppContent::appId)
        val requiredAppIds = appIdsByNotification.values
            .flatten()
            .distinct()
            .take(MAX_RESOLVED_APPS)
        val resolvedContent = requiredAppIds.map { appId ->
            async(Dispatchers.IO) {
                appId to resolveApp(account, appId)
            }
        }.awaitAll().mapNotNull { (appId, content) ->
            (content ?: cachedContent[appId])?.let { appId to it }
        }.toMap()

        enrichSteamNotificationSnapshot(
            snapshot = snapshot,
            appIdsByNotification = appIdsByNotification,
            resolvedContent = resolvedContent
        )
    }

    private fun resolveApp(account: SteamAccount, appId: Int): SteamNotificationAppContent? {
        val detail = storeCache.readDetail(account.id, appId) ?: runCatching {
            storeService.compactDetail(
                appId = appId,
                steamLoginSecure = account.steamLoginSecure,
                accessToken = account.accessToken
            )
        }.getOrNull()?.also { storeCache.writeDetail(account.id, it) }
        return detail?.toNotificationContent()
    }

    private fun notificationAppIds(notification: SteamNotification): List<Int> =
        SteamNotificationDetailParser.parse(
            bodyData = notification.bodyData,
            title = notification.title,
            summary = notification.summary
        ).appIds

    private fun SteamStoreDetail.toNotificationContent() = SteamNotificationAppContent(
        appId = appId,
        name = name,
        description = shortDescription,
        imageUrl = headerImageUrl,
        formattedInitialPrice = formattedInitialPrice,
        formattedFinalPrice = formattedFinalPrice,
        discountPercent = discountPercent,
        availableInAccountRegion = availableInAccountRegion
    )

    private companion object {
        const val MAX_RESOLVED_APPS = 12
    }
}

internal fun enrichSteamNotificationSnapshot(
    snapshot: SteamNotificationSnapshot,
    appIdsByNotification: Map<String, List<Int>>,
    resolvedContent: Map<Int, SteamNotificationAppContent>
): SteamNotificationSnapshot = snapshot.copy(
    notifications = snapshot.notifications.map { notification ->
        notification.copy(
            appContent = appIdsByNotification[notification.id]
                .orEmpty()
                .mapNotNull(resolvedContent::get)
        )
    }
)
