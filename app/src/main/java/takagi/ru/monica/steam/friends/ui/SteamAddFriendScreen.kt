package takagi.ru.monica.steam.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.foundation.ui.SteamListSearchField

@Composable
internal fun SteamAddFriendScreen(
    state: SteamFriendsUiState,
    onSearch: (String) -> Unit,
    onOpenFriend: (SteamFriend) -> Unit,
    onAddFriend: (SteamFriend) -> Unit,
    onRespondToInvite: (SteamFriend, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val dockClearance = LocalSteamDockContentClearance.current
    val incomingRequests = state.snapshot?.incomingRequests.orEmpty()
    val discovery = state.discovery
    var query by rememberSaveable(state.accountId) { mutableStateOf("") }

    fun submitSearch() {
        if (query.isBlank() || discovery.searching) return
        focusManager.clearFocus()
        onSearch(query)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = dockClearance + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "add-friend-search") {
            SteamListSearchField(
                value = query,
                onValueChange = { value ->
                    val nextQuery = value.take(MAX_QUERY_LENGTH)
                    if (query.isNotBlank() && nextQuery.isBlank()) onSearch("")
                    query = nextQuery
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.steam_friend_add_search_hint),
                showClear = !discovery.searching,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { submitSearch() },
                            enabled = query.isNotBlank() && !discovery.searching
                        ) {
                            if (discovery.searching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(
                                        R.string.steam_friend_add_search_action
                                    )
                                )
                            }
                        }
                    }
                },
                onSearch = { submitSearch() }
            )
        }

        item(key = "add-friend-requests-title") {
            SectionTitle(
                text = stringResource(
                    R.string.steam_friend_requests_title,
                    incomingRequests.size
                )
            )
        }
        if (state.loading && state.snapshot == null) {
            item(key = "add-friend-requests-loading") {
                FriendLoadingCard()
            }
        } else if (incomingRequests.isEmpty()) {
            item(key = "add-friend-requests-empty") {
                FriendRequestsEmptyState()
            }
        } else {
            items(incomingRequests, key = { "request-${it.steamId}" }) { friend ->
                SteamFriendCard(
                    friend = friend,
                    actionInProgress = state.actionSteamId == friend.steamId,
                    actionsEnabled = state.actionSteamId == null,
                    onClick = { onOpenFriend(friend) },
                    onRespondToInvite = { accept -> onRespondToInvite(friend, accept) }
                )
            }
        }

        discovery.failure?.let { failure ->
            item(key = "add-friend-search-error") {
                FriendsErrorBanner(
                    failure = failure,
                    onRetry = { onSearch(discovery.submittedQuery) }
                )
            }
        }

        if (discovery.results.isNotEmpty()) {
            item(key = "add-friend-results-title") {
                SectionTitle(
                    text = stringResource(
                        R.string.steam_friend_search_results_title,
                        discovery.results.size
                    )
                )
            }
            items(discovery.results, key = { "candidate-${it.steamId}" }) { friend ->
                SteamFriendSearchResultCard(
                    friend = friend,
                    actionInProgress = state.actionSteamId == friend.steamId,
                    actionsEnabled = state.actionSteamId == null,
                    onAddFriend = { onAddFriend(friend) },
                    onAcceptInvite = { onRespondToInvite(friend, true) }
                )
            }
        } else if (discovery.searched && discovery.failure == null) {
            item(key = "add-friend-search-empty") {
                FriendDiscoveryMessage(
                    icon = Icons.Default.PersonSearch,
                    text = stringResource(R.string.steam_friend_search_empty)
                )
            }
        } else if (!discovery.searching) {
            item(key = "add-friend-search-intro") {
                FriendDiscoveryMessage(
                    icon = Icons.Default.PersonAdd,
                    text = stringResource(R.string.steam_friend_add_intro)
                )
            }
        }
    }
}

@Composable
private fun FriendRequestsEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.steam_friend_requests_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamFriendSearchResultCard(
    friend: SteamFriend,
    actionInProgress: Boolean,
    actionsEnabled: Boolean,
    onAddFriend: () -> Unit,
    onAcceptInvite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(friend = friend, size = 52)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = friend.steamId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (actionInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                when (friend.relationship) {
                    SteamFriendRelationship.UNKNOWN -> Button(
                        onClick = onAddFriend,
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.steam_friend_send_invite))
                    }
                    SteamFriendRelationship.REQUEST_INCOMING -> Button(
                        onClick = onAcceptInvite,
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.steam_friend_accept))
                    }
                    else -> FriendRelationshipLabel(friend.relationship)
                }
            }
        }
    }
}

@Composable
private fun FriendRelationshipLabel(relationship: SteamFriendRelationship) {
    val label = when (relationship) {
        SteamFriendRelationship.FRIEND -> R.string.steam_friend_already_friend
        SteamFriendRelationship.REQUEST_OUTGOING -> R.string.steam_friend_outgoing_request
        SteamFriendRelationship.BLOCKED -> R.string.steam_friend_blocked_status
        SteamFriendRelationship.REQUEST_INCOMING -> R.string.steam_friend_incoming_request
        SteamFriendRelationship.UNKNOWN -> R.string.steam_friend_send_invite
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun FriendDiscoveryMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val MAX_QUERY_LENGTH = 160
