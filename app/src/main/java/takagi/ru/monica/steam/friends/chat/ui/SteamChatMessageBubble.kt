package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
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
    modifier: Modifier = Modifier
) {
    val outgoing = message.isOutgoing(accountSteamId)
    val retryable = outgoing && message.deliveryState == SteamChatDeliveryState.FAILED_RETRYABLE
    val haptics = LocalHapticFeedback.current
    val retryLabel = stringResource(R.string.steam_chat_retry_send)
    val bubbleShape = chatBubbleShape(outgoing, groupedWithPrevious, groupedWithNext)
    val richContent = remember(message.body) { SteamChatRichContentParser.parse(message.body) }
    val standaloneCard = richContent is SteamChatRichContent.GameInvite ||
        richContent is SteamChatRichContent.StoreGameShare
    val transparentMedia = richContent is SteamChatRichContent.Sticker ||
        (richContent is SteamChatRichContent.Attachment &&
            richContent.kind == SteamChatAttachmentKind.IMAGE) ||
        isSingleSteamEmoticonMessage(message.body)
    val interactionModifier = Modifier.pointerInput(retryable, message.stableId) {
        detectTapGestures(
            onTap = {
                if (retryable) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onRetry()
                }
            },
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
            if (standaloneCard) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 324.dp)
                        .then(interactionModifier),
                    horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
                ) {
                    replyToMessage?.let { ReplyPreview(it) }
                    SteamChatRichMessageContent(
                        body = message.body,
                        onOpenStoreApp = onOpenStoreApp
                    )
                    DeliveryMetadata(
                        message = message,
                        outgoing = outgoing,
                        retryLabel = retryLabel,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 3.dp, end = 4.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 324.dp)
                        .then(interactionModifier),
                    shape = bubbleShape,
                    color = if (transparentMedia) Color.Transparent else if (outgoing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (outgoing) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ) {
                    if (transparentMedia) {
                        Box {
                            Column {
                                replyToMessage?.let { ReplyPreview(it) }
                                SteamChatRichMessageContent(
                                    body = message.body,
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
                                onOpenStoreApp = onOpenStoreApp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            DeliveryMetadata(message, outgoing, retryLabel)
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

@Composable
private fun DeliveryMetadata(
    message: SteamChatMessage,
    outgoing: Boolean,
    retryLabel: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp * 1_000L)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
        if (outgoing) {
            Spacer(Modifier.width(2.dp))
            AnimatedContent(
                targetState = message.deliveryState,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(initialScale = 0.8f, animationSpec = spring()))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
                        .using(SizeTransform(clip = false))
                },
                label = "SteamChatDelivery"
            ) { delivery ->
                when (delivery) {
                    SteamChatDeliveryState.QUEUED,
                    SteamChatDeliveryState.SENDING,
                    SteamChatDeliveryState.VERIFYING -> AnimatedSendingClock(Modifier.size(15.dp))
                    SteamChatDeliveryState.SENT -> Icon(
                        Icons.Default.Done,
                        contentDescription = stringResource(R.string.steam_chat_sent),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    SteamChatDeliveryState.FAILED_RETRYABLE,
                    SteamChatDeliveryState.FAILED_PERMANENT -> Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = retryLabel,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedSendingClock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "SteamChatSendingClock")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SteamChatSendingClockHand"
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val stroke = with(LocalDensity.current) { 1.35.dp.toPx() }
    Canvas(modifier) {
        val radius = size.minDimension / 2f - stroke
        drawCircle(color = color, radius = radius, style = Stroke(stroke))
        drawLine(
            color = color,
            start = center,
            end = center.copy(y = center.y - radius * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val radians = Math.toRadians((rotation - 90f).toDouble())
        drawLine(
            color = color,
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                x = center.x + kotlin.math.cos(radians).toFloat() * radius * 0.68f,
                y = center.y + kotlin.math.sin(radians).toFloat() * radius * 0.68f
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun chatBubbleShape(
    outgoing: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean
): RoundedCornerShape {
    val large = 18.dp
    val joined = 5.dp
    return if (outgoing) {
        RoundedCornerShape(
            topStart = large,
            topEnd = if (groupedWithPrevious) joined else large,
            bottomStart = large,
            bottomEnd = if (groupedWithNext) joined else large
        )
    } else {
        RoundedCornerShape(
            topStart = if (groupedWithPrevious) joined else large,
            topEnd = large,
            bottomStart = if (groupedWithNext) joined else large,
            bottomEnd = large
        )
    }
}
