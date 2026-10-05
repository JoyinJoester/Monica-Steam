package takagi.ru.monica.steam.friends.chat.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatCache
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeGateway
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

class SteamChatViewModel(
    private val gateway: SteamChatGateway,
    private val cache: SteamChatCache,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val clientMessageId: () -> String = { UUID.randomUUID().toString() },
    private val outbox: SteamChatOutbox? = null,
    private val realtime: SteamChatRealtimeGateway? = null,
    private val accountKeyResolver: (SteamAccount) -> String = { account ->
        "${account.id}|${account.steamId}"
    }
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamChatUiState())
    val uiState: StateFlow<SteamChatUiState> = _uiState.asStateFlow()
    private var activeAccount: SteamAccount? = null
    private var activeAccountKey: String = ""
    private val requestGuard = SteamChatRequestGuard()
    private var pollingJob: Job? = null
    private var foreground = false
    private val realtimeCoordinator = SteamChatRealtimeCoordinator(
        scope = viewModelScope,
        gateway = realtime,
        cache = cache,
        outbox = outbox,
        ioDispatcher = ioDispatcher,
        nowMillis = nowMillis,
        state = { _uiState.value },
        updateState = { _uiState.value = it },
        acknowledge = { account, partnerSteamId ->
            val generation = requestGuard.currentThreadGeneration()
            val snapshot = _uiState.value.thread
            if (snapshot != null &&
                _uiState.value.selectedPartnerSteamId == partnerSteamId
            ) {
                dataCoordinator.acknowledgeLatest(account, partnerSteamId, snapshot, generation)
            }
        },
        reconcile = {
            refreshSessions(silent = true)
            if (_uiState.value.selectedPartnerSteamId != null) refreshThread(silent = true)
        }
    )
    private val dataCoordinator = SteamChatDataCoordinator(
        scope = viewModelScope,
        gateway = gateway,
        cache = cache,
        sessionResolver = sessionResolver,
        ioDispatcher = ioDispatcher,
        nowMillis = nowMillis,
        state = { _uiState.value },
        updateState = { _uiState.value = it },
        isSessionsCurrent = ::isSessionsCurrent,
        isThreadCurrent = ::isThreadCurrent,
        onSessionResolved = ::onSessionResolved,
        isSending = { outgoingCoordinator.isSending(it) }
    )
    private val outgoingCoordinator = SteamChatOutgoingCoordinator(
        scope = viewModelScope,
        gateway = gateway,
        sessionResolver = sessionResolver,
        ioDispatcher = ioDispatcher,
        outbox = outbox
    )

    fun selectAccount(account: SteamAccount?) {
        val resolvedAccountKey = account?.let(::resolveAccountKey).orEmpty()
        if (
            account?.id == activeAccount?.id &&
            account?.steamId == activeAccount?.steamId &&
            resolvedAccountKey == activeAccountKey
            ) {
                val credentialsChanged = account?.accessToken != activeAccount?.accessToken ||
                account?.steamLoginSecure != activeAccount?.steamLoginSecure
                activeAccount = account
            if (credentialsChanged) {
                activeAccountKey = resolvedAccountKey
                realtimeCoordinator.selectAccount(account, resolvedAccountKey)
            }
            return
        }
        activeAccount = account
        activeAccountKey = resolvedAccountKey
        val generation = requestGuard.selectAccount(account)
        if (account == null) {
            _uiState.value = SteamChatUiState(
                sessionsFailure = SteamChatFailureReason.ACCOUNT_REQUIRED
            )
            restartPolling()
            realtimeCoordinator.selectAccount(null, "")
            return
        }
        _uiState.value = SteamChatUiState(
            accountId = account.id,
            accountSteamId = account.steamId,
            sessionsLoading = true
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.loadSessions(account.steamId) }
            if (!isSessionsCurrent(account, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                sessions = cached?.let { reconcileSteamChatSessions(it, _uiState.value.sessions) } ?: _uiState.value.sessions,
                sessionsLoading = cached == null,
                sessionsRefreshing = cached != null,
                sessionsFromCache = cached != null
            )
            dataCoordinator.fetchSessions(account, generation, silent = cached != null)
        }
        restartPolling()
        realtimeCoordinator.selectAccount(account, resolvedAccountKey)
    }
    fun openThread(partnerSteamId: String) {
        val account = activeAccount ?: return
        if (partnerSteamId.isBlank()) return
        val generation = requestGuard.selectThread(partnerSteamId)
        _uiState.value = _uiState.value.copy(
            selectedPartnerSteamId = partnerSteamId,
            thread = null,
            threadLoading = true,
            threadRefreshing = false,
            loadingOlder = false,
            threadFromCache = false,
            threadFailure = null
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) {
                cache.loadThread(account.steamId, partnerSteamId)
            }
            if (!isThreadCurrent(account, partnerSteamId, generation)) return@launch
            val live = _uiState.value.thread
            val hydrated = cached?.copy(messages = mergeSteamChatMessages(cached.messages, live?.messages.orEmpty())) ?: live
            _uiState.value = _uiState.value.copy(
                thread = hydrated,
                threadLoading = hydrated == null,
                threadRefreshing = hydrated != null,
                threadFromCache = cached != null && live == null
            )
            recoverPendingSteamChatOutbox(
                outbox = outbox,
                account = account,
                partnerSteamId = partnerSteamId,
                accountKey = activeAccountKey,
                ioDispatcher = ioDispatcher,
                isCurrent = { isThreadCurrent(account, partnerSteamId, generation) },
                onRecovered = { item ->
                    updateMessage(account, partnerSteamId, item.message)
                    dispatchSend(
                        account = account,
                        partnerSteamId = partnerSteamId,
                        pending = item.message,
                        verifyBeforeSend = item.verifyBeforeSend
                    )
                }
            )
            dataCoordinator.fetchThread(account, partnerSteamId, generation, silent = cached != null)
        }
    }
    fun closeThread() {
        realtimeCoordinator.closeThread(_uiState.value.selectedPartnerSteamId)
        requestGuard.closeThread()
        _uiState.value = _uiState.value.copy(
            selectedPartnerSteamId = null,
            thread = null,
            threadLoading = false,
            threadRefreshing = false,
            loadingOlder = false,
            threadFromCache = false,
            threadFailure = null
        )
    }
    fun refreshSessions() = refreshSessions(silent = false)

    private fun refreshSessions(silent: Boolean) {
        val account = activeAccount ?: return
        val generation = requestGuard.nextSessions()
        _uiState.value = _uiState.value.copy(
            sessionsLoading = _uiState.value.sessions == null,
            sessionsRefreshing = !silent && _uiState.value.sessions != null,
            sessionsFailure = null
        )
        dataCoordinator.fetchSessions(account, generation, silent = silent)
    }
    fun refreshThread() = refreshThread(silent = false)

    private fun refreshThread(silent: Boolean) {
        val account = activeAccount ?: return
        val partnerSteamId = _uiState.value.selectedPartnerSteamId ?: return
        val generation = requestGuard.currentThreadGeneration()
        _uiState.value = _uiState.value.copy(
            threadLoading = _uiState.value.thread == null,
            threadRefreshing = !silent && _uiState.value.thread != null,
            threadFailure = null
        )
        dataCoordinator.fetchThread(account, partnerSteamId, generation, silent = silent)
    }

    fun loadOlder() {
        val account = activeAccount ?: return
        val state = _uiState.value
        val partnerSteamId = state.selectedPartnerSteamId ?: return
        val generation = requestGuard.currentThreadGeneration()
        dataCoordinator.loadOlder(account, partnerSteamId, generation)
    }

    fun sendMessage(body: String) = sendMessage(body, replyToStableId = null)
    fun sendReply(body: String, replyToStableId: String) {
        if (replyToStableId.isNotBlank()) sendMessage(body, replyToStableId)
    }
    private fun sendMessage(body: String, replyToStableId: String?) {
        val normalized = body.trim()
        if (normalized.isBlank()) return
        val account = activeAccount ?: return
        val partnerSteamId = _uiState.value.selectedPartnerSteamId ?: return
        val id = clientMessageId()
        val createdAt = nowMillis()
        val optimistic = newPendingSteamChatMessage(
            accountSteamId = account.steamId,
            partnerSteamId = partnerSteamId,
            body = normalized,
            timestamp = createdAt / 1000L,
            localCreatedAtMillis = createdAt,
            clientMessageId = id,
            replyToStableId = replyToStableId
        )
        updateMessage(account, partnerSteamId, optimistic)
        dispatchSend(account, partnerSteamId, optimistic)
    }

    fun retryMessage(clientMessageId: String) {
        val account = activeAccount ?: return
        val partnerSteamId = _uiState.value.selectedPartnerSteamId ?: return
        val failed = _uiState.value.thread?.messages?.firstOrNull {
            it.clientMessageId == clientMessageId &&
                it.deliveryState == SteamChatDeliveryState.FAILED_RETRYABLE
        } ?: return
        val pending = failed.copy(deliveryState = SteamChatDeliveryState.VERIFYING)
        updateMessage(account, partnerSteamId, pending)
        dispatchSend(
            account,
            partnerSteamId,
            pending,
            verifyBeforeSend = true,
            forceRetry = true
        )
    }

    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        restartPolling()
        realtimeCoordinator.setForeground(active)
        if (active) {
            refreshSessions(silent = true)
            refreshThread(silent = true)
        }
    }

    fun clearThreadFailure() {
        _uiState.value = _uiState.value.copy(threadFailure = null)
    }

    private fun dispatchSend(
        account: SteamAccount,
        partnerSteamId: String,
        pending: SteamChatMessage,
        verifyBeforeSend: Boolean = false,
        forceRetry: Boolean = false
    ) {
        outgoingCoordinator.dispatch(
            account = account,
            partnerSteamId = partnerSteamId,
            accountKey = activeAccountKey.ifBlank { resolveAccountKey(account) },
            pending = pending,
            verifyBeforeSend = verifyBeforeSend,
            forceRetry = forceRetry,
            // Delivery belongs to the account/conversation, not to the currently visible page.
            isCurrent = { true },
            onSessionRefreshed = { refreshedAccount ->
                if (activeAccount?.id == account.id && activeAccount?.steamId == account.steamId) {
                    activeAccount = refreshedAccount
                    activeAccountKey = resolveAccountKey(refreshedAccount)
                    realtimeCoordinator.selectAccount(refreshedAccount, activeAccountKey)
                }
            },
            onUpdate = { updateMessage(account, partnerSteamId, it) }
        )
    }

    private fun updateMessage(
        account: SteamAccount,
        partnerSteamId: String,
        message: SteamChatMessage
    ) {
        if (activeAccount?.id != account.id || activeAccount?.steamId != account.steamId ||
            _uiState.value.selectedPartnerSteamId != partnerSteamId
        ) {
            viewModelScope.launch(ioDispatcher) {
                runSteamChatCatching {
                    val cached = cache.loadThread(account.steamId, partnerSteamId)
                        ?: SteamChatThreadSnapshot(account.steamId, partnerSteamId, emptyList(), false, nowMillis())
                    cache.saveThread(account.steamId, partnerSteamId, cached.copy(
                        messages = mergeSteamChatMessages(cached.messages, listOf(message)),
                        fetchedAt = nowMillis()
                    ))
                }.onFailure { logSteamChatFailure("outgoing_cache", it) }
            }
            return
        }
        val updatedState = _uiState.value.withChatMessage(
            accountSteamId = account.steamId,
            partnerSteamId = partnerSteamId,
            message = message,
            nowMillis = nowMillis()
        )
        _uiState.value = updatedState
        val updatedThread = updatedState.thread ?: return
        val updatedSessions = updatedState.sessions ?: return
        dataCoordinator.persistThread(account, partnerSteamId, updatedThread)
        viewModelScope.launch(ioDispatcher) {
            cache.saveSessions(account.steamId, updatedSessions)
        }
    }
    private fun resolveAccountKey(account: SteamAccount): String = runCatching {
        accountKeyResolver(account).takeIf(String::isNotBlank)
    }.getOrNull() ?: "${account.id}|${account.steamId}"

    private fun onSessionResolved(resolved: SteamAccount) {
        val current = activeAccount ?: return
        if (current.id != resolved.id || current.steamId != resolved.steamId) return
        val credentialsChanged = current.accessToken != resolved.accessToken ||
            current.refreshToken != resolved.refreshToken ||
            current.steamLoginSecure != resolved.steamLoginSecure
        activeAccount = resolved
        if (credentialsChanged) {
            realtimeCoordinator.selectAccount(resolved, activeAccountKey)
        }
    }

    private fun restartPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (!foreground || activeAccount == null) return
        pollingJob = viewModelScope.launch {
            uiState.map { it.realtimeConnected }.distinctUntilChanged().collectLatest { connected ->
                val interval = if (connected) REALTIME_RECONCILIATION_INTERVAL_MILLIS
                else LEGACY_POLL_INTERVAL_MILLIS
                while (isActive) {
                    delay(interval)
                    refreshSessions(silent = true)
                    refreshThread(silent = true)
                }
            }
        }
    }

    private fun isSessionsCurrent(account: SteamAccount, generation: Long): Boolean =
        requestGuard.isSessionsCurrent(account, generation)
    private fun isThreadCurrent(
        account: SteamAccount,
        partnerSteamId: String,
        generation: Long
    ): Boolean = requestGuard.isThreadCurrent(account, partnerSteamId, generation)
    companion object {
        private const val LEGACY_POLL_INTERVAL_MILLIS = 15_000L
        private const val REALTIME_RECONCILIATION_INTERVAL_MILLIS = 180_000L

        fun factory(context: Context): ViewModelProvider.Factory =
            SteamChatViewModelFactory.create(context)
    }
}
