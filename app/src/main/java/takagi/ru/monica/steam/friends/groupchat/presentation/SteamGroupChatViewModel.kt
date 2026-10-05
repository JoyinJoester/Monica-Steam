package takagi.ru.monica.steam.friends.groupchat.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatCache
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatOutbox
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatPreferencesCache
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatRoomOutbox
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatService
import takagi.ru.monica.steam.friends.groupchat.avatar.data.SteamGroupAvatarUploader
import takagi.ru.monica.steam.friends.groupchat.avatar.domain.SteamGroupAvatarUploadGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatChannelCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatAdminSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGroupsSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatHistoryBoundary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatInviteLink
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoleActions
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReactionType
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReportReason
import takagi.ru.monica.steam.friends.groupchat.domain.steamGroupAvatarUrl
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatThreadSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.mergeSteamGroupMessages
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeEvent
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeGateway
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

data class SteamGroupChatUiState(
    val accountSteamId: String = "",
    val groups: List<SteamGroupChatSummary> = emptyList(),
    val selectedGroupId: String? = null,
    val selectedChatId: String? = null,
    val thread: SteamGroupChatThreadSnapshot? = null,
    val groupsLoading: Boolean = false,
    val groupsRefreshing: Boolean = false,
    val threadLoading: Boolean = false,
    val loadingOlder: Boolean = false,
    /** Failure of the background group-list read, kept separate from room/action errors. */
    val groupsFailure: Boolean = false,
    val creatingGroup: Boolean = false,
    val updatingGroup: Boolean = false,
    val updatingGroupAvatar: Boolean = false,
    val channelActionLoading: Boolean = false,
    val adminSnapshot: SteamGroupChatAdminSnapshot? = null,
    val adminLoading: Boolean = false,
    val adminActionLoading: Boolean = false,
    val createdInviteLink: SteamGroupChatInviteLink? = null,
    val createdGroupId: String? = null,
    val realtimeConnected: Boolean = false,
    val failure: String? = null
)

