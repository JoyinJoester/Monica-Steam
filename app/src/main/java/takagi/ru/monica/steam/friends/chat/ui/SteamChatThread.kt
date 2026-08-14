package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingConversationKey
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatAutoScrollToLatestEffect
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatJumpToLatestButton
import takagi.ru.monica.steam.friends.chat.position.ui.animateToLatestSteamChatMessage
import takagi.ru.monica.steam.friends.chat.position.ui.rememberSteamChatReadingPosition
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatMessageActionMenu
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReactionPicker
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReportDialog
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.ui.label
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceStatusBanner
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.steam.navigation.ui.steamWindowBottomPadding
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
@Composable
internal fun SteamChatThread(
    state: SteamChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    friend: SteamFriend?,
    targetMessageId: String? = null,
    gameShareDraft: SteamStoreGameShare? = null,
    onNavigateBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onReact: (takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage, String) -> Unit,
    onStickerReply: (takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage, String) -> Unit,
    onReport: (takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage, SteamChatReportReason) -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    onConsumeGameShareDraft: () -> Unit = {},
    voiceState: SteamVoiceCallState = SteamVoiceCallState(),
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onToggleVoiceMicrophone: () -> Unit = {},
    onToggleVoiceOutput: () -> Unit = {},
    onSelectVoiceAudioRoute: (SteamVoiceAudioRoute) -> Unit = {},
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages = state.thread?.messages.orEmpty()
    val messagesById = remember(messages) { messages.associateBy { it.stableId } }
    val clipboard = LocalClipboardManager.current
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var reactionMessageId by remember { mutableStateOf<String?>(null) }
    var reportMessage by remember { mutableStateOf<takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage?>(null) }
    var reportReason by remember { mutableStateOf(SteamChatReportReason.HARASSMENT) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val partnerSteamId = state.selectedPartnerSteamId.orEmpty()
    val currentPartnerVoiceActive = voiceState.target?.type == SteamVoiceTargetType.DIRECT &&
        voiceState.target?.partnerSteamId == partnerSteamId && voiceState.isActive
    val anotherVoiceCallActive = voiceState.isActive && !currentPartnerVoiceActive
    val conversationKey = remember(state.accountSteamId, partnerSteamId) {
        SteamChatReadingConversationKey.direct(state.accountSteamId, partnerSteamId)
    }
    val messageIds = remember(messages) { messages.map { it.stableId } }
    val leadingItemCount = if (state.loadingOlder) 1 else 0
    val readingUi by rememberSteamChatReadingPosition(
        conversationKey = conversationKey,
        messageIds = messageIds,
        requestedMessageId = targetMessageId,
        leadingItemCount = leadingItemCount,
        listState = listState
    )
    val jumpUi = rememberDirectSteamChatJumpToLatestState(
        state = state,
        messages = messages,
        conversationKey = conversationKey,
        readingUi = readingUi
    )
    val shouldLoadOlder by remember(listState, state.thread?.moreAvailable, state.loadingOlder) {
        derivedStateOf {
            state.thread?.moreAvailable == true &&
                !state.loadingOlder &&
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index?.let { it <= 2 } == true
        }
    }

    LaunchedEffect(shouldLoadOlder) {
        if (shouldLoadOlder) onLoadOlder()
    }

    SteamChatAutoScrollToLatestEffect(
        conversationKey = conversationKey,
        latestMessageId = messages.lastOrNull()?.stableId,
        latestMessageIsOutgoing = messages.lastOrNull()?.isOutgoing(state.accountSteamId) == true,
        messageCount = messages.size,
        leadingItemCount = leadingItemCount,
        messagesBelow = readingUi.messagesBelow,
        restored = readingUi.restored,
        listState = listState
    )

    // The activity is edge-to-edge, so the private thread owns both system
    // navigation and IME insets. Compose consumes the overlapping portion,
    // keeping the composer above gesture/three-button navigation when the
    // keyboard is closed and directly above the IME when it is open.
    Column(
        modifier = modifier
            .fillMaxSize()
            .steamWindowBottomPadding(suppressWhenImeVisible = true)
            .imePadding()
    ) {
        ChatThreadHeader(
            friend = friend,
            partnerSteamId = state.selectedPartnerSteamId.orEmpty(),
            typing = state.typingPartnerSteamIds.contains(state.selectedPartnerSteamId),
            refreshing = state.threadRefreshing,
            onNavigateBack = onNavigateBack,
            onOpenInfo = onOpenInfo,
            onRefresh = onRefresh,
            voiceActive = currentPartnerVoiceActive,
            voiceBusy = anotherVoiceCallActive,
            onStartVoice = onStartVoice,
            onStopVoice = onStopVoice
        )
        if (voiceState.isActive) {
            SteamVoiceStatusBanner(
                state = voiceState,
                fallbackTitle = friend?.displayName ?: partnerSteamId,
                onLeave = onStopVoice,
                onToggleMicrophone = onToggleVoiceMicrophone,
                onToggleOutput = onToggleVoiceOutput,
                onSelectAudioRoute = onSelectVoiceAudioRoute
            )
        }
        if (state.threadRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.threadFailure?.let { failure ->
            ChatFailureBanner(
                failure = failure,
                onRetry = onRefresh
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.threadLoading && state.thread == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                messages.isEmpty() -> {
                    ChatThreadEmptyState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (state.loadingOlder) {
                            item(key = "chat-history-loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        itemsIndexed(
                            items = messages,
                            key = { _, message -> message.stableId }
                        ) { index, message ->
                            val previous = messages.getOrNull(index - 1)
                            val next = messages.getOrNull(index + 1)
                            val showDate = previous == null || !sameChatDay(
                                previous.timestamp,
                                message.timestamp
                            )
                            if (showDate) {
                                ChatDateSeparator(timestampSeconds = message.timestamp)
                            }
                            val serverConfirmed = message.timestamp > 0L &&
                                message.ordinal != Int.MAX_VALUE
                            Box(
                                modifier = Modifier.animateItem()
                            ) {
                                SteamChatMessageBubble(
                                    message = message,
                                    replyToMessage = message.replyToStableId?.let { replyId ->
                                        messagesById[replyId]
                                    },
                                    accountSteamId = state.accountSteamId,
                                    groupedWithPrevious = previous?.senderSteamId == message.senderSteamId &&
                                        sameChatDay(previous.timestamp, message.timestamp),
                                    groupedWithNext = next?.senderSteamId == message.senderSteamId &&
                                        sameChatDay(next.timestamp, message.timestamp),
                                    onRetry = { onRetryMessage(message.clientMessageId) },
                                    onOpenStoreApp = onOpenStoreApp,
                                    onLongClick = {
                                        selectedMessageId = message.stableId
                                    }
                                )
                                if (selectedMessageId == message.stableId) {
                                    SteamChatMessageActionMenu(
                                        canReport = serverConfirmed && !message.isOutgoing(state.accountSteamId),
                                        onDismiss = { selectedMessageId = null },
                                        onOpenReactions = {
                                            selectedMessageId = null
                                            reactionMessageId = message.stableId
                                        },
                                        onCopy = {
                                            clipboard.setText(AnnotatedString(message.body))
                                            selectedMessageId = null
                                        },
                                        onReport = {
                                            selectedMessageId = null
                                            reportMessage = message
                                        }
                                    )
                                }
                                if (reactionMessageId == message.stableId) {
                                    SteamChatReactionPicker(
                                        emoticons = if (serverConfirmed) richMediaState.emoticons else emptyList(),
                                        stickers = richMediaState.stickers,
                                        onDismiss = { reactionMessageId = null },
                                        onReact = {
                                            reactionMessageId = null
                                            onReact(message, it.name)
                                        },
                                        onStickerReply = {
                                            reactionMessageId = null
                                            onStickerReply(message, it.messageCode)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SteamChatJumpToLatestButton(
                visible = jumpUi.visible,
                messagesBelow = jumpUi.unreadBelowCount,
                onClick = {
                    scrollScope.launch {
                        listState.animateToLatestSteamChatMessage(messages.size, leadingItemCount)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )
        }
        SteamChatComposer(
            draftKey = conversationKey,
            richMediaState = richMediaState,
            initialGameShare = gameShareDraft,
            onConsumeInitialGameShare = onConsumeGameShareDraft,
            onSend = onSend,
            onAttachmentSelected = onAttachmentSelected,
            onAttachmentSpoilerChanged = onAttachmentSpoilerChanged,
            onUploadAttachment = onUploadAttachment,
            onClearAttachment = onClearAttachment,
            onClearAttachmentFailure = onClearAttachmentFailure,
            onRefreshCatalogs = onRefreshCatalogs,
            onOpenStoreApp = onOpenStoreApp
        )
    }
    reportMessage?.let { message ->
        SteamChatReportDialog(
            selectedReason = reportReason,
            onReasonSelected = { reportReason = it },
            onConfirm = {
                reportMessage = null
                onReport(message, reportReason)
            },
            onDismiss = { reportMessage = null }
        )
    }
}

@Composable
private fun ChatThreadHeader(
    friend: SteamFriend?,
    partnerSteamId: String,
    typing: Boolean,
    refreshing: Boolean,
    onNavigateBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onRefresh: () -> Unit,
    voiceActive: Boolean,
    voiceBusy: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().steamWindowTopPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back)
            )
        }
        if (friend != null) {
            Box(Modifier.size(48.dp).clickable(onClick = onOpenInfo), contentAlignment = Alignment.Center) {
                FriendAvatar(friend = friend, size = 42)
            }
        } else {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onOpenInfo).padding(vertical = 4.dp)) {
            Text(
                text = friend?.displayName ?: partnerSteamId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (typing) stringResource(R.string.steam_chat_typing)
                else friend?.let { it.personaState.label() }
                    ?: stringResource(R.string.steam_chat_conversation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = if (voiceActive) onStopVoice else onStartVoice,
            enabled = !voiceBusy
        ) {
            Icon(
                if (voiceActive) Icons.Default.CallEnd else Icons.Default.Call,
                contentDescription = when {
                    voiceActive -> "结束语音"
                    voiceBusy -> "已有进行中的语音通话"
                    else -> "发起语音"
                }
            )
        }
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
        }
    }
}
