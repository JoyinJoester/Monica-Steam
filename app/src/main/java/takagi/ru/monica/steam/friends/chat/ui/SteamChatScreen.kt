package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.runtime.CompositionLocalProvider
import takagi.ru.monica.steam.friends.voice.ui.LocalSteamVoiceAudioLevels
import takagi.ru.monica.steam.friends.voice.ui.LocalSteamVoiceFriends

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import takagi.ru.monica.steam.navigation.ui.rememberSteamAdaptiveLayout
import takagi.ru.monica.steam.space.ui.SteamSpaceRoute

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
    // Retained tabs stay composed at CREATED; their UI must stop collecting too.
    // ViewModels keep background messaging alive and publish the latest state on return.
    val accountSourceState by accountSourceRepository.state.collectAsStateWithLifecycle()
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
    val friendsState by friendsViewModel.uiState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val groupChatState by groupChatViewModel.state.collectAsStateWithLifecycle()
    val richMediaState by richMediaViewModel.uiState.collectAsStateWithLifecycle()
    val voiceRuntime = remember(context) { SteamVoiceCallRuntime.get(context) }
    val voiceState by voiceRuntime.state.collectAsStateWithLifecycle()
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
    var showSpace by rememberSaveable(selectedAccount?.id, selectedAccount?.steamId) { mutableStateOf(false) }
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
    val jump = remember { SteamChatJumpRequest() }
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
    val conversationPreferencesState = rememberSteamChatConversationPreferences(infoPreferencesStore, currentConversationId)
    var conversationPreferences by conversationPreferencesState
    // The thread opens immediately; an early info request waits for its editable preferences,
    // so the user cannot overwrite stored settings with temporary defaults during the read.
    val visibleSubpage = subpage.takeUnless { it == SteamChatSubpage.INFO && !conversationPreferencesState.loaded }
    var conversationFlagsRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentConversationId) {
        subpage = null
        jump.clear()
    }
    val localConversations = rememberSteamConversationLocalState(
        store = infoPreferencesStore,
        chatState = chatState,
        groupState = groupChatState,
        refreshKey = conversationFlagsRevision
    )
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
            showSpace = false
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

    val adaptiveLayout = rememberSteamAdaptiveLayout()

    @Composable
    fun renderConversationList(targetModifier: Modifier) {
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
            conversationFlags = localConversations.flags,
            conversationActions = localConversations.actions,
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
            modifier = targetModifier,
            onOpenSpace = {
                showFriends = false
                addFriendOpen = false
                subpage = null
                chatViewModel.closeThread()
                groupChatViewModel.closeRoom()
                showSpace = true
            }
        )
    }

    @Composable
    fun renderSelectedConversation(
        partnerSteamId: String?,
        currentSubpage: SteamChatSubpage?,
        targetModifier: Modifier
    ) {
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
            targetMessageId = jump.messageId,
            targetMessageNonce = jump.nonce,
            highlightMessageId = jump.highlightId,
            onHighlightConsumed = jump::consumeHighlight,
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
            // Opening any subpage drops a pending jump so the thread does not re-scroll to an old
            // hit when the user comes back to it later.
            onSubpageChange = { next ->
                subpage = next
                if (next != null) jump.clear()
            },
            onCreateGroupFromFriend = { steamId ->
                initialGroupInvitees = setOf(steamId)
                showCreateGroup = true
            },
            onInviteFriend = { showInviteFriend = true },
            onPreferencesChange = { updated ->
                val flagsChanged = conversationPreferences.pinned != updated.pinned ||
                    conversationPreferences.archived != updated.archived ||
                    conversationPreferences.hiddenAt != updated.hiddenAt
                conversationPreferences = updated
                currentConversationId?.let { infoPreferencesStore.save(it, updated) }
                if (flagsChanged) conversationFlagsRevision++
            },
            onOpenTargetMessage = { messageId ->
                jump.request(messageId)
                subpage = null
            },
            onConsumeGameShareDraft = onConsumeRequestedGameShare,
            onOpenStoreApp = onOpenStoreApp,
            modifier = targetModifier
        )
    }

    CompositionLocalProvider(
        LocalSteamVoiceAudioLevels provides voiceRuntime.audioLevels,
        LocalSteamVoiceFriends provides (friendsState.snapshot?.friends.orEmpty().associateBy { it.steamId })
    ) {
    if (showSpace) SteamSpaceRoute(
        account = selectedAccount,
        friendsState = friendsState,
        onRefreshFriends = friendsViewModel::refresh,
        onBack = { showSpace = false },
        onOpenGame = onOpenStoreApp,
        onManageAccounts = onAddSteamAccount,
        onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
        onMessage = { steamId ->
            showSpace = false
            groupChatViewModel.closeRoom()
            chatViewModel.openThread(steamId)
        },
        modifier = modifier
    ) else SteamChatNavigation(
        targetState = Triple(chatState.selectedPartnerSteamId, groupChatState.selectedChatId, visibleSubpage),
        modifier = modifier.fillMaxSize(),
        reduceAnimations = reduceAnimations
    ) { (partnerSteamId, groupRoomId, currentSubpage) ->
        if (partnerSteamId == null && groupRoomId == null) {
            renderConversationList(Modifier.fillMaxSize())
        } else {
            if (adaptiveLayout.useTwoPaneLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    renderConversationList(
                        Modifier.width(adaptiveLayout.preferredListPaneWidthDp.dp)
                    )
                    renderSelectedConversation(
                        partnerSteamId = partnerSteamId,
                        currentSubpage = currentSubpage,
                        targetModifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            } else {
                renderSelectedConversation(
                    partnerSteamId = partnerSteamId,
                    currentSubpage = currentSubpage,
                    targetModifier = Modifier.fillMaxSize()
                )
            }
        }
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
