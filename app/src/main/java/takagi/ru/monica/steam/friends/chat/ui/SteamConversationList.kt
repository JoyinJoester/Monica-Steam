package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatFailureReason
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.SteamGroupAvatarImage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceStatusBanner
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance

internal enum class SteamConversationType { DIRECT, GROUP }

internal data class SteamConversationListEntry(
    val type: SteamConversationType,
    val id: String,
    val chatId: String = "",
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val unreadCount: Int,
    val pinned: Boolean,
    val friend: SteamFriend? = null,
    val avatarUrl: String = "",
    val groupMembers: List<SteamFriend> = emptyList(),
    val voiceActive: Boolean = false,
    val voiceMemberCount: Int = 0
) {
    val stableKey: String get() = "${type.name}:$id:$chatId"
}

internal fun buildSteamConversationEntries(
    sessions: List<SteamChatSession>,
    groups: List<SteamGroupChatSummary>,
    friends: List<SteamFriend>,
    query: String,
    pinnedPartnerSteamIds: Set<String>,
    pinnedGroupIds: Set<String>,
    voiceState: SteamVoiceCallState = SteamVoiceCallState()
): List<SteamConversationListEntry> {
    val normalizedQuery = query.trim()
    val friendsById = friends.associateBy(SteamFriend::steamId)
    val activeDirectPartner = voiceState.target
        ?.takeIf { voiceState.isActive && it.type == SteamVoiceTargetType.DIRECT }
        ?.partnerSteamId
    val activeGroupId = voiceState.target
        ?.takeIf { voiceState.isActive && it.type == SteamVoiceTargetType.GROUP }
        ?.groupId
    val localVoiceMemberCount = if (voiceState.isActive) {
        (voiceState.participants.map { it.steamId } + voiceState.accountSteamId)
            .filter(String::isNotBlank)
            .distinct()
            .size
    } else 0
    val direct = sessions.map { session ->
        val friend = friendsById[session.partnerSteamId]
        SteamConversationListEntry(
            type = SteamConversationType.DIRECT,
            id = session.partnerSteamId,
            title = friend?.displayName ?: session.partnerSteamId,
            subtitle = session.lastMessage.replace(Regex("\\s+"), " ").trim()
                .ifBlank { friend?.gameName.orEmpty() },
            timestamp = session.lastMessageTimestamp,
            unreadCount = session.unreadCount,
            pinned = session.partnerSteamId in pinnedPartnerSteamIds,
            friend = friend,
            voiceActive = session.partnerSteamId == activeDirectPartner
        )
    }.toMutableList()
    if (normalizedQuery.isNotBlank()) {
        val existing = direct.mapTo(mutableSetOf()) { it.id }
        friends.filter { friend ->
            friend.steamId !in existing && (
                friend.displayName.contains(normalizedQuery, true) ||
                    friend.realName.contains(normalizedQuery, true) ||
                    friend.steamId.contains(normalizedQuery, true)
                )
        }.forEach { friend ->
            direct += SteamConversationListEntry(
                type = SteamConversationType.DIRECT,
                id = friend.steamId,
                title = friend.displayName,
                subtitle = friend.gameName,
                timestamp = 0L,
                unreadCount = 0,
                pinned = friend.steamId in pinnedPartnerSteamIds,
                friend = friend,
                voiceActive = friend.steamId == activeDirectPartner
            )
        }
    }
    val groupEntries = groups.map { group ->
        // A group's default room is only its navigation fallback. Activity can
        // happen in any text channel, so using the default room here leaves a
        // busy multi-channel group at the bottom of the conversation list.
        val latestRoom = group.rooms.maxWithOrNull(
            compareBy<SteamGroupChatRoom> { it.lastMessageTimestamp }
                .thenByDescending { it.sortOrder }
        )
        val room = latestRoom?.takeIf { it.lastMessageTimestamp > 0L }
            ?: group.rooms.firstOrNull { it.chatId == group.preferredChatId }
            ?: latestRoom
        val localVoiceActive = group.groupId == activeGroupId
        SteamConversationListEntry(
            type = SteamConversationType.GROUP,
            id = group.groupId,
            chatId = room?.chatId ?: group.preferredChatId,
            title = group.name,
            subtitle = room?.lastMessage.orEmpty().ifBlank { group.tagline }
                .ifBlank { "${group.activeMemberCount} members" },
            timestamp = room?.lastMessageTimestamp ?: 0L,
            unreadCount = group.unreadCount,
            pinned = group.groupId in pinnedGroupIds,
            avatarUrl = group.avatarUrl,
            groupMembers = group.topMemberSteamIds
                .mapNotNull(friendsById::get),
            voiceActive = group.isVoiceActive || localVoiceActive,
            voiceMemberCount = maxOf(
                group.activeVoiceMemberCount,
                if (localVoiceActive) localVoiceMemberCount else 0
            )
        )
    }
    return (direct + groupEntries)
        .filter { entry ->
            normalizedQuery.isBlank() || entry.title.contains(normalizedQuery, true) ||
                entry.subtitle.contains(normalizedQuery, true) || entry.id.contains(normalizedQuery)
        }
        .sortedWith(
            compareByDescending<SteamConversationListEntry> { it.pinned }
                .thenByDescending { it.timestamp }
                .thenBy { it.title.lowercase() }
        )
}

