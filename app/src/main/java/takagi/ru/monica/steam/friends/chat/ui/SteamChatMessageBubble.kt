package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatReaction
import takagi.ru.monica.steam.friends.chat.domain.SteamChatReactionType
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImageMode
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMessageContent
import takagi.ru.monica.steam.friends.chat.richmedia.ui.isSingleSteamEmoticonMessage
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser

import takagi.ru.monica.steam.friends.chat.telegram.*
import com.monica.telegramchat.TelegramMessageTextView

@Composable
internal fun SteamChatMessageBubble(
    message: SteamChatMessage,
    replyToMessage: SteamChatMessage?,
    accountSteamId: String,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    onRetry: () -> Unit,
    onLongClick: () -> Unit,
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    onDeleteMessage: (() -> Unit)? = null
) {
    val outgoing = message.isOutgoing(accountSteamId)
    val haptics = LocalHapticFeedback.current
    val retryLabel = stringResource(R.string.steam_chat_retry_send)
    val colors = telegramChatColors()
    val richContent = remember(message.body) { SteamChatRichContentParser.parse(message.body) }
    val standaloneCard = richContent is SteamChatRichContent.GameInvite ||
        richContent is SteamChatRichContent.StoreGameShare ||
        richContent is SteamChatRichContent.OfficialMessage
    val transparentMedia = richContent is SteamChatRichContent.Sticker ||
        (richContent is SteamChatRichContent.Attachment &&
            richContent.kind == SteamChatAttachmentKind.IMAGE) ||
        isSingleSteamEmoticonMessage(message.body)
    val interactionModifier = Modifier.pointerInput(message.stableId) {
        detectTapGestures(
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }
        )
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
        ) {
            val plainText = richContent.nativeTelegramText()
            if (plainText != null && replyToMessage == null) {
                TelegramTextMessage(
                    content = plainText,
                    timestamp = message.timestamp,
                    outgoing = outgoing,
                    delivery = when (message.deliveryState) {
                        SteamChatDeliveryState.SENT -> TelegramMessageTextView.DELIVERY_SENT
                        SteamChatDeliveryState.QUEUED, SteamChatDeliveryState.SENDING, SteamChatDeliveryState.VERIFYING -> TelegramMessageTextView.DELIVERY_PENDING
                        else -> TelegramMessageTextView.DELIVERY_NONE
                    },
                    groupedWithPrevious = groupedWithPrevious,
                    groupedWithNext = groupedWithNext,
                    onLongClick = onLongClick,
                    modifier = Modifier.widthIn(max = 324.dp)
                )
                if (outgoing && message.deliveryState in listOf(SteamChatDeliveryState.FAILED_RETRYABLE, SteamChatDeliveryState.FAILED_PERMANENT)) {
                    DeliveryMetadata(message, outgoing, retryLabel, onRetry, Modifier.align(Alignment.End), showTime = false)
                }
            } else if (richContent is SteamChatRichContent.Attachment && richContent.kind == SteamChatAttachmentKind.IMAGE) {
                Column(Modifier.widthIn(max = 324.dp)) {
                    replyToMessage?.let { ReplyPreview(it) }
                    takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatImageMessage(
                        content = richContent, outgoing = outgoing, groupedWithPrevious = groupedWithPrevious,
                        groupedWithNext = groupedWithNext, onLongClick = onLongClick,
                        metadata = { DeliveryMetadata(message, outgoing, retryLabel, onRetry,
                            onMedia = richContent.caption.isBlank()) })
                }
            } else if (standaloneCard) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 324.dp),
                    horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
                ) {
                    replyToMessage?.let { ReplyPreview(it) }
                    SteamChatRichMessageContent(
                        body = message.body,
                        messageSenderSteamId = message.senderSteamId,
                        onLongClick = onLongClick,
                        onDeleteMessage = onDeleteMessage,
                        onOpenStoreApp = onOpenStoreApp
                    )
                    DeliveryMetadata(
                        message = message,
                        outgoing = outgoing,
                        retryLabel = retryLabel,
                        onRetry = onRetry,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 3.dp, end = 4.dp)
                    )
                }
            } else {
                TelegramMessageSurface(
                    outgoing = outgoing,
                    groupedWithPrevious = groupedWithPrevious,
                    groupedWithNext = groupedWithNext,
                    modifier = Modifier.widthIn(max = 324.dp).then(interactionModifier),
                    color = if (transparentMedia) Color.Transparent else if (outgoing) colors.outgoing else colors.incoming
                ) {
                    if (transparentMedia) {
                        Box {
                            Column {
                                replyToMessage?.let { ReplyPreview(it) }
                                SteamChatRichMessageContent(
                                    body = message.body,
                                    messageSenderSteamId = message.senderSteamId,
                                    onOpenStoreApp = onOpenStoreApp
                                )
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                DeliveryMetadata(
                                    message = message,
                                    outgoing = outgoing,
                                    retryLabel = retryLabel,
                                    onRetry = onRetry,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    } else Column {
                        replyToMessage?.let { ReplyPreview(it) }
                        Row(
                            modifier = Modifier.padding(
                                start = 13.dp,
                                top = if (groupedWithPrevious) 7.dp else 10.dp,
                                end = if (outgoing) 7.dp else 11.dp,
                                bottom = if (groupedWithNext) 7.dp else 9.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            SteamChatRichMessageContent(
                                body = message.body,
                                messageSenderSteamId = message.senderSteamId,
                                onOpenStoreApp = onOpenStoreApp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            DeliveryMetadata(
                                message = message,
                                outgoing = outgoing,
                                retryLabel = retryLabel,
                                onRetry = onRetry
                            )
                        }
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                MessageReactionStrip(
                    reactions = message.reactions,
                    accountSteamId = accountSteamId,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageReactionStrip(
    reactions: List<SteamChatReaction>,
    accountSteamId: String,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .widthIn(max = 324.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.filter { it.count > 0 }.forEach { reaction ->
            val selected = accountSteamId in reaction.reactorSteamIds
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 30.dp)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SteamChatRemoteImage(
                        url = when (reaction.type) {
                            SteamChatReactionType.EMOTICON ->
                                SteamChatEmoticon(reaction.name).imageUrl
                            SteamChatReactionType.STICKER ->
                                SteamChatSticker(reaction.name).imageUrl
                        },
                        contentDescription = reaction.name,
                        modifier = Modifier.size(22.dp),
                        mode = when (reaction.type) {
                            SteamChatReactionType.EMOTICON -> SteamChatRemoteImageMode.EMOTICON
                            SteamChatReactionType.STICKER -> SteamChatRemoteImageMode.STICKER
                        }
                    )
                    Text(
                        text = reaction.count.toString(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyPreview(message: SteamChatMessage) {
    Surface(
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    ) {
        Text(
            text = message.body.replace(Regex("\\s+"), " ").take(72),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2
        )
    }
}
