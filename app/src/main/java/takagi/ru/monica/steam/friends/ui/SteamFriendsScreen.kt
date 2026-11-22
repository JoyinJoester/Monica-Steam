package takagi.ru.monica.steam.friends.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassFloatingActionButton as FloatingActionButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.data.hasAuthenticatedSession
import takagi.ru.monica.steam.friends.domain.SteamFriendsFilter
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget
import takagi.ru.monica.steam.profile.viewer.ui.SteamProfileViewerScreen
import takagi.ru.monica.steam.token.presentation.SteamViewModel
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.navigation.steamPageTransition

private sealed interface SteamFriendsDestination {
    data object List : SteamFriendsDestination
    data object AddFriend : SteamFriendsDestination
    data class Detail(val steamId: String) : SteamFriendsDestination
    data class Profile(val steamId: String) : SteamFriendsDestination
}

@Composable
fun SteamFriendsScreen(
    searchQuery: String,
    refreshRequest: Long,
    selectedFriendId: String?,
    onSelectedFriendIdChange: (String?) -> Unit,
    addFriendOpen: Boolean,
    onAddFriendOpenChange: (Boolean) -> Unit,
    onStartChat: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onRefreshingChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val reduceAnimations = LocalReduceAnimations.current
    val steamViewModel: SteamViewModel = viewModel(
        factory = remember(context) { SteamViewModel.factory(context) }
    )
    val friendsViewModel: SteamFriendsViewModel = viewModel(
        factory = remember(context) { SteamFriendsViewModel.factory(context) }
    )
    val steamState by steamViewModel.uiState.collectAsState()
    val state by friendsViewModel.uiState.collectAsState()
    val reportRefreshing by androidx.compose.runtime.rememberUpdatedState(onRefreshingChanged)
    LaunchedEffect(state.loading, state.refreshing) { reportRefreshing(state.loading || state.refreshing) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { reportRefreshing(false) } }
    val sessionAccounts = remember(steamState.accounts) {
        steamState.accounts.filter { it.hasAuthenticatedSession }
    }
    val selectedAccount = sessionAccounts.firstOrNull {
        it.id == steamState.selectedAccountId
    } ?: sessionAccounts.firstOrNull()
    val friendsById = remember(state.snapshot?.friends) {
        state.snapshot?.friends.orEmpty().associateBy { it.steamId }
    }
    var filterName by rememberSaveable { mutableStateOf(SteamFriendsFilter.ALL.name) }
    var profileSteamId by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = SteamFriendsFilter.entries.firstOrNull { it.name == filterName }
        ?: SteamFriendsFilter.ALL
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(
        selectedAccount?.id,
        selectedAccount?.accessToken,
        selectedAccount?.steamLoginSecure
    ) {
        onSelectedFriendIdChange(null)
        profileSteamId = null
        friendsViewModel.selectAccount(selectedAccount)
    }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest > 0L) friendsViewModel.refresh()
    }

    LaunchedEffect(addFriendOpen) {
        if (!addFriendOpen) friendsViewModel.clearFriendDiscovery()
    }

    val feedback = state.actionFeedback
    val acceptSuccessText = stringResource(R.string.steam_friend_accept_success)
    val ignoreSuccessText = stringResource(R.string.steam_friend_ignore_success)
    val actionFailedText = stringResource(R.string.steam_friend_action_failed)
    val relationshipSuccessText = stringResource(R.string.steam_friend_relationship_action_success)
    val addFriendSuccessText = stringResource(R.string.steam_friend_add_success)
    LaunchedEffect(feedback) {
        if (feedback != null) {
            val text = when {
                feedback.success &&
                    feedback.relationshipAction == SteamFriendRelationshipAction.ADD ->
                    addFriendSuccessText
                feedback.success && feedback.relationshipAction != null -> relationshipSuccessText
                feedback.success && feedback.accepted -> acceptSuccessText
                feedback.success -> ignoreSuccessText
                !feedback.message.isNullOrBlank() -> feedback.message
                else -> actionFailedText
            }
            snackbarHostState.showSnackbar(text)
            friendsViewModel.consumeActionFeedback()
        }
    }

    BackHandler(enabled = profileSteamId != null) {
        profileSteamId = null
    }

    BackHandler(enabled = profileSteamId == null && addFriendOpen) {
        onAddFriendOpenChange(false)
    }

    BackHandler(
        enabled = profileSteamId == null && !addFriendOpen && selectedFriendId != null
    ) {
        onSelectedFriendIdChange(null)
    }

    val destination: SteamFriendsDestination = when {
        profileSteamId != null -> SteamFriendsDestination.Profile(requireNotNull(profileSteamId))
        addFriendOpen -> SteamFriendsDestination.AddFriend
        selectedFriendId != null -> SteamFriendsDestination.Detail(selectedFriendId)
        else -> SteamFriendsDestination.List
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                steamPageTransition(reduceAnimations, forward = targetState != SteamFriendsDestination.List)
            },
            label = "SteamFriendsNavigation"
        ) { animatedDestination ->
            when (animatedDestination) {
                SteamFriendsDestination.List -> {
                    SteamExpressivePullToRefresh(
                        showIndicator = false,
                        refreshing = state.loading || state.refreshing,
                        onRefresh = friendsViewModel::refresh,
                        enabled = selectedAccount?.hasRealSteamId == true,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SteamFriendsListContent(
                            state = state,
                            query = searchQuery,
                            filter = filter,
                            onFilterChange = { filterName = it.name },
                            onOpenFriend = { onSelectedFriendIdChange(it.steamId) },
                            onRespondToInvite = friendsViewModel::respondToInvite,
                            onRetry = friendsViewModel::refresh,
                            onAddFriend = { onAddFriendOpenChange(true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                SteamFriendsDestination.AddFriend -> {
                    SteamAddFriendScreen(
                        state = state,
                        onSearch = friendsViewModel::findFriendCandidates,
                        onOpenFriend = { friend ->
                            onAddFriendOpenChange(false)
                            onSelectedFriendIdChange(friend.steamId)
                        },
                        onAddFriend = { friend ->
                            friendsViewModel.changeRelationship(
                                friend,
                                SteamFriendRelationshipAction.ADD
                            )
                        },
                        onRespondToInvite = friendsViewModel::respondToInvite,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is SteamFriendsDestination.Detail -> {
                    val animatedFriend = friendsById[animatedDestination.steamId]
                    if (animatedFriend != null) {
                        SteamFriendDetailScreen(
                            friend = animatedFriend,
                            actionInProgress = state.actionSteamId == animatedFriend.steamId,
                            onStartChat = { onStartChat(animatedFriend.steamId) },
                            onOpenProfile = { profileSteamId = animatedFriend.steamId },
                            onChangeRelationship = { action: SteamFriendRelationshipAction ->
                                friendsViewModel.changeRelationship(animatedFriend, action)
                            }
                        )
                    }
                }
                is SteamFriendsDestination.Profile -> {
                    val animatedFriend = friendsById[animatedDestination.steamId]
                    if (selectedAccount != null) {
                        SteamProfileViewerScreen(
                            viewerAccount = selectedAccount,
                            target = SteamProfileViewerTarget(
                                steamId = animatedDestination.steamId,
                                fallbackName = animatedFriend?.displayName.orEmpty(),
                                fallbackAvatarUrl = animatedFriend?.avatarUrl.orEmpty(),
                                fallbackProfileUrl = animatedFriend?.profileUrl.orEmpty()
                            ),
                            onNavigateBack = { profileSteamId = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        if (destination == SteamFriendsDestination.List && selectedAccount != null) {
            FloatingActionButton(
                onClick = {
                    onSelectedFriendIdChange(null)
                    onAddFriendOpenChange(true)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .steamDockActionClearance(extraBottomSpacing = 12.dp)
                    .padding(end = 18.dp)
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = stringResource(R.string.steam_friend_add_title)
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .steamDockActionClearance()
        )
    }
}