internal fun shouldShowDirectConversationFailure(state: SteamChatUiState): Boolean =
    state.sessionsFailure != null && state.sessions?.sessions.isNullOrEmpty()

internal fun shouldShowGroupConversationFailure(state: SteamGroupChatUiState): Boolean =
    state.groupsFailure && state.groups.isEmpty()

@Composable
internal fun SteamConversationList(
    chatState: SteamChatUiState,
    groupState: SteamGroupChatUiState,
    friends: List<SteamFriend>,
    query: String,
    pinnedPartnerSteamIds: Set<String>,
    pinnedGroupIds: Set<String>,
    onOpenDirect: (String) -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onCreateGroup: () -> Unit,
    voiceState: SteamVoiceCallState = SteamVoiceCallState(),
    onLeaveVoice: () -> Unit = {},
    onToggleVoiceMicrophone: () -> Unit = {},
    onToggleVoiceOutput: () -> Unit = {},
    onSelectVoiceAudioRoute: (SteamVoiceAudioRoute) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    val entries = buildSteamConversationEntries(
        sessions = chatState.sessions?.sessions.orEmpty(),
        groups = groupState.groups,
        friends = friends,
        query = query,
        pinnedPartnerSteamIds = pinnedPartnerSteamIds,
        pinnedGroupIds = pinnedGroupIds,
        voiceState = voiceState
    )
    SteamExpressivePullToRefresh(
        refreshing = chatState.sessionsRefreshing || groupState.groupsRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = dockClearance + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (voiceState.isActive) {
                item("active-voice-call") {
                    SteamVoiceStatusBanner(
                        state = voiceState,
                        fallbackTitle = "Steam 语音通话",
                        onLeave = onLeaveVoice,
                        onToggleMicrophone = onToggleVoiceMicrophone,
                        onToggleOutput = onToggleVoiceOutput,
                        onSelectAudioRoute = onSelectVoiceAudioRoute
                    )
                }
            }
            if (shouldShowDirectConversationFailure(chatState)) {
                item("conversation-error") {
                    ChatFailureBanner(requireNotNull(chatState.sessionsFailure), onRefresh)
                }
            }
            if (shouldShowGroupConversationFailure(groupState)) {
                item("group-conversation-error") {
                    ChatFailureBanner(SteamChatFailureReason.UNAVAILABLE, onRefresh)
                }
            }
            if ((chatState.sessionsLoading || groupState.groupsLoading) && entries.isEmpty()) {
                item("conversation-loading") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (entries.isEmpty()) {
                item("conversation-empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("暂无聊天", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                items(entries, key = SteamConversationListEntry::stableKey) { entry ->
                    SteamConversationRow(
                        entry = entry,
                        onClick = {
                            if (entry.type == SteamConversationType.DIRECT) onOpenDirect(entry.id)
                            else onOpenGroup(entry.id, entry.chatId)
                        },
                        modifier = Modifier.animateItem()
                    )
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
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("新建群聊") }
        )
    }
}

@Composable
private fun SteamConversationRow(
    entry: SteamConversationListEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (entry.unreadCount > 0) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                entry.friend != null -> FriendAvatar(entry.friend, 50)
                entry.type == SteamConversationType.GROUP -> SteamGroupAvatarImage(
                    url = entry.avatarUrl,
                    members = entry.groupMembers,
                    contentDescription = entry.title,
                    modifier = Modifier.size(50.dp)
                )
                else -> Surface(Modifier.size(50.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.timestamp > 0L) Text(
                        compactConversationTime(entry.timestamp),
                        modifier = Modifier.widthIn(max = 80.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.subtitle, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.unreadCount > 0) Badge { Text(entry.unreadCount.coerceAtMost(99).toString()) }
                    if (entry.voiceActive) Icon(Icons.Default.Call, contentDescription = "语音通话中",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                if (entry.voiceActive) {
                    Text(
                        text = "语音通话中${entry.voiceMemberCount.takeIf { it > 0 }?.let { " · $it 人" }.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        }
    }
}


private fun compactConversationTime(timestampSeconds: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochSecond(timestampSeconds).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val pattern = when {
        date.toLocalDate() == today -> "HH:mm"
        date.year == today.year -> "M/d"
        else -> "yy/M/d"
    }
    return date.format(DateTimeFormatter.ofPattern(pattern))
}
