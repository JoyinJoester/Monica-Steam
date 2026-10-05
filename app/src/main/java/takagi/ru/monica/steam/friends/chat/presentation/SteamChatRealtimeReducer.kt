package takagi.ru.monica.steam.friends.chat.presentation

import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatReaction
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages

internal data class SteamChatRealtimeEffect(
    val state: SteamChatUiState,
    val message: SteamChatMessage? = null,
    val acknowledgePartnerSteamId: String? = null,
    val acknowledgeTimestamp: Long = 0L,
    val reconcileAuthoritativeState: Boolean = false
)

/**
 * Account-local event reducer. It deliberately has no Android, network, or
 * persistence dependencies, so duplicate CM deliveries can be tested without
 * a socket and account switching can reset all remembered event identities.
 */
internal class SteamChatRealtimeReducer(
    private val maxRememberedServerMessages: Int = 1_024
) {
    private val seenServerMessages = LinkedHashSet<String>()

    fun reset() {
        seenServerMessages.clear()
    }

    fun reduce(
        state: SteamChatUiState,
        event: SteamChatRealtimeEvent,
        accountSteamId: String,
        nowMillis: Long
    ): SteamChatRealtimeEffect = when (event) {
        is SteamChatRealtimeEvent.ConnectionChanged -> SteamChatRealtimeEffect(
            state = state.copy(realtimeConnected = event.connected),
            reconcileAuthoritativeState = event.connected
        )

        is SteamChatRealtimeEvent.Message -> reduceMessage(
            state = state,
            message = event.message,
            accountSteamId = accountSteamId,
            nowMillis = nowMillis
        )

        is SteamChatRealtimeEvent.Acknowledged -> {
            val sessions = state.sessions ?: return SteamChatRealtimeEffect(state)
            val updated = sessions.updateAcknowledgement(
                partnerSteamId = event.partnerSteamId,
                timestamp = event.timestamp
            )
            SteamChatRealtimeEffect(state.copy(sessions = updated))
        }

        is SteamChatRealtimeEvent.ReactionChanged -> reduceReaction(state, event, nowMillis)

        is SteamChatRealtimeEvent.Typing -> {
            val typing = state.typingPartnerSteamIds.toMutableSet()
            if (event.localEcho) typing.remove(event.partnerSteamId)
            else typing += event.partnerSteamId
            SteamChatRealtimeEffect(state.copy(typingPartnerSteamIds = typing))
        }

        is SteamChatRealtimeEvent.ConversationLeft -> SteamChatRealtimeEffect(
            state = state.copy(
                typingPartnerSteamIds = state.typingPartnerSteamIds - event.partnerSteamId
            ),
            reconcileAuthoritativeState = true
        )
    }

    private fun reduceReaction(
        state: SteamChatUiState,
        event: SteamChatRealtimeEvent.ReactionChanged,
        nowMillis: Long
    ): SteamChatRealtimeEffect {
        val thread = state.thread?.takeIf { it.partnerSteamId == event.partnerSteamId }
            ?: return SteamChatRealtimeEffect(state, reconcileAuthoritativeState = true)
        var changed = false
        val messages = thread.messages.map { message ->
            if (message.timestamp != event.timestamp || message.ordinal != event.ordinal) {
                message
            } else {
                changed = true
                message.copy(reactions = message.reactions.withReactionChange(event))
            }
        }
        return SteamChatRealtimeEffect(
            state = if (changed) {
                state.copy(thread = thread.copy(messages = messages, fetchedAt = nowMillis))
            } else {
                state
            },
            reconcileAuthoritativeState = true
        )
    }

    private fun reduceMessage(
        state: SteamChatUiState,
        message: SteamChatMessage,
        accountSteamId: String,
        nowMillis: Long
    ): SteamChatRealtimeEffect {
        val duplicateServerMessage = isDuplicateServerMessage(message)
        val selected = state.selectedPartnerSteamId == message.partnerSteamId
        val incoming = !message.isOutgoing(accountSteamId)
        val existingSession = state.sessions?.sessions?.firstOrNull {
            it.partnerSteamId == message.partnerSteamId
        }
        val currentSessions = state.sessions ?: SteamChatSessionsSnapshot(
            accountSteamId = accountSteamId,
            sessions = emptyList(),
            fetchedAt = nowMillis
        )
        val unreadCount = when {
            !incoming -> existingSession?.unreadCount ?: 0
            selected -> 0
            duplicateServerMessage -> existingSession?.unreadCount ?: 0
            else -> (existingSession?.unreadCount ?: 0) + 1
        }
        val lastViewTimestamp = if (selected && incoming) {
            maxOf(existingSession?.lastViewTimestamp ?: 0L, message.timestamp)
        } else {
            existingSession?.lastViewTimestamp ?: 0L
        }
        val updatedSession = (existingSession ?: SteamChatSession(message.partnerSteamId)).copy(
            lastMessageTimestamp = maxOf(
                existingSession?.lastMessageTimestamp ?: 0L,
                message.timestamp
            ),
            lastViewTimestamp = lastViewTimestamp,
            unreadCount = unreadCount,
            lastMessage = if (message.timestamp >= (existingSession?.lastMessageTimestamp ?: 0L)) {
                message.body
            } else existingSession?.lastMessage.orEmpty()
        )
        val updatedSessions = currentSessions.copy(
            sessions = (currentSessions.sessions.filterNot {
                it.partnerSteamId == message.partnerSteamId
            } + updatedSession).sortedByDescending(SteamChatSession::lastMessageTimestamp),
            fetchedAt = nowMillis
        )
        val updatedThread = if (selected) {
            val currentThread = state.thread?.takeIf {
                it.partnerSteamId == message.partnerSteamId
            } ?: SteamChatThreadSnapshot(
                accountSteamId = accountSteamId,
                partnerSteamId = message.partnerSteamId,
                messages = emptyList(),
                moreAvailable = false,
                fetchedAt = nowMillis
            )
            currentThread.copy(
                messages = mergeSteamChatMessages(currentThread.messages, listOf(message)),
                fetchedAt = nowMillis
            )
        } else {
            state.thread
        }
        val updatedState = state.copy(
            sessions = updatedSessions,
            thread = updatedThread,
            typingPartnerSteamIds = state.typingPartnerSteamIds - message.partnerSteamId,
            threadFailure = if (selected) null else state.threadFailure
        )
        return SteamChatRealtimeEffect(
            state = updatedState,
            message = message,
            acknowledgePartnerSteamId = message.partnerSteamId.takeIf { selected && incoming },
            acknowledgeTimestamp = message.timestamp
        )
    }

    private fun isDuplicateServerMessage(message: SteamChatMessage): Boolean {
        if (message.timestamp <= 0L || message.ordinal == Int.MAX_VALUE) return false
        val key = buildString {
            append(message.partnerSteamId)
            append('|')
            append(message.timestamp)
            append('|')
            append(message.ordinal)
            append('|')
            append(message.senderSteamId)
        }
        if (!seenServerMessages.add(key)) return true
        while (seenServerMessages.size > maxRememberedServerMessages) {
            seenServerMessages.remove(seenServerMessages.first())
        }
        return false
    }
}

