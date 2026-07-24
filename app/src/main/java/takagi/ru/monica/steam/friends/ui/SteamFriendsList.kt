package takagi.ru.monica.steam.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendsFilter
import takagi.ru.monica.steam.friends.domain.filterSteamFriends
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamFriendsListContent(
    state: SteamFriendsUiState,
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
            item(key = "friends-cache") { FriendsCacheBanner(snapshot.fetchedAt) }
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
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
            items(6, key = { "friends-skeleton-$it" }) { FriendLoadingCard() }
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
