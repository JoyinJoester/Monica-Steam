package takagi.ru.monica.steam.friends.groupchat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import takagi.ru.monica.steam.foundation.ui.SteamCardPreviewHost
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import takagi.ru.monica.steam.friends.chat.ui.SteamThreadSearchState
import takagi.ru.monica.steam.friends.chat.ui.SteamThreadSearchHeader
import takagi.ru.monica.steam.friends.chat.ui.SteamThreadSearchBottom
import takagi.ru.monica.steam.friends.chat.ui.steamThreadSearchResults
import takagi.ru.monica.steam.friends.chat.ui.buildSteamChatHistory
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.monica.telegramchat.TelegramMessageTextView
import takagi.ru.monica.steam.friends.chat.telegram.TelegramTextMessage
import takagi.ru.monica.steam.friends.chat.telegram.nativeTelegramText
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.ui.isSingleSteamEmoticonMessage
import com.monica.telegramchat.TelegramGlassView
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatMessageActionMenu
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReactionPicker
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReportDialog
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingConversationKey
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatJumpMessage
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatJumpToLatestButton
import takagi.ru.monica.steam.friends.chat.position.ui.rememberSteamChatJumpToLatestState
import takagi.ru.monica.steam.friends.chat.telegram.TelegramChatAction
import takagi.ru.monica.steam.friends.chat.telegram.TelegramChatHeader
import takagi.ru.monica.steam.friends.chat.telegram.TelegramChatScaffold
import takagi.ru.monica.steam.friends.chat.telegram.TelegramDatePill
import takagi.ru.monica.steam.friends.chat.telegram.TelegramMessageList
import takagi.ru.monica.steam.friends.chat.telegram.TelegramMessageHighlight
import takagi.ru.monica.steam.friends.chat.telegram.TELEGRAM_HIGHLIGHT_HOLD_MILLIS
import takagi.ru.monica.steam.friends.chat.telegram.TelegramMessageSurface
import takagi.ru.monica.steam.friends.chat.telegram.rememberTelegramChatListState
import takagi.ru.monica.steam.friends.chat.telegram.telegramChatColors
import takagi.ru.monica.steam.friends.chat.telegram.telegramMessageDay
import takagi.ru.monica.steam.friends.chat.telegram.telegramMessagesShareGroup
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMessageContent
import takagi.ru.monica.steam.friends.chat.ui.SteamChatComposer
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReactionType
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReportReason
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceChannelPanel
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceStatusBanner
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding

