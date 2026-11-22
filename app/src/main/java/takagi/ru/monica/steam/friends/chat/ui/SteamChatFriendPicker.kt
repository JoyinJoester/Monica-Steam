package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassExtendedFloatingActionButton as ExtendedFloatingActionButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.telegram.*
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.sortSteamFriendsForList
import takagi.ru.monica.steam.friends.ui.label
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance

@Composable
internal fun SteamChatFriendPicker(
    friends: List<SteamFriend>, loading: Boolean, query: String,
    onOpenThread: (String) -> Unit, onAddFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val normalizedQuery = query.trim()
    val visibleFriends = sortSteamChatFriendsForPicker(friends).filter { friend ->
        (filter == 0 || (filter == 1 && friend.isOnlineOrPlaying) || (filter == 2 && friend.isPlaying)) &&
            (normalizedQuery.isBlank() || friend.displayName.contains(normalizedQuery, true) ||
                friend.realName.contains(normalizedQuery, true) || friend.steamId.contains(normalizedQuery))
    }
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    TelegramSocialTheme {
        SteamGlassHost(modifier.fillMaxSize(), content = {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = with(density) { headerHeight.toDp() }, bottom = dockClearance + 112.dp)
            ) {
                if (loading) item("contacts-loading") {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                itemsIndexed(visibleFriends, key = { _, friend -> friend.steamId }) { index, friend ->
                    TelegramNativeContactRow(
                        friend,
                        if (friend.isPlaying) stringResource(R.string.steam_friend_playing, friend.gameName.ifBlank { friend.gameId }) else friend.personaState.label(),
                        onClick = { onOpenThread(friend.steamId) },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    )
                }
                if (!loading && visibleFriends.isEmpty()) item("contacts-empty") {
                    Text(stringResource(R.string.steam_friends_empty), Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)).padding(22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }, overlay = {
            Box(Modifier.align(Alignment.TopStart).onSizeChanged { headerHeight = it.height }
                .testTag("steam-contacts-filter")) {
                TelegramContactsFilter(listOf(stringResource(R.string.steam_friends_filter_all),
                    stringResource(R.string.steam_friends_filter_online), stringResource(R.string.steam_friends_filter_playing)),
                    filter, { filter = it })
            }
            ExtendedFloatingActionButton(
                onClick = onAddFriend,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .steamDockActionClearance(extraBottomSpacing = 12.dp).padding(end = 18.dp),
                // M3E extended FAB: tonal container, labelled, 16dp corners instead of a blue circle.
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = {
                    Text(
                        stringResource(R.string.steam_friend_add_title),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        })
    }
}

internal fun sortSteamChatFriendsForPicker(friends: List<SteamFriend>): List<SteamFriend> = sortSteamFriendsForList(friends)