class SteamGroupChatViewModel(
    private val gateway: SteamGroupChatGateway,
    private val cache: SteamGroupChatCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newClientId: () -> String = { UUID.randomUUID().toString() },
    private val realtime: SteamGroupChatRealtimeGateway? = null,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val avatarUploader: SteamGroupAvatarUploadGateway? = null,
    private val outbox: SteamGroupChatOutbox? = null,
    private val accountKeyResolver: (SteamAccount) -> String = { current ->
        "${current.id}|${current.steamId}"
    }
) : ViewModel() {
    private val _state = MutableStateFlow(SteamGroupChatUiState())
    val state: StateFlow<SteamGroupChatUiState> = _state.asStateFlow()
    private var account: SteamAccount? = null
    private var activeAccountKey: String = ""
    private var accountGeneration = 0L
    private var roomGeneration = 0L
    private var foreground = false
    private var pollingJob: Job? = null
    private var realtimeJob: Job? = null
    private var avatarResolutionJob: Job? = null
    private val pendingAvatarUrls = mutableMapOf<String, PendingGroupAvatar>()
    private val outgoingCoordinator = SteamGroupChatOutgoingCoordinator(
        scope = viewModelScope,
        gateway = gateway,
        sessionResolver = sessionResolver,
        ioDispatcher = ioDispatcher,
        outbox = outbox
    )

    fun selectAccount(account: SteamAccount?) {
        val resolvedAccountKey = account?.let(::resolveAccountKey).orEmpty()
        if (this.account?.id == account?.id &&
            this.account?.steamId == account?.steamId &&
            activeAccountKey == resolvedAccountKey
        ) {
            this.account = account
            restartRealtime()
            restartPolling()
            return
        }
        this.account = account
        activeAccountKey = resolvedAccountKey
        accountGeneration++
        roomGeneration++
        avatarResolutionJob?.cancel()
        avatarResolutionJob = null
        pendingAvatarUrls.clear()
        if (account == null) {
            _state.value = SteamGroupChatUiState(failure = "Steam account required")
            restartRealtime()
            restartPolling()
            return
        }
        val currentGeneration = accountGeneration
        _state.value = SteamGroupChatUiState(accountSteamId = account.steamId, groupsLoading = true)
        restartRealtime()
        restartPolling()
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.loadGroups(account.steamId) }
            if (!isCurrent(account, currentGeneration)) return@launch
            _state.value = _state.value.copy(
                groups = cached?.groups.orEmpty(),
                groupsLoading = cached == null,
                groupsRefreshing = cached != null
            )
            fetchGroups(account, currentGeneration)
        }
    }

    fun refreshGroups() {
        val current = account ?: return
        _state.value = _state.value.copy(
            groupsRefreshing = _state.value.groups.isNotEmpty(),
            groupsLoading = _state.value.groups.isEmpty(),
            groupsFailure = false
        )
        fetchGroups(current, accountGeneration)
    }

    fun openRoom(groupId: String, chatId: String) {
        val current = account ?: return
        if (groupId.isBlank() || chatId.isBlank()) return
        val sameGroup = _state.value.selectedGroupId == groupId
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = ++roomGeneration
        _state.value = _state.value.copy(
            selectedGroupId = groupId,
            selectedChatId = chatId,
            thread = null,
            threadLoading = true,
            adminSnapshot = _state.value.adminSnapshot.takeIf { sameGroup },
            createdInviteLink = null,
            failure = null
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.loadThread(current.steamId, groupId, chatId) }
            if (!isRoomCurrent(
                    current,
                    groupId,
                    chatId,
                    currentAccountGeneration,
                    currentRoomGeneration
                )
            ) return@launch
            val recoveryThread = cached ?: SteamGroupChatThreadSnapshot(
                accountSteamId = current.steamId,
                groupId = groupId,
                chatId = chatId,
                messages = emptyList(),
                moreAvailable = false,
                fetchedAt = nowMillis()
            )
            _state.value = _state.value.copy(
                thread = recoveryThread,
                threadLoading = cached == null
            )
            recoverPendingSteamGroupChatOutbox(
                outbox = outbox,
                account = current,
                groupId = groupId,
                chatId = chatId,
                accountKey = activeAccountKey.ifBlank { resolveAccountKey(current) },
                ioDispatcher = ioDispatcher,
                isCurrent = {
                    isRoomCurrent(
                        current,
                        groupId,
                        chatId,
                        currentAccountGeneration,
                        currentRoomGeneration
                    )
                },
                onRecovered = { item ->
                    updateMessage(item.message)
                    dispatchSend(
                        account = current,
                        pending = item.message,
                        verifyBeforeSend = item.verifyBeforeSend
                    )
                }
            )
            fetchThread(
                current,
                groupId,
                chatId,
                currentAccountGeneration,
                currentRoomGeneration
            )
        }
    }

    fun closeRoom() {
        roomGeneration++
        _state.value = _state.value.copy(selectedGroupId = null, selectedChatId = null, thread = null, threadLoading = false)
    }

    fun refreshThread() {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        val chatId = _state.value.selectedChatId ?: return
        _state.value = _state.value.copy(threadLoading = _state.value.thread == null, failure = null)
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = roomGeneration
        viewModelScope.launch {
            fetchThread(
                current,
                groupId,
                chatId,
                currentAccountGeneration,
                currentRoomGeneration
            )
        }
    }

    fun loadOlder() {
        val current = account ?: return
        val thread = _state.value.thread ?: return
        if (!thread.moreAvailable || _state.value.loadingOlder) return
        val oldest = thread.messages.firstOrNull() ?: return
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = roomGeneration
        _state.value = _state.value.copy(loadingOlder = true)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.getHistory(
                        prepared,
                        thread.groupId,
                        thread.chatId,
                        SteamGroupChatHistoryBoundary(oldest.timestamp, oldest.ordinal)
                    )
                }
            } }
            if (!isRoomCurrent(
                    current,
                    thread.groupId,
                    thread.chatId,
                    currentAccountGeneration,
                    currentRoomGeneration
                )
            ) return@launch
            result.fold(
                onSuccess = { page ->
                    val updated = thread.copy(
                        messages = mergeSteamGroupMessages(page.messages, _state.value.thread?.messages.orEmpty()),
                        moreAvailable = page.moreAvailable,
                        fetchedAt = nowMillis()
                    )
                    updateThread(updated)
                    _state.value = _state.value.copy(loadingOlder = false)
                },
                onFailure = { _state.value = _state.value.copy(loadingOlder = false, failure = it.groupChatMessage()) }
            )
        }
    }

    fun sendMessage(body: String) {
        val current = account ?: return
        val thread = _state.value.thread ?: return
        val normalized = body.trim()
        if (normalized.isBlank()) return
        val createdAtMillis = nowMillis()
        val optimistic = SteamGroupChatMessage(
            groupId = thread.groupId,
            chatId = thread.chatId,
            senderSteamId = current.steamId,
            timestamp = createdAtMillis / 1_000L,
            ordinal = Int.MAX_VALUE,
            body = normalized,
            clientMessageId = newClientId(),
            localCreatedAtMillis = createdAtMillis,
            deliveryState = SteamGroupChatDeliveryState.QUEUED
        )
        updateMessage(optimistic)
        dispatchSend(current, optimistic)
    }

    fun retryMessage(clientMessageId: String) {
        val current = account ?: return
        val failed = _state.value.thread?.messages?.firstOrNull {
            it.clientMessageId == clientMessageId &&
                it.deliveryState in setOf(
                    SteamGroupChatDeliveryState.FAILED_RETRYABLE,
                    SteamGroupChatDeliveryState.FAILED
                )
        } ?: return
        val pending = failed.copy(deliveryState = SteamGroupChatDeliveryState.VERIFYING)
        updateMessage(pending)
        dispatchSend(
            account = current,
            pending = pending,
            verifyBeforeSend = true,
            forceRetry = true
        )
    }

    fun createGroup(name: String, inviteeSteamIds: List<String>) {
        val current = account ?: return
        if (_state.value.creatingGroup) return
        _state.value = _state.value.copy(creatingGroup = true, createdGroupId = null, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.createGroup(prepared, SteamGroupChatCreateRequest(name, inviteeSteamIds))
                }
            } }
            result.fold(
                onSuccess = { groupId ->
                    _state.value = _state.value.copy(creatingGroup = false, createdGroupId = groupId)
                    refreshGroups()
                },
                onFailure = { _state.value = _state.value.copy(creatingGroup = false, failure = it.groupChatMessage()) }
            )
        }
    }

    fun inviteFriend(steamId: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        val chatId = _state.value.selectedChatId ?: return
        viewModelScope.launch {
            runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.inviteFriend(prepared, groupId, chatId, steamId)
                }
            } }
                .onFailure { _state.value = _state.value.copy(failure = it.groupChatMessage()) }
        }
    }

    fun updateGroup(name: String, tagline: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.updatingGroup || name.isBlank()) return
        _state.value = _state.value.copy(updatingGroup = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.updateGroup(prepared, groupId, name, tagline)
                }
            } }
            result.fold(
                onSuccess = {
                    val groups = _state.value.groups.map { group ->
                        if (group.groupId == groupId) group.copy(name = name.trim(), tagline = tagline.trim()) else group
                    }
                    _state.value = _state.value.copy(groups = groups, updatingGroup = false)
                    withContext(ioDispatcher) {
                        cache.saveGroups(SteamGroupChatGroupsSnapshot(current.steamId, groups, nowMillis()))
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(updatingGroup = false, failure = it.groupChatMessage())
                }
            )
        }
    }

    fun updateGroupAvatar(rawUri: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        val uploader = avatarUploader ?: return
        if (_state.value.updatingGroupAvatar || rawUri.isBlank()) return
        _state.value = _state.value.copy(updatingGroupAvatar = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    val sha = uploader.upload(prepared, rawUri)
                    val expectedAvatarUrl = steamGroupAvatarUrl(sha)
                    val verifiedGroups = try {
                        gateway.updateGroupAvatar(prepared, groupId, sha)
                        null
                    } catch (error: IOException) {
                        val refreshed = runCatching { gateway.getMyGroups(prepared) }.getOrNull()
                        val changedOnSteam = refreshed?.any { group ->
                            group.groupId == groupId && group.avatarUrl == expectedAvatarUrl
                        } == true
                        if (!changedOnSteam) throw error
                        refreshed
                    }
                    SteamGroupAvatarUpdateResult(sha, verifiedGroups)
                }
            } }
            result.fold(
                onSuccess = { update ->
                    val avatarUrl = steamGroupAvatarUrl(update.sha)
                    pendingAvatarUrls[groupId] = PendingGroupAvatar(
                        url = avatarUrl,
                        expiresAtMillis = nowMillis() + AVATAR_OVERRIDE_TTL_MILLIS
                    )
                    val groups = update.verifiedGroups ?: _state.value.groups.map { group ->
                        if (group.groupId == groupId) group.copy(avatarUrl = avatarUrl) else group
                    }
                    _state.value = _state.value.copy(
                        groups = groups,
                        updatingGroupAvatar = false
                    )
                    withContext(ioDispatcher) {
                        cache.saveGroups(SteamGroupChatGroupsSnapshot(current.steamId, groups, nowMillis()))
                    }
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        updatingGroupAvatar = false,
                        failure = error.groupChatMessage()
                    )
                }
            )
        }
    }

    fun createChannel(name: String, allowVoice: Boolean) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.channelActionLoading) return
        _state.value = _state.value.copy(channelActionLoading = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.createChannel(
                        prepared,
                        groupId,
                        SteamGroupChatChannelCreateRequest(name, allowVoice)
                    )
                }
            } }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(channelActionLoading = false)
                    refreshGroups()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        channelActionLoading = false,
                        failure = it.groupChatMessage()
                    )
                }
            )
        }
    }

    fun renameChannel(chatId: String, name: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.channelActionLoading) return
        _state.value = _state.value.copy(channelActionLoading = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.renameChannel(prepared, groupId, chatId, name)
                }
            } }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(channelActionLoading = false)
                    refreshGroups()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        channelActionLoading = false,
                        failure = it.groupChatMessage()
                    )
                }
            )
        }
    }

    fun deleteChannel(chatId: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.channelActionLoading) return
        _state.value = _state.value.copy(channelActionLoading = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.deleteChannel(prepared, groupId, chatId)
                }
            } }
            result.fold(
                onSuccess = {
                    if (_state.value.selectedChatId == chatId) {
                        val fallback = _state.value.groups
                            .firstOrNull { it.groupId == groupId }
                            ?.rooms
                            ?.firstOrNull { it.chatId != chatId }
                            ?.chatId
                        if (fallback != null) openRoom(groupId, fallback) else closeRoom()
                    }
                    _state.value = _state.value.copy(channelActionLoading = false)
                    refreshGroups()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        channelActionLoading = false,
                        failure = it.groupChatMessage()
                    )
                }
            )
        }
    }

    fun reorderChannel(chatId: String, moveAfterChatId: String?) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.channelActionLoading) return
        _state.value = _state.value.copy(channelActionLoading = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.reorderChannel(prepared, groupId, chatId, moveAfterChatId)
                }
            } }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(channelActionLoading = false)
                    refreshGroups()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        channelActionLoading = false,
                        failure = it.groupChatMessage()
                    )
                }
            )
        }
    }

    fun refreshAdminSnapshot() {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.adminLoading) return
        _state.value = _state.value.copy(adminLoading = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.getAdminSnapshot(prepared, groupId)
                }
            } }
            result.fold(
                onSuccess = { snapshot ->
                    if (_state.value.selectedGroupId == groupId) {
                        _state.value = _state.value.copy(
                            adminSnapshot = snapshot,
                            adminLoading = false
                        )
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        adminLoading = false,
                        failure = it.groupChatMessage()
                    )
                }
            )
        }
    }

    fun createInviteLink(secondsValid: Long, chatId: String?) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.adminActionLoading) return
        _state.value = _state.value.copy(
            adminActionLoading = true,
            createdInviteLink = null,
            failure = null
        )
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.createInviteLink(prepared, groupId, secondsValid, chatId)
                }
            } }
            result.fold(
                onSuccess = { link ->
                    val snapshot = _state.value.adminSnapshot
                    _state.value = _state.value.copy(
                        adminActionLoading = false,
                        createdInviteLink = link,
                        adminSnapshot = snapshot?.copy(
                            inviteLinks = (snapshot.inviteLinks + link)
                                .distinctBy { it.inviteCode }
                        )
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        adminActionLoading = false,
                        failure = it.groupChatMessage()
                    )
                }
            )
        }
    }

    fun deleteInviteLink(inviteCode: String) = runAdminAction { prepared, groupId ->
        gateway.deleteInviteLink(prepared, groupId, inviteCode)
    }

    fun revokeInvite(steamId: String) = runAdminAction { prepared, groupId ->
        gateway.revokeInvite(prepared, groupId, steamId)
    }

    fun setUserBanState(steamId: String, banned: Boolean) = runAdminAction { prepared, groupId ->
        gateway.setUserBanState(prepared, groupId, steamId, banned)
    }

    fun kickUser(steamId: String, expirationSeconds: Int) = runAdminAction { prepared, groupId ->
        gateway.kickUser(prepared, groupId, steamId, expirationSeconds)
    }

    fun muteUser(steamId: String, expirationSeconds: Int) = runAdminAction { prepared, groupId ->
        gateway.muteUser(prepared, groupId, steamId, expirationSeconds)
    }

    fun createRole(name: String) = runAdminAction { prepared, groupId ->
        gateway.createRole(prepared, groupId, name)
    }

    fun renameRole(roleId: String, name: String) = runAdminAction { prepared, groupId ->
        gateway.renameRole(prepared, groupId, roleId, name)
    }

    fun deleteRole(roleId: String) = runAdminAction { prepared, groupId ->
        gateway.deleteRole(prepared, groupId, roleId)
    }

    fun replaceRoleActions(actions: SteamGroupChatRoleActions) = runAdminAction { prepared, groupId ->
        gateway.replaceRoleActions(prepared, groupId, actions)
    }

    fun addRoleToUser(roleId: String, steamId: String) = runAdminAction { prepared, groupId ->
        gateway.addRoleToUser(prepared, groupId, roleId, steamId)
    }

    fun removeRoleFromUser(roleId: String, steamId: String) = runAdminAction { prepared, groupId ->
        gateway.removeRoleFromUser(prepared, groupId, roleId, steamId)
    }

    fun clearCreatedInviteLink() {
        _state.value = _state.value.copy(createdInviteLink = null)
    }

    fun updateMessageReaction(
        message: SteamGroupChatMessage,
        type: SteamGroupChatReactionType,
        reaction: String,
        add: Boolean = true
    ) {
        val current = account ?: return
        viewModelScope.launch {
            runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.updateMessageReaction(prepared, message, type, reaction, add)
                }
            } }.fold(
                onSuccess = { refreshThread() },
                onFailure = { _state.value = _state.value.copy(failure = it.groupChatMessage()) }
            )
        }
    }

    fun reportMessage(message: SteamGroupChatMessage, reason: SteamGroupChatReportReason) {
        val current = account ?: return
        viewModelScope.launch {
            runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.reportMessage(prepared, message, reason)
                }
            } }.onFailure {
                _state.value = _state.value.copy(failure = it.groupChatMessage())
            }
        }
    }

    fun deleteMessage(message: SteamGroupChatMessage) {
        val current = account ?: return
        viewModelScope.launch {
            runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared -> gateway.deleteMessage(prepared, message) }
            } }.fold(
                onSuccess = {
                    val thread = _state.value.thread ?: return@fold
                    updateThread(thread.copy(
                        messages = thread.messages.map { currentMessage ->
                            if (currentMessage.stableId == message.stableId) {
                                currentMessage.copy(deleted = true)
                            } else currentMessage
                        },
                        fetchedAt = nowMillis()
                    ))
                },
                onFailure = { _state.value = _state.value.copy(failure = it.groupChatMessage()) }
            )
        }
    }

    fun clearCreatedGroup() { _state.value = _state.value.copy(createdGroupId = null) }
    fun clearFailure() { _state.value = _state.value.copy(failure = null) }

    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        restartRealtime()
        restartPolling()
        if (active) {
            refreshGroups()
            refreshThread()
        }
    }

    private fun restartRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        val current = account
        val gateway = realtime
        if (!foreground || current == null || gateway == null) {
            _state.value = _state.value.copy(realtimeConnected = false)
            return
        }
        val currentGeneration = accountGeneration
        realtimeJob = viewModelScope.launch {
            try {
                gateway.events(current).collect { event ->
                    if (!isCurrent(current, currentGeneration)) return@collect
                    when (event) {
                        is SteamGroupChatRealtimeEvent.ConnectionChanged -> {
                            if (_state.value.realtimeConnected != event.connected) {
                                _state.value = _state.value.copy(
                                    realtimeConnected = event.connected
                                )
                                restartPolling()
                                if (event.connected) {
                                    refreshGroups()
                                    refreshThread()
                                }
                            }
                        }
                        is SteamGroupChatRealtimeEvent.Message ->
                            applyRealtimeMessage(current, event.message)
                        is SteamGroupChatRealtimeEvent.MessageModified ->
                            applyRealtimeModification(event)
                        is SteamGroupChatRealtimeEvent.Acknowledged ->
                            applyRealtimeAcknowledgement(event)
                        is SteamGroupChatRealtimeEvent.RoomChanged -> {
                            refreshGroups()
                            if (_state.value.selectedGroupId == event.groupId) refreshThread()
                        }
                        is SteamGroupChatRealtimeEvent.HeaderChanged -> {
                            val updatedGroups = _state.value.groups.map { group ->
                                if (group.groupId != event.groupId) group else group.copy(
                                    name = event.name ?: group.name,
                                    tagline = event.tagline ?: group.tagline,
                                    avatarUrl = event.avatarUrl ?: group.avatarUrl
                                )
                            }
                            _state.value = _state.value.copy(groups = updatedGroups)
                            viewModelScope.launch(ioDispatcher) {
                                cache.saveGroups(
                                    SteamGroupChatGroupsSnapshot(
                                        accountSteamId = current.steamId,
                                        groups = updatedGroups,
                                        fetchedAt = nowMillis()
                                    )
                                )
                            }
                            refreshGroups()
                        }
                        is SteamGroupChatRealtimeEvent.Disconnected -> {
                            if (event.groupIds.isEmpty() ||
                                _state.value.selectedGroupId in event.groupIds
                            ) {
                                refreshGroups()
                                if (_state.value.selectedChatId != null) refreshThread()
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (isCurrent(current, currentGeneration) && _state.value.realtimeConnected) {
                    _state.value = _state.value.copy(realtimeConnected = false)
                    restartPolling()
                }
            }
        }
    }

    private fun applyRealtimeMessage(
        current: SteamAccount,
        message: SteamGroupChatMessage
    ) {
        val currentOutbox = outbox
        if (currentOutbox != null && message.senderSteamId == current.steamId) {
            val accountKey = activeAccountKey.ifBlank { resolveAccountKey(current) }
            viewModelScope.launch(ioDispatcher) {
                runGroupChatCatching {
                    completeMatchingRealtimeGroupOutboxEcho(
                        outbox = currentOutbox,
                        account = current,
                        accountKey = accountKey,
                        message = message
                    )
                }.onFailure { logGroupChatSendFailure("realtime_outbox_complete", it) }
            }
        }
        val thread = _state.value.thread
        if (thread?.groupId == message.groupId && thread.chatId == message.chatId) {
            val updated = thread.copy(
                messages = mergeSteamGroupMessages(thread.messages, listOf(message)),
                fetchedAt = nowMillis()
            )
            updateThread(updated)
            acknowledgeLatest(current, updated)
            return
        }
        val groups = _state.value.groups.map { group ->
            if (group.groupId != message.groupId) return@map group
            val updatedRooms = group.rooms.map { room ->
                if (room.chatId != message.chatId ||
                    room.lastMessageTimestamp > message.timestamp ||
                    (room.lastMessageTimestamp == message.timestamp &&
                        room.lastMessage == message.body)
                ) return@map room
                room.copy(
                    lastMessageTimestamp = message.timestamp,
                    lastMessage = message.body,
                    lastSenderSteamId = message.senderSteamId,
                    unread = message.senderSteamId != current.steamId
                )
            }
            group.copy(
                rooms = updatedRooms,
                unreadCount = updatedRooms.count { it.unread }
            )
        }
        _state.value = _state.value.copy(groups = groups)
        viewModelScope.launch(ioDispatcher) {
            cache.saveGroups(
                SteamGroupChatGroupsSnapshot(current.steamId, groups, nowMillis())
            )
        }
    }

    private fun applyRealtimeModification(
        event: SteamGroupChatRealtimeEvent.MessageModified
    ) {
        val thread = _state.value.thread ?: return
        if (thread.groupId != event.groupId || thread.chatId != event.chatId) return
        val updatedMessages = thread.messages.map { message ->
            val modification = event.changes.firstOrNull {
                it.timestamp == message.timestamp && it.ordinal == message.ordinal
            }
            if (modification != null) {
                message.copy(deleted = modification.deleted)
            } else {
                message
            }
        }
        if (updatedMessages == thread.messages) return
        updateThread(thread.copy(messages = updatedMessages, fetchedAt = nowMillis()))
    }

    private fun applyRealtimeAcknowledgement(
        event: SteamGroupChatRealtimeEvent.Acknowledged
    ) {
        val groups = _state.value.groups.map { group ->
            if (group.groupId != event.groupId) return@map group
            val updatedRooms = group.rooms.map { room ->
                if (room.chatId != event.chatId) return@map room
                room.copy(
                    lastAcknowledgedTimestamp = maxOf(
                        room.lastAcknowledgedTimestamp,
                        event.timestamp
                    ),
                    unread = room.lastMessageTimestamp > event.timestamp
                )
            }
            group.copy(
                rooms = updatedRooms,
                unreadCount = updatedRooms.count { it.unread }
            )
        }
        _state.value = _state.value.copy(groups = groups)
    }

    private fun fetchGroups(current: SteamAccount, currentGeneration: Long) {
        avatarResolutionJob?.cancel()
        avatarResolutionJob = null
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared -> gateway.getMyGroups(prepared) }
            } }
            if (!isCurrent(current, currentGeneration)) return@launch
            result.fold(
                onSuccess = { groups ->
                    val reconciledGroups = applyPendingAvatarUrls(groups)
                    val snapshot = SteamGroupChatGroupsSnapshot(current.steamId, reconciledGroups, nowMillis())
                    withContext(ioDispatcher) { cache.saveGroups(snapshot) }
                    if (!isCurrent(current, currentGeneration)) return@launch
                    _state.value = _state.value.copy(
                        groups = reconciledGroups,
                        groupsLoading = false,
                        groupsRefreshing = false,
                        groupsFailure = false,
                        failure = null
                    )
                    resolveMissingGroupAvatars(current, currentGeneration, reconciledGroups)
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        groupsLoading = false,
                        groupsRefreshing = false,
                        // Cached conversations remain usable during a transient
                        // CM/community route failure.
                        groupsFailure = _state.value.groups.isEmpty()
                    )
                }
            )
        }
    }

    private fun resolveMissingGroupAvatars(
        current: SteamAccount,
        currentGeneration: Long,
        groups: List<SteamGroupChatSummary>
    ) {
        val missing = groups.filter { it.avatarUrl.isBlank() }
        if (missing.isEmpty()) return
        avatarResolutionJob?.cancel()
        avatarResolutionJob = viewModelScope.launch {
            val prepared = runCatchingCancellable {
                withContext(ioDispatcher) { sessionResolver.resolveOrKeep(current) }
            }.getOrNull() ?: return@launch
            for (group in missing) {
                if (!isCurrent(current, currentGeneration)) return@launch
                val resolved = runCatchingCancellable {
                    withContext(ioDispatcher) {
                        gateway.getGroupAvatarUrl(prepared, group.groupId)
                    }
                }.getOrNull().orEmpty()
                if (resolved.isBlank() || !isCurrent(current, currentGeneration)) continue
                val updated = _state.value.groups.map { existing ->
                    if (existing.groupId == group.groupId && existing.avatarUrl.isBlank()) {
                        existing.copy(avatarUrl = resolved)
                    } else existing
                }
                _state.value = _state.value.copy(groups = updated)
            }
            if (!isCurrent(current, currentGeneration)) return@launch
            withContext(ioDispatcher) {
                cache.saveGroups(
                    SteamGroupChatGroupsSnapshot(
                        accountSteamId = current.steamId,
                        groups = _state.value.groups,
                        fetchedAt = nowMillis()
                    )
                )
            }
        }
    }

    private fun applyPendingAvatarUrls(
        groups: List<SteamGroupChatSummary>
    ): List<SteamGroupChatSummary> {
        val now = nowMillis()
        val updated = groups.map { group ->
            val pending = pendingAvatarUrls[group.groupId]
            when {
                pending == null -> group
                group.avatarUrl == pending.url -> {
                    pendingAvatarUrls.remove(group.groupId)
                    group
                }
                pending.expiresAtMillis <= now -> {
                    pendingAvatarUrls.remove(group.groupId)
                    group
                }
                else -> group.copy(avatarUrl = pending.url)
            }
        }
        val returnedIds = groups.mapTo(mutableSetOf(), SteamGroupChatSummary::groupId)
        pendingAvatarUrls.keys.removeAll { it !in returnedIds }
        return updated
    }

    private suspend fun fetchThread(
        current: SteamAccount,
        groupId: String,
        chatId: String,
        currentAccountGeneration: Long,
        currentRoomGeneration: Long
    ) {
        val result = runCatchingCancellable { withContext(ioDispatcher) {
            withPreparedSession(current) { prepared ->
                gateway.getHistory(prepared, groupId, chatId)
            }
        } }
        if (!isRoomCurrent(
                current,
                groupId,
                chatId,
                currentAccountGeneration,
                currentRoomGeneration
            )
        ) return
        result.fold(
            onSuccess = { page ->
                val snapshot = SteamGroupChatThreadSnapshot(
                    current.steamId, groupId, chatId,
                    mergeSteamGroupMessages(_state.value.thread?.messages.orEmpty(), page.messages),
                    page.moreAvailable, nowMillis()
                )
                updateThread(snapshot)
                _state.value = _state.value.copy(threadLoading = false, failure = null)
                acknowledgeLatest(current, snapshot)
            },
            onFailure = { error ->
                _state.value = _state.value.copy(
                    threadLoading = false,
                    // A short CM outage is already recovered by the realtime supervisor
                    // and the polling loop. Keep the cached/recovered thread usable instead
                    // of presenting the transport failure as a fatal room error.
                    failure = error.takeUnless(Throwable::isTransientGroupChatCmFailure)
                        ?.groupChatMessage()
                )
            }
        )
    }

    private fun acknowledgeLatest(current: SteamAccount, snapshot: SteamGroupChatThreadSnapshot) {
        val timestamp = snapshot.messages.lastOrNull()?.timestamp?.takeIf { it > 0L } ?: return
        viewModelScope.launch(ioDispatcher) {
            runCatchingCancellable {
                withPreparedSession(current) { prepared ->
                    gateway.acknowledge(prepared, snapshot.groupId, snapshot.chatId, timestamp)
                }
            }
        }
    }

    private fun updateMessage(message: SteamGroupChatMessage) {
        val thread = _state.value.thread ?: return
        updateThread(thread.copy(messages = mergeSteamGroupMessages(thread.messages, listOf(message)), fetchedAt = nowMillis()))
    }

    private fun dispatchSend(
        account: SteamAccount,
        pending: SteamGroupChatMessage,
        verifyBeforeSend: Boolean = false,
        forceRetry: Boolean = false
    ) {
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = roomGeneration
        outgoingCoordinator.dispatch(
            account = account,
            accountKey = activeAccountKey.ifBlank { resolveAccountKey(account) },
            pending = pending,
            verifyBeforeSend = verifyBeforeSend,
            forceRetry = forceRetry,
            isCurrent = {
                isRoomCurrent(
                    account,
                    pending.groupId,
                    pending.chatId,
                    currentAccountGeneration,
                    currentRoomGeneration
                )
            },
            onSessionRefreshed = ::onSessionRefreshed,
            onUpdate = ::updateMessage
        )
    }

    private fun runAdminAction(
        block: suspend (SteamAccount, String) -> Unit
    ) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.adminActionLoading) return
        _state.value = _state.value.copy(adminActionLoading = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared -> block(prepared, groupId) }
            } }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(adminActionLoading = false)
                    refreshAdminSnapshot()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        adminActionLoading = false,
                        failure = it.groupChatMessage()
                    )
                }
            )
        }
    }

    private fun updateThread(snapshot: SteamGroupChatThreadSnapshot) {
        _state.value = _state.value.copy(thread = snapshot)
        viewModelScope.launch(ioDispatcher) { cache.saveThread(snapshot) }
    }

    private suspend fun <T> withPreparedSession(
        current: SteamAccount,
        block: suspend (SteamAccount) -> T
    ): T {
        val prepared = sessionResolver.resolveOrKeep(current)
        if (account?.id == current.id && account?.steamId == current.steamId) {
            account = prepared
        }
        return block(prepared)
    }

    private fun resolveAccountKey(current: SteamAccount): String = runCatching {
        accountKeyResolver(current).takeIf(String::isNotBlank)
    }.getOrNull() ?: "${current.id}|${current.steamId}"

    private fun onSessionRefreshed(refreshed: SteamAccount) {
        val current = account ?: return
        if (current.id != refreshed.id || current.steamId != refreshed.steamId) return
        val credentialsChanged = current.accessToken != refreshed.accessToken ||
            current.refreshToken != refreshed.refreshToken ||
            current.steamLoginSecure != refreshed.steamLoginSecure
        account = refreshed
        if (credentialsChanged) restartRealtime()
    }

    private fun isCurrent(current: SteamAccount, expectedGeneration: Long): Boolean =
        account?.id == current.id && account?.steamId == current.steamId &&
            accountGeneration == expectedGeneration

    private fun isRoomCurrent(
        current: SteamAccount,
        groupId: String,
        chatId: String,
        expectedAccountGeneration: Long,
        expectedRoomGeneration: Long
    ) = isCurrent(current, expectedAccountGeneration) &&
        roomGeneration == expectedRoomGeneration &&
        _state.value.selectedGroupId == groupId &&
        _state.value.selectedChatId == chatId

    private fun restartPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (!foreground || account == null) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(
                    if (_state.value.realtimeConnected) REALTIME_RECONCILIATION_MILLIS
                    else LEGACY_POLLING_MILLIS
                )
                if (_state.value.selectedChatId != null) refreshThread() else refreshGroups()
            }
        }
    }

    companion object {
        private const val LEGACY_POLLING_MILLIS = 15_000L
        private const val REALTIME_RECONCILIATION_MILLIS = 60_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val sourceRepository = takagi.ru.monica.steam.data.SteamAccountSourceRepository
                .get(appContext)
            val resolver = sourceRepository.sessionResolver()
            val accountKeyResolver = { account: SteamAccount ->
                sourceRepository.sessionHandle(account)?.stableKey
                    ?: takagi.ru.monica.steam.network.cm.steamCmAccountKey(account)
            }
            val cm = takagi.ru.monica.steam.network.cm.SteamCmClient(accountKeyResolver)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamGroupChatViewModel(
                        gateway = SteamGroupChatService(cm = cm),
                        cache = SteamGroupChatPreferencesCache(appContext),
                        realtime = takagi.ru.monica.steam.friends.groupchat.data
                            .SteamGroupChatRealtimeService(cm, resolver),
                        sessionResolver = resolver,
                        avatarUploader = SteamGroupAvatarUploader(appContext),
                        outbox = SteamGroupChatRoomOutbox.from(appContext),
                        accountKeyResolver = accountKeyResolver
                    ) as T
            }
        }
    }
}

