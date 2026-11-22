package takagi.ru.monica.steam.friends.groupchat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassExtendedFloatingActionButton as ExtendedFloatingActionButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.SteamGroupAvatarImage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance

@Composable
internal fun SteamGroupChatList(
    state: SteamGroupChatUiState,
    query: String,
    friends: List<SteamFriend> = emptyList(),
    pinnedGroupIds: Set<String> = emptySet(),
    onOpenRoom: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    val friendsById = remember(friends) { friends.associateBy(SteamFriend::steamId) }
    val groups = state.groups.sortedWith(
        compareByDescending<SteamGroupChatSummary> { it.groupId in pinnedGroupIds }
            .thenByDescending { group -> group.rooms.maxOfOrNull { it.lastMessageTimestamp } ?: 0L }
    ).filter {
        query.isBlank() || it.name.contains(query, true) || it.tagline.contains(query, true)
    }
    SteamExpressivePullToRefresh(
        refreshing = state.groupsRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = dockClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                state.groupsLoading && groups.isEmpty() -> item(key = "groups_loading") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                groups.isEmpty() -> item(key = "groups_empty") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyGroups()
                    }
                }
                else -> {
                    items(groups, key = SteamGroupChatSummary::groupId) { group ->
                        val groupMembers = group.topMemberSteamIds
                            .mapNotNull(friendsById::get)
                        GroupCard(group, groupMembers) {
                            onOpenRoom(group.groupId, group.preferredChatId)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreateGroup,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .steamDockActionClearance(extraBottomSpacing = 12.dp)
                .padding(end = 18.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.steam_group_chat_create)) }
        )
    }
}

@Composable
private fun GroupCard(
    group: SteamGroupChatSummary,
    groupMembers: List<SteamFriend>,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SteamGroupAvatarImage(
                url = group.avatarUrl,
                members = groupMembers,
                contentDescription = group.name,
                modifier = Modifier.size(54.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    group.tagline.ifBlank { group.rooms.firstOrNull()?.lastMessage.orEmpty() }
                        .ifBlank { stringResource(R.string.steam_group_chat_members, group.activeMemberCount) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.isVoiceActive) {
                    Text(
                        text = "语音通话中${group.activeVoiceMemberCount.takeIf { it > 0 }?.let { " · $it 人" }.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (group.isVoiceActive) {
                Icon(Icons.Default.Call, contentDescription = "语音通话中", tint = MaterialTheme.colorScheme.primary)
            }
            if (group.unreadCount > 0) Badge { Text(group.unreadCount.toString()) }
        }
    }
}

@Composable
private fun EmptyGroups(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Forum, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.steam_group_chat_empty), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.steam_group_chat_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
