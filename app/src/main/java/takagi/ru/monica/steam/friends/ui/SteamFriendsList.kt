package takagi.ru.monica.steam.friends.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialTheme
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialDivider
import takagi.ru.monica.steam.friends.chat.telegram.TelegramContactsFilter
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialInfoRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Mail
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
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
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance

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
    modifier: Modifier = Modifier,
    onAddFriend: (() -> Unit)? = null
) {
    val dockContentClearance = LocalSteamDockContentClearance.current
    val snapshot = state.snapshot
    val filtered = remember(snapshot, query, filter) {
        filterSteamFriends(snapshot?.friends.orEmpty(), query, filter)
    }
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    TelegramSocialTheme {
    SteamGlassHost(modifier, content = {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = with(density) { headerHeight.toDp() },
            bottom = dockContentClearance + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (state.fromCache && snapshot != null) {
            item(key = "friends-cache") { FriendsCacheBanner(snapshot.fetchedAt) }
        }
        state.failure?.let { failure ->
            item(key = "friends-error") {
                FriendsErrorBanner(failure = failure, onRetry = onRetry)
            }
        }
        item(key = "friends-actions") {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)) {
                Column {
                    if (onAddFriend != null) TelegramSocialInfoRow(stringResource(R.string.steam_friend_add_title),
                        icon = Icons.Default.PersonAdd, onClick = onAddFriend)
                    TelegramSocialInfoRow(stringResource(R.string.steam_friends_filter_requests),
                        icon = Icons.Default.Mail, onClick = { onFilterChange(SteamFriendsFilter.REQUESTS) })
                }
            }
        }
        if (state.loading && snapshot == null) {
            items(6, key = { "friends-skeleton-$it" }) { FriendLoadingCard() }
        } else {
            items(filtered, key = SteamFriend::steamId) { friend ->
                val last = friend.steamId == filtered.lastOrNull()?.steamId
                Box(Modifier.background(MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(bottomStart = if (last) 26.dp else 0.dp, bottomEnd = if (last) 26.dp else 0.dp))) {
                SteamFriendCard(
                    friend = friend,
                    actionInProgress = state.actionSteamId == friend.steamId,
                    actionsEnabled = state.actionSteamId == null,
                    onClick = { onOpenFriend(friend) },
                    onRespondToInvite = { accept -> onRespondToInvite(friend, accept) }
                )
                }
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
    }, overlay = {
        Box(Modifier.onSizeChanged { headerHeight = it.height }) {
            TelegramContactsFilter(SteamFriendsFilter.entries.map { stringResource(it.labelRes()) },
                filter.ordinal, { onFilterChange(SteamFriendsFilter.entries[it]) })
        }
    })
    }
}
