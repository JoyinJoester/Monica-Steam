package takagi.ru.monica.steam.friends.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun SteamFriendDetailScreen(
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
            item(key = "friend-detail-hero") { FriendDetailHero(friend) }
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
private fun FriendDetailHero(friend: SteamFriend) {
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
                    Surface(
                        modifier = Modifier.size(9.dp),
                        shape = CircleShape,
                        color = friend.statusColor()
                    ) {}
                    Text(friend.personaState.label(), style = MaterialTheme.typography.labelLarge)
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
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
