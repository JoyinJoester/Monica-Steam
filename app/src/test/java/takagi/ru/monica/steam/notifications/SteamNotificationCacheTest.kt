package takagi.ru.monica.steam.notifications

import takagi.ru.monica.steam.gifts.domain.*
import takagi.ru.monica.steam.notifications.data.*
import takagi.ru.monica.steam.notifications.domain.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNotificationCacheTest {
    @Test
    fun cachedSnapshotKeepsNotificationsAndPendingGiftsOffline() {
        val original = SteamNotificationSnapshot(
            notifications = listOf(
                SteamNotification(
                    id = "1",
                    type = 2,
                    kind = SteamNotificationKind.GIFT,
                    title = "Portal 2",
                    summary = "Alice",
                    appContent = listOf(
                        SteamNotificationAppContent(
                            appId = 620,
                            name = "Portal 2",
                            imageUrl = "https://cdn.example/620.jpg"
                        )
                    )
                )
            ),
            unreadCount = 1,
            pendingGiftCount = 1,
            pendingGifts = listOf(
                SteamPendingGift(
                    id = "9",
                    name = "Portal 2",
                    actions = setOf(SteamGiftAction.ADD_TO_LIBRARY)
                )
            ),
            fetchedAt = 99L
        )

        val restored = SteamNotificationCacheCodec.decode(
            SteamNotificationCacheCodec.encode(original)
        )!!

        assertEquals(original, restored)
        assertEquals("Portal 2", restored.notifications.single().appContent.single().name)
        assertTrue(SteamGiftAction.ADD_TO_LIBRARY in restored.pendingGifts.single().actions)
    }
}
