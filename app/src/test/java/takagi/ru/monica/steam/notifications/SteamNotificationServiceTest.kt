package takagi.ru.monica.steam.notifications

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient

class SteamNotificationServiceTest {
    @Test
    fun fetchesOfficialNotificationEndpointAndParsesCounters() {
        lateinit var captured: Request
        val payload = """
            {
              "response": {
                "notifications": [{
                  "notification_id": "1234567890123456789",
                  "notification_type": 2,
                  "body_data": "{\"title\":\"Portal 2\",\"gifter_name\":\"Alice\",\"giftid\":\"987\"}",
                  "read": false,
                  "timestamp": 1700000000,
                  "hidden": false,
                  "expiry": 1800000000,
                  "viewed": 0
                }],
                "confirmation_count": 3,
                "pending_gift_count": 1,
                "pending_friend_count": 2,
                "unread_count": 4,
                "pending_family_invite_count": 1
              }
            }
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured = chain.request()
                Response.Builder()
                    .request(captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val snapshot = SteamNotificationService(SteamApiClient(client))
            .fetch(account(), fetchedAt = 42L)

        assertEquals(
            "/ISteamNotificationService/GetSteamNotifications/v1/",
            captured.url.encodedPath
        )
        assertEquals("access-token", captured.url.queryParameter("access_token"))
        assertEquals("false", captured.url.queryParameter("include_hidden"))
        assertEquals("true", captured.url.queryParameter("include_read"))
        assertEquals("false", captured.url.queryParameter("count_only"))
        assertEquals(4, snapshot.unreadCount)
        assertEquals(1, snapshot.pendingGiftCount)
        assertEquals(3, snapshot.confirmationCount)
        assertEquals(42L, snapshot.fetchedAt)
        val notification = snapshot.notifications.single()
        assertEquals("1234567890123456789", notification.id)
        assertEquals(SteamNotificationKind.GIFT, notification.kind)
        assertEquals("Portal 2", notification.title)
        assertEquals("Alice", notification.summary)
        assertEquals("987", notification.relatedId)
        assertFalse(notification.read)
    }

    @Test
    fun parserKeepsUnknownNotificationsAndMalformedBodyData() {
        val snapshot = SteamNotificationParser.parse(
            """{
              "response": {
                "notifications": [{
                  "notification_id": "9",
                  "notification_type": 99,
                  "body_data": "not-json",
                  "read": true,
                  "timestamp": 12
                }]
              }
            }""",
            fetchedAt = 20L
        )

        val notification = snapshot.notifications.single()
        assertEquals(SteamNotificationKind.UNKNOWN, notification.kind)
        assertEquals("Steam notification", notification.title)
        assertEquals("not-json", notification.summary)
        assertTrue(notification.read)
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "steam_user",
        displayName = "steam_user",
        deviceId = "android:test",
        sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
        identitySecret = "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
        revocationCode = "R12345",
        tokenGid = "token-gid",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}
