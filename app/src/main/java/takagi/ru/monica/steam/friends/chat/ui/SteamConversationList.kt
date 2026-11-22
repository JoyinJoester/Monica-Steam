package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatFailureReason
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceStatusBanner
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamDockActionClearance
import takagi.ru.monica.steam.design.telegram.SteamTelegramGlassHost

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
    val voiceMemberCount: Int = 0,
    val archived: Boolean = false,
    /** Local delete marker: the row stays hidden until a newer message arrives. */
    val hiddenAt: Long? = null
) {
    val stableKey: String get() = "${type.name}:$id:$chatId"
    val hidden: Boolean get() = hiddenAt != null && timestamp <= hiddenAt
}

internal fun buildSteamConversationEntries(
    sessions: List<SteamChatSession>,
    groups: List<SteamGroupChatSummary>,
    friends: List<SteamFriend>,
    query: String,
    pinnedPartnerSteamIds: Set<String>,
    pinnedGroupIds: Set<String>,
    voiceState: SteamVoiceCallState = SteamVoiceCallState(),
    archivedPartnerSteamIds: Set<String> = emptySet(),
    archivedGroupIds: Set<String> = emptySet(),
    hiddenPartnerAt: Map<String, Long> = emptyMap(),
    hiddenGroupAt: Map<String, Long> = emptyMap()
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
            subtitle = friend?.gameName?.takeIf(String::isNotBlank).orEmpty(),
            timestamp = session.lastMessageTimestamp,
            unreadCount = session.unreadCount,
            pinned = session.partnerSteamId in pinnedPartnerSteamIds,
            friend = friend,
            voiceActive = session.partnerSteamId == activeDirectPartner,
            archived = session.partnerSteamId in archivedPartnerSteamIds,
            hiddenAt = hiddenPartnerAt[session.partnerSteamId]
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
                voiceActive = friend.steamId == activeDirectPartner,
                archived = friend.steamId in archivedPartnerSteamIds,
                hiddenAt = hiddenPartnerAt[friend.steamId]
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
            ),
            archived = group.groupId in archivedGroupIds,
            hiddenAt = hiddenGroupAt[group.groupId]
        )
    }
    return (direct + groupEntries)
        .filter { entry ->
            normalizedQuery.isBlank() || entry.title.contains(normalizedQuery, true) ||
                entry.subtitle.contains(normalizedQuery, true) || entry.id.contains(normalizedQuery)
        }
        .filterNot { it.hidden }
        .sortedWith(
            compareByDescending<SteamConversationListEntry> { it.pinned }
                .thenByDescending { it.timestamp }
                .thenBy { it.title.lowercase() }
        )
}

/** Tabs of the inbox. Order matters: it is the chip order left to right. */
internal enum class SteamConversationFilter { ALL, UNREAD, DIRECT, GROUP, PINNED, ARCHIVED }

