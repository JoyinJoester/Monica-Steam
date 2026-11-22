package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatViewModel
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaViewModel
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatViewModel
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel

@Composable
internal fun SteamChatThreadLifecycle(
    chatState: SteamChatUiState,
    groupChatState: SteamGroupChatUiState,
    uploadCompletedAt: Long,
    refreshRequest: Long,
    chatViewModel: SteamChatViewModel,
    groupChatViewModel: SteamGroupChatViewModel,
    richMediaViewModel: SteamChatRichMediaViewModel,
    friendsViewModel: SteamFriendsViewModel,
    onThreadVisibilityChange: (Boolean) -> Unit
) {
    LaunchedEffect(
        chatState.selectedPartnerSteamId,
        groupChatState.selectedGroupId,
        groupChatState.selectedChatId
    ) {
        when {
            chatState.selectedPartnerSteamId != null ->
                richMediaViewModel.selectPartner(chatState.selectedPartnerSteamId)
            groupChatState.selectedGroupId != null && groupChatState.selectedChatId != null ->
                richMediaViewModel.selectGroupRoom(
                    groupChatState.selectedGroupId,
                    groupChatState.selectedChatId
                )
            else -> richMediaViewModel.selectPartner(null)
        }
    }
    // Returning to a retained tab changes its callback even when the open thread is unchanged.
    SideEffect {
        onThreadVisibilityChange(
            chatState.selectedPartnerSteamId != null || groupChatState.selectedChatId != null
        )
    }
    DisposableEffect(Unit) {
        onDispose { onThreadVisibilityChange(false) }
    }
    LaunchedEffect(uploadCompletedAt) {
        if (uploadCompletedAt <= 0L) return@LaunchedEffect
        if (groupChatState.selectedChatId != null) {
            groupChatViewModel.refreshThread()
        } else {
            chatViewModel.refreshThread()
        }
    }
    LaunchedEffect(refreshRequest) {
        if (refreshRequest <= 0L) return@LaunchedEffect
        if (groupChatState.selectedChatId != null) {
            groupChatViewModel.refreshThread()
        } else if (chatState.selectedPartnerSteamId == null) {
            chatViewModel.refreshSessions()
            groupChatViewModel.refreshGroups()
            friendsViewModel.refresh()
        } else {
            chatViewModel.refreshThread()
        }
    }
}
