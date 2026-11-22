package takagi.ru.monica.steam.friends.chat.data

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.ui.SteamChatLocalDeletionState

class SteamChatLocalDeletionTest {
    private val message = SteamChatMessage("peer", "sender", 100, 4, "Synthetic invite", clientMessageId = "local-echo")

    @Test fun serverIdentitySurvivesEchoIdsAndDoesNotHideNearbyMessages() {
        val key = steamChatLocalDeletionKey(message)
        assertNotNull(key)
        assertEquals(key, steamChatLocalDeletionKey(message.copy(clientMessageId = "")))
        assertNotEquals(key, steamChatLocalDeletionKey(message.copy(ordinal = 5)))
        assertNotEquals(key, steamChatLocalDeletionKey(message.copy(timestamp = 101)))
        assertNotEquals(key, steamChatLocalDeletionKey(message.copy(senderSteamId = "another")))
    }

    @Test fun queuedAndUnconfirmedMessagesCannotBeDeletedWhileSending() {
        for (state in SteamChatDeliveryState.entries.filter { it != SteamChatDeliveryState.SENT }) {
            assertNull(steamChatLocalDeletionKey(message.copy(deliveryState = state)))
        }
        assertNull(steamChatLocalDeletionKey(message.copy(timestamp = 0)))
        assertNull(steamChatLocalDeletionKey(message.copy(ordinal = Int.MAX_VALUE)))
    }

    @Test fun storageKeysIsolateAccountsAndPeers() {
        val key = SteamChatLocalDeletionStore.conversationKey("account", "peer")
        assertNotEquals(key, SteamChatLocalDeletionStore.conversationKey("account2", "peer"))
        assertNotEquals(key, SteamChatLocalDeletionStore.conversationKey("account", "peer2"))
    }

    @Test fun refreshKeepsMessageHiddenWithoutChangingRawPaginationHistory() {
        val raw = SteamChatThreadSnapshot("account", "peer", listOf(message), true, 10)
        val state = SteamChatUiState(accountSteamId = "account", selectedPartnerSteamId = "peer", thread = raw)
        val deletion = SteamChatLocalDeletionState().apply { markers = setOf(requireNotNull(steamChatLocalDeletionKey(message))) }
        val visible = deletion.visibleState(state)
        val visibleThread = requireNotNull(visible.thread)
        assertTrue(visibleThread.messages.isEmpty())
        assertTrue(visibleThread.moreAvailable)
        assertEquals(listOf(message), raw.messages)
        val refreshed = state.copy(thread = raw.copy(messages = listOf(message.copy(clientMessageId = ""), message.copy(ordinal = 5))))
        assertEquals(listOf(5), deletion.visibleState(refreshed).thread!!.messages.map { it.ordinal })
    }

    @Test fun historyIsNotShownBeforeLocalDeletionMarkersAreRead() {
        val state = SteamChatUiState(thread = SteamChatThreadSnapshot("account", "peer", listOf(message), false, 10))
        val deletion = SteamChatLocalDeletionState()
        assertNull(deletion.visibleState(state).thread)
        assertTrue(deletion.visibleState(state).threadLoading)
        deletion.failed = true
        assertNull(deletion.visibleState(state).thread)
        assertNotNull(deletion.visibleState(state).threadFailure)
    }
}
