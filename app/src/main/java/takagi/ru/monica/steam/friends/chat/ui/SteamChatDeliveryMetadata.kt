package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
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

import takagi.ru.monica.steam.friends.chat.telegram.*

@Composable
internal fun DeliveryMetadata(
    message: SteamChatMessage,
    outgoing: Boolean,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
    onMedia: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showTime) Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp * 1_000L)),
            style = MaterialTheme.typography.labelSmall,
            color = if (onMedia) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    SteamChatDeliveryState.VERIFYING -> AnimatedSendingClock(Modifier.size(15.dp),
                        if (onMedia) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    SteamChatDeliveryState.SENT -> Icon(
                        Icons.Default.Done,
                        contentDescription = stringResource(R.string.steam_chat_sent),
                        modifier = Modifier.size(15.dp),
                        tint = if (onMedia) Color.White else MaterialTheme.colorScheme.primary
                    )
                    SteamChatDeliveryState.FAILED_RETRYABLE -> IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onRetry()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = retryLabel,
                            modifier = Modifier.size(18.dp),
                            tint = if (onMedia) Color(0xffffb4ab) else MaterialTheme.colorScheme.error
                        )
                    }
                    SteamChatDeliveryState.FAILED_PERMANENT -> Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = retryLabel,
                        modifier = Modifier.size(17.dp),
                        tint = if (onMedia) Color(0xffffb4ab) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedSendingClock(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
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
