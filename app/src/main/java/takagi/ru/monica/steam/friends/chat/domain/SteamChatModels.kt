package takagi.ru.monica.steam.friends.chat.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamChatSession(
    val partnerSteamId: String,
    val lastMessageTimestamp: Long = 0L,
    val lastViewTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val lastMessage: String = ""
)

@Serializable
enum class SteamChatDeliveryState {
    QUEUED,
    SENDING,
    VERIFYING,
    SENT,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}

@Serializable
enum class SteamChatReactionType {
    EMOTICON,
    STICKER
}

@Serializable
data class SteamChatReaction(
    val type: SteamChatReactionType,
    val name: String,
    val reactorSteamIds: List<String> = emptyList()
) {
    val count: Int get() = reactorSteamIds.size
}

@Serializable
data class SteamChatMessage(
    val partnerSteamId: String,
    val senderSteamId: String,
    val timestamp: Long,
    val ordinal: Int,
    val body: String,
    val deliveryState: SteamChatDeliveryState = SteamChatDeliveryState.SENT,
    val clientMessageId: String = "",
    val localCreatedAtMillis: Long = 0L,
    val contentSignature: String = steamChatContentSignature(body),
    val replyToStableId: String? = null,
    val reactions: List<SteamChatReaction> = emptyList()
) {
    val stableId: String
        get() = if (clientMessageId.isNotBlank()) {
            "client:$clientMessageId"
        } else {
            "$timestamp:$ordinal:$senderSteamId"
        }

    fun isOutgoing(accountSteamId: String): Boolean = senderSteamId == accountSteamId
}

data class SteamChatPage(
    val messages: List<SteamChatMessage>,
    val moreAvailable: Boolean
)

@Serializable
data class SteamChatSessionsSnapshot(
    val accountSteamId: String,
    val sessions: List<SteamChatSession>,
    val fetchedAt: Long
) {
    val unreadCount: Int get() = sessions.sumOf(SteamChatSession::unreadCount)
}

@Serializable
data class SteamChatThreadSnapshot(
    val accountSteamId: String,
    val partnerSteamId: String,
    val messages: List<SteamChatMessage>,
    val moreAvailable: Boolean,
    val fetchedAt: Long
)

data class SteamChatHistoryBoundary(
    val timestamp: Long,
    val ordinal: Int
)

internal fun mergeSteamChatMessages(
    current: List<SteamChatMessage>,
    incoming: List<SteamChatMessage>
): List<SteamChatMessage> {
    val merged = mutableListOf<SteamChatMessage>()
    (current + incoming).forEach { message ->
        val exactIndices = merged.indices.filter { index ->
            val existing = merged[index]
            existing.stableId == message.stableId ||
                existing.hasSameServerIdentity(message)
        }
        val echoIndex = if (message.isServerConfirmed()) {
            merged.indices.firstOrNull { index ->
                index !in exactIndices && merged[index].canReconcileWith(message)
            }
        } else null
        val matchingIndices = exactIndices + listOfNotNull(echoIndex)
        if (matchingIndices.isEmpty()) {
            merged += message
        } else {
            val localEcho = matchingIndices.asSequence()
                .map(merged::get)
                .firstOrNull { it.clientMessageId.isNotBlank() }
            val confirmed = matchingIndices.asSequence().map(merged::get).firstOrNull { it.isServerConfirmed() }
            val replacement = if (confirmed != null && !message.isServerConfirmed()) {
                // A push may confirm delivery before the send request times out.
                confirmed
            } else if (localEcho != null && message.clientMessageId.isBlank()) {
                message.copy(
                    deliveryState = SteamChatDeliveryState.SENT,
                    clientMessageId = localEcho.clientMessageId,
                    localCreatedAtMillis = localEcho.localCreatedAtMillis,
                    contentSignature = localEcho.contentSignature,
                    replyToStableId = localEcho.replyToStableId
                )
            } else {
                message
            }
            matchingIndices.asReversed().forEach(merged::removeAt)
            merged += replacement
        }
    }
    return merged.sortedWith(
        compareBy<SteamChatMessage> { it.timestamp }
            .thenBy { it.ordinal }
            .thenBy { it.localCreatedAtMillis }
            .thenBy { it.stableId }
    )
}

private fun SteamChatMessage.hasSameServerIdentity(other: SteamChatMessage): Boolean =
    timestamp > 0L &&
        ordinal != Int.MAX_VALUE &&
        timestamp == other.timestamp &&
        ordinal == other.ordinal &&
        senderSteamId == other.senderSteamId

internal fun SteamChatMessage.isServerConfirmed(): Boolean =
    timestamp > 0L && ordinal != Int.MAX_VALUE

private fun SteamChatMessage.canReconcileWith(serverMessage: SteamChatMessage): Boolean {
    if (clientMessageId.isBlank() || isServerConfirmed()) return false
    if (deliveryState !in RECONCILABLE_DELIVERY_STATES) return false
    if (partnerSteamId != serverMessage.partnerSteamId || senderSteamId != serverMessage.senderSteamId) {
        return false
    }
    if (effectiveContentSignature() != serverMessage.effectiveContentSignature()) return false
    val localSeconds = when {
        localCreatedAtMillis > 0L -> localCreatedAtMillis / 1_000L
        timestamp > 0L -> timestamp
        else -> return false
    }
    return kotlin.math.abs(serverMessage.timestamp - localSeconds) <= ECHO_WINDOW_SECONDS
}

internal fun steamChatContentSignature(body: String): String = body
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase()

private fun SteamChatMessage.effectiveContentSignature(): String =
    contentSignature.ifBlank { steamChatContentSignature(body) }

private val RECONCILABLE_DELIVERY_STATES = setOf(
    SteamChatDeliveryState.QUEUED,
    SteamChatDeliveryState.SENDING,
    SteamChatDeliveryState.VERIFYING,
    SteamChatDeliveryState.FAILED_RETRYABLE
)

private const val ECHO_WINDOW_SECONDS = 45L

internal const val STEAM_ID64_INDIVIDUAL_BASE = 76561197960265728L

internal fun steamId64FromAccountId(accountId: Long): String =
    (STEAM_ID64_INDIVIDUAL_BASE + (accountId and 0xffff_ffffL)).toString()