internal fun filterSteamConversationEntries(
    entries: List<SteamConversationListEntry>,
    filter: SteamConversationFilter
): List<SteamConversationListEntry> = entries.filter { entry ->
    when (filter) {
        SteamConversationFilter.ALL -> !entry.archived
        SteamConversationFilter.UNREAD -> !entry.archived && entry.unreadCount > 0
        SteamConversationFilter.DIRECT -> !entry.archived && entry.type == SteamConversationType.DIRECT
        SteamConversationFilter.GROUP -> !entry.archived && entry.type == SteamConversationType.GROUP
        SteamConversationFilter.PINNED -> !entry.archived && entry.pinned
        SteamConversationFilter.ARCHIVED -> entry.archived
    }
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
    flags: SteamConversationFlags = SteamConversationFlags(),
    actions: SteamConversationActions = SteamConversationActions(),
    onOpenDirect: (String) -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onCreateGroup: () -> Unit,
    voiceState: SteamVoiceCallState = SteamVoiceCallState(),
    onLeaveVoice: () -> Unit = {},
    onToggleVoiceMicrophone: () -> Unit = {},
    onToggleVoiceOutput: () -> Unit = {},
    onSelectVoiceAudioRoute: (SteamVoiceAudioRoute) -> Unit = {},
    onNewChat: () -> Unit = onCreateGroup,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    var filter by rememberSaveable { mutableStateOf(SteamConversationFilter.ALL) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf<List<SteamConversationListEntry>>(emptyList()) }
    val allEntries = rememberSteamConversationEntries(chatState, groupState, friends, query, flags, voiceState)
    val entries = remember(allEntries, filter) { filterSteamConversationEntries(allEntries, filter) }
    val selectedEntries = remember(selectedKeys, entries) { entries.filter { it.stableKey in selectedKeys } }
    val unreadConversations = remember(allEntries) { allEntries.count { it.unreadCount > 0 && !it.archived } }
    // Each visit and each search/filter/account has its own scroll state. Recreate it with the
    // new rows, rather than drawing a stale offset for one frame before a coroutine resets it.
    // Live updates within this result set retain LazyColumn's stable-key reading anchor.
    val listState = remember(filter, query.trim(), chatState.accountSteamId, groupState.accountSteamId) {
        LazyListState()
    }
    fun endSelection() {
        selectionMode = false
        selectedKeys = emptySet()
    }
    fun toggle(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }
    // Back closes the multi-selection before it navigates: leaving the page with rows still
    // ticked reads as a lost action.
    BackHandler(enabled = selectionMode) { endSelection() }
    val density = LocalDensity.current
    var filterHeight by remember(density) { mutableIntStateOf(with(density) { 60.dp.roundToPx() }) }
    // The floating filters cover both the list and its refresh indicator.
    val listTopInset = with(density) { filterHeight.toDp() } + 4.dp
    ConversationGlassHost(modifier.fillMaxSize(), content = {
        SteamExpressivePullToRefresh(
            refreshing = chatState.sessionsRefreshing || groupState.groupsRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            showIndicator = false,
            indicatorTopInset = listTopInset
        ) {
            LazyColumn(
                state = listState,
                // Keep the rendered rows and the floating backdrop in one coordinate space
                // at either edge. Pull-to-refresh retains its own top-edge gesture.
                overscrollEffect = null,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = listTopInset,
                    bottom = dockClearance + 108.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
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
                if ((!flags.loaded || chatState.sessionsLoading || groupState.groupsLoading) && entries.isEmpty()) {
                    item("conversation-loading") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.loading))
                        }
                    }
                } else if (entries.isEmpty()) {
                    item("conversation-empty") {
                        SteamConversationEmptyState(filter)
                    }
                } else {
                    items(entries, key = SteamConversationListEntry::stableKey) { entry ->
                        SteamConversationRow(
                            entry = entry,
                            selectionMode = selectionMode,
                            selected = entry.stableKey in selectedKeys,
                            actions = actions,
                            onToggleSelected = { toggle(entry.stableKey) },
                            onSwipeSelect = {
                                if (selectionMode) {
                                    toggle(entry.stableKey)
                                } else {
                                    selectionMode = true
                                    selectedKeys = setOf(entry.stableKey)
                                }
                            },
                            onRequestDelete = { deleting = it },
                            onClick = {
                                when {
                                    selectionMode -> toggle(entry.stableKey)
                                    entry.type == SteamConversationType.DIRECT -> onOpenDirect(entry.id)
                                    else -> onOpenGroup(entry.id, entry.chatId)
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }, overlay = {
        SteamConversationFilters(
            selected = filter,
            onSelect = { endSelection(); filter = it },
            unreadCount = unreadConversations,
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { filterHeight = it.height }
        )
            // Liquid glass refracts the conversations scrolling underneath; M3E keeps tonal containers.
            if (selectionMode) {
                SteamConversationSelectionBar(
                    count = selectedEntries.size,
                    onCancel = ::endSelection,
                    onSelectAll = { selectedKeys = entries.map { it.stableKey }.toSet() },
                    onTogglePin = {
                        selectedEntries.forEach(actions.onTogglePin)
                        endSelection()
                    },
                    onToggleArchive = {
                        selectedEntries.forEach(actions.onToggleArchive)
                        endSelection()
                    },
                    onDelete = { deleting = selectedEntries },
                    modifier = Modifier.align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .steamDockActionClearance(extraBottomSpacing = 16.dp)
                        .padding(horizontal = 12.dp)
                )
            } else SteamInboxActionsBar(
                onNewChat = onNewChat,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding()
                    .steamDockActionClearance(extraBottomSpacing = 16.dp)
                    .padding(horizontal = 24.dp)
            )
    })
    if (deleting.isNotEmpty()) {
        SteamConversationDeleteDialog(
            deleting = deleting,
            onDismiss = { deleting = emptyList() },
            onConfirm = { deleteContent ->
                actions.onDelete(deleting, deleteContent)
                deleting = emptyList()
                endSelection()
            }
        )
    }
}

@Composable
private fun ConversationGlassHost(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost(modifier = modifier, content = content, overlay = overlay)
}