private data class SteamGroupAvatarUpdateResult(
    val sha: ByteArray,
    val verifiedGroups: List<SteamGroupChatSummary>?
)

private data class PendingGroupAvatar(
    val url: String,
    val expiresAtMillis: Long
)

private const val AVATAR_OVERRIDE_TTL_MILLIS = 10 * 60 * 1_000L

internal fun Throwable.isTransientGroupChatCmFailure(): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        when (current) {
            is SocketTimeoutException,
            is ConnectException,
            is UnknownHostException -> return true
            is IOException -> {
                val detail = current.message.orEmpty()
                if (TRANSIENT_GROUP_CHAT_NETWORK_MARKERS.any { marker ->
                        detail.contains(marker, ignoreCase = true)
                    }
                ) return true
            }
        }
        current = current.cause
    }
    return false
}

private fun Throwable.groupChatMessage(): String = when {
    isTransientGroupChatCmFailure() -> "Steam 聊天服务暂时不可用，正在重新连接"
    else -> message
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { it.contains("Steam CM", ignoreCase = true) }
        ?.take(220)
        ?: "Steam 群聊暂时不可用，请稍后重试"
}

private val TRANSIENT_GROUP_CHAT_NETWORK_MARKERS = listOf(
    "Steam CM is unavailable",
    "Steam CM logon timed out",
    "timeout",
    "timed out",
    "connection",
    "socket",
    "network is unreachable",
    "route to host",
    "broken pipe",
    "stream was reset"
)

private suspend inline fun <T> runCatchingCancellable(
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
