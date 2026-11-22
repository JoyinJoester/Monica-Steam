package takagi.ru.monica.steam.friends.groupchat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage

class TelegramGroupMessageRowsTest {
    @Test fun appendingSameSenderMovesTheAvatarToTheNewGroupEnd() {
        val initial = groupTelegramMessageRows(listOf(message(1_000), message(1_100)))
        assertFalse(initial.first().groupedWithPrevious)
        assertTrue(initial.first().groupedWithNext)
        assertTrue(initial.last().groupedWithPrevious)
        assertFalse(initial.last().groupedWithNext)

        val updated = groupTelegramMessageRows(initial.map { it.message } + message(1_200))
        assertTrue(updated[1].groupedWithNext)
        assertFalse(updated.last().groupedWithNext)
        assertEquals(initial[1].message.stableId, updated[1].message.stableId)
    }

    @Test fun serviceEventsAndSenderChangesSeparateBubbles() {
        val rows = groupTelegramMessageRows(listOf(
            message(1_000),
            message(1_100).copy(serverEventType = 1),
            message(1_200),
            message(1_300).copy(senderSteamId = "other")
        ))
        rows.forEach {
            assertFalse(it.groupedWithPrevious)
            assertFalse(it.groupedWithNext)
        }
    }

    @Test fun dateHeadersDoNotChangeSteamIdentityOrMetadata() {
        val original = message(1_000).copy(clientMessageId = "pending", body = "[img]steam-cdn[/img]")
        val rows = groupTelegramMessageRows(listOf(original, message(1_100)))
        assertTrue(rows.first().showDate)
        assertFalse(rows.last().showDate)
        assertEquals(original, rows.first().message)
    }

    private fun message(timestamp: Long) = SteamGroupChatMessage(
        groupId = "group",
        chatId = "room",
        senderSteamId = "sender",
        timestamp = timestamp,
        ordinal = 1,
        body = "Message"
    )
}
