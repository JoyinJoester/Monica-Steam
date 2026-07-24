package takagi.ru.monica.steam.friends.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamChatModelsTest {
    @Test
    fun mergingHistoryPagesRemovesDuplicatesAndKeepsChronologicalOrder() {
        val first = message(timestamp = 100L, ordinal = 1, body = "First")
        val duplicate = first.copy(body = "First updated")
        val second = message(timestamp = 101L, ordinal = 2, body = "Second")

        val merged = mergeSteamChatMessages(listOf(second, first), listOf(duplicate))

        assertEquals(listOf("First updated", "Second"), merged.map(SteamChatMessage::body))
    }

    @Test
    fun steamAccountIdConvertsToIndividualSteamId64() {
        assertEquals(
            "76561198000000002",
            steamId64FromAccountId(39_734_274L)
        )
    }

    @Test
    fun confirmedClientMessageAndSyncedHistoryEntryDoNotDuplicate() {
        val confirmed = message(timestamp = 200L, ordinal = 7, body = "Sent")
            .copy(clientMessageId = "client-1")
        val history = message(timestamp = 200L, ordinal = 7, body = "Sent")

        val merged = mergeSteamChatMessages(listOf(confirmed), listOf(history))

        assertEquals(1, merged.size)
        assertEquals("client-1", merged.single().clientMessageId)
    }

    private fun message(timestamp: Long, ordinal: Int, body: String) = SteamChatMessage(
        partnerSteamId = "76561198000000002",
        senderSteamId = "76561198000000002",
        timestamp = timestamp,
        ordinal = ordinal,
        body = body
    )
}
