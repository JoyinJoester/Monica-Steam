package takagi.ru.monica.steam.friends.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriendsFilter
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel
import takagi.ru.monica.steam.token.presentation.SteamViewModel
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
fun SteamFriendsScreen(
    searchQuery: String,
    refreshRequest: Long,
    selectedFriendId: String?,
    onSelectedFriendIdChange: (String?) -> Unit,
    onStartChat: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val steamViewModel: SteamViewModel = viewModel(
        factory = remember(context) { SteamViewModel.factory(context) }
    )
    val friendsViewModel: SteamFriendsViewModel = viewModel(
        factory = remember(context) { SteamFriendsViewModel.factory(context) }
    )
    val steamState by steamViewModel.uiState.collectAsState()
    val state by friendsViewModel.uiState.collectAsState()
    val selectedAccount = steamState.accounts.firstOrNull {
        it.id == steamState.selectedAccountId
    } ?: steamState.accounts.firstOrNull()
    val friendsById = remember(state.snapshot?.friends) {
        state.snapshot?.friends.orEmpty().associateBy { it.steamId }
    }
    var filterName by rememberSaveable { mutableStateOf(SteamFriendsFilter.ALL.name) }
    val filter = SteamFriendsFilter.entries.firstOrNull { it.name == filterName }
        ?: SteamFriendsFilter.ALL
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(
        selectedAccount?.id,
        selectedAccount?.accessToken,
        selectedAccount?.steamLoginSecure
    ) {
        onSelectedFriendIdChange(null)
        friendsViewModel.selectAccount(selectedAccount)
    }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest > 0L) friendsViewModel.refresh()
    }

    val feedback = state.actionFeedback
    val acceptSuccessText = stringResource(R.string.steam_friend_accept_success)
    val ignoreSuccessText = stringResource(R.string.steam_friend_ignore_success)
    val actionFailedText = stringResource(R.string.steam_friend_action_failed)
    LaunchedEffect(feedback) {
        if (feedback != null) {
            val text = when {
                feedback.success && feedback.accepted -> acceptSuccessText
                feedback.success -> ignoreSuccessText
                !feedback.message.isNullOrBlank() -> feedback.message
                else -> actionFailedText
            }
            snackbarHostState.showSnackbar(text)
            friendsViewModel.consumeActionFeedback()
        }
    }

    BackHandler(enabled = selectedFriendId != null) {
        onSelectedFriendIdChange(null)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedFriendId,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { easyNotesScreenEnter().togetherWith(easyNotesScreenExit()) },
            label = "SteamFriendsNavigation"
        ) { detailSteamId ->
            val animatedFriend = detailSteamId?.let(friendsById::get)
            if (animatedFriend != null) {
                SteamFriendDetailScreen(
                    friend = animatedFriend,
                    onStartChat = { onStartChat(animatedFriend.steamId) }
                )
            } else {
                SteamFriendsListContent(
                    state = state,
                    account = selectedAccount,
                    query = searchQuery,
                    filter = filter,
                    onFilterChange = { filterName = it.name },
                    onOpenFriend = { onSelectedFriendIdChange(it.steamId) },
                    onRespondToInvite = friendsViewModel::respondToInvite,
                    onRetry = friendsViewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
