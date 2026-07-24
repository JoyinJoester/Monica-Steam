package takagi.ru.monica.steam.friends.chat.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamChatSession(
    val partnerSteamId: String,
    val lastMessageTimestamp: Long = 0L,
    val lastViewTimestamp: Long = 0L,
    val unreadCount: Int = 0
)

@Serializable
enum class SteamChatDeliveryState {
    SENT,
    PENDING,
    FAILED
}

@Serializable
data class SteamChatMessage(
    val partnerSteamId: String,
    val senderSteamId: String,
    val timestamp: Long,
    val ordinal: Int,
    val body: String,
    val deliveryState: SteamChatDeliveryState = SteamChatDeliveryState.SENT,
    val clientMessageId: String = ""
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
        val existingIndex = merged.indexOfFirst { existing ->
            existing.stableId == message.stableId || existing.hasSameServerIdentity(message)
        }
        if (existingIndex < 0) {
            merged += message
        } else {
            val existing = merged[existingIndex]
            merged[existingIndex] = if (
                existing.clientMessageId.isNotBlank() && message.clientMessageId.isBlank()
            ) {
                message.copy(clientMessageId = existing.clientMessageId)
            } else {
                message
            }
        }
    }
    return merged.sortedWith(
        compareBy<SteamChatMessage> { it.timestamp }
            .thenBy { it.ordinal }
            .thenBy { it.stableId }
    )
}

private fun SteamChatMessage.hasSameServerIdentity(other: SteamChatMessage): Boolean =
    timestamp > 0L &&
        ordinal != Int.MAX_VALUE &&
        timestamp == other.timestamp &&
        ordinal == other.ordinal &&
        senderSteamId == other.senderSteamId

internal const val STEAM_ID64_INDIVIDUAL_BASE = 76561197960265728L

internal fun steamId64FromAccountId(accountId: Long): String =
    (STEAM_ID64_INDIVIDUAL_BASE + (accountId and 0xffff_ffffL)).toString()
