package takagi.ru.monica.steam.friends.chat.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatCache
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

/** Coordinates authoritative chat reads and cache writes outside the screen ViewModel. */
internal class SteamChatDataCoordinator(
    private val scope: CoroutineScope,
    private val gateway: SteamChatGateway,
    private val cache: SteamChatCache,
    private val sessionResolver: SteamAccountSessionResolver?,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
    private val state: () -> SteamChatUiState,
    private val updateState: (SteamChatUiState) -> Unit,
    private val isSessionsCurrent: (SteamAccount, Long) -> Boolean,
    private val isThreadCurrent: (SteamAccount, String, Long) -> Boolean,
    private val onSessionResolved: (SteamAccount) -> Unit = {},
    private val isSending: (String) -> Boolean = { false }
) {
    private var threadRequest = 0L

    fun fetchSessions(account: SteamAccount, generation: Long, silent: Boolean) {
        scope.launch {
            val result = runSteamChatCatching {
                withContext(ioDispatcher) {
                    gateway.fetchSessions(resolve(account))
                }
            }
            if (!isSessionsCurrent(account, generation)) return@launch
            result.fold(
                onSuccess = { snapshot ->
                    val reconciled = reconcileSteamChatSessions(snapshot, state().sessions)
                    updateState(
                        state().copy(
                            sessions = reconciled,
                            sessionsLoading = false,
                            sessionsRefreshing = false,
                            sessionsFromCache = false,
                            sessionsFailure = null
                        )
                    )
                    withContext(ioDispatcher) {
                        runSteamChatCatching { cache.saveSessions(account.steamId, reconciled) }
                            .onFailure { logSteamChatFailure("sessions_cache", it) }
                    }
                },
                onFailure = { error ->
                    logSteamChatFailure("sessions", error)
                    updateState(
                        state().copy(
                            sessionsLoading = false,
                            sessionsRefreshing = false,
                            sessionsFromCache = state().sessions != null,
                            sessionsFailure = if (silent && state().sessions != null) null
                            else error.toSteamChatFailureReason()
                        )
                    )
                }
            )
        }
    }

    fun fetchThread(
        account: SteamAccount,
        partnerSteamId: String,
        generation: Long,
        silent: Boolean
    ) {
        val request = ++threadRequest
        scope.launch {
            val result = runSteamChatCatching {
                withContext(ioDispatcher) {
                    gateway.fetchMessages(
                        resolve(account),
                        partnerSteamId
                    )
                }
            }
            if (!isThreadCurrent(account, partnerSteamId, generation) || request != threadRequest) return@launch
            result.fold(
                onSuccess = { page ->
                    val current = state().thread
                    val snapshot = SteamChatThreadSnapshot(
                        accountSteamId = account.steamId,
                        partnerSteamId = partnerSteamId,
                        messages = mergeSteamChatMessages(current?.messages.orEmpty(), page.messages),
                        moreAvailable = if (current?.messages?.firstOrNull()?.timestamp?.let { oldest ->
                            page.messages.firstOrNull()?.timestamp?.let { oldest < it }
                        } == true) current.moreAvailable else page.moreAvailable,
                        fetchedAt = nowMillis()
                    ).failUnresolvedVerification(isSending)
                    persistThread(account, partnerSteamId, snapshot)
                    updateState(
                        state().copy(
                            thread = snapshot,
                            threadLoading = false,
                            threadRefreshing = false,
                            threadFromCache = false,
                            threadFailure = null
                        )
                    )
                    snapshot.messages.lastOrNull()?.let { latest ->
                        updateState(state().withChatMessage(account.steamId, partnerSteamId, latest, nowMillis()))
                    }
                    acknowledgeLatest(account, partnerSteamId, snapshot, generation)
                },
                onFailure = { error ->
                    logSteamChatFailure("thread", error)
                    updateState(
                        state().copy(
                            threadLoading = false,
                            threadRefreshing = false,
                            threadFromCache = state().thread != null,
                            threadFailure = if (silent && state().thread != null) null
                            else error.toSteamChatFailureReason()
                        )
                    )
                }
            )
        }
    }

    fun loadOlder(account: SteamAccount, partnerSteamId: String, generation: Long) {
        val currentState = state()
        val thread = currentState.thread ?: return
        val oldest = thread.messages.firstOrNull() ?: return
        if (!thread.moreAvailable || currentState.loadingOlder) return
        updateState(currentState.copy(loadingOlder = true, threadFailure = null))
        scope.launch {
            val result = runSteamChatCatching {
                withContext(ioDispatcher) {
                    gateway.fetchMessages(
                        account = resolve(account),
                        partnerSteamId = partnerSteamId,
                        before = SteamChatHistoryBoundary(oldest.timestamp, oldest.ordinal)
                    )
                }
            }
            if (!isThreadCurrent(account, partnerSteamId, generation)) return@launch
            result.fold(
                onSuccess = { page ->
                    val latest = state().thread ?: thread
                    val updated = latest.copy(
                        messages = mergeSteamChatMessages(page.messages, latest.messages),
                        moreAvailable = page.moreAvailable,
                        fetchedAt = nowMillis()
                    )
                    persistThread(account, partnerSteamId, updated)
                    updateState(
                        state().copy(
                            thread = updated,
                            loadingOlder = false,
                            threadFromCache = false,
                            threadFailure = null
                        )
                    )
                },
                onFailure = { error ->
                    logSteamChatFailure("load_older", error)
                    updateState(
                        state().copy(
                            loadingOlder = false,
                            threadFailure = error.toSteamChatFailureReason()
                        )
                    )
                }
            )
        }
    }

    fun acknowledgeLatest(
        account: SteamAccount,
        partnerSteamId: String,
        snapshot: SteamChatThreadSnapshot,
        generation: Long
    ) {
        val timestamp = snapshot.messages.asReversed()
            .firstOrNull { !it.isOutgoing(account.steamId) }
            ?.timestamp ?: return
        scope.launch {
            runSteamChatCatching {
                withContext(ioDispatcher) {
                    gateway.acknowledge(
                        resolve(account),
                        partnerSteamId,
                        timestamp
                    )
                }
            }.onFailure { logSteamChatFailure("ack", it) }
            if (!isThreadCurrent(account, partnerSteamId, generation)) return@launch
            val sessions = state().sessions ?: return@launch
            val updated = sessions.copy(
                sessions = sessions.sessions.map { session ->
                    if (session.partnerSteamId == partnerSteamId) {
                        session.copy(unreadCount = 0, lastViewTimestamp = maxOf(session.lastViewTimestamp, timestamp))
                    } else session
                }
            )
            updateState(state().copy(sessions = updated))
            withContext(ioDispatcher) { cache.saveSessions(account.steamId, updated) }
        }
    }

    fun persistThread(
        account: SteamAccount,
        partnerSteamId: String,
        snapshot: SteamChatThreadSnapshot
    ) {
        scope.launch(ioDispatcher) { cache.saveThread(account.steamId, partnerSteamId, snapshot) }
    }

    private suspend fun resolve(account: SteamAccount): SteamAccount {
        val resolved = resolveSteamChatSession(account, sessionResolver)
        if (hasSessionChanged(account, resolved)) onSessionResolved(resolved)
        return resolved
    }

    private fun hasSessionChanged(previous: SteamAccount, current: SteamAccount): Boolean =
        previous.accessToken != current.accessToken ||
            previous.refreshToken != current.refreshToken ||
            previous.steamLoginSecure != current.steamLoginSecure
}
