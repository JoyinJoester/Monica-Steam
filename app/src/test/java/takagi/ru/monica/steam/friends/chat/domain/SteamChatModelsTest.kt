package takagi.ru.monica.steam.friends.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamChatModelsTest {
    @Test
    fun rapidLocalMessagesKeepComposeOrderWithinTheSameSecond() {
        val first = message(100L, Int.MAX_VALUE, "first").copy(clientMessageId = "z", localCreatedAtMillis = 100_010L,
            deliveryState = SteamChatDeliveryState.QUEUED)
        val second = first.copy(body = "second", clientMessageId = "a", localCreatedAtMillis = 100_020L)
        assertEquals(listOf("first", "second"), mergeSteamChatMessages(listOf(first), listOf(second)).map { it.body })
    }

    @Test
    fun lateSendFailureCannotDowngradeAnAlreadyConfirmedRealtimeEcho() {
        val sent = message(100L, 1, "hello").copy(clientMessageId = "client-echo")
        val lateFailure = sent.copy(ordinal = Int.MAX_VALUE, deliveryState = SteamChatDeliveryState.FAILED_RETRYABLE)
        val merged = mergeSteamChatMessages(listOf(sent), listOf(lateFailure))
        assertEquals(SteamChatDeliveryState.SENT, merged.single().deliveryState)
        assertEquals(1, merged.single().ordinal)
    }

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

    @Test
    fun pendingLocalEchoAndServerHistoryEntryReconcileByContentAndTime() {
        val local = SteamChatMessage(
            partnerSteamId = "76561198000000002",
            senderSteamId = "76561198000000001",
            timestamp = 100L,
            ordinal = Int.MAX_VALUE,
            body = "  Hello   Steam ",
            deliveryState = SteamChatDeliveryState.VERIFYING,
            clientMessageId = "client-echo",
            localCreatedAtMillis = 100_000L
        )
        val server = local.copy(
            timestamp = 103L,
            ordinal = 8,
            body = "hello steam",
            deliveryState = SteamChatDeliveryState.SENT,
            clientMessageId = "",
            localCreatedAtMillis = 0L
        )

        val merged = mergeSteamChatMessages(listOf(local), listOf(server))

        assertEquals(1, merged.size)
        assertEquals("client-echo", merged.single().clientMessageId)
        assertEquals(8, merged.single().ordinal)
        assertEquals(SteamChatDeliveryState.SENT, merged.single().deliveryState)
    }

    @Test
    fun repeatedIdenticalMessagesMatchDistinctLocalEchoesInOrder() {
        val locals = listOf("one", "two").mapIndexed { index, id ->
            SteamChatMessage(
                partnerSteamId = "76561198000000002",
                senderSteamId = "76561198000000001",
                timestamp = 100L + index,
                ordinal = Int.MAX_VALUE,
                body = "same",
                deliveryState = SteamChatDeliveryState.VERIFYING,
                clientMessageId = id,
                localCreatedAtMillis = (100L + index) * 1_000L
            )
        }
        val server = locals.mapIndexed { index, message ->
            message.copy(
                timestamp = 102L + index,
                ordinal = index + 1,
                clientMessageId = "",
                localCreatedAtMillis = 0L,
                deliveryState = SteamChatDeliveryState.SENT
            )
        }

        val merged = mergeSteamChatMessages(locals, server)

        assertEquals(2, merged.size)
        assertEquals(listOf("one", "two"), merged.map(SteamChatMessage::clientMessageId))
        assertEquals(listOf(1, 2), merged.map(SteamChatMessage::ordinal))
    }

    private fun message(timestamp: Long, ordinal: Int, body: String) = SteamChatMessage(
        partnerSteamId = "76561198000000002",
        senderSteamId = "76561198000000002",
        timestamp = timestamp,
        ordinal = ordinal,
        body = body
    )
}
