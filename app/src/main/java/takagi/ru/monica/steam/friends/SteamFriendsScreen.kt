package takagi.ru.monica.steam.friends

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.profile.SteamRemoteImageCache
import takagi.ru.monica.steam.ui.SteamViewModel
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val selectedAccount = steamState.accounts.firstOrNull { it.id == steamState.selectedAccountId }
        ?: steamState.accounts.firstOrNull()
    var selectedFriendId by rememberSaveable(selectedAccount?.id) { mutableStateOf<String?>(null) }
    val selectedFriend = state.snapshot?.friends?.firstOrNull { it.steamId == selectedFriendId }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(SteamFriendsFilter.ALL.name) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    val filter = SteamFriendsFilter.entries.firstOrNull { it.name == filterName }
        ?: SteamFriendsFilter.ALL
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedAccount?.id, selectedAccount?.accessToken, selectedAccount?.steamLoginSecure) {
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
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    ExpressiveTopBar(
                        modifier = Modifier.statusBarsPadding(),
                        title = stringResource(R.string.steam_friends_title),
                        searchQuery = query,
                        onSearchQueryChange = { query = it },
                        isSearchExpanded = searchExpanded,
                        onSearchExpandedChange = { searchExpanded = it },
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
                            IconButton(
                                onClick = { showAccountSheet = true },
                                enabled = steamState.accounts.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.SwitchAccount,
                                    contentDescription = stringResource(R.string.steam_friends_switch_account)
                                )
                            }
                            IconButton(
                                onClick = friendsViewModel::refresh,
                                enabled = selectedAccount != null && !state.loading && !state.refreshing
                            ) {
                                if (state.refreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh)
                                    )
                                }
                            }
                            IconButton(onClick = { searchExpanded = true }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.topbar_search_hint)
                                )
                            }
                        }
                    )
                }
            ) { contentPadding ->
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
            }
        }
    }

    if (showAccountSheet) {
        ModalBottomSheet(onDismissRequest = { showAccountSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = stringResource(R.string.steam_friends_switch_account),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                steamState.accounts.forEach { account ->
                    FriendAccountRow(
                        account = account,
                        selected = account.id == selectedAccount?.id,
                        onClick = {
                            steamViewModel.selectAccount(account.id)
                            showAccountSheet = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteamFriendsListContent(
    state: SteamFriendsUiState,
    account: SteamAccount?,
    query: String,
    filter: SteamFriendsFilter,
    onFilterChange: (SteamFriendsFilter) -> Unit,
    onOpenFriend: (SteamFriend) -> Unit,
    onRespondToInvite: (SteamFriend, Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val filtered = remember(snapshot, query, filter) {
        filterSteamFriends(snapshot?.friends.orEmpty(), query, filter)
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "friends-summary") {
            FriendsSummaryCard(snapshot = snapshot, account = account)
        }
        if (state.refreshing) {
            item(key = "friends-refreshing") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.steam_friends_syncing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (state.fromCache && snapshot != null) {
            item(key = "friends-cache") {
                FriendsCacheBanner(snapshot.fetchedAt)
            }
        }
        state.failure?.let { failure ->
            item(key = "friends-error") {
                FriendsErrorBanner(failure = failure, onRetry = onRetry)
            }
        }
        item(key = "friends-filters") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SteamFriendsFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilterChange(option) },
                        label = { Text(stringResource(option.labelRes())) },
                        leadingIcon = if (filter == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }
        if (state.loading && snapshot == null) {
            items(6, key = { "friends-skeleton-$it" }) {
                FriendLoadingCard()
            }
        } else {
            items(filtered, key = SteamFriend::steamId) { friend ->
                SteamFriendCard(
                    friend = friend,
                    actionInProgress = state.actionSteamId == friend.steamId,
                    actionsEnabled = state.actionSteamId == null,
                    onClick = { onOpenFriend(friend) },
                    onRespondToInvite = { accept -> onRespondToInvite(friend, accept) }
                )
            }
            if (
                filtered.isEmpty() &&
                !state.loading &&
                (state.failure == null || snapshot != null)
            ) {
                item(key = "friends-empty") {
                    FriendsEmptyState(filter = filter, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun FriendsSummaryCard(snapshot: SteamFriendsSnapshot?, account: SteamAccount?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(
                        R.string.steam_friends_summary,
                        snapshot?.acceptedFriends?.size ?: 0,
                        snapshot?.onlineCount ?: 0
                    ),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansFlexFontFamily),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = account?.displayName?.ifBlank { account.accountName }
                        ?: stringResource(R.string.steam_friends_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (snapshot != null &&
                    (snapshot.incomingRequests.isNotEmpty() || snapshot.outgoingRequests.isNotEmpty())
                ) {
                    Text(
                        text = stringResource(
                            R.string.steam_friends_requests_summary,
                            snapshot.incomingRequests.size,
                            snapshot.outgoingRequests.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamFriendCard(
    friend: SteamFriend,
    actionInProgress: Boolean,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onRespondToInvite: (Boolean) -> Unit
) {
    val isIncoming = friend.relationship == SteamFriendRelationship.REQUEST_INCOMING
    val isOutgoing = friend.relationship == SteamFriendRelationship.REQUEST_OUTGOING
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isIncoming -> MaterialTheme.colorScheme.tertiaryContainer
                friend.isPlaying -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FriendAvatar(friend = friend, size = 52)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isIncoming -> stringResource(R.string.steam_friend_incoming_request)
                            isOutgoing -> stringResource(R.string.steam_friend_outgoing_request)
                            friend.isPlaying -> stringResource(
                                R.string.steam_friend_playing,
                                friend.gameName.ifBlank { friend.gameId }
                            )
                            else -> friend.personaState.label()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (friend.isPlaying) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (actionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = CircleShape,
                        color = friend.statusColor()
                    ) {}
                }
            }
            if (isIncoming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = { onRespondToInvite(false) },
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.steam_friend_ignore))
                    }
                    Button(
                        onClick = { onRespondToInvite(true) },
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.steam_friend_accept))
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamFriendDetailScreen(
    friend: SteamFriend,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveTopBar(
                modifier = Modifier.statusBarsPadding(),
                title = stringResource(R.string.steam_friend_details_title),
                searchQuery = "",
                onSearchQueryChange = {},
                isSearchExpanded = false,
                onSearchExpandedChange = {},
                collapsedTitleEndPadding = 76.dp,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { openSteamProfile(context, friend) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.steam_friend_open_profile)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "friend-detail-hero") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (friend.isPlaying) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FriendAvatar(friend = friend, size = 96)
                        Text(
                            text = friend.displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = GoogleSansFlexFontFamily
                            ),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        friend.realName.takeIf(String::isNotBlank)?.let { realName ->
                            Text(
                                text = realName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = friend.statusColor().copy(alpha = 0.18f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(modifier = Modifier.size(9.dp), shape = CircleShape, color = friend.statusColor()) {}
                                Text(friend.personaState.label(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (friend.isPlaying) {
                item(key = "friend-detail-game") {
                    DetailSectionCard(
                        icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                        title = stringResource(R.string.steam_friend_current_game),
                        value = friend.gameName.ifBlank { friend.gameId },
                        emphasized = true
                    )
                }
            }
            item(key = "friend-detail-information-title") {
                Text(
                    text = stringResource(R.string.steam_friend_profile_information),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            item(key = "friend-detail-steamid") {
                DetailSectionCard(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    title = stringResource(R.string.steam_friend_steam_id),
                    value = friend.steamId
                )
            }
            if (friend.friendSince > 0L) {
                item(key = "friend-detail-friends-since") {
                    DetailSectionCard(
                        icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                        title = stringResource(R.string.steam_friend_friends_since),
                        value = formatSteamFriendTime(friend.friendSince)
                    )
                }
            }
            item(key = "friend-detail-last-online") {
                DetailSectionCard(
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    title = stringResource(R.string.steam_friend_last_online),
                    value = if (friend.lastLogoff > 0L) {
                        formatSteamFriendTime(friend.lastLogoff)
                    } else {
                        stringResource(R.string.steam_friend_unknown_time)
                    }
                )
            }
            if (friend.countryCode.isNotBlank()) {
                item(key = "friend-detail-location") {
                    DetailSectionCard(
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        title = stringResource(R.string.steam_friend_location),
                        value = friend.countryCode
                    )
                }
            }
            item(key = "friend-detail-open-profile") {
                FilledTonalButton(
                    onClick = { openSteamProfile(context, friend) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_friend_open_profile))
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    emphasized: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FriendAccountRow(account: SteamAccount, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                account.displayName.ifBlank { account.accountName }.ifBlank { account.visibleSteamId },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(account.accountName.ifBlank { account.visibleSteamId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        account.displayName.ifBlank { account.accountName }.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        trailingContent = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Composable
private fun FriendsCacheBanner(fetchedAt: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.steam_friends_cached, formatSteamFriendTime(fetchedAt / 1000L)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun FriendsErrorBanner(failure: SteamFriendsFailureReason, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Text(
                text = stringResource(failure.messageRes()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (failure != SteamFriendsFailureReason.ACCOUNT_REQUIRED) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.steam_friends_retry))
                }
            }
        }
    }
}

@Composable
private fun FriendsEmptyState(filter: SteamFriendsFilter, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            text = stringResource(
                if (filter == SteamFriendsFilter.REQUESTS) {
                    R.string.steam_friends_empty_requests
                } else {
                    R.string.steam_friends_empty
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.steam_friends_retry))
        }
    }
}

@Composable
private fun FriendLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth().height(78.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier.fillMaxWidth(0.58f).height(14.dp), shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
                Surface(modifier = Modifier.fillMaxWidth(0.38f).height(10.dp), shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
            }
        }
    }
}

@Composable
private fun FriendAvatar(friend: SteamFriend, size: Int) {
    val avatar = rememberSteamFriendAvatar(friend.avatarUrl)
    Box(modifier = Modifier.size(size.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = friend.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        friend.displayName.take(1).uppercase(),
                        style = if (size >= 80) MaterialTheme.typography.headlineLarge
                        else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.size((size / 4).coerceAtLeast(10).dp).align(Alignment.BottomEnd),
            shape = CircleShape,
            color = friend.statusColor()
        ) {}
    }
}

@Composable
private fun rememberSteamFriendAvatar(url: String): ImageBitmap? {
    val context = LocalContext.current
    val cache = remember(context) { SteamRemoteImageCache.get(context.applicationContext) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = url.takeIf(String::isNotBlank)?.let { cache.load(it)?.asImageBitmap() }
    }
    return bitmap
}

@Composable
private fun SteamPersonaState.label(): String = stringResource(
    when (this) {
        SteamPersonaState.ONLINE -> R.string.steam_friend_online
        SteamPersonaState.BUSY -> R.string.steam_friend_busy
        SteamPersonaState.AWAY -> R.string.steam_friend_away
        SteamPersonaState.SNOOZE -> R.string.steam_friend_snooze
        SteamPersonaState.LOOKING_TO_TRADE -> R.string.steam_friend_looking_to_trade
        SteamPersonaState.LOOKING_TO_PLAY -> R.string.steam_friend_looking_to_play
        SteamPersonaState.OFFLINE, SteamPersonaState.INVISIBLE -> R.string.steam_friend_offline
    }
)

@Composable
private fun SteamFriend.statusColor() = when {
    isPlaying -> MaterialTheme.colorScheme.tertiary
    personaState.isOnline -> MaterialTheme.colorScheme.primary
    relationship == SteamFriendRelationship.REQUEST_INCOMING -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outline
}

private fun SteamFriendsFilter.labelRes(): Int = when (this) {
    SteamFriendsFilter.ALL -> R.string.steam_friends_filter_all
    SteamFriendsFilter.ONLINE -> R.string.steam_friends_filter_online
    SteamFriendsFilter.PLAYING -> R.string.steam_friends_filter_playing
    SteamFriendsFilter.REQUESTS -> R.string.steam_friends_filter_requests
}

private fun SteamFriendsFailureReason.messageRes(): Int = when (this) {
    SteamFriendsFailureReason.ACCOUNT_REQUIRED -> R.string.steam_friends_account_required
    SteamFriendsFailureReason.SESSION_REQUIRED -> R.string.steam_friends_session_required
    SteamFriendsFailureReason.NETWORK -> R.string.steam_friends_network_error
    SteamFriendsFailureReason.UNAVAILABLE -> R.string.steam_friends_unavailable
}

private fun formatSteamFriendTime(timestampSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1_000L))

private fun openSteamProfile(context: android.content.Context, friend: SteamFriend) {
    val url = friend.profileUrl.takeIf { it.startsWith("https://steamcommunity.com/") }
        ?: "https://steamcommunity.com/profiles/${friend.steamId}"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
