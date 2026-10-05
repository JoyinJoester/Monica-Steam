package takagi.ru.monica.steam.friends.chat.presentation

import java.io.IOException
import kotlinx.coroutines.CancellationException
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

internal fun newPendingSteamChatMessage(
    accountSteamId: String,
    partnerSteamId: String,
    body: String,
    timestamp: Long,
    clientMessageId: String,
    replyToStableId: String? = null,
    localCreatedAtMillis: Long = timestamp * 1_000L
) = SteamChatMessage(
    partnerSteamId = partnerSteamId,
    senderSteamId = accountSteamId,
    timestamp = timestamp,
    ordinal = Int.MAX_VALUE,
    body = body,
    deliveryState = SteamChatDeliveryState.QUEUED,
    clientMessageId = clientMessageId,
    localCreatedAtMillis = localCreatedAtMillis,
    replyToStableId = replyToStableId
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
        ),
        lastMessage = if (message.timestamp >= (existingSession?.lastMessageTimestamp ?: 0L)) {
            message.body
        } else existingSession?.lastMessage.orEmpty()
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

internal fun reconcileSteamChatSessions(
    remote: SteamChatSessionsSnapshot,
    local: SteamChatSessionsSnapshot?
): SteamChatSessionsSnapshot {
    if (local == null || local.accountSteamId != remote.accountSteamId) return remote
    val localByPartner = local.sessions.associateBy(SteamChatSession::partnerSteamId)
    val remotePartners = remote.sessions.mapTo(mutableSetOf(), SteamChatSession::partnerSteamId)
    val reconciledRemote = remote.sessions.map { remoteSession ->
        val localSession = localByPartner[remoteSession.partnerSteamId]
            ?: return@map remoteSession
        // A history request can complete after a newer push/optimistic send.
        if (localSession.lastMessageTimestamp > remoteSession.lastMessageTimestamp) return@map localSession
        val withPreview = if (localSession.lastMessageTimestamp == remoteSession.lastMessageTimestamp) {
            remoteSession.copy(lastMessage = remoteSession.lastMessage.ifBlank { localSession.lastMessage })
        } else remoteSession
        val localAcknowledgementCoversRemote =
            localSession.unreadCount == 0 &&
                localSession.lastViewTimestamp >= remoteSession.lastViewTimestamp &&
                remoteSession.lastMessageTimestamp <= localSession.lastViewTimestamp
        if (localAcknowledgementCoversRemote) {
            withPreview.copy(
                lastViewTimestamp = localSession.lastViewTimestamp,
                unreadCount = 0
            )
        } else {
            withPreview
        }
    }
    return remote.copy(
        // GetActiveMessageSessions may omit a freshly-created or old cached
        // conversation. Keep those local rows so optimistic sends and offline
        // history do not disappear during an otherwise successful refresh.
        sessions = (reconciledRemote + local.sessions.filter {
            it.partnerSteamId !in remotePartners
        }).sortedByDescending(SteamChatSession::lastMessageTimestamp)
    )
}

internal fun SteamChatThreadSnapshot.failUnresolvedVerification(
    isSending: (String) -> Boolean = { false }
): SteamChatThreadSnapshot = copy(
    messages = messages.map { message ->
        if (message.deliveryState == SteamChatDeliveryState.VERIFYING &&
            message.ordinal == Int.MAX_VALUE && !isSending(message.clientMessageId)
        ) message.copy(deliveryState = SteamChatDeliveryState.FAILED_RETRYABLE) else message
    }
)

internal suspend fun resolveSteamChatSession(
    account: SteamAccount,
    resolver: SteamAccountSessionResolver?,
    forceRefresh: Boolean = false
): SteamAccount = resolver.resolveOrKeep(account, forceRefresh)

/** Result wrapper for suspend work that never converts structured cancellation into a failure. */
internal suspend inline fun <T> runSteamChatCatching(
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

internal fun logSteamChatFailure(operation: String, error: Throwable) {
    runCatching {
        val details = when (error) {
            is SteamApiException -> buildString {
                append("type=SteamApiException")
                append(" eresult=${error.eResult ?: "none"}")
                append(" http=${error.httpStatusCode ?: "none"}")
                error.message?.sanitizeSteamChatDiagnostic()?.let { append(" message=$it") }
            }
            is java.net.SocketTimeoutException -> "type=SocketTimeoutException"
            is java.net.ConnectException -> "type=ConnectException"
            is IOException -> "type=IOException"
            else -> "type=${error.javaClass.simpleName.ifBlank { "Unknown" }}"
        }
        SteamDiagLogger.append("friend_chat $operation failed $details")
    }
}

internal fun Throwable.requiresSteamChatSessionRefresh(): Boolean {
    val error = this as? SteamApiException ?: return false
    if (error.eResult?.let { it in SESSION_REFRESH_ERESULTS } == true ||
        error.httpStatusCode?.let { it in SESSION_REFRESH_HTTP_CODES } == true
    ) {
        return true
    }
    val message = error.message.orEmpty().lowercase()
    return SESSION_ERROR_WORDS.any { word -> message.contains(word) }
}

internal fun Throwable.isTransientSteamChatNetworkFailure(): Boolean = when (this) {
    is java.net.SocketTimeoutException,
    is java.net.ConnectException,
    is java.net.UnknownHostException -> true
    is IOException -> message.orEmpty().contains("timeout", ignoreCase = true) ||
        message.orEmpty().contains("connection", ignoreCase = true)
    else -> false
}

internal fun Throwable.toSteamChatFailureReason(): SteamChatFailureReason = when (this) {
    is IOException -> SteamChatFailureReason.NETWORK
    is SteamApiException -> if (requiresSteamChatSessionRefresh()) {
        SteamChatFailureReason.SESSION_REQUIRED
    } else {
        SteamChatFailureReason.UNAVAILABLE
    }
    is IllegalArgumentException, is IllegalStateException -> SteamChatFailureReason.SESSION_REQUIRED
    else -> SteamChatFailureReason.UNAVAILABLE
}

private fun String.sanitizeSteamChatDiagnostic(): String =
    replace(Regex("[\\r\\n\\t]+"), " ")
        .take(180)

private val SESSION_REFRESH_ERESULTS = setOf(5, 15)
private val SESSION_REFRESH_HTTP_CODES = setOf(401, 403)
private val SESSION_ERROR_WORDS = setOf(
    "session expired",
    "access token",
    "unauthorized",
    "forbidden",
    "not logged in",
    "login required"
)
