package takagi.ru.monica.steam.friends.chat.background.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationIdentity

class SteamChatNotificationAddressTest {
    @Test
    fun sameSecondMessagesAreSeparateAndReplaysKeepTheirAddress() {
        val identity = SteamChatNotificationIdentity("room|1|76561198000000001", "76561198000000002", 100L, 1)
        val first = steamChatMessageNotificationAddress(identity)
        val next = steamChatMessageNotificationAddress(identity.copy(ordinal = 2))
        assertNotEquals(first.tag, next.tag)
        assertEquals(first.id, next.id)
        assertEquals(first.groupKey, next.groupKey)
        assertEquals(first, steamChatMessageNotificationAddress(identity.copy()))
        assertNotEquals(first.tag, steamChatMessageNotificationAddress(identity.copy(timestamp = 101L)).tag)
    }

    @Test
    fun conversationPrefixIsExactOpaqueAndSourceIsolated() {
        val identity = SteamChatNotificationIdentity("room|1|76561198000000001", "76561198000000002", 100L, 1)
        val prefix = steamChatConversationNotificationPrefix(identity.accountKey, identity.partnerSteamId)
        val first = steamChatMessageNotificationAddress(identity)
        assertEquals(true, first.tag.startsWith(prefix))
        assertFalse(first.tag.contains(identity.partnerSteamId))
        assertFalse(first.tag.contains(identity.accountKey))
        assertFalse(steamChatMessageNotificationAddress(identity.copy(partnerSteamId = "76561198000000003")).tag.startsWith(prefix))
        assertFalse(steamChatMessageNotificationAddress(identity.copy(accountKey = "mdbx:9:entry|1|76561198000000001")).tag.startsWith(prefix))
    }

    @Test
    fun accountSourceIsolatedTagsPreventCrossAccountReplacement() {
        val local = steamChatNotificationAddress(
            "room|1|76561198000000001",
            "76561198000000002"
        )
        val mdbx = steamChatNotificationAddress(
            "mdbx:9:entry|1|76561198000000001",
            "76561198000000002"
        )

        assertNotEquals(local.tag, mdbx.tag)
        assertEquals(local.id, mdbx.id)
        assertNotEquals(local.groupKey, mdbx.groupKey)
        assertFalse(local.tag.contains("76561198000000001"))
        assertFalse(mdbx.tag.contains("entry"))
    }

    @Test
    fun sameConversationProducesAStableAddress() {
        val first = steamChatNotificationAddress("account-key", "76561198000000002")
        val second = steamChatNotificationAddress("account-key", "76561198000000002")

        assertEquals(first, second)
    }
}
