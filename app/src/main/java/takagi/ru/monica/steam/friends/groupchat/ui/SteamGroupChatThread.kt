package takagi.ru.monica.steam.friends.groupchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatMessageActionMenu
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReactionPicker
import takagi.ru.monica.steam.friends.chat.actions.ui.SteamChatReportDialog
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingConversationKey
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatJumpMessage
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatAutoScrollToLatestEffect
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatJumpToLatestButton
import takagi.ru.monica.steam.friends.chat.position.ui.animateToLatestSteamChatMessage
import takagi.ru.monica.steam.friends.chat.position.ui.rememberSteamChatJumpToLatestState
import takagi.ru.monica.steam.friends.chat.position.ui.rememberSteamChatReadingPosition
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMessageContent
import takagi.ru.monica.steam.friends.chat.ui.SteamChatComposer
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.SteamGroupAvatarImage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReactionType
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatReportReason
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceChannelPanel
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceStatusBanner
import takagi.ru.monica.steam.navigation.ui.steamWindowBottomPadding
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding

@Composable
internal fun SteamGroupChatThread(
    state: SteamGroupChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    group: SteamGroupChatSummary,
    friends: List<SteamFriend>,
    targetMessageId: String? = null,
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
    modifier: Modifier = Modifier
) {
    val messages = state.thread?.messages.orEmpty()
    val friendsById = remember(friends) { friends.associateBy(SteamFriend::steamId) }
    val groupMembers = remember(group.topMemberSteamIds, friends) {
        group.topMemberSteamIds.mapNotNull(friendsById::get)
    }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val conversationKey = remember(state.accountSteamId, group.groupId, state.selectedChatId) {
        SteamChatReadingConversationKey.group(
            state.accountSteamId,
            group.groupId,
            state.selectedChatId.orEmpty()
        )
    }
    val messageIds = remember(messages) { messages.map(SteamGroupChatMessage::stableId) }
    val leadingItemCount = if (state.loadingOlder) 1 else 0
    val selectedRoom = group.rooms.firstOrNull { it.chatId == state.selectedChatId }
    val readingUi by rememberSteamChatReadingPosition(
        conversationKey = conversationKey,
        messageIds = messageIds,
        requestedMessageId = targetMessageId,
        leadingItemCount = leadingItemCount,
        listState = listState
    )
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
    val shouldLoadOlder by remember(listState, state.loadingOlder, state.thread?.moreAvailable) {
        derivedStateOf {
            state.thread?.moreAvailable == true && !state.loadingOlder &&
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index?.let { it <= 2 } == true
        }
    }
    LaunchedEffect(shouldLoadOlder) { if (shouldLoadOlder) onLoadOlder() }
    SteamChatAutoScrollToLatestEffect(
        conversationKey = conversationKey,
        latestMessageId = messages.lastOrNull()?.stableId,
        latestMessageIsOutgoing = messages.lastOrNull()?.senderSteamId == state.accountSteamId,
        messageCount = messages.size,
        leadingItemCount = leadingItemCount,
        messagesBelow = readingUi.messagesBelow,
        restored = readingUi.restored,
        listState = listState
    )

    Column(
        modifier
            .fillMaxSize()
            .steamWindowBottomPadding(suppressWhenImeVisible = true)
            .imePadding()
    ) {
        GroupThreadHeader(group, groupMembers, onBack, onOpenInfo, onInvite)
        SteamGroupChannelQuickFilter(
            rooms = group.rooms,
            selectedChatId = state.selectedChatId,
            onSelect = { chatId -> onOpenRoom(group.groupId, chatId) }
        )
        val voiceRoom = selectedRoom?.takeIf { it.voiceAllowed }
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
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
                    modifier = Modifier.fillMaxSize()
                )
                state.threadLoading && state.thread == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.loadingOlder) item("older-loading") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                    items(messages, key = SteamGroupChatMessage::stableId) { message ->
                        GroupMessageBubble(
                            message = message,
                            outgoing = message.senderSteamId == state.accountSteamId,
                            senderName = friendsById[message.senderSteamId]?.displayName
                                ?: message.senderSteamId.takeLast(8),
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
                onClick = {
                    scrollScope.launch {
                        listState.animateToLatestSteamChatMessage(messages.size, leadingItemCount)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )
        }
        if (voiceRoom == null) {
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun SteamGroupChatThreadHost(
    state: SteamGroupChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    friends: List<SteamFriend>,
    targetMessageId: String? = null,
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
    modifier: Modifier = Modifier
) {
    val group = state.groups.firstOrNull { it.groupId == state.selectedGroupId } ?: return
    SteamGroupChatThread(
        state = state,
        richMediaState = richMediaState,
        group = group,
        friends = friends,
        targetMessageId = targetMessageId,
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

@Composable
private fun GroupThreadHeader(
    group: SteamGroupChatSummary,
    members: List<SteamFriend>,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onInvite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .steamWindowTopPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        SteamGroupAvatarImage(
            url = group.avatarUrl,
            members = members,
            contentDescription = group.name,
            modifier = Modifier.size(40.dp).clickable(onClick = onOpenInfo)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onOpenInfo).padding(vertical = 4.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.steam_group_chat_members, group.activeMemberCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onInvite) { Icon(Icons.Default.GroupAdd, stringResource(R.string.steam_group_chat_invite)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageBubble(
    message: SteamGroupChatMessage,
    outgoing: Boolean,
    senderName: String,
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
    if (message.serverEventType > 0) {
        val eventText = if (message.senderSteamId.isNotBlank() && senderName.isNotBlank()) {
            "$senderName ${message.body}"
        } else message.body
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(eventText, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            ),
            shape = RoundedCornerShape(18.dp),
            color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (!outgoing) Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (message.deleted) {
                    Text("Message deleted", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else SteamChatRichMessageContent(
                    body = message.body,
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
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp * 1_000L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            title = { Text("删除这条群组消息？") },
            confirmButton = {
                TextButton(onClick = { onDeleteMessage(message); showDelete = false }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

private fun SteamChatReportReason.toGroupReason(): SteamGroupChatReportReason = when (this) {
    SteamChatReportReason.HARASSMENT -> SteamGroupChatReportReason.HARASSMENT
    SteamChatReportReason.SCAM -> SteamGroupChatReportReason.SCAM
    SteamChatReportReason.SPAM -> SteamGroupChatReportReason.SPAM
    SteamChatReportReason.OTHER -> SteamGroupChatReportReason.OTHER
}
