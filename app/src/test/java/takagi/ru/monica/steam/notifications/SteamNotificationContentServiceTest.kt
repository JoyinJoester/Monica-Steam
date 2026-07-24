package takagi.ru.monica.steam.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.notifications.data.enrichSteamNotificationSnapshot
import takagi.ru.monica.steam.notifications.domain.SteamNotification
import takagi.ru.monica.steam.notifications.domain.SteamNotificationAppContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationActorContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationKind
import takagi.ru.monica.steam.notifications.domain.SteamNotificationItemContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationSnapshot

class SteamNotificationContentServiceTest {
    @Test
    fun resolvedWishlistAppReplacesTechnicalMetadataWithGameContent() {
        val notification = SteamNotification(
            id = "wishlist-1",
            type = 8,
            kind = SteamNotificationKind.WISHLIST,
            title = "Wishlist update",
            summary = "",
            bodyData = """{"appid":430960,"count":1}"""
        )
        val content = SteamNotificationAppContent(
            appId = 430960,
            name = "Wallpaper Engine",
            description = "Use live wallpapers on your desktop.",
            imageUrl = "https://cdn.example/430960.jpg",
            formattedInitialPrice = "¥ 18.00",
            formattedFinalPrice = "¥ 14.40",
            discountPercent = 20
        )

        val enriched = enrichSteamNotificationSnapshot(
            snapshot = SteamNotificationSnapshot(notifications = listOf(notification)),
            appIdsByNotification = mapOf(notification.id to listOf(430960)),
            resolvedContent = mapOf(content.appId to content)
        )

        assertEquals(listOf(content), enriched.notifications.single().appContent)
        assertTrue(enriched.notifications.single().bodyData.contains("appid"))
    }

    @Test
    fun resolvedFriendActorAndInventoryItemAreAttachedToTheirNotifications() {
        val friend = SteamNotification(
            id = "friend-1",
            type = 5,
            kind = SteamNotificationKind.FRIEND_INVITE,
            title = "Friend invitation",
            summary = "",
            bodyData = """{"requestor_id":1487451525,"state":2}"""
        )
        val item = SteamNotification(
            id = "item-1",
            type = 4,
            kind = SteamNotificationKind.ITEM,
            title = "New item",
            summary = "",
            bodyData = """{"appid":753,"contextid":"6","assetid":"123"}"""
        )
        val actor = SteamNotificationActorContent(
            steamId = "76561199447717253",
            displayName = "Alice"
        )
        val inventoryItem = SteamNotificationItemContent(
            appId = 753,
            contextId = "6",
            assetId = "123",
            name = "Community Badge"
        )

        val enriched = enrichSteamNotificationSnapshot(
            snapshot = SteamNotificationSnapshot(notifications = listOf(friend, item)),
            appIdsByNotification = mapOf("friend-1" to emptyList(), "item-1" to emptyList()),
            resolvedContent = emptyMap(),
            resolvedActors = mapOf(actor.steamId to actor),
            resolvedItems = mapOf(item.id to inventoryItem)
        )

        assertEquals(actor, enriched.notifications[0].actorContent)
        assertEquals(inventoryItem, enriched.notifications[1].itemContent)
    }
}
