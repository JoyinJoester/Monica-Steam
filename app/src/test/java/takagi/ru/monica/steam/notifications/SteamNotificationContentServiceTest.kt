package takagi.ru.monica.steam.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.notifications.data.enrichSteamNotificationSnapshot
import takagi.ru.monica.steam.notifications.domain.SteamNotification
import takagi.ru.monica.steam.notifications.domain.SteamNotificationAppContent
import takagi.ru.monica.steam.notifications.domain.SteamNotificationKind
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
}
