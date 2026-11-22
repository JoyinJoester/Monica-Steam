package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState

@Composable
internal fun rememberSteamConversationEntries(
    chatState: SteamChatUiState,
    groupState: SteamGroupChatUiState,
    friends: List<SteamFriend>,
    query: String,
    flags: SteamConversationFlags,
    voiceState: SteamVoiceCallState
): List<SteamConversationListEntry> {
    val sessions = chatState.sessions?.sessions.orEmpty()
    // Typing, connection and refresh flags do not affect the rows. Reuse the projection
    // so those emissions don't allocate/sort the entire inbox or invalidate every item.
    return remember(sessions, groupState.groups, friends, query, flags, voiceState) {
        if (!flags.loaded) return@remember emptyList()
        buildSteamConversationEntries(
            sessions = sessions,
            groups = groupState.groups,
            friends = friends,
            query = query,
            pinnedPartnerSteamIds = flags.pinnedDirectIds,
            pinnedGroupIds = flags.pinnedGroupIds,
            voiceState = voiceState,
            archivedPartnerSteamIds = flags.archivedDirectIds,
            archivedGroupIds = flags.archivedGroupIds,
            hiddenPartnerAt = flags.hiddenDirectAt,
            hiddenGroupAt = flags.hiddenGroupAt
        )
    }
}
