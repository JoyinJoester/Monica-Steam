package takagi.ru.monica.steam.friends.chat.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

internal class SteamChatOutgoingCoordinator(
    private val scope: CoroutineScope,
    private val gateway: SteamChatGateway,
    private val sessionResolver: SteamAccountSessionResolver?,
    private val ioDispatcher: CoroutineDispatcher,
    private val outbox: SteamChatOutbox? = null
) {
    private val jobs = mutableMapOf<String, Job>()

    fun isSending(clientMessageId: String): Boolean = jobs[clientMessageId]?.isActive == true

    fun dispatch(
        account: SteamAccount,
        partnerSteamId: String,
        accountKey: String = "${account.id}:${account.steamId}",
        pending: SteamChatMessage,
        verifyBeforeSend: Boolean,
        forceRetry: Boolean = false,
        isCurrent: () -> Boolean,
        onSessionRefreshed: (SteamAccount) -> Unit,
        onUpdate: (SteamChatMessage) -> Unit
    ) {
        if (jobs[pending.clientMessageId]?.isActive == true) return
        val job = scope.launch {
            val outboxRecord = runSteamChatCatching {
                withContext(ioDispatcher) { outbox?.enqueue(account, pending, accountKey) }
            }.onFailure { logSteamChatFailure("outbox_enqueue", it) }.getOrElse {
                if (isCurrent()) {
                    onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.FAILED_RETRYABLE))
                }
                return@launch
            }
            if (outboxRecord?.status == SteamOutboxStatus.COMPLETED) {
                if (isCurrent()) onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.SENT))
                return@launch
            }
            if (outboxRecord?.status in TERMINAL_OUTBOX_FAILURES) {
                if (isCurrent()) {
                    onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.FAILED_PERMANENT))
                }
                return@launch
            }
            val needsVerification = verifyBeforeSend ||
                outboxRecord?.status == SteamOutboxStatus.IN_FLIGHT ||
                outboxRecord?.status == SteamOutboxStatus.AWAITING_CONFIRMATION
            if (needsVerification) {
                verify(account, partnerSteamId, pending, onSessionRefreshed)?.let {
                    completeOutbox(pending.clientMessageId)
                    if (isCurrent()) onUpdate(it)
                    return@launch
                }
                if (outboxRecord?.status == SteamOutboxStatus.IN_FLIGHT ||
                    outboxRecord?.status == SteamOutboxStatus.AWAITING_CONFIRMATION
                ) {
                    val retryStatus = retryOutbox(
                        pending.clientMessageId,
                        "unconfirmed-after-restart"
                    )
                    if (isCurrent()) {
                        onUpdate(
                            pending.copy(
                                deliveryState = retryStatus.toDeliveryFailureState()
                            )
                        )
                    }
                    return@launch
                }
            }
            if (outbox != null) {
                val claimed = runSteamChatCatching {
                    withContext(ioDispatcher) {
                        outbox.claim(pending.clientMessageId, force = forceRetry)
                    }
                }.onFailure { logSteamChatFailure("outbox_claim", it) }.isSuccess
                if (!claimed) {
                    if (isCurrent()) {
                        onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.FAILED_RETRYABLE))
                    }
                    return@launch
                }
            }
            if (isCurrent()) onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.SENDING))
            val result = withContext(ioDispatcher) {
                sendSteamChatMessageWithSessionRecovery(
                    gateway = gateway,
                    account = account,
                    partnerSteamId = partnerSteamId,
                    pending = pending,
                    sessionResolver = sessionResolver,
                    onSessionRefreshed = { refreshed ->
                        onSessionRefreshed(refreshed)
                    }
                )
            }
            result.getOrNull()?.let { response ->
                completeOutbox(pending.clientMessageId)
                if (isCurrent()) onUpdate(response.asConfirmedEchoOf(pending))
                return@launch
            }
            val error = result.exceptionOrNull() ?: return@launch
            logSteamChatFailure("send", error)
            if (error.isTransientSteamChatNetworkFailure()) {
                runSteamChatCatching {
                    withContext(ioDispatcher) {
                        outbox?.awaitingConfirmation(pending.clientMessageId)
                    }
                }.onFailure { logSteamChatFailure("outbox_await_confirmation", it) }
                val verifying = pending.copy(deliveryState = SteamChatDeliveryState.VERIFYING)
                if (isCurrent()) onUpdate(verifying)
                val verified = verify(account, partnerSteamId, verifying, onSessionRefreshed)
                if (verified != null) {
                    completeOutbox(pending.clientMessageId)
                    if (isCurrent()) onUpdate(verified)
                } else {
                    val retryStatus = retryOutbox(
                        pending.clientMessageId,
                        error.outboxDiagnostic()
                    )
                    if (isCurrent()) {
                        onUpdate(
                            verifying.copy(
                                deliveryState = retryStatus.toDeliveryFailureState()
                            )
                        )
                    }
                }
            } else {
                runSteamChatCatching {
                    withContext(ioDispatcher) {
                        outbox?.permanentFailure(
                            pending.clientMessageId,
                            error.outboxDiagnostic()
                        )
                    }
                }.onFailure { logSteamChatFailure("outbox_permanent_failure", it) }
                if (isCurrent()) {
                    onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.FAILED_PERMANENT))
                }
            }
        }
        jobs[pending.clientMessageId] = job
        job.invokeOnCompletion { jobs.remove(pending.clientMessageId, job) }
    }

    private suspend fun verify(
        account: SteamAccount,
        partnerSteamId: String,
        pending: SteamChatMessage,
        onSessionRefreshed: (SteamAccount) -> Unit
    ): SteamChatMessage? {
        val page = runSteamChatCatching {
            withContext(ioDispatcher) {
                val resolved = resolveSteamChatSession(account, sessionResolver)
                if (hasSessionChanged(account, resolved)) onSessionRefreshed(resolved)
                gateway.fetchMessages(resolved, partnerSteamId)
            }
        }.onFailure { logSteamChatFailure("send_verify", it) }.getOrNull() ?: return null
        return mergeSteamChatMessages(listOf(pending), page.messages)
            .firstOrNull { it.clientMessageId == pending.clientMessageId && it.ordinal != Int.MAX_VALUE }
            ?.copy(deliveryState = SteamChatDeliveryState.SENT)
    }

    private fun hasSessionChanged(previous: SteamAccount, current: SteamAccount): Boolean =
        previous.accessToken != current.accessToken ||
            previous.refreshToken != current.refreshToken ||
            previous.steamLoginSecure != current.steamLoginSecure

    private suspend fun completeOutbox(clientMessageId: String) {
        runSteamChatCatching {
            withContext(ioDispatcher) { outbox?.complete(clientMessageId) }
        }.onFailure { logSteamChatFailure("outbox_complete", it) }
    }

    private suspend fun retryOutbox(
        clientMessageId: String,
        error: String?
    ): SteamOutboxStatus? = runSteamChatCatching {
        withContext(ioDispatcher) { outbox?.retry(clientMessageId, error) }
    }.onFailure { logSteamChatFailure("outbox_retry", it) }
        .getOrNull()
        ?.status
}

private fun SteamChatMessage.asConfirmedEchoOf(pending: SteamChatMessage): SteamChatMessage = copy(
    deliveryState = SteamChatDeliveryState.SENT,
    clientMessageId = pending.clientMessageId,
    localCreatedAtMillis = pending.localCreatedAtMillis,
    contentSignature = pending.contentSignature,
    replyToStableId = pending.replyToStableId
)

private fun Throwable.outboxDiagnostic(): String =
    javaClass.simpleName.ifBlank { "SteamWriteFailure" }.take(96)

private val TERMINAL_OUTBOX_FAILURES = setOf(
    SteamOutboxStatus.PERMANENT_FAILURE,
    SteamOutboxStatus.CANCELLED
)

private fun SteamOutboxStatus?.toDeliveryFailureState(): SteamChatDeliveryState =
    if (this == SteamOutboxStatus.PERMANENT_FAILURE || this == SteamOutboxStatus.CANCELLED) {
        SteamChatDeliveryState.FAILED_PERMANENT
    } else {
        SteamChatDeliveryState.FAILED_RETRYABLE
    }
