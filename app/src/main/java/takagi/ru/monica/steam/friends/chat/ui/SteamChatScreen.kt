package takagi.ru.monica.steam.friends.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.hasAuthenticatedSession
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatViewModel
import takagi.ru.monica.steam.friends.chat.actions.presentation.SteamChatMessageActionResult
import takagi.ru.monica.steam.friends.chat.actions.presentation.SteamChatMessageActionViewModel
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaViewModel
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel
import takagi.ru.monica.steam.friends.ui.SteamOfficialAddFriendDialog
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatViewModel
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationType
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
fun SteamChatScreen(
    searchQuery: String = "",
    refreshRequest: Long = 0L,
    standalone: Boolean = false,
    requestedPartnerSteamId: String? = null,
    onConsumeRequestedPartner: () -> Unit = {},
    requestedGameShare: SteamStoreGameShare? = null,
    requestedGameSharePartnerSteamId: String? = null,
    onConsumeRequestedGameShare: () -> Unit = {},
    onUnreadCountChange: (Int) -> Unit = {},
    onThreadVisibilityChange: (Boolean) -> Unit = {},
    onPlatformViewVisibilityChanged: (Boolean) -> Unit = {},
    onOpenStoreApp: (Int) -> Unit = {},
    onAddSteamAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceAnimations = LocalReduceAnimations.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accountSourceRepository = remember(context) {
        SteamAccountSourceRepository.get(context)
    }
    val accountSourceState by accountSourceRepository.state.collectAsState()
    val friendsViewModel: SteamFriendsViewModel = viewModel(
        factory = remember(context) { SteamFriendsViewModel.factory(context) }
    )
    val chatViewModel: SteamChatViewModel = viewModel(
        factory = remember(context) { SteamChatViewModel.factory(context) }
    )
    val groupChatViewModel: SteamGroupChatViewModel = viewModel(
        factory = remember(context) { SteamGroupChatViewModel.factory(context) }
    )
    val richMediaViewModel: SteamChatRichMediaViewModel = viewModel(
        factory = remember(context) { SteamChatRichMediaViewModel.factory(context) }
    )
    val messageActionViewModel: SteamChatMessageActionViewModel = viewModel(
        factory = remember(context) { SteamChatMessageActionViewModel.factory(context) }
    )
    val friendsState by friendsViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val groupChatState by groupChatViewModel.state.collectAsState()
    val richMediaState by richMediaViewModel.uiState.collectAsState()
    val voiceRuntime = remember(context) { SteamVoiceCallRuntime.get(context) }
    val voiceState by voiceRuntime.state.collectAsState()
    var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingVoiceAction
        pendingVoiceAction = null
        if (granted) action?.invoke()
    }
    fun runVoiceAction(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) action() else {
            pendingVoiceAction = action
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val sessionAccounts = remember(accountSourceState.accounts) {
        accountSourceState.accounts.filter { it.hasAuthenticatedSession }
    }
    val storedSelectedAccount = accountSourceState.accounts.firstOrNull {
        it.id == accountSourceState.selectedAccountId
    }
    val selectedAccount = sessionAccounts.firstOrNull {
        it.id == storedSelectedAccount?.id
    } ?: sessionAccounts.firstOrNull()
    val selectedFriend = friendsState.snapshot?.friends?.firstOrNull {
        it.steamId == chatState.selectedPartnerSteamId
    }
    val infoPreferencesStore = remember(context) { SteamChatInfoPreferencesStore(context) }
    var standaloneSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var showFriends by rememberSaveable { mutableStateOf(false) }
    var addFriendOpen by rememberSaveable { mutableStateOf(false) }
    var officialAddFriendOpen by rememberSaveable { mutableStateOf(false) }
    var showCreateGroup by rememberSaveable { mutableStateOf(false) }
    var showInviteFriend by rememberSaveable { mutableStateOf(false) }
    var initialGroupInvitees by remember { mutableStateOf(emptySet<String>()) }
    var subpage by remember { mutableStateOf<SteamChatSubpage?>(null) }
    var targetMessageId by remember { mutableStateOf<String?>(null) }
    var conversationPreferences by remember { mutableStateOf(SteamChatConversationPreferences()) }
    val currentConversationId = when {
        chatState.selectedPartnerSteamId != null -> SteamChatConversationId(
            accountSteamId = chatState.accountSteamId,
            type = SteamChatConversationType.DIRECT,
            peerOrGroupId = chatState.selectedPartnerSteamId.orEmpty()
        )
        groupChatState.selectedGroupId != null -> SteamChatConversationId(
            accountSteamId = groupChatState.accountSteamId,
            type = SteamChatConversationType.GROUP,
            peerOrGroupId = groupChatState.selectedGroupId.orEmpty()
        )
        else -> null
    }
    LaunchedEffect(currentConversationId) {
        conversationPreferences = currentConversationId?.let(infoPreferencesStore::load)
            ?: SteamChatConversationPreferences()
        subpage = null
        targetMessageId = null
    }
    val pinnedDirectIds = remember(chatState.sessions, conversationPreferences, chatState.accountSteamId) {
        chatState.sessions?.sessions.orEmpty().mapNotNull { session ->
            val id = SteamChatConversationId(
                chatState.accountSteamId,
                SteamChatConversationType.DIRECT,
                session.partnerSteamId
            )
            session.partnerSteamId.takeIf { infoPreferencesStore.load(id).pinned }
        }.toSet()
    }
    val pinnedGroupIds = remember(groupChatState.groups, conversationPreferences, groupChatState.accountSteamId) {
        groupChatState.groups.mapNotNull { group ->
            val id = SteamChatConversationId(
                groupChatState.accountSteamId,
                SteamChatConversationType.GROUP,
                group.groupId
            )
            group.groupId.takeIf { infoPreferencesStore.load(id).pinned }
        }.toSet()
    }
    val effectiveSearchQuery = if (standalone) standaloneSearchQuery else searchQuery
    LaunchedEffect(accountSourceState.selectedAccountId, selectedAccount?.id) {
        if (selectedAccount != null && selectedAccount.id != accountSourceState.selectedAccountId) {
            accountSourceRepository.selectAccount(selectedAccount.id)
        }
    }
    LaunchedEffect(
        accountSourceState.loading,
        selectedAccount?.id,
        selectedAccount?.steamId,
        selectedAccount?.accessToken,
        selectedAccount?.steamLoginSecure
    ) {
        if (!shouldApplySteamAccountSelection(accountSourceState)) return@LaunchedEffect
        chatViewModel.selectAccount(selectedAccount)
        groupChatViewModel.selectAccount(selectedAccount)
        richMediaViewModel.selectAccount(selectedAccount)
        messageActionViewModel.selectAccount(selectedAccount)
        friendsViewModel.selectAccount(selectedAccount)
        addFriendOpen = false
    }
    LaunchedEffect(
        accountSourceState.loading,
        selectedAccount?.id,
        selectedAccount?.steamId,
        selectedAccount?.accessToken
    ) {
        if (!shouldApplySteamAccountSelection(accountSourceState)) return@LaunchedEffect
        selectedAccount?.let(voiceRuntime::observeAccount)
    }
    LaunchedEffect(accountSourceState.loading, selectedAccount?.id, selectedAccount?.steamId) {
        if (!shouldApplySteamAccountSelection(accountSourceState)) return@LaunchedEffect
        if (voiceState.isActive && selectedAccount?.steamId != voiceState.accountSteamId) {
            voiceRuntime.stop()
        }
    }
    LaunchedEffect(messageActionViewModel) {
        messageActionViewModel.results.collect { result ->
            val message = when (result) {
                SteamChatMessageActionResult.REACTION_ADDED -> R.string.steam_chat_reaction_added
                SteamChatMessageActionResult.MESSAGE_REPORTED -> R.string.steam_chat_reported
                SteamChatMessageActionResult.FAILED -> R.string.steam_chat_action_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(friendsState.actionFeedback) {
        val feedback = friendsState.actionFeedback ?: return@LaunchedEffect
        val message = when {
            feedback.success &&
                feedback.relationshipAction == SteamFriendRelationshipAction.ADD ->
                context.getString(R.string.steam_friend_add_success)
            feedback.success && feedback.relationshipAction != null ->
                context.getString(R.string.steam_friend_relationship_action_success)
            feedback.success && feedback.accepted ->
                context.getString(R.string.steam_friend_accept_success)
            feedback.success -> context.getString(R.string.steam_friend_ignore_success)
            !feedback.message.isNullOrBlank() -> feedback.message
            else -> context.getString(R.string.steam_friend_action_failed)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        friendsViewModel.consumeActionFeedback()
    }
    LaunchedEffect(voiceState.failure) {
        val failure = voiceState.failure ?: return@LaunchedEffect
        val message = if (failure.contains("Microphone permission", ignoreCase = true)) {
            "需要麦克风权限才能使用 Steam 语音聊天"
        } else failure
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        voiceRuntime.clearFailure()
    }
    SteamChatThreadLifecycle(
        chatState = chatState,
        groupChatState = groupChatState,
        uploadCompletedAt = richMediaState.uploadCompletedAt,
        refreshRequest = refreshRequest,
        chatViewModel = chatViewModel,
        groupChatViewModel = groupChatViewModel,
        richMediaViewModel = richMediaViewModel,
        friendsViewModel = friendsViewModel,
        onThreadVisibilityChange = onThreadVisibilityChange
    )
    LaunchedEffect(requestedPartnerSteamId, selectedAccount?.id) {
        val partner = requestedPartnerSteamId?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (selectedAccount != null) {
            showFriends = false
            addFriendOpen = false
            chatViewModel.openThread(partner)
            onConsumeRequestedPartner()
        }
    }

    LaunchedEffect(chatState.unreadCount, groupChatState.groups) {
        onUnreadCountChange(chatState.unreadCount + groupChatState.groups.sumOf { it.unreadCount })
    }

    LaunchedEffect(groupChatState.createdGroupId, groupChatState.groups) {
        val createdGroupId = groupChatState.createdGroupId ?: return@LaunchedEffect
        val createdGroup = groupChatState.groups.firstOrNull { it.groupId == createdGroupId }
        if (createdGroup != null) {
            showCreateGroup = false
            showFriends = false
            addFriendOpen = false
            initialGroupInvitees = emptySet()
            subpage = null
            chatViewModel.closeThread()
            groupChatViewModel.openRoom(createdGroup.groupId, createdGroup.preferredChatId)
            Toast.makeText(context, R.string.steam_group_chat_created, Toast.LENGTH_SHORT).show()
            groupChatViewModel.clearCreatedGroup()
        } else if (!groupChatState.groupsRefreshing && !groupChatState.groupsLoading) {
            groupChatViewModel.refreshGroups()
        }
    }
    LaunchedEffect(groupChatState.failure) {
        groupChatState.failure?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            groupChatViewModel.clearFailure()
        }
    }
    LaunchedEffect(subpage, groupChatState.selectedGroupId) {
        if (subpage == SteamChatSubpage.ADMIN && groupChatState.selectedGroupId != null) {
            groupChatViewModel.refreshAdminSnapshot()
        }
    }

    DisposableEffect(lifecycleOwner, chatViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> chatViewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> chatViewModel.setForeground(false)
                else -> Unit
            }
            when (event) {
                Lifecycle.Event.ON_START -> groupChatViewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> groupChatViewModel.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        chatViewModel.setForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        groupChatViewModel.setForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chatViewModel.setForeground(false)
            groupChatViewModel.setForeground(false)
        }
    }

    SteamChatBackHandlers(
        standalone = standalone,
        showFriends = showFriends,
        addFriendOpen = addFriendOpen,
        subpage = subpage,
        directThreadOpen = chatState.selectedPartnerSteamId != null,
        groupThreadOpen = groupChatState.selectedChatId != null,
        onShowFriendsChange = { showFriends = it },
        onAddFriendOpenChange = { open ->
            addFriendOpen = open
            if (!open) friendsViewModel.clearFriendDiscovery()
        },
        onSubpageChange = { subpage = it },
        onCloseDirectThread = chatViewModel::closeThread,
        onCloseGroupThread = groupChatViewModel::closeRoom
    )

    AnimatedContent(
        targetState = Triple(chatState.selectedPartnerSteamId, groupChatState.selectedChatId, subpage),
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            easyNotesScreenEnter(reduceAnimations)
                .togetherWith(easyNotesScreenExit(reduceAnimations))
        },
        label = "SteamChatNavigation"
    ) { (partnerSteamId, groupRoomId, currentSubpage) ->
        if (partnerSteamId == null && groupRoomId == null) {
            SteamChatRootContent(
                standalone = standalone,
                showFriends = showFriends,
                addFriendOpen = addFriendOpen,
                standaloneSearchQuery = standaloneSearchQuery,
                searchExpanded = searchExpanded,
                accountSourceState = accountSourceState,
                friendsState = friendsState,
                chatState = chatState,
                groupChatState = groupChatState,
                voiceState = voiceState,
                effectiveSearchQuery = effectiveSearchQuery,
                pinnedDirectIds = pinnedDirectIds,
                pinnedGroupIds = pinnedGroupIds,
                onStandaloneSearchQueryChange = { standaloneSearchQuery = it },
                onSearchExpandedChange = { expanded ->
                    searchExpanded = expanded
                    if (!expanded) standaloneSearchQuery = ""
                },
                onShowAccounts = { showAccounts = true },
                onToggleFriends = {
                    addFriendOpen = false
                    showFriends = !showFriends
                },
                onAddFriendOpenChange = { open ->
                    addFriendOpen = open
                    if (open) {
                        showFriends = true
                        searchExpanded = false
                        standaloneSearchQuery = ""
                    } else {
                        friendsViewModel.clearFriendDiscovery()
                    }
                },
                onOpenOfficialAddFriend = { officialAddFriendOpen = true },
                onFindFriendCandidates = friendsViewModel::findFriendCandidates,
                onAddFriend = { friend ->
                    friendsViewModel.changeRelationship(
                        friend,
                        SteamFriendRelationshipAction.ADD
                    )
                },
                onRespondToInvite = friendsViewModel::respondToInvite,
                onOpenDirect = { steamId ->
                    showFriends = false
                    addFriendOpen = false
                    groupChatViewModel.closeRoom()
                    chatViewModel.openThread(steamId)
                },
                onOpenGroup = { groupId, chatId ->
                    chatViewModel.closeThread()
                    groupChatViewModel.openRoom(groupId, chatId)
                },
                onRefreshFriends = friendsViewModel::refresh,
                onRefreshConversations = {
                    chatViewModel.refreshSessions()
                    groupChatViewModel.refreshGroups()
                },
                onCreateGroup = { showCreateGroup = true },
                onLeaveVoice = voiceRuntime::stop,
                onToggleVoiceMicrophone = voiceRuntime::toggleMicrophone,
                onToggleVoiceOutput = voiceRuntime::toggleOutput,
                onSelectVoiceAudioRoute = voiceRuntime::selectAudioRoute,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SteamChatSelectedContent(
                partnerSteamId = partnerSteamId,
                currentSubpage = currentSubpage,
                selectedAccount = selectedAccount,
                selectedFriend = selectedFriend,
                friendsState = friendsState,
                chatState = chatState,
                groupChatState = groupChatState,
                richMediaState = richMediaState,
                voiceState = voiceState,
                conversationPreferences = conversationPreferences,
                targetMessageId = targetMessageId,
                gameShareDraft = requestedGameShare.takeIf {
                    partnerSteamId == requestedGameSharePartnerSteamId
                },
                chatViewModel = chatViewModel,
                friendsViewModel = friendsViewModel,
                groupChatViewModel = groupChatViewModel,
                richMediaViewModel = richMediaViewModel,
                messageActionViewModel = messageActionViewModel,
                voiceRuntime = voiceRuntime,
                runVoiceAction = ::runVoiceAction,
                onSubpageChange = { subpage = it },
                onCreateGroupFromFriend = { steamId ->
                    initialGroupInvitees = setOf(steamId)
                    showCreateGroup = true
                },
                onInviteFriend = { showInviteFriend = true },
                onPreferencesChange = { updated ->
                    conversationPreferences = updated
                    currentConversationId?.let { infoPreferencesStore.save(it, updated) }
                },
                onOpenTargetMessage = { messageId ->
                    targetMessageId = messageId
                    subpage = null
                },
                onConsumeGameShareDraft = onConsumeRequestedGameShare,
                onOpenStoreApp = onOpenStoreApp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    SteamChatScreenDialogs(
        standalone = standalone,
        showAccounts = showAccounts,
        showCreateGroup = showCreateGroup,
        showInviteFriend = showInviteFriend,
        initialGroupInvitees = initialGroupInvitees,
        selectedAccount = selectedAccount,
        accountSourceState = accountSourceState,
        friendsState = friendsState,
        groupChatState = groupChatState,
        voiceState = voiceState,
        accountSourceRepository = accountSourceRepository,
        groupChatViewModel = groupChatViewModel,
        voiceRuntime = voiceRuntime,
        runVoiceAction = ::runVoiceAction,
        onAddSteamAccount = onAddSteamAccount,
        onShowAccountsChange = { showAccounts = it },
        onShowCreateGroupChange = { showCreateGroup = it },
        onShowInviteFriendChange = { showInviteFriend = it },
        onInitialGroupInviteesChange = { initialGroupInvitees = it }
    )

    if (officialAddFriendOpen) {
        SteamOfficialAddFriendDialog(
            account = selectedAccount,
            onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
            onDismiss = { officialAddFriendOpen = false }
        )
    }
}
