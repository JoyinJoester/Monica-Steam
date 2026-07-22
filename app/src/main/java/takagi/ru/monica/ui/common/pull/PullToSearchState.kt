package takagi.ru.monica.ui.common.pull

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import takagi.ru.monica.ui.haptic.rememberHapticFeedback

@Stable
data class PullToSearchStateHandle(
    val currentOffset: Float,
    val nestedScrollConnection: NestedScrollConnection,
    val onVerticalDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit
)

@Composable
fun rememberPullToSearchState(
    isSearchExpanded: Boolean,
    searchTriggerDistance: Float,
    maxDragDistance: Float,
    onSearchTriggered: () -> Unit
): PullToSearchStateHandle {
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val onSearchTriggeredState by rememberUpdatedState(onSearchTriggered)
    var currentOffset by remember { mutableFloatStateOf(0f) }
    var thresholdFeedbackSent by remember { mutableStateOf(false) }
    var searchTriggeredForPull by remember { mutableStateOf(false) }
    val collapseAnimatable = remember { Animatable(0f) }

    fun updateOffset(newOffset: Float) {
        val oldOffset = currentOffset
        currentOffset = newOffset
        if (oldOffset < searchTriggerDistance && currentOffset >= searchTriggerDistance) {
            if (!thresholdFeedbackSent) {
                thresholdFeedbackSent = true
                haptic.performLightClick()
            }
        } else if (currentOffset < searchTriggerDistance) {
            thresholdFeedbackSent = false
        }
    }

    fun interruptCollapseAnimation() {
        if (!collapseAnimatable.isRunning) return
        scope.launch {
            collapseAnimatable.stop()
            collapseAnimatable.snapTo(currentOffset)
        }
    }

    suspend fun collapsePullOffsetSmoothly() {
        if (currentOffset <= 0.5f) {
            currentOffset = 0f
            return
        }
        if (collapseAnimatable.isRunning) return
        collapseAnimatable.snapTo(currentOffset)
        try {
            collapseAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 140,
                    easing = FastOutLinearInEasing
                )
            ) {
                currentOffset = value
            }
        } finally {
            currentOffset = 0f
            collapseAnimatable.snapTo(0f)
            thresholdFeedbackSent = false
            searchTriggeredForPull = false
        }
    }

    fun triggerSearchIfReady() {
        if (
            !isSearchExpanded &&
            !searchTriggeredForPull &&
            currentOffset >= searchTriggerDistance
        ) {
            searchTriggeredForPull = true
            onSearchTriggeredState()
        }
    }

    fun onVerticalDrag(dragAmount: Float) {
        interruptCollapseAnimation()
        if (dragAmount < 0f) {
            updateOffset((currentOffset + dragAmount).coerceAtLeast(0f))
            return
        }
        if (dragAmount == 0f) return
        updateOffset(
            calculateDampedPullOffset(
                currentOffset = currentOffset,
                dragDelta = dragAmount,
                maxDragDistance = maxDragDistance
            )
        )
    }

    val onDragEnd: () -> Unit = {
        scope.launch {
            triggerSearchIfReady()
            collapsePullOffsetSmoothly()
        }
    }
    val onDragCancel: () -> Unit = {
        scope.launch { collapsePullOffsetSmoothly() }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            collapsePullOffsetSmoothly()
        }
    }

    val nestedScrollConnection = remember(isSearchExpanded, searchTriggerDistance, maxDragDistance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentOffset > 0f && available.y < 0f) {
                    interruptCollapseAnimation()
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    updateOffset(newOffset)
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (
                    available.y > 0f &&
                    source == NestedScrollSource.UserInput
                ) {
                    interruptCollapseAnimation()
                    updateOffset(
                        calculateDampedPullOffset(
                            currentOffset = currentOffset,
                            dragDelta = available.y,
                            maxDragDistance = maxDragDistance
                        )
                    )
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                triggerSearchIfReady()
                collapsePullOffsetSmoothly()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (currentOffset > 0f) {
                    triggerSearchIfReady()
                    collapsePullOffsetSmoothly()
                }
                return Velocity.Zero
            }
        }
    }

    return PullToSearchStateHandle(
        currentOffset = currentOffset,
        nestedScrollConnection = nestedScrollConnection,
        onVerticalDrag = ::onVerticalDrag,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel
    )
}
