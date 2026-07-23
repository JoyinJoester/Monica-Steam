package takagi.ru.monica.steam.friends.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriendsFilter
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel
import takagi.ru.monica.steam.ui.SteamViewModel
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
fun SteamFriendsScreen(
    onNavigateBack: () -> Unit,
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
    var selectedFriendId by rememberSaveable(selectedAccount?.id) {
        mutableStateOf<String?>(null)
    }
    val selectedFriend = state.snapshot?.friends?.firstOrNull {
        it.steamId == selectedFriendId
    }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(SteamFriendsFilter.ALL.name) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    val filter = SteamFriendsFilter.entries.firstOrNull { it.name == filterName }
        ?: SteamFriendsFilter.ALL
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(
        selectedAccount?.id,
        selectedAccount?.accessToken,
        selectedAccount?.steamLoginSecure
    ) {
        selectedFriendId = null
        friendsViewModel.selectAccount(selectedAccount)
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

    BackHandler {
        if (selectedFriendId != null) {
            selectedFriendId = null
        } else {
            onNavigateBack()
        }
    }

    AnimatedContent(
        targetState = selectedFriend?.steamId,
        modifier = modifier,
        transitionSpec = { easyNotesScreenEnter().togetherWith(easyNotesScreenExit()) },
        label = "SteamFriendsNavigation"
    ) { detailSteamId ->
        if (detailSteamId != null && selectedFriend != null) {
            SteamFriendDetailScreen(
                friend = selectedFriend,
                onNavigateBack = { selectedFriendId = null },
                snackbarHostState = snackbarHostState
            )
        } else {
            SteamFriendsOverview(
                query = query,
                searchExpanded = searchExpanded,
                onQueryChange = { query = it },
                onSearchExpandedChange = { searchExpanded = it },
                onNavigateBack = onNavigateBack,
                onOpenAccountSheet = { showAccountSheet = true },
                onRefresh = friendsViewModel::refresh,
                canSwitchAccount = steamState.accounts.isNotEmpty(),
                canRefresh = selectedAccount != null && !state.loading && !state.refreshing,
                refreshing = state.refreshing,
                content = { contentPadding ->
                    SteamFriendsListContent(
                        state = state,
                        account = selectedAccount,
                        query = query,
                        filter = filter,
                        onFilterChange = { filterName = it.name },
                        onOpenFriend = { selectedFriendId = it.steamId },
                        onRespondToInvite = friendsViewModel::respondToInvite,
                        onRetry = friendsViewModel::refresh,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                    )
                },
                snackbarHostState = snackbarHostState
            )
        }
    }

    if (showAccountSheet) {
        SteamFriendAccountSheet(
            accounts = steamState.accounts,
            selectedAccountId = selectedAccount?.id,
            onSelectAccount = { accountId ->
                steamViewModel.selectAccount(accountId)
                showAccountSheet = false
            },
            onDismissRequest = { showAccountSheet = false }
        )
    }
}

@Composable
private fun SteamFriendsOverview(
    query: String,
    searchExpanded: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    onRefresh: () -> Unit,
    canSwitchAccount: Boolean,
    canRefresh: Boolean,
    refreshing: Boolean,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveTopBar(
                modifier = Modifier.statusBarsPadding(),
                title = stringResource(R.string.steam_friends_title),
                searchQuery = query,
                onSearchQueryChange = onQueryChange,
                isSearchExpanded = searchExpanded,
                onSearchExpandedChange = onSearchExpandedChange,
                searchHint = stringResource(R.string.steam_friends_search_hint),
                collapsedTitleEndPadding = 184.dp,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAccountSheet, enabled = canSwitchAccount) {
                        Icon(
                            Icons.Default.SwitchAccount,
                            contentDescription = stringResource(R.string.steam_friends_switch_account)
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = canRefresh) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    }
                    IconButton(onClick = { onSearchExpandedChange(true) }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.topbar_search_hint)
                        )
                    }
                }
            )
        },
        content = content
    )
}
