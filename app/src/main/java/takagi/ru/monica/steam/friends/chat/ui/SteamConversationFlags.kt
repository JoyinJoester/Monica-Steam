package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import takagi.ru.monica.steam.friends.chat.data.SteamChatCache
import takagi.ru.monica.steam.friends.chat.data.SteamChatPreferencesCache
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationType
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatCache
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatPreferencesCache
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState

/**
 * Per-conversation local flags the inbox has to know before it can order or hide a row.
 * Loaded in one pass so pinning, archiving and deleting all read the same store snapshot.
 */
internal data class SteamConversationFlags(
    val pinnedDirectIds: Set<String> = emptySet(),
    val pinnedGroupIds: Set<String> = emptySet(),
    val archivedDirectIds: Set<String> = emptySet(),
    val archivedGroupIds: Set<String> = emptySet(),
    val hiddenDirectAt: Map<String, Long> = emptyMap(),
    val hiddenGroupAt: Map<String, Long> = emptyMap(),
    val loaded: Boolean = true
)

internal fun loadSteamConversationFlags(
    store: SteamChatInfoPreferencesStore,
    accountSteamId: String,
    sessions: List<SteamChatSession>,
    groupAccountSteamId: String,
    groups: List<SteamGroupChatSummary>
): SteamConversationFlags {
    val pinnedDirect = mutableSetOf<String>()
    val archivedDirect = mutableSetOf<String>()
    val hiddenDirect = mutableMapOf<String, Long>()
    sessions.forEach { session ->
        val preferences = store.load(
            SteamChatConversationId(
                accountSteamId,
                SteamChatConversationType.DIRECT,
                session.partnerSteamId
            )
        )
        val id = session.partnerSteamId
        if (preferences.pinned) pinnedDirect += id
        if (preferences.archived) archivedDirect += id
        if (preferences.hidden) hiddenDirect[id] = preferences.hiddenAt
    }
    val pinnedGroup = mutableSetOf<String>()
    val archivedGroup = mutableSetOf<String>()
    val hiddenGroup = mutableMapOf<String, Long>()
    groups.forEach { group ->
        val preferences = store.load(
            SteamChatConversationId(
                groupAccountSteamId,
                SteamChatConversationType.GROUP,
                group.groupId
            )
        )
        if (preferences.pinned) pinnedGroup += group.groupId
        if (preferences.archived) archivedGroup += group.groupId
        if (preferences.hidden) hiddenGroup[group.groupId] = preferences.hiddenAt
    }
    return SteamConversationFlags(
        pinnedDirectIds = pinnedDirect,
        pinnedGroupIds = pinnedGroup,
        archivedDirectIds = archivedDirect,
        archivedGroupIds = archivedGroup,
        hiddenDirectAt = hiddenDirect,
        hiddenGroupAt = hiddenGroup
    )
}

/** Flags plus the write side of the same model, kept together so the screen stays declarative. */
internal class SteamConversationLocalState(
    val flags: SteamConversationFlags,
    val actions: SteamConversationActions
)

@Composable
internal fun rememberSteamConversationLocalState(
    store: SteamChatInfoPreferencesStore,
    chatState: SteamChatUiState,
    groupState: SteamGroupChatUiState,
    refreshKey: Any? = null
): SteamConversationLocalState {
    val context = LocalContext.current
    val chatCache: SteamChatCache = remember(context) { SteamChatPreferencesCache(context) }
    val groupCache: SteamGroupChatCache = remember(context) { SteamGroupChatPreferencesCache(context) }
    var revision by remember { mutableIntStateOf(0) }
    val sessions = chatState.sessions?.sessions.orEmpty()
    // Message timestamps and connection updates do not change persisted local preferences.
    val directIds = remember(sessions) { sessions.map { it.partnerSteamId } }
    val groupIds = remember(groupState.groups) { groupState.groups.map { it.groupId } }
    val flagsState = remember(store, directIds, groupIds, chatState.accountSteamId,
        groupState.accountSteamId, refreshKey, revision) {
        mutableStateOf(SteamConversationFlags(loaded = directIds.isEmpty() && groupIds.isEmpty()))
    }
    LaunchedEffect(flagsState) {
        if (!flagsState.value.loaded) {
            // The remembered holder changes with the account/list/revision. Cancellation and
            // withContext's return check prevent a late read from publishing into a new inbox.
            flagsState.value = withContext(Dispatchers.IO) {
                loadSteamConversationFlags(store, chatState.accountSteamId, sessions,
                    groupState.accountSteamId, groupState.groups)
            }
        }
    }
    val flags = flagsState.value
    val actions = remember(
        store,
        chatCache,
        groupCache,
        chatState.accountSteamId,
        groupState.accountSteamId,
        revision
    ) {
        buildSteamConversationActions(
            store = store,
            accountSteamId = chatState.accountSteamId,
            groupAccountSteamId = groupState.accountSteamId,
            chatCache = chatCache,
            groupChatCache = groupCache,
            onChanged = { revision++ }
        )
    }
    return remember(flags, actions) { SteamConversationLocalState(flags, actions) }
}
