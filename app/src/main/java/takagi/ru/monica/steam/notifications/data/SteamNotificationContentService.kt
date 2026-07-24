package takagi.ru.monica.steam.notifications.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.data.SteamFriendsParser
import takagi.ru.monica.steam.market.SteamInventoryItem
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.notifications.domain.SteamNotification
import takagi.ru.monica.steam.notifications.domain.SteamNotificationAppContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationDetailParser
import takagi.ru.monica.steam.notifications.domain.SteamNotificationActorContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationInventoryReference
import takagi.ru.monica.steam.notifications.domain.SteamNotificationItemContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationKind
import takagi.ru.monica.steam.notifications.domain.SteamNotificationSnapshot
import takagi.ru.monica.steam.store.data.SteamStoreCache
import takagi.ru.monica.steam.store.data.SteamStoreService
import takagi.ru.monica.steam.store.domain.SteamStoreDetail

class SteamNotificationContentService(
    private val storeService: SteamStoreService,
    private val storeCache: SteamStoreCache,
    private val api: SteamApiClient = SteamApiClient(),
    private val inventoryService: SteamInventoryService = SteamInventoryService(api)
) {
    suspend fun enrich(
        account: SteamAccount,
        snapshot: SteamNotificationSnapshot,
        cachedSnapshot: SteamNotificationSnapshot? = null
    ): SteamNotificationSnapshot = coroutineScope {
        val appIdsByNotification = snapshot.notifications.associate { notification ->
            notification.id to notificationAppIds(notification)
        }
        val detailsByNotification = snapshot.notifications.associate { notification ->
            notification.id to notificationDetails(notification)
        }
        val cachedContent = cachedSnapshot?.notifications.orEmpty()
            .flatMap(SteamNotification::appContent)
            .associateBy(SteamNotificationAppContent::appId)
        val cachedActors = cachedSnapshot?.notifications.orEmpty()
            .mapNotNull { it.actorContent }
            .associateBy(SteamNotificationActorContent::steamId)
        val cachedItems = cachedSnapshot?.notifications.orEmpty()
            .mapNotNull { content -> content.itemContent?.let { content.id to it } }
            .toMap()
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
        val actorSteamIds = detailsByNotification.values.mapNotNull { it.actorSteamId }.distinct()
        val resolvedActors = cachedActors + resolveActors(account, actorSteamIds)
        val resolvedItems = detailsByNotification.mapNotNull { (notificationId, details) ->
            val reference = details.inventoryReference ?: return@mapNotNull null
            val item = runCatching { resolveItem(account, reference) }.getOrNull()
                ?: cachedItems[notificationId]
            item?.let { notificationId to it }
        }.toMap()

        enrichSteamNotificationSnapshot(
            snapshot = snapshot,
            appIdsByNotification = appIdsByNotification,
            resolvedContent = resolvedContent,
            resolvedActors = resolvedActors,
            resolvedItems = resolvedItems
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
        notificationDetails(notification)
            .appIds

    private fun notificationDetails(notification: SteamNotification) =
        SteamNotificationDetailParser.parse(
            bodyData = notification.bodyData,
            title = notification.title,
            summary = notification.summary,
            kind = notification.kind
        )

    private fun resolveActors(
        account: SteamAccount,
        steamIds: List<String>
    ): Map<String, SteamNotificationActorContent> {
        if (steamIds.isEmpty()) return emptyMap()
        val accessToken = account.accessToken?.takeIf(String::isNotBlank) ?: return emptyMap()
        return steamIds.chunked(MAX_PROFILE_BATCH).flatMap { ids ->
            runCatching {
                val payload = api.steamApiGetJson(
                    path = "/ISteamUserOAuth/GetUserSummaries/v1/",
                    query = mapOf("steamids" to ids.joinToString(",")),
                    accessToken = accessToken
                )
                SteamFriendsParser.parseProfiles(payload)
            }.getOrDefault(emptyList())
        }.associate { profile ->
            profile.steamId to SteamNotificationActorContent(
                steamId = profile.steamId,
                displayName = profile.personaName.ifBlank { profile.steamId },
                avatarUrl = profile.avatarUrl,
                profileUrl = profile.profileUrl
            )
        }
    }

    private fun resolveItem(
        account: SteamAccount,
        reference: SteamNotificationInventoryReference
    ): SteamNotificationItemContent? {
        var startAssetId: String? = null
        repeat(MAX_ITEM_PAGES) {
            val page = inventoryService.fetchItems(
                account = account,
                appId = reference.appId,
                contextId = reference.contextId,
                language = notificationLanguage(),
                startAssetId = startAssetId,
                count = MAX_ITEMS_PER_PAGE
            )
            page.items.firstOrNull { it.assetId == reference.assetId }?.let {
                return it.toNotificationContent()
            }
            if (!page.hasMore || page.lastAssetId.isNullOrBlank()) return null
            startAssetId = page.lastAssetId
        }
        return null
    }

    private fun notificationLanguage(): String =
        if (Locale.getDefault().language.startsWith("zh")) "schinese" else "english"

    private fun SteamInventoryItem.toNotificationContent() = SteamNotificationItemContent(
        appId = appId,
        contextId = contextId,
        assetId = assetId,
        name = name.ifBlank { marketHashName }.ifBlank { "Steam inventory item" },
        type = type,
        iconUrl = iconUrl,
        marketable = marketable,
        tradable = tradable
    )

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
    resolvedContent: Map<Int, SteamNotificationAppContent>,
    resolvedActors: Map<String, SteamNotificationActorContent> = emptyMap(),
    resolvedItems: Map<String, SteamNotificationItemContent> = emptyMap()
): SteamNotificationSnapshot = snapshot.copy(
    notifications = snapshot.notifications.map { notification ->
        notification.copy(
            appContent = appIdsByNotification[notification.id]
                .orEmpty()
                .mapNotNull(resolvedContent::get),
            actorContent = notification.bodyData
                .takeIf { notification.kind == SteamNotificationKind.FRIEND_INVITE }
                ?.let {
                    SteamNotificationDetailParser.parse(
                        bodyData = it,
                        title = notification.title,
                        summary = notification.summary,
                        kind = notification.kind
                    ).actorSteamId
                }
                ?.let(resolvedActors::get),
            itemContent = resolvedItems[notification.id]
        )
    }
)

private const val MAX_PROFILE_BATCH = 100
private const val MAX_ITEM_PAGES = 3
private const val MAX_ITEMS_PER_PAGE = 2000