private fun List<SteamChatReaction>.withReactionChange(
    event: SteamChatRealtimeEvent.ReactionChanged
): List<SteamChatReaction> {
    val matching = firstOrNull {
        it.type == event.reactionType && it.name.equals(event.reactionName, ignoreCase = true)
    }
    if (matching == null) {
        return if (event.isAdd) {
            this + SteamChatReaction(
                type = event.reactionType,
                name = event.reactionName,
                reactorSteamIds = listOf(event.reactorSteamId)
            )
        } else {
            this
        }
    }
    val updatedReactors = if (event.isAdd) {
        (matching.reactorSteamIds + event.reactorSteamId).distinct()
    } else {
        matching.reactorSteamIds - event.reactorSteamId
    }
    return mapNotNull { reaction ->
        if (reaction !== matching) reaction
        else reaction.copy(reactorSteamIds = updatedReactors).takeIf { updatedReactors.isNotEmpty() }
    }
}

private fun SteamChatSessionsSnapshot.updateAcknowledgement(
    partnerSteamId: String,
    timestamp: Long
): SteamChatSessionsSnapshot = copy(
    sessions = sessions.map { session ->
        if (session.partnerSteamId != partnerSteamId) session
        else session.copy(
            unreadCount = 0,
            lastViewTimestamp = maxOf(session.lastViewTimestamp, timestamp)
        )
    }
)
