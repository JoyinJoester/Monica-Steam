package takagi.ru.monica.steam.friends.chat.presentation

import java.io.IOException
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamSessionRefreshService

internal fun newPendingSteamChatMessage(
    accountSteamId: String,
    partnerSteamId: String,
    body: String,
    timestamp: Long,
    clientMessageId: String
) = SteamChatMessage(
    partnerSteamId = partnerSteamId,
    senderSteamId = accountSteamId,
    timestamp = timestamp,
    ordinal = Int.MAX_VALUE,
    body = body,
    deliveryState = SteamChatDeliveryState.PENDING,
    clientMessageId = clientMessageId
)

internal fun SteamChatUiState.withChatMessage(
    accountSteamId: String,
    partnerSteamId: String,
    message: SteamChatMessage,
    nowMillis: Long
): SteamChatUiState {
    val currentThread = thread ?: SteamChatThreadSnapshot(
        accountSteamId = accountSteamId,
        partnerSteamId = partnerSteamId,
        messages = emptyList(),
        moreAvailable = false,
        fetchedAt = nowMillis
    )
    val updatedThread = currentThread.copy(
        messages = mergeSteamChatMessages(currentThread.messages, listOf(message)),
        fetchedAt = nowMillis
    )
    val currentSessions = sessions ?: SteamChatSessionsSnapshot(
        accountSteamId = accountSteamId,
        sessions = emptyList(),
        fetchedAt = nowMillis
    )
    val existingSession = currentSessions.sessions.firstOrNull {
        it.partnerSteamId == partnerSteamId
    }
    val updatedSession = (existingSession ?: SteamChatSession(partnerSteamId)).copy(
        lastMessageTimestamp = maxOf(
            existingSession?.lastMessageTimestamp ?: 0L,
            message.timestamp
        )
    )
    val updatedSessions = currentSessions.copy(
        sessions = (currentSessions.sessions.filterNot {
            it.partnerSteamId == partnerSteamId
        } + updatedSession).sortedByDescending(SteamChatSession::lastMessageTimestamp),
        fetchedAt = nowMillis
    )
    return copy(
        thread = updatedThread,
        sessions = updatedSessions,
        threadFailure = null
    )
}

internal fun prepareSteamChatSession(
    account: SteamAccount,
    service: SteamSessionRefreshService?
): SteamAccount {
    val refreshed = service?.refreshIfNeeded(account) ?: return account
    return account.copy(
        accessToken = refreshed.accessToken,
        refreshToken = refreshed.refreshToken ?: account.refreshToken,
        steamLoginSecure = "${account.steamId}||${refreshed.accessToken}"
    )
}

internal fun logSteamChatFailure(operation: String, error: Throwable) {
    runCatching {
        SteamDiagLogger.append(
            "friend_chat $operation failed type=${error.javaClass.simpleName}"
        )
    }
}

internal fun Throwable.toSteamChatFailureReason(): SteamChatFailureReason = when (this) {
    is IOException -> SteamChatFailureReason.NETWORK
    is SteamApiException -> when (eResult) {
        401, 403, 5, 15 -> SteamChatFailureReason.SESSION_REQUIRED
        else -> SteamChatFailureReason.UNAVAILABLE
    }
    is IllegalArgumentException, is IllegalStateException -> SteamChatFailureReason.SESSION_REQUIRED
    else -> SteamChatFailureReason.UNAVAILABLE
}
