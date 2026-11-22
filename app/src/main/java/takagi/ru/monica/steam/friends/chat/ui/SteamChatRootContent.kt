package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialTheme
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialTopBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.data.SteamAccountSourceState
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.friends.ui.SteamAddFriendScreen
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.design.telegram.SteamTelegramTopBar
import takagi.ru.monica.steam.navigation.steamPageTransition

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SteamChatRootContent(
    standalone: Boolean,
    showFriends: Boolean,
    addFriendOpen: Boolean,
    standaloneSearchQuery: String,
    searchExpanded: Boolean,
    accountSourceState: SteamAccountSourceState,
    friendsState: SteamFriendsUiState,
    chatState: SteamChatUiState,
    groupChatState: SteamGroupChatUiState,
    voiceState: SteamVoiceCallState,
    effectiveSearchQuery: String,
    conversationFlags: SteamConversationFlags,
    conversationActions: SteamConversationActions,
    onStandaloneSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onShowAccounts: () -> Unit,
    onToggleFriends: () -> Unit,
    onAddFriendOpenChange: (Boolean) -> Unit,
    onOpenOfficialAddFriend: () -> Unit,
    onFindFriendCandidates: (String) -> Unit,
    onAddFriend: (SteamFriend) -> Unit,
    onRespondToInvite: (SteamFriend, Boolean) -> Unit,
    onOpenDirect: (String) -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onRefreshFriends: () -> Unit,
    onRefreshConversations: () -> Unit,
    onCreateGroup: () -> Unit,
    onLeaveVoice: () -> Unit,
    onToggleVoiceMicrophone: () -> Unit,
    onToggleVoiceOutput: () -> Unit,
    onSelectVoiceAudioRoute: (SteamVoiceAudioRoute) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSpace: (() -> Unit)? = null
) {
    val reduceAnimations = LocalReduceAnimations.current
    var newChatOpen by remember { mutableStateOf(false) }
    TelegramSocialTheme {
    if (standalone) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(Modifier.steamWindowTopPadding()) {
                val account = accountSourceState.accounts.firstOrNull { it.id == accountSourceState.selectedAccountId }
                val canSwitchAccounts = accountSourceState.accounts.isNotEmpty() || accountSourceState.mdbxDatabases.isNotEmpty()
                when {
                    addFriendOpen -> TelegramSocialTopBar(
                        title = stringResource(R.string.steam_friend_add_title),
                        onBack = ({ onAddFriendOpenChange(false) }),
                        actions = {
                            IconButton(onClick = onOpenOfficialAddFriend) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(
                                        R.string.steam_friend_add_on_steam
                                    )
                                )
                            }
                        }
                    )
                    showFriends -> SteamFriendsTopBar(
                        account = account,
                        query = standaloneSearchQuery,
                        onQueryChange = onStandaloneSearchQueryChange,
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = onSearchExpandedChange,
                        canSwitchAccounts = canSwitchAccounts,
                        onRefresh = onRefreshFriends,
                        refreshing = friendsState.loading || friendsState.refreshing,
                        onCreateGroup = onCreateGroup,
                        onOpenChats = onToggleFriends,
                        onShowAccounts = onShowAccounts
                    )
                    else -> SteamInboxTopBar(
                        account = account,
                        canSwitchAccounts = canSwitchAccounts,
                        query = standaloneSearchQuery,
                        onQueryChange = onStandaloneSearchQueryChange,
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = onSearchExpandedChange,
                        onShowAccounts = onShowAccounts,
                        onOpenSpace = onOpenSpace,
                        onCreateGroup = onCreateGroup,
                        onRefresh = onRefreshConversations,
                        refreshing = chatState.sessionsLoading || chatState.sessionsRefreshing || groupChatState.groupsLoading || groupChatState.groupsRefreshing
                    )
                }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = when {
                    addFriendOpen -> 2
                    showFriends -> 1
                    else -> 0
                },
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = {
                    steamPageTransition(reduceAnimations, forward = targetState > initialState)
                },
                label = "SteamChatRootMode"
            ) { rootMode ->
                when (rootMode) {
                    2 -> SteamAddFriendScreen(
                        state = friendsState,
                        onSearch = onFindFriendCandidates,
                        onOpenFriend = { friend ->
                            onAddFriendOpenChange(false)
                            onOpenDirect(friend.steamId)
                        },
                        onAddFriend = onAddFriend,
                        onRespondToInvite = onRespondToInvite,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> SteamChatFriendPicker(
                        friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                        loading = friendsState.loading && friendsState.snapshot == null,
                        query = effectiveSearchQuery,
                        onOpenThread = onOpenDirect,
                        onAddFriend = { onAddFriendOpenChange(true) },
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> SteamChatConversationRoot(
                        chatState = chatState,
                        groupChatState = groupChatState,
                        friendsState = friendsState,
                        voiceState = voiceState,
                        effectiveSearchQuery = effectiveSearchQuery,
                        conversationFlags = conversationFlags,
                        conversationActions = conversationActions,
                        onOpenDirect = onOpenDirect,
                        onOpenGroup = onOpenGroup,
                        onRefresh = onRefreshConversations,
                        onCreateGroup = onCreateGroup,
                        onNewChat = { onSearchExpandedChange(false); onToggleFriends() },
                        onLeaveVoice = onLeaveVoice,
                        onToggleVoiceMicrophone = onToggleVoiceMicrophone,
                        onToggleVoiceOutput = onToggleVoiceOutput,
                        onSelectVoiceAudioRoute = onSelectVoiceAudioRoute
                    )
                }
            }
        }
    } else {
        Column(modifier) {
        SteamTelegramTopBar(title = stringResource(R.string.steam_chat_title), actions = {
            SteamInboxOverflowMenu(onOpenSpace, onCreateGroup, onRefreshConversations,
                chatState.sessionsLoading || chatState.sessionsRefreshing || groupChatState.groupsLoading || groupChatState.groupsRefreshing)
        })
        SteamChatConversationRoot(
            chatState = chatState,
            groupChatState = groupChatState,
            friendsState = friendsState,
            voiceState = voiceState,
            effectiveSearchQuery = effectiveSearchQuery,
            conversationFlags = conversationFlags,
            conversationActions = conversationActions,
            onOpenDirect = onOpenDirect,
            onOpenGroup = onOpenGroup,
            onRefresh = onRefreshConversations,
            onCreateGroup = onCreateGroup,
            onNewChat = { newChatOpen = true },
            onLeaveVoice = onLeaveVoice,
            onToggleVoiceMicrophone = onToggleVoiceMicrophone,
            onToggleVoiceOutput = onToggleVoiceOutput,
            onSelectVoiceAudioRoute = onSelectVoiceAudioRoute,
            modifier = Modifier.weight(1f)
        )
        }
        if (newChatOpen) ModalBottomSheet(onDismissRequest = { newChatOpen = false }) {
            SteamChatFriendPicker(
                friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                loading = friendsState.loading && friendsState.snapshot == null,
                query = "", onOpenThread = { newChatOpen = false; onOpenDirect(it) },
                onAddFriend = { newChatOpen = false; onAddFriendOpenChange(true) }
            )
        }
        if (addFriendOpen) ModalBottomSheet(onDismissRequest = { onAddFriendOpenChange(false) }) {
            SteamAddFriendScreen(
                state = friendsState, onSearch = onFindFriendCandidates,
                onOpenFriend = { onAddFriendOpenChange(false); onOpenDirect(it.steamId) },
                onAddFriend = onAddFriend, onRespondToInvite = onRespondToInvite,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    }
}

@Composable
private fun SteamChatConversationRoot(
    chatState: SteamChatUiState,
    groupChatState: SteamGroupChatUiState,
    friendsState: SteamFriendsUiState,
    voiceState: SteamVoiceCallState,
    effectiveSearchQuery: String,
    conversationFlags: SteamConversationFlags,
    conversationActions: SteamConversationActions,
    onOpenDirect: (String) -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onCreateGroup: () -> Unit,
    onNewChat: () -> Unit,
    onLeaveVoice: () -> Unit,
    onToggleVoiceMicrophone: () -> Unit,
    onToggleVoiceOutput: () -> Unit,
    onSelectVoiceAudioRoute: (SteamVoiceAudioRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    SteamConversationList(
        chatState = chatState,
        groupState = groupChatState,
        friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
        query = effectiveSearchQuery,
        flags = conversationFlags,
        actions = conversationActions,
        onOpenDirect = onOpenDirect,
        onOpenGroup = onOpenGroup,
        onRefresh = onRefresh,
        onCreateGroup = onCreateGroup,
        onNewChat = onNewChat,
        voiceState = voiceState,
        onLeaveVoice = onLeaveVoice,
        onToggleVoiceMicrophone = onToggleVoiceMicrophone,
        onToggleVoiceOutput = onToggleVoiceOutput,
        onSelectVoiceAudioRoute = onSelectVoiceAudioRoute,
        modifier = modifier.fillMaxSize()
    )
}