@Composable
internal fun SteamGroupChatThread(
    state: SteamGroupChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    group: SteamGroupChatSummary,
    friends: List<SteamFriend>,
    targetMessageId: String? = null,
    targetMessageNonce: Int = 0,
    highlightMessageId: String? = null,
    onHighlightConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenRoom: (String, String) -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onInvite: () -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    onUpdateReaction: (SteamGroupChatMessage, SteamGroupChatReactionType, String, Boolean) -> Unit,
    onReportMessage: (SteamGroupChatMessage, SteamGroupChatReportReason) -> Unit,
    onDeleteMessage: (SteamGroupChatMessage) -> Unit,
    voiceState: SteamVoiceCallState = SteamVoiceCallState(),
    onJoinVoice: (String) -> Unit = {},
    onLeaveVoice: () -> Unit = {},
    onToggleVoiceMicrophone: () -> Unit = {},
    onToggleVoiceOutput: () -> Unit = {},
    onSelectVoiceAudioRoute: (SteamVoiceAudioRoute) -> Unit = {},
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    wallpaperPreset: takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset = takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset.TELEGRAM_PATTERN
) {
    val messages = state.thread?.messages.orEmpty()
    val friendsById = remember(friends) { friends.associateBy(SteamFriend::steamId) }
    val conversationKey = remember(state.accountSteamId, group.groupId, state.selectedChatId) {
        SteamChatReadingConversationKey.group(
            state.accountSteamId,
            group.groupId,
            state.selectedChatId.orEmpty()
        )
    }
    val listState = rememberTelegramChatListState(conversationKey)
    val search = remember(conversationKey) { SteamThreadSearchState() }
    val composerState = rememberSaveableStateHolder()
    val searchItems = remember(search.active, messages, friendsById) {
        if (!search.active) emptyList() else buildSteamChatHistory(
            null, null, friendsById, emptyList(), "", messages, state.accountSteamId
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
    val messageRows = remember(messages) { groupTelegramMessageRows(messages) }
    val selectedRoom = group.rooms.firstOrNull { it.chatId == state.selectedChatId }
    val readingUi = listState.readingUi
    val jumpMessages = remember(messages, state.accountSteamId) {
        messages.map { message ->
            SteamChatJumpMessage(
                id = message.stableId,
                timestamp = message.timestamp,
                incoming = message.senderSteamId != state.accountSteamId
            )
        }
    }
    val jumpUi = rememberSteamChatJumpToLatestState(
        conversationKey = conversationKey,
        initialAcknowledgedTimestamp = selectedRoom?.lastAcknowledgedTimestamp ?: 0L,
        messages = jumpMessages,
        lastVisibleMessageId = readingUi.lastVisibleMessageId,
        messagesBelow = readingUi.messagesBelow,
        restored = readingUi.restored
    )
    val voiceRoom = selectedRoom?.takeIf { it.voiceAllowed }
    val inviteLabel = stringResource(R.string.steam_group_chat_invite)
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
                    title = group.name,
                    subtitle = stringResource(R.string.steam_group_chat_members, group.activeMemberCount),
                    avatarUrl = group.avatarUrl,
                    refreshing = state.threadLoading,
                    onBack = onBack,
                    onInfo = onOpenInfo,
                    actions = listOf(TelegramChatAction(stringResource(R.string.steam_thread_search), { search.active = true }),
                        TelegramChatAction(inviteLabel, onInvite)),
                    sourceView = listState.sourceView,
                    modifier = Modifier.steamWindowTopPadding()
                )
                TelegramGroupChannelPill(
                    rooms = group.rooms,
                    selectedChatId = state.selectedChatId,
                    onSelect = { chatId -> onOpenRoom(group.groupId, chatId) },
                    sourceView = listState.sourceView
                )
                val activeVoiceRoom = group.rooms.firstOrNull { it.isVoiceActive }
                val joinVoiceRoom = voiceRoom ?: activeVoiceRoom
                val localGroupCall = voiceState.isActive &&
                    voiceState.target?.type == SteamVoiceTargetType.GROUP &&
                    voiceState.target?.groupId == group.groupId
                if (voiceState.isActive || group.isVoiceActive) {
                    SteamVoiceStatusBanner(
                        state = voiceState,
                        fallbackTitle = group.name,
                        activeMemberCount = if (!voiceState.isActive || localGroupCall) {
                            group.activeVoiceMemberCount
                        } else 0,
                        onJoin = joinVoiceRoom?.takeIf { !voiceState.isActive }
                            ?.let { { onJoinVoice(it.chatId) } },
                        onLeave = voiceState.isActive.takeIf { it }?.let { { onLeaveVoice() } },
                        onToggleMicrophone = voiceState.isActive.takeIf { it }?.let { { onToggleVoiceMicrophone() } },
                        onToggleOutput = voiceState.isActive.takeIf { it }?.let { { onToggleVoiceOutput() } },
                        onSelectAudioRoute = voiceState.isActive.takeIf { it }
                            ?.let { { route -> onSelectVoiceAudioRoute(route) } }
                    )
                }
            },
            bottom = {
                if (search.active) SteamThreadSearchBottom(search, searchResults)
                else if (voiceRoom == null) composerState.SaveableStateProvider(conversationKey) {
                    SteamChatComposer(
                        draftKey = conversationKey,
                        richMediaState = richMediaState,
                        onSend = onSend,
                        onAttachmentSelected = onAttachmentSelected,
                        onAttachmentSpoilerChanged = onAttachmentSpoilerChanged,
                        onUploadAttachment = onUploadAttachment,
                        onClearAttachment = onClearAttachment,
                        onClearAttachmentFailure = onClearAttachmentFailure,
                        onRefreshCatalogs = onRefreshCatalogs,
                        onOpenStoreApp = onOpenStoreApp,
                        sourceView = listState.sourceView,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { contentPadding ->
            Box(Modifier.fillMaxSize()) {
                when {
                    voiceRoom != null -> SteamVoiceChannelPanel(
                        room = voiceRoom,
                        state = voiceState,
                        friends = friends,
                        accountSteamId = state.accountSteamId,
                        onJoin = { onJoinVoice(voiceRoom.chatId) },
                        onLeave = onLeaveVoice,
                        onToggleMicrophone = onToggleVoiceMicrophone,
                        onToggleOutput = onToggleVoiceOutput,
                        onSelectAudioRoute = onSelectVoiceAudioRoute,
                        modifier = Modifier.fillMaxSize().padding(contentPadding)
                    )
                    state.threadLoading && state.thread == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> TelegramMessageList(
                        items = messageRows,
                        itemKey = { it.message.stableId },
                        state = listState,
                        conversationKey = conversationKey,
                        requestedMessageId = search.jump.messageId ?: targetMessageId,
                        requestedNonce = if (search.jump.messageId != null) search.jump.nonce else targetMessageNonce,
                        loadingOlder = state.loadingOlder,
                        moreAvailable = state.thread?.moreAvailable == true,
                        latestMessageOutgoing = messages.lastOrNull()?.senderSteamId == state.accountSteamId,
                        onLoadOlder = onLoadOlder,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding
                    ) { row ->
                        Column(Modifier.fillMaxWidth()) {
                            if (row.showDate) {
                                TelegramDatePill(
                                    row.message.timestamp,
                                    Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
                                )
                            }
                            GroupMessageHighlighted(
                                active = row.message.stableId == activeHighlight,
                                message = row.message,
                                outgoing = row.message.senderSteamId == state.accountSteamId,
                                groupedWithPrevious = row.groupedWithPrevious,
                                groupedWithNext = row.groupedWithNext,
                                sender = friendsById[row.message.senderSteamId],
                                richMediaState = richMediaState,
                                onRetryMessage = onRetryMessage,
                                onUpdateReaction = onUpdateReaction,
                                onReportMessage = onReportMessage,
                                onDeleteMessage = onDeleteMessage,
                                onOpenStoreApp = onOpenStoreApp
                            )
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

}

@Composable
internal fun SteamGroupChatThreadHost(
    state: SteamGroupChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    friends: List<SteamFriend>,
    targetMessageId: String? = null,
    targetMessageNonce: Int = 0,
    highlightMessageId: String? = null,
    onHighlightConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenRoom: (String, String) -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onInvite: () -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    onUpdateReaction: (SteamGroupChatMessage, SteamGroupChatReactionType, String, Boolean) -> Unit,
    onReportMessage: (SteamGroupChatMessage, SteamGroupChatReportReason) -> Unit,
    onDeleteMessage: (SteamGroupChatMessage) -> Unit,
    voiceState: SteamVoiceCallState = SteamVoiceCallState(),
    onJoinVoice: (String) -> Unit = {},
    onLeaveVoice: () -> Unit = {},
    onToggleVoiceMicrophone: () -> Unit = {},
    onToggleVoiceOutput: () -> Unit = {},
    onSelectVoiceAudioRoute: (SteamVoiceAudioRoute) -> Unit = {},
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    wallpaperPreset: takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset = takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset.TELEGRAM_PATTERN
) {
    val group = state.groups.firstOrNull { it.groupId == state.selectedGroupId } ?: return
    SteamGroupChatThread(
        state = state,
        wallpaperPreset = wallpaperPreset,
        richMediaState = richMediaState,
        group = group,
        friends = friends,
        targetMessageId = targetMessageId,
        targetMessageNonce = targetMessageNonce,
        highlightMessageId = highlightMessageId,
        onHighlightConsumed = onHighlightConsumed,
        onBack = onBack,
        onOpenInfo = onOpenInfo,
        onOpenRoom = onOpenRoom,
        onLoadOlder = onLoadOlder,
        onSend = onSend,
        onRetryMessage = onRetryMessage,
        onInvite = onInvite,
        onAttachmentSelected = onAttachmentSelected,
        onAttachmentSpoilerChanged = onAttachmentSpoilerChanged,
        onUploadAttachment = onUploadAttachment,
        onClearAttachment = onClearAttachment,
        onClearAttachmentFailure = onClearAttachmentFailure,
        onRefreshCatalogs = onRefreshCatalogs,
        onUpdateReaction = onUpdateReaction,
        onReportMessage = onReportMessage,
        onDeleteMessage = onDeleteMessage,
        voiceState = voiceState,
        onJoinVoice = onJoinVoice,
        onLeaveVoice = onLeaveVoice,
        onToggleVoiceMicrophone = onToggleVoiceMicrophone,
        onToggleVoiceOutput = onToggleVoiceOutput,
        onSelectVoiceAudioRoute = onSelectVoiceAudioRoute,
        onOpenStoreApp = onOpenStoreApp,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageHighlighted(
    active: Boolean,
    message: SteamGroupChatMessage,
    outgoing: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    sender: SteamFriend?,
    richMediaState: SteamChatRichMediaUiState,
    onRetryMessage: (String) -> Unit,
    onUpdateReaction: (SteamGroupChatMessage, SteamGroupChatReactionType, String, Boolean) -> Unit,
    onReportMessage: (SteamGroupChatMessage, SteamGroupChatReportReason) -> Unit,
    onDeleteMessage: (SteamGroupChatMessage) -> Unit,
    onOpenStoreApp: (Int) -> Unit
) {
    TelegramMessageHighlight(active = active) {
        GroupMessageBubble(
            message = message,
            outgoing = outgoing,
            groupedWithPrevious = groupedWithPrevious,
            groupedWithNext = groupedWithNext,
            sender = sender,
            richMediaState = richMediaState,
            onRetryMessage = onRetryMessage,
            onUpdateReaction = onUpdateReaction,
            onReportMessage = onReportMessage,
            onDeleteMessage = onDeleteMessage,
            onOpenStoreApp = onOpenStoreApp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageBubble(
    message: SteamGroupChatMessage,
    outgoing: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    sender: SteamFriend?,
    richMediaState: SteamChatRichMediaUiState,
    onRetryMessage: (String) -> Unit,
    onUpdateReaction: (SteamGroupChatMessage, SteamGroupChatReactionType, String, Boolean) -> Unit,
    onReportMessage: (SteamGroupChatMessage, SteamGroupChatReportReason) -> Unit,
    onDeleteMessage: (SteamGroupChatMessage) -> Unit,
    onOpenStoreApp: (Int) -> Unit
) {
    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showReactions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showReport by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var reportReason by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SteamChatReportReason.HARASSMENT) }
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val colors = telegramChatColors()
    val senderName = sender?.displayName ?: message.senderSteamId.takeLast(8)
    val richContent = remember(message.body) { SteamChatRichContentParser.parse(message.body) }
    val plainText = richContent.nativeTelegramText()
    val transparentMedia = richContent is SteamChatRichContent.Sticker ||
        (richContent is SteamChatRichContent.Attachment && richContent.kind == SteamChatAttachmentKind.IMAGE) ||
        isSingleSteamEmoticonMessage(message.body)
    if (message.serverEventType > 0) {
        val eventText = if (message.senderSteamId.isNotBlank() && senderName.isNotBlank()) {
            "$senderName ${message.body}"
        } else message.body
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.glass, contentColor = colors.text) {
                Text(eventText, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(
            start = if (outgoing) 46.dp else 0.dp,
            end = if (outgoing) 0.dp else 42.dp,
            top = if (groupedWithPrevious) 0.dp else 4.dp
        ),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!outgoing) {
            // ChatMessageCell uses a 42dp avatar with a 21dp round radius.
            Box(Modifier.size(42.dp)) {
                if (!groupedWithNext) GroupSenderAvatar(senderName, sender?.avatarUrl.orEmpty())
            }
            Spacer(Modifier.width(6.dp))
        }
        TelegramMessageSurface(
            outgoing = outgoing,
            groupedWithPrevious = groupedWithPrevious,
            groupedWithNext = groupedWithNext,
            modifier = Modifier.weight(1f, fill = false).combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            ),
            color = if (transparentMedia) androidx.compose.ui.graphics.Color.Transparent else if (outgoing) colors.outgoing else colors.incoming
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                if (!outgoing && !groupedWithPrevious) {
                    Text(senderName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accent)
                }
                if (message.deleted) {
                    Text("Message deleted", color = colors.secondary)
                } else if (plainText != null) TelegramTextMessage(
                    content = plainText,
                    timestamp = message.timestamp,
                    outgoing = outgoing,
                    delivery = when (message.deliveryState) {
                        SteamGroupChatDeliveryState.SENT -> TelegramMessageTextView.DELIVERY_SENT
                        SteamGroupChatDeliveryState.QUEUED, SteamGroupChatDeliveryState.SENDING, SteamGroupChatDeliveryState.VERIFYING -> TelegramMessageTextView.DELIVERY_PENDING
                        else -> TelegramMessageTextView.DELIVERY_NONE
                    },
                    groupedWithPrevious = groupedWithPrevious,
                    groupedWithNext = groupedWithNext,
                    onLongClick = { showMenu = true },
                    showBubble = false
                ) else SteamChatRichMessageContent(
                    body = message.body,
                    messageSenderSteamId = message.senderSteamId,
                    onLongClick = { showMenu = true },
                    onDeleteMessage = if (outgoing && !message.deleted && message.ordinal != Int.MAX_VALUE) {
                        { showDelete = true }
                    } else null,
                    onOpenStoreApp = onOpenStoreApp
                )
                if (message.reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.reactions.forEach { reaction ->
                            Surface(
                                onClick = {
                                    onUpdateReaction(
                                        message,
                                        reaction.type,
                                        reaction.name,
                                        !reaction.hasUserReacted
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (reaction.hasUserReacted) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    "${reaction.name} ${reaction.count}",
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                if (plainText == null || message.deleted || message.deliveryState in listOf(
                        SteamGroupChatDeliveryState.FAILED, SteamGroupChatDeliveryState.FAILED_RETRYABLE,
                        SteamGroupChatDeliveryState.FAILED_PERMANENT))
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp * 1_000L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondary
                    )
                    if (outgoing) {
                        Spacer(Modifier.width(3.dp))
                        when (message.deliveryState) {
                            SteamGroupChatDeliveryState.QUEUED,
                            SteamGroupChatDeliveryState.SENDING,
                            SteamGroupChatDeliveryState.VERIFYING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            SteamGroupChatDeliveryState.SENT -> Icon(Icons.Default.Done, null, Modifier.size(15.dp))
                            SteamGroupChatDeliveryState.FAILED,
                            SteamGroupChatDeliveryState.FAILED_RETRYABLE -> IconButton(
                                onClick = { onRetryMessage(message.clientMessageId) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    stringResource(R.string.steam_chat_retry_send),
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            SteamGroupChatDeliveryState.FAILED_PERMANENT -> Icon(
                                Icons.Default.ErrorOutline,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
    if (showMenu) {
        SteamChatMessageActionMenu(
            canReport = !outgoing && !message.deleted && message.ordinal != Int.MAX_VALUE,
            canReact = !message.deleted && message.ordinal != Int.MAX_VALUE,
            onDismiss = { showMenu = false },
            onOpenReactions = { showMenu = false; showReactions = true },
            onCopy = {
                clipboard.setText(AnnotatedString(message.body))
                showMenu = false
            },
            onReport = { showMenu = false; showReport = true },
            onDelete = if (outgoing && !message.deleted && message.ordinal != Int.MAX_VALUE) {
                { showMenu = false; showDelete = true }
            } else null
        )
    }
    if (showReactions) {
        SteamChatReactionPicker(
            emoticons = richMediaState.emoticons,
            stickers = richMediaState.stickers,
            onDismiss = { showReactions = false },
            onReact = { emoticon ->
                onUpdateReaction(message, SteamGroupChatReactionType.EMOTICON, emoticon.name, true)
                showReactions = false
            },
            onStickerReply = { sticker ->
                onUpdateReaction(message, SteamGroupChatReactionType.STICKER, sticker.name, true)
                showReactions = false
            }
        )
    }
    if (showReport) {
        SteamChatReportDialog(
            selectedReason = reportReason,
            onReasonSelected = { reportReason = it },
            onConfirm = {
                onReportMessage(message, reportReason.toGroupReason())
                showReport = false
            },
            onDismiss = { showReport = false }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.steam_chat_group_delete_title)) },
            confirmButton = {
                TextButton(onClick = { onDeleteMessage(message); showDelete = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** Steam room navigation, hosted in the same native Telegram glass as its toolbar. */
@Composable
private fun TelegramGroupChannelPill(
    rooms: List<SteamGroupChatRoom>,
    selectedChatId: String?,
    onSelect: (String) -> Unit,
    sourceView: View?
) {
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    if (rooms.isEmpty()) return
    val colors = telegramChatColors()
    val orderedRooms = remember(rooms) {
        rooms.sortedWith(compareBy<SteamGroupChatRoom> { it.sortOrder }.thenBy { it.chatId })
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 6.dp).height(38.dp),
        factory = { context ->
            FrameLayout(context).apply {
                addView(TelegramGlassView(context).apply { setCornerRadius(19 * resources.displayMetrics.density) },
                    FrameLayout.LayoutParams(-1, -1))
                addView(android.widget.HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(android.widget.LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(6, 0, 6, 0)
                    }, FrameLayout.LayoutParams(-2, -1))
                }, FrameLayout.LayoutParams(-1, -1))
            }
        },
        onRelease = { (it.getChildAt(0) as TelegramGlassView).setSourceView(null) },
        update = { frame ->
            (frame.getChildAt(0) as TelegramGlassView).apply {
                setLiquidStyle(liquid)
                setSourceView(sourceView); setTintColor(colors.native.glassTint); setDark(colors.native.dark)
            }
            val scroll = frame.getChildAt(1) as android.widget.HorizontalScrollView
            val strip = scroll.getChildAt(0) as android.widget.LinearLayout
            val density = frame.resources.displayMetrics.density
            // Reuse native buttons during typing/reactions so focus and horizontal position survive.
            if (strip.childCount != orderedRooms.size) {
                strip.removeAllViews()
                orderedRooms.forEach { _ ->
                    strip.addView(TextView(frame.context).apply {
                        gravity = Gravity.CENTER
                        textSize = 14f
                        setSingleLine()
                        setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                        isClickable = true; isFocusable = true
                    }, android.widget.LinearLayout.LayoutParams(-2, (30 * density).toInt()).apply {
                        setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
                    })
                }
            }
            orderedRooms.forEachIndexed { index, room ->
                val selected = room.chatId == (selectedChatId ?: orderedRooms.first().chatId)
                (strip.getChildAt(index) as TextView).apply {
                    text = (if (room.voiceAllowed) "♫ " else "# ") + room.name + if (room.unread) " •" else ""
                    setTextColor(if (selected) colors.native.accent else colors.native.secondaryText)
                    isSelected = selected
                    setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(0x20ffffff),
                        android.graphics.drawable.GradientDrawable().apply {
                            setColor(if (selected) colors.native.outgoing else android.graphics.Color.TRANSPARENT)
                            cornerRadius = 15 * density
                        }, null
                    )
                    setOnClickListener { onSelect(room.chatId) }
                }
            }
        }
    )
}

@Composable
private fun GroupSenderAvatar(name: String, avatarUrl: String) {
    val context = LocalContext.current
    val colors = telegramChatColors()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, context, avatarUrl) {
        value = avatarUrl.takeIf(String::isNotBlank)?.let { loadSteamRemoteImage(context, it) }
    }
    Surface(Modifier.fillMaxSize(), shape = CircleShape, color = colors.accent, contentColor = colors.accentText) {
        val image = bitmap
        if (image != null) {
            Image(image, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

internal data class TelegramGroupMessageRow(
    val message: SteamGroupChatMessage,
    val showDate: Boolean,
    val groupedWithPrevious: Boolean,
    val groupedWithNext: Boolean
)

internal fun groupTelegramMessageRows(messages: List<SteamGroupChatMessage>): List<TelegramGroupMessageRow> =
    messages.mapIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        fun sharesGroup(other: SteamGroupChatMessage?) = telegramMessagesShareGroup(
            senderId = message.senderSteamId,
            timestampSeconds = message.timestamp,
            otherSenderId = other?.senderSteamId,
            otherTimestampSeconds = other?.timestamp,
            serviceMessage = message.serverEventType > 0,
            otherServiceMessage = other?.serverEventType?.let { it > 0 } == true
        )
        TelegramGroupMessageRow(
            message = message,
            showDate = previous == null || telegramMessageDay(previous.timestamp) != telegramMessageDay(message.timestamp),
            groupedWithPrevious = sharesGroup(previous),
            groupedWithNext = sharesGroup(next)
        )
    }

private fun SteamChatReportReason.toGroupReason(): SteamGroupChatReportReason = when (this) {
    SteamChatReportReason.HARASSMENT -> SteamGroupChatReportReason.HARASSMENT
    SteamChatReportReason.SCAM -> SteamGroupChatReportReason.SCAM
    SteamChatReportReason.SPAM -> SteamGroupChatReportReason.SPAM
    SteamChatReportReason.OTHER -> SteamGroupChatReportReason.OTHER
}
