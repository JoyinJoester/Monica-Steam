package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
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
    pinnedDirectIds: Set<String>,
    pinnedGroupIds: Set<String>,
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
    modifier: Modifier = Modifier
) {
    val reduceAnimations = LocalReduceAnimations.current
    if (standalone) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!addFriendOpen) SteamChatSearchBar(
                    query = standaloneSearchQuery,
                    showingFriends = showFriends,
                    accountEnabled = accountSourceState.accounts.isNotEmpty() || accountSourceState.mdbxDatabases.isNotEmpty(),
                    onQueryChange = onStandaloneSearchQueryChange,
                    onToggleFriends = onToggleFriends,
                    onShowAccounts = onShowAccounts
                ) else ExpressiveTopBar(
                    title = stringResource(when {
                        addFriendOpen -> R.string.steam_friend_add_title
                        showFriends -> R.string.steam_friends_title
                        else -> R.string.steam_chat_title
                    }),
                    searchQuery = if (addFriendOpen) "" else standaloneSearchQuery,
                    onSearchQueryChange = onStandaloneSearchQueryChange,
                    isSearchExpanded = !addFriendOpen && searchExpanded,
                    onSearchExpandedChange = onSearchExpandedChange,
                    searchHint = stringResource(R.string.steam_chat_search_hint),
                    modifier = Modifier.steamWindowTopPadding(),
                    navigationIcon = if (addFriendOpen) {
                        {
                            IconButton(onClick = { onAddFriendOpenChange(false) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        }
                    } else {
                        null
                    },
                    compact = addFriendOpen,
                    actions = {
                        if (addFriendOpen) {
                            IconButton(onClick = onOpenOfficialAddFriend) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(
                                        R.string.steam_friend_add_on_steam
                                    )
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onShowAccounts,
                                enabled = accountSourceState.accounts.isNotEmpty() ||
                                    accountSourceState.mdbxDatabases.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.SwitchAccount,
                                    contentDescription = stringResource(R.string.steam_switch_account)
                                )
                            }
                            IconButton(onClick = onToggleFriends) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = stringResource(R.string.steam_friends_title)
                                )
                            }
                            IconButton(onClick = { onSearchExpandedChange(true) }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.steam_store_search)
                                )
                            }
                        }
                    }
                )
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
                    easyNotesScreenEnter(reduceAnimations)
                        .togetherWith(easyNotesScreenExit(reduceAnimations))
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
                        onRefresh = onRefreshFriends,
                        onAddFriend = { onAddFriendOpenChange(true) },
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> SteamChatConversationRoot(
                        chatState = chatState,
                        groupChatState = groupChatState,
                        friendsState = friendsState,
                        voiceState = voiceState,
                        effectiveSearchQuery = effectiveSearchQuery,
                        pinnedDirectIds = pinnedDirectIds,
                        pinnedGroupIds = pinnedGroupIds,
                        onOpenDirect = onOpenDirect,
                        onOpenGroup = onOpenGroup,
                        onRefresh = onRefreshConversations,
                        onCreateGroup = onCreateGroup,
                        onLeaveVoice = onLeaveVoice,
                        onToggleVoiceMicrophone = onToggleVoiceMicrophone,
                        onToggleVoiceOutput = onToggleVoiceOutput,
                        onSelectVoiceAudioRoute = onSelectVoiceAudioRoute
                    )
                }
            }
        }
    } else {
        SteamChatConversationRoot(
            chatState = chatState,
            groupChatState = groupChatState,
            friendsState = friendsState,
            voiceState = voiceState,
            effectiveSearchQuery = effectiveSearchQuery,
            pinnedDirectIds = pinnedDirectIds,
            pinnedGroupIds = pinnedGroupIds,
            onOpenDirect = onOpenDirect,
            onOpenGroup = onOpenGroup,
            onRefresh = onRefreshConversations,
            onCreateGroup = onCreateGroup,
            onLeaveVoice = onLeaveVoice,
            onToggleVoiceMicrophone = onToggleVoiceMicrophone,
            onToggleVoiceOutput = onToggleVoiceOutput,
            onSelectVoiceAudioRoute = onSelectVoiceAudioRoute,
            modifier = modifier
        )
    }
}

@Composable
private fun SteamChatConversationRoot(
    chatState: SteamChatUiState,
    groupChatState: SteamGroupChatUiState,
    friendsState: SteamFriendsUiState,
    voiceState: SteamVoiceCallState,
    effectiveSearchQuery: String,
    pinnedDirectIds: Set<String>,
    pinnedGroupIds: Set<String>,
    onOpenDirect: (String) -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onCreateGroup: () -> Unit,
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
        pinnedPartnerSteamIds = pinnedDirectIds,
        pinnedGroupIds = pinnedGroupIds,
        onOpenDirect = onOpenDirect,
        onOpenGroup = onOpenGroup,
        onRefresh = onRefresh,
        onCreateGroup = onCreateGroup,
        voiceState = voiceState,
        onLeaveVoice = onLeaveVoice,
        onToggleVoiceMicrophone = onToggleVoiceMicrophone,
        onToggleVoiceOutput = onToggleVoiceOutput,
        onSelectVoiceAudioRoute = onSelectVoiceAudioRoute,
        modifier = modifier.fillMaxSize()
    )
}
