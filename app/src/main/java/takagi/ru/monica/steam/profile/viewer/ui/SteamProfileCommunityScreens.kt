package takagi.ru.monica.steam.profile.viewer.ui

import takagi.ru.monica.steam.foundation.ui.SteamRefreshIconButton
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.sortSteamFriendsForList
import takagi.ru.monica.steam.friends.ui.SteamFriendCard
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGroup
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerFailureReason
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot

@Composable
internal fun SteamProfileFriendsScreen(
    friends: List<SteamFriend>,
    loading: Boolean,
    failure: SteamProfileViewerFailureReason?,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFriend: (SteamFriend) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedFriends = remember(friends) { sortSteamFriendsForList(friends) }
    SteamProfileCommunityListScreen(
        title = stringResource(R.string.steam_friends_title),
        countText = stringResource(R.string.steam_profile_friends_title_count, sortedFriends.size),
        loading = loading,
        failure = failure,
        empty = sortedFriends.isEmpty(),
        emptyIcon = Icons.Default.People,
        emptyText = stringResource(R.string.steam_profile_no_friends),
        onNavigateBack = onNavigateBack,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        items(sortedFriends, key = SteamFriend::steamId) { friend ->
            SteamFriendCard(
                friend = friend,
                actionInProgress = false,
                actionsEnabled = false,
                onClick = { onOpenFriend(friend) },
                onRespondToInvite = {}
            )
        }
    }
}

@Composable
internal fun SteamProfileGroupsScreen(
    groups: List<SteamProfileGroup>,
    loading: Boolean,
    failure: SteamProfileViewerFailureReason?,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenGroup: (SteamProfileGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    SteamProfileCommunityListScreen(
        title = stringResource(R.string.steam_community_groups),
        countText = stringResource(R.string.steam_profile_groups_title_count, groups.size),
        loading = loading,
        failure = failure,
        empty = groups.isEmpty(),
        emptyIcon = Icons.Default.Groups,
        emptyText = stringResource(R.string.steam_profile_no_groups),
        onNavigateBack = onNavigateBack,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        items(groups, key = SteamProfileGroup::groupId) { group ->
            SteamProfileGroupRow(group = group, onClick = { onOpenGroup(group) })
        }
    }
}

@Composable
internal fun SteamProfilePerfectGamesScreen(
    snapshot: SteamProfileViewerSnapshot,
    onNavigateBack: () -> Unit,
    onOpenGame: (SteamGame) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    Column(modifier = modifier.fillMaxSize()) {
        SteamProfileSubpageHeader(
            title = stringResource(R.string.steam_profile_perfect_games),
            subtitle = stringResource(
                R.string.steam_profile_perfect_games_title_count,
                snapshot.perfectGameCount
            ),
            onNavigateBack = onNavigateBack
        )
        if (snapshot.perfectGames.isEmpty()) {
            SteamProfileEmptyState(
                icon = Icons.Default.SportsEsports,
                text = stringResource(R.string.steam_profile_no_perfect_games),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = dockClearance + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(snapshot.perfectGames, key = SteamGame::appId) { game ->
                    SteamProfileGameRow(game = game, onClick = { onOpenGame(game) })
                }
            }
        }
    }
}

@Composable
internal fun SteamProfileGroupDetailScreen(
    group: SteamProfileGroup,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val openFailed = stringResource(R.string.steam_identity_open_failed)
    val image = rememberSteamProfileViewerImage(group.avatarUrl)
    val dockClearance = LocalSteamDockContentClearance.current
    Column(modifier = modifier.fillMaxSize()) {
        SteamProfileSubpageHeader(
            title = group.name,
            subtitle = stringResource(R.string.steam_community_groups),
            onNavigateBack = onNavigateBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = dockClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "group_identity") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(96.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            if (image != null) {
                                Image(
                                    bitmap = image,
                                    contentDescription = group.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Groups,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item(key = "group_stats") {
                SteamProfileGroupStats(group)
            }
            if (group.profileUrl.isNotBlank()) {
                item(key = "group_official") {
                    FilledTonalButton(
                        onClick = {
                            runCatching { uriHandler.openUri(group.profileUrl) }
                                .onFailure {
                                    Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text(
                            text = stringResource(R.string.steam_profile_open_official_group),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamProfileGroupStats(group: SteamProfileGroup) {
    val facts = listOfNotNull(
        group.memberCount?.let { stringResource(R.string.steam_profile_group_members, it) },
        group.onlineCount?.let { stringResource(R.string.steam_profile_group_online, it) },
        group.inGameCount?.let { stringResource(R.string.steam_profile_group_in_game, it) },
        group.groupChatCount?.let { stringResource(R.string.steam_profile_group_chatting, it) }
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            facts.ifEmpty { listOf(stringResource(R.string.steam_profile_group_stats_unavailable)) }
                .forEach { fact ->
                    Text(fact, style = MaterialTheme.typography.bodyLarge)
                }
        }
    }
}

@Composable
private fun SteamProfileCommunityListScreen(
    title: String,
    countText: String,
    loading: Boolean,
    failure: SteamProfileViewerFailureReason?,
    empty: Boolean,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyText: String,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val dockClearance = LocalSteamDockContentClearance.current
    Column(modifier = modifier.fillMaxSize()) {
        SteamProfileSubpageHeader(
            title = title,
            subtitle = countText,
            refreshAction = { SteamRefreshIconButton(loading, onRefresh) },
            onNavigateBack = onNavigateBack
        )
        SteamExpressivePullToRefresh(
            showIndicator = false,
            refreshing = loading,
            onRefresh = onRefresh,
            enabled = !loading,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                loading && empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                failure != null && empty -> SteamProfileFailureState(
                    failure = failure,
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize()
                )
                empty -> SteamProfileEmptyState(
                    icon = emptyIcon,
                    text = emptyText,
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = dockClearance + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SteamProfileSubpageHeader(
    title: String,
    subtitle: String,
    refreshAction: @Composable () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        refreshAction()
    }
}

@Composable
private fun SteamProfileGroupRow(
    group: SteamProfileGroup,
    onClick: () -> Unit
) {
    val image = rememberSteamProfileViewerImage(group.avatarUrl)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = group.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, contentDescription = null)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                group.memberCount?.let { members ->
                    Text(
                        text = stringResource(R.string.steam_profile_group_members, members),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamProfileFailureState(
    failure: SteamProfileViewerFailureReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = steamProfileFailureMessage(failure),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.steam_community_retry))
        }
    }
}

@Composable
private fun SteamProfileEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
