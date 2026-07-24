package takagi.ru.monica.steam.friends.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatViewModel
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaViewModel
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel
import takagi.ru.monica.steam.token.presentation.SteamViewModel
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
fun SteamChatScreen(
    searchQuery: String,
    refreshRequest: Long,
    requestedPartnerSteamId: String? = null,
    onConsumeRequestedPartner: () -> Unit = {},
    onUnreadCountChange: (Int) -> Unit = {},
    onThreadVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val steamViewModel: SteamViewModel = viewModel(
        factory = remember(context) { SteamViewModel.factory(context) }
    )
    val friendsViewModel: SteamFriendsViewModel = viewModel(
        factory = remember(context) { SteamFriendsViewModel.factory(context) }
    )
    val chatViewModel: SteamChatViewModel = viewModel(
        factory = remember(context) { SteamChatViewModel.factory(context) }
    )
    val richMediaViewModel: SteamChatRichMediaViewModel = viewModel(
        factory = remember(context) { SteamChatRichMediaViewModel.factory(context) }
    )
    val steamState by steamViewModel.uiState.collectAsState()
    val friendsState by friendsViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val richMediaState by richMediaViewModel.uiState.collectAsState()
    val selectedAccount = steamState.accounts.firstOrNull {
        it.id == steamState.selectedAccountId
    } ?: steamState.accounts.firstOrNull()
    val selectedFriend = friendsState.snapshot?.friends?.firstOrNull {
        it.steamId == chatState.selectedPartnerSteamId
    }

    LaunchedEffect(
        selectedAccount?.id,
        selectedAccount?.steamId,
        selectedAccount?.accessToken,
        selectedAccount?.steamLoginSecure
    ) {
        chatViewModel.selectAccount(selectedAccount)
        richMediaViewModel.selectAccount(selectedAccount)
        friendsViewModel.selectAccount(selectedAccount)
    }

    LaunchedEffect(chatState.selectedPartnerSteamId) {
        richMediaViewModel.selectPartner(chatState.selectedPartnerSteamId)
        onThreadVisibilityChange(chatState.selectedPartnerSteamId != null)
    }

    DisposableEffect(Unit) {
        onDispose { onThreadVisibilityChange(false) }
    }

    LaunchedEffect(richMediaState.uploadCompletedAt) {
        if (richMediaState.uploadCompletedAt > 0L) chatViewModel.refreshThread()
    }

    LaunchedEffect(requestedPartnerSteamId, selectedAccount?.id) {
        val partner = requestedPartnerSteamId?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (selectedAccount != null) {
            chatViewModel.openThread(partner)
            onConsumeRequestedPartner()
        }
    }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest <= 0L) return@LaunchedEffect
        if (chatState.selectedPartnerSteamId == null) {
            chatViewModel.refreshSessions()
            friendsViewModel.refresh()
        } else {
            chatViewModel.refreshThread()
        }
    }

    LaunchedEffect(chatState.unreadCount) {
        onUnreadCountChange(chatState.unreadCount)
    }

    DisposableEffect(lifecycleOwner, chatViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> chatViewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> chatViewModel.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        chatViewModel.setForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chatViewModel.setForeground(false)
        }
    }

    BackHandler(enabled = chatState.selectedPartnerSteamId != null) {
        chatViewModel.closeThread()
    }

    AnimatedContent(
        targetState = chatState.selectedPartnerSteamId,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { easyNotesScreenEnter().togetherWith(easyNotesScreenExit()) },
        label = "SteamChatNavigation"
    ) { partnerSteamId ->
        if (partnerSteamId == null) {
            SteamChatSessionList(
                state = chatState,
                friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                query = searchQuery,
                onOpenThread = chatViewModel::openThread,
                onRetry = chatViewModel::refreshSessions,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SteamChatThread(
                state = chatState,
                richMediaState = richMediaState,
                friend = selectedFriend,
                onNavigateBack = chatViewModel::closeThread,
                onRefresh = chatViewModel::refreshThread,
                onLoadOlder = chatViewModel::loadOlder,
                onSend = chatViewModel::sendMessage,
                onRetryMessage = chatViewModel::retryMessage,
                onAttachmentSelected = richMediaViewModel::selectAttachment,
                onAttachmentSpoilerChanged = richMediaViewModel::setAttachmentSpoiler,
                onUploadAttachment = richMediaViewModel::uploadAttachment,
                onClearAttachment = richMediaViewModel::clearAttachment,
                onClearAttachmentFailure = richMediaViewModel::clearAttachmentFailure,
                onRefreshCatalogs = richMediaViewModel::refreshCatalogs,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
