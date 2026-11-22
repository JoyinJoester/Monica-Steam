package takagi.ru.monica.steam.friends.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot

class SteamChatCacheTest {
    @Test
    fun preferencesCache_encryptsAndIsolatesAccountAndThreadPayloads() {
        val store = FakeStore()
        val cache = SteamChatPreferencesCache(
            store = store,
            encrypt = { "encrypted:${it.reversed()}" },
            decrypt = { it.removePrefix("encrypted:").reversed() }
        )
        val accountA = "76561198000000001"
        val accountB = "76561198000000002"
        val partner = "76561198000000003"
        cache.saveSessions(
            accountA,
            SteamChatSessionsSnapshot(
                accountSteamId = accountA,
                sessions = listOf(SteamChatSession(partnerSteamId = partner, unreadCount = 2)),
                fetchedAt = 100L
            )
        )
        cache.saveThread(
            accountA,
            partner,
            SteamChatThreadSnapshot(
                accountSteamId = accountA,
                partnerSteamId = partner,
                messages = listOf(
                    SteamChatMessage(
                        partnerSteamId = partner,
                        senderSteamId = accountA,
                        timestamp = 10L,
                        ordinal = 1,
                        body = "private message"
                    )
                ),
                moreAvailable = false,
                fetchedAt = 100L
            )
        )

        assertEquals(2, store.values.size)
        assertTrue(store.values.values.all { it.startsWith("encrypted:") })
        assertFalse(store.values.values.any { it.contains("private message") })
        assertEquals(2, cache.loadSessions(accountA)?.unreadCount)
        assertEquals("private message", cache.loadThread(accountA, partner)?.messages?.single()?.body)
        assertEquals(null, cache.loadSessions(accountB))
    }

    @Test
    fun pendingMessageBecomesRetryableAfterProcessRestore() {
        val store = FakeStore()
        val cache = SteamChatPreferencesCache(
            store = store,
            encrypt = { it },
            decrypt = { it }
        )
        val account = "76561198000000001"
        val partner = "76561198000000003"
        cache.saveThread(
            account,
            partner,
            SteamChatThreadSnapshot(
                accountSteamId = account,
                partnerSteamId = partner,
                messages = listOf(
                    SteamChatMessage(
                        partnerSteamId = partner,
                        senderSteamId = account,
                        timestamp = 10L,
                        ordinal = 1,
                        body = "retry me",
                        deliveryState = SteamChatDeliveryState.SENDING,
                        clientMessageId = "client-1"
                    )
                ),
                moreAvailable = false,
                fetchedAt = 100L
            )
        )

        val restored = cache.loadThread(account, partner)

        assertNotNull(restored)
        assertEquals(SteamChatDeliveryState.VERIFYING, restored?.messages?.single()?.deliveryState)
    }

    @Test
    fun largeThreadCacheKeepsRecentHistoryAndOlderFailedMessages() {
        val account = "76561198000000001"
        val partner = "76561198000000003"
        val failed = SteamChatMessage(
            partnerSteamId = partner,
            senderSteamId = account,
            timestamp = 1L,
            ordinal = 1,
            body = "failed",
            deliveryState = SteamChatDeliveryState.FAILED_RETRYABLE,
            clientMessageId = "failed-1"
        )
        val sent = (2L..601L).map { timestamp ->
            SteamChatMessage(
                partnerSteamId = partner,
                senderSteamId = account,
                timestamp = timestamp,
                ordinal = timestamp.toInt(),
                body = "message-$timestamp"
            )
        }

        val bounded = boundSteamChatThreadForCache(
            SteamChatThreadSnapshot(
                accountSteamId = account,
                partnerSteamId = partner,
                messages = listOf(failed) + sent,
                moreAvailable = false,
                fetchedAt = 100L
            )
        )

        assertEquals(501, bounded.messages.size)
        assertEquals("failed", bounded.messages.first().body)
        assertEquals("message-102", bounded.messages[1].body)
        assertEquals("message-601", bounded.messages.last().body)
        assertTrue(bounded.moreAvailable)
    }

    private class FakeStore : SteamChatKeyValueStore {
        val values = linkedMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }
}
