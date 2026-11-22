package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.foundation.ui.SteamCardPreviewHost
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingConversationKey
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatJumpToLatestButton
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatMessageActionMenu
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReactionPicker
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReportDialog
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCardLinks
import takagi.ru.monica.steam.friends.chat.richmedia.ui.openSteamChatCard
import takagi.ru.monica.steam.friends.chat.data.steamChatLocalDeletionKey
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.ui.label
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceStatusBanner
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding
import takagi.ru.monica.steam.friends.chat.telegram.*

@Composable
internal fun SteamChatThread(
    state: SteamChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    friend: SteamFriend?,
    targetMessageId: String? = null,
    targetMessageNonce: Int = 0,
    highlightMessageId: String? = null,
    onHighlightConsumed: () -> Unit = {},
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
    modifier: Modifier = Modifier,
    wallpaperPreset: takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset = takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset.TELEGRAM_PATTERN,
    onDeleteMessageLocally: ((takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage) -> Unit)? = null
) {
    val messages = state.thread?.messages.orEmpty()
    val messagesById = remember(messages) { messages.associateBy { it.stableId } }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var reactionMessageId by remember { mutableStateOf<String?>(null) }
    var reportMessage by remember { mutableStateOf<takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage?>(null) }
    var reportReason by remember { mutableStateOf(SteamChatReportReason.HARASSMENT) }
    val partnerSteamId = state.selectedPartnerSteamId.orEmpty()
    val tradeUrl = remember(partnerSteamId, state.accountSteamId) {
        SteamChatCardLinks.tradeWith(partnerSteamId.takeUnless { it == state.accountSteamId })
    }
    val currentPartnerVoiceActive = voiceState.target?.type == SteamVoiceTargetType.DIRECT &&
        voiceState.target?.partnerSteamId == partnerSteamId && voiceState.isActive
    val anotherVoiceCallActive = voiceState.isActive && !currentPartnerVoiceActive
    val conversationKey = remember(state.accountSteamId, partnerSteamId) {
        SteamChatReadingConversationKey.direct(state.accountSteamId, partnerSteamId)
    }
    var pendingLocalDelete by remember(conversationKey) {
        mutableStateOf<takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage?>(null)
    }
    val listState = rememberTelegramChatListState(conversationKey)
    val search = remember(conversationKey) { SteamThreadSearchState() }
    val composerState = rememberSaveableStateHolder()
    val searchItems = remember(search.active, messages, friend) {
        if (!search.active) emptyList() else buildSteamChatHistory(
            partnerSteamId, friend, emptyMap(), messages, state.accountSteamId, emptyList(), ""
        )
    }
    val searchResults = steamThreadSearchResults(search, searchItems)
    LaunchedEffect(targetMessageId, targetMessageNonce) { search.jump.clear() }
    val activeHighlight = search.jump.highlightId ?: highlightMessageId
    LaunchedEffect(search.jump.highlightId) {
        if (search.jump.highlightId != null) {
            delay(TELEGRAM_HIGHLIGHT_HOLD_MILLIS)
            search.jump.consumeHighlight()
        }
    }
    val readingUi = listState.readingUi
    val rows = remember(messages) { directTelegramMessageRows(messages) }
    val jumpUi = rememberDirectSteamChatJumpToLatestState(
        state = state,
        messages = messages,
        conversationKey = conversationKey,
        readingUi = readingUi
    )
    val refreshLabel = stringResource(R.string.refresh)
    val callLabel = if (currentPartnerVoiceActive) "结束语音" else "发起语音"
    LaunchedEffect(highlightMessageId) {
        if (highlightMessageId != null) {
            delay(TELEGRAM_HIGHLIGHT_HOLD_MILLIS)
            onHighlightConsumed()
        }
    }
    SteamCardPreviewHost(conversationKey) {
        TelegramChatScaffold(
            state = listState,
            wallpaperPreset = wallpaperPreset,
            modifier = modifier,
            top = {
                if (search.active) SteamThreadSearchHeader(search, searchItems, Modifier.steamWindowTopPadding())
                else TelegramChatHeader(
                    title = friend?.displayName ?: partnerSteamId,
                    subtitle = if (state.typingPartnerSteamIds.contains(partnerSteamId)) stringResource(R.string.steam_chat_typing)
                        else friend?.personaState?.label() ?: partnerSteamId,
                    avatarUrl = friend?.avatarUrl,
                    refreshing = state.threadLoading || state.threadRefreshing,
                    typing = state.typingPartnerSteamIds.contains(partnerSteamId),
                    onBack = onNavigateBack,
                    onInfo = onOpenInfo,
                    actions = listOfNotNull(
                        TelegramChatAction(stringResource(R.string.steam_thread_search), { search.active = true }),
                        tradeUrl?.let { url ->
                            TelegramChatAction(stringResource(R.string.steam_card_start_trade), { openSteamChatCard(context, url) })
                        },
                        TelegramChatAction(refreshLabel, onRefresh, !state.threadRefreshing),
                        TelegramChatAction(callLabel, if (currentPartnerVoiceActive) onStopVoice else onStartVoice, !anotherVoiceCallActive)
                    ),
                    sourceView = listState.sourceView,
                    modifier = Modifier.steamWindowTopPadding()
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
                state.threadFailure?.let { failure ->
                    ChatFailureBanner(
                        failure = failure,
                        onRetry = onRefresh
                    )
                }
            },
            bottom = {
                if (search.active) SteamThreadSearchBottom(search, searchResults)
                else composerState.SaveableStateProvider(conversationKey) {
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
                        onOpenStoreApp = onOpenStoreApp,
                        sourceView = listState.sourceView
                    )
                }
            }
        ) { contentPadding ->
            Box(Modifier.fillMaxSize()) {
                when {
                    state.threadLoading && state.thread == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    messages.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(contentPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                        ChatThreadEmptyState()
                        if (state.thread?.moreAvailable == true) {
                            if (state.loadingOlder) CircularProgressIndicator()
                            else TextButton(onClick = onLoadOlder) { Text(stringResource(R.string.steam_chat_load_older_messages)) }
                        }
                    }
                    else -> TelegramMessageList(
                        items = rows,
                        itemKey = { it.message.stableId },
                        state = listState,
                        conversationKey = conversationKey,
                        requestedMessageId = search.jump.messageId ?: targetMessageId,
                        requestedNonce = if (search.jump.messageId != null) search.jump.nonce else targetMessageNonce,
                        loadingOlder = state.loadingOlder,
                        moreAvailable = state.thread?.moreAvailable == true,
                        latestMessageOutgoing = messages.lastOrNull()?.isOutgoing(state.accountSteamId) == true,
                        onLoadOlder = onLoadOlder,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize()
                    ) { row ->
                        val message = row.message
                        Column(Modifier.fillMaxWidth()) {
                            if (row.showDate) TelegramDatePill(message.timestamp)
                            val serverConfirmed = message.timestamp > 0L &&
                                message.ordinal != Int.MAX_VALUE
                            TelegramMessageHighlight(active = message.stableId == activeHighlight) {
                                Box {
                                    SteamChatMessageBubble(
                                        message = message,
                                        replyToMessage = message.replyToStableId?.let { replyId ->
                                            messagesById[replyId]
                                        },
                                        accountSteamId = state.accountSteamId,
                                        groupedWithPrevious = row.groupedWithPrevious,
                                        groupedWithNext = row.groupedWithNext,
                                        onRetry = { onRetryMessage(message.clientMessageId) },
                                        onOpenStoreApp = onOpenStoreApp,
                                        onDeleteMessage = if (onDeleteMessageLocally != null && steamChatLocalDeletionKey(message) != null) {
                                            { pendingLocalDelete = message }
                                        } else null,
                                        onLongClick = {
                                            selectedMessageId = message.stableId
                                        }
                                    )
                                    if (selectedMessageId == message.stableId) {
                                        SteamChatMessageActionMenu(
                                            canReport = serverConfirmed && !message.isOutgoing(state.accountSteamId),
                                            onDismiss = { selectedMessageId = null },
                                            onDelete = if (onDeleteMessageLocally != null && steamChatLocalDeletionKey(message) != null) {
                                                { selectedMessageId = null; pendingLocalDelete = message }
                                            } else null,
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
                    onClick = listState::jumpToLatest,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp, end = 12.dp)
                )
            }
        }
    }

    pendingLocalDelete?.let { message ->
        SteamGlassAlertDialog(
            onDismissRequest = { pendingLocalDelete = null },
            title = { Text(stringResource(R.string.steam_chat_local_delete_title)) },
            text = { Text(stringResource(R.string.steam_chat_local_delete_description)) },
            confirmButton = {
                TextButton(enabled = onDeleteMessageLocally != null, onClick = {
                    pendingLocalDelete = null
                    onDeleteMessageLocally?.invoke(message)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingLocalDelete = null }) { Text(stringResource(R.string.cancel)) } }
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

internal data class DirectTelegramMessageRow(
    val message: takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage,
    val showDate: Boolean,
    val groupedWithPrevious: Boolean,
    val groupedWithNext: Boolean
)

internal fun directTelegramMessageRows(messages: List<takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage>): List<DirectTelegramMessageRow> =
    messages.mapIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        DirectTelegramMessageRow(
            message,
            previous == null || telegramMessageDay(previous.timestamp) != telegramMessageDay(message.timestamp),
            telegramMessagesShareGroup(message.senderSteamId, message.timestamp, previous?.senderSteamId, previous?.timestamp),
            telegramMessagesShareGroup(message.senderSteamId, message.timestamp, next?.senderSteamId, next?.timestamp)
        )
    }
