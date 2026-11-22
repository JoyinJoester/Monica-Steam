package takagi.ru.monica.steam.store.share.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.sortSteamFriendsForList
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.ui.label
import takagi.ru.monica.steam.store.gift.domain.SteamStoreGiftFailure
import takagi.ru.monica.steam.store.gift.presentation.SteamStoreGiftUiState
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.ui.components.MonicaModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamStoreGameShareSheet(
    share: SteamStoreGameShare,
    friendsState: SteamStoreGiftUiState,
    onOpenChat: (SteamFriend) -> Unit,
    onShareExternal: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable(share.appId) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val friends = sortSteamFriendsForList(friendsState.friends).filter { friend ->
        normalizedQuery.isBlank() ||
            friend.displayName.contains(normalizedQuery, ignoreCase = true) ||
            friend.realName.contains(normalizedQuery, ignoreCase = true) ||
            friend.steamId.contains(normalizedQuery, ignoreCase = true)
    }
    MonicaModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)

        ) {
            SteamSheetHeader(
                title = stringResource(R.string.steam_store_share_title),
                subtitle = share.name, onDismiss = onDismiss,
                actions = {
                    IconButton(onClick = onRefresh, enabled = !friendsState.loading && !friendsState.refreshing) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                    }
                }
            )
            SteamSheetSearchField(query, { query = it }, stringResource(R.string.steam_store_share_search_hint),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            when {
                friendsState.loading && friendsState.friends.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                friendsState.failure != null && friendsState.friends.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shareFriendFailureMessage(friendsState.failure),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                friends.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.steam_store_share_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(friends, key = { _, friend -> friend.steamId }) { index, friend ->
                        Surface(
                            onClick = { onOpenChat(friend) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            shape = steamSheetRowShape(index, friends.size),
                            color = steamSheetGroupColor()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FriendAvatar(friend = friend, size = 48)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = friend.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = friend.personaState.label(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SteamSheetFooter {
                FilledTonalButton(onClick = onShareExternal,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(stringResource(R.string.steam_store_share_external), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun shareFriendFailureMessage(failure: SteamStoreGiftFailure): String = when (failure) {
    SteamStoreGiftFailure.ACCOUNT_REQUIRED -> stringResource(R.string.steam_store_share_account_required)
    else -> stringResource(R.string.steam_store_share_friends_failed)
}
