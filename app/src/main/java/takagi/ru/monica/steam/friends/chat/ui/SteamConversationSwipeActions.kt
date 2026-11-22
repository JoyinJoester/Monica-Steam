package takagi.ru.monica.steam.friends.chat.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import kotlin.math.abs

/**
 * Monica Pass list actions, adapted for an inbox with native AndroidView children.
 * The entire foreground (including the selection avatar) moves as one layer. The initial
 * pointer pass cancels the native cell's press only after a horizontal direction is locked.
 */
@Composable
internal fun SteamConversationSwipeActions(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    allowSwipeLeft: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var width by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rebound = remember { Animatable(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentLeft by rememberUpdatedState(onSwipeLeft)
    val currentRight by rememberUpdatedState(onSwipeRight)
    val haptic = rememberHapticFeedback()
    val offset = if (dragging) dragOffset else rebound.value
    val threshold = (width * 0.2f).coerceAtLeast(1f)
    val progress = (abs(offset) / threshold).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(16.dp * progress)
    val parallaxLimit = with(LocalDensity.current) { 40.dp.toPx() }

    fun settle() {
        val from = dragOffset
        settleJob?.cancel()
        settleJob = scope.launch {
            rebound.snapTo(from)
            dragging = false
            if (ValueAnimator.areAnimatorsEnabled()) {
                rebound.animateTo(0f, spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                    visibilityThreshold = 0.2f
                ))
            } else {
                rebound.snapTo(0f)
            }
        }
    }

    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }.clip(shape)
        .pointerInput(allowSwipeLeft) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                var locked = false
                while (!locked) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed || event.changes.count { it.pressed } > 1 || change.isConsumed) return@awaitEachGesture
                    val delta = change.position - down.position
                    val x = abs(delta.x)
                    val y = abs(delta.y)
                    // Once the user starts scrolling, this row cannot steal the gesture later.
                    if (y > slop && y > x * 0.8f) return@awaitEachGesture
                    if (x > slop * 1.35f && x > y * 1.5f) {
                        // A disabled left action still cancels the native cell's press. Giving
                        // this horizontal drag back to AndroidView would turn it into a click.
                        settleJob?.cancel()
                        val limit = width * 0.65f
                        dragOffset = (rebound.value + delta.x)
                            .coerceIn(if (allowSwipeLeft) -limit else 0f, limit)
                        dragging = true
                        change.consume()
                        locked = true
                    }
                }
                var released = false
                var thresholdSide = 0
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.singleOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                        val limit = width * 0.65f
                        val resistance = if (abs(dragOffset) > width * 0.5f) 0.25f else 1f
                        dragOffset = (dragOffset + change.positionChange().x * resistance)
                            .coerceIn(if (allowSwipeLeft) -limit else 0f, limit)
                        if (!change.pressed) {
                            // A system/native cancellation does not count as a release.
                            released = change.previousPressed && event.type == androidx.compose.ui.input.pointer.PointerEventType.Release
                            change.consume()
                            break
                        }
                        change.consume()
                        val side = when {
                            dragOffset > width * 0.2f -> 1
                            allowSwipeLeft && dragOffset < -width * 0.2f -> -1
                            else -> 0
                        }
                        if (side != 0 && side != thresholdSide) haptic.performLightClick()
                        thresholdSide = side
                    }
                } finally {
                    val action = if (released) when {
                        dragOffset > width * 0.2f -> 1
                        allowSwipeLeft && dragOffset < -width * 0.2f -> -1
                        else -> 0
                    } else 0
                    settle()
                    if (action == 1) currentRight()
                    if (action == -1) currentLeft()
                }
            }
        }) {
        if (progress > 0f) {
            val right = offset > 0f
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.matchParentSize(), shape = shape,
                color = (if (right) colors.primaryContainer else colors.errorContainer).copy(alpha = progress),
                contentColor = if (right) colors.onPrimaryContainer else colors.onErrorContainer
            ) {
                Box(contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer {
                            translationX = (offset * 0.3f).coerceIn(-parallaxLimit, parallaxLimit)
                            alpha = progress
                        },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (right) Icons.Default.CheckCircle else Icons.Default.Delete, null,
                            Modifier.size(24.dp).graphicsLayer { scaleX = 0.8f + progress * 0.4f; scaleY = scaleX })
                        Text(stringResource(if (right) R.string.swipe_action_select else R.string.swipe_action_delete),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = offset; this.shape = shape; clip = true },
            shape = shape, color = MaterialTheme.colorScheme.surface
        ) { content() }
    }
}
