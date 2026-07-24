package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
internal fun SteamChatMessageBubble(
    message: SteamChatMessage,
    accountSteamId: String,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outgoing = message.isOutgoing(accountSteamId)
    val retryable = outgoing && message.deliveryState == SteamChatDeliveryState.FAILED
    val retryLabel = stringResource(R.string.steam_chat_retry_send)
    val bubbleShape = chatBubbleShape(outgoing, groupedWithPrevious, groupedWithNext)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 324.dp)
                .then(
                    if (retryable) {
                        Modifier.clickable(onClickLabel = retryLabel, onClick = onRetry)
                    } else {
                        Modifier
                    }
                ),
            shape = bubbleShape,
            color = if (outgoing) {
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
                Text(
                    text = message.body,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(message.timestamp * 1_000L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (outgoing) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                                SteamChatDeliveryState.PENDING -> Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = stringResource(R.string.steam_chat_sending),
                                    modifier = Modifier.size(15.dp)
                                )
                                SteamChatDeliveryState.SENT -> Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = stringResource(R.string.steam_chat_sent),
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                SteamChatDeliveryState.FAILED -> Icon(
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
        }
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
