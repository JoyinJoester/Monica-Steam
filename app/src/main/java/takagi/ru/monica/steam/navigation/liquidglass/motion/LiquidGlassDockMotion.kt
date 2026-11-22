/*
 * Motion model adapted from KernelSU FloatingBottomBar and the BiliPai
 * KernelSU-aligned Dock implementation. KernelSU is GPL-3.0; Monica Steam is
 * distributed under the same license.
 */
package takagi.ru.monica.steam.navigation.liquidglass.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal data class LiquidGlassDockDragSpec(
    val baseResistance: Float = 1.02f,
    val overscrollResistance: Float = 0.34f,
    val overscrollLimitItems: Float = 0.5f,
    val flingProjectionTimeSeconds: Float = 0.20f,
    val maxReleaseStepCount: Int = 1
)

internal fun resolveLiquidGlassDockVelocityItemsPerSecond(
    velocityPxPerSecond: Float,
    itemWidthPx: Float
): Float = if (itemWidthPx > 0f) velocityPxPerSecond / itemWidthPx else 0f

internal fun resolveLiquidGlassDockReleaseTargetIndex(
    currentValue: Float,
    velocityPxPerSecond: Float,
    itemWidthPx: Float,
    itemCount: Int,
    dragSpec: LiquidGlassDockDragSpec = LiquidGlassDockDragSpec()
): Int {
    if (itemCount <= 0) return 0
    val projectedValue = currentValue +
        resolveLiquidGlassDockVelocityItemsPerSecond(
            velocityPxPerSecond = velocityPxPerSecond,
            itemWidthPx = itemWidthPx
        ) * dragSpec.flingProjectionTimeSeconds
    var targetIndex = projectedValue.roundToInt()
    val baseIndex = currentValue.roundToInt()
    val maxStep = dragSpec.maxReleaseStepCount.coerceAtLeast(1)
    if (abs(targetIndex - baseIndex) > maxStep) {
        targetIndex = baseIndex + (targetIndex - baseIndex).sign * maxStep
    }
    return targetIndex.coerceIn(0, itemCount - 1)
}

internal class LiquidGlassDockMotionState internal constructor(
    initialIndex: Int,
    private val itemCount: Int,
    private val scope: CoroutineScope,
    private val onIndexChanged: (Int) -> Unit,
    reduceMotion: Boolean,
    private val dragSpec: LiquidGlassDockDragSpec = LiquidGlassDockDragSpec(),
    private val awaitReleaseFrame: suspend () -> Unit = { awaitFrame() }
) {
    private val reducedMotionAnimationSpec: AnimationSpec<Float> =
        tween<Float>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)
    private val valueAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 1000f, 0.001f)
    }
    private val velocityAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(0.5f, 300f, 0.01f)
    }
    private val pressProgressAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 1000f, 0.001f)
    }
    private val offsetSnapAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 300f, 0.5f)
    }

    private val valueAnimation = Animatable(initialIndex.toFloat(), 0.001f)
    private val velocityAnimation = AnimationState(0f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val offsetAnimation = Animatable(0f)
    private val deformationVelocityTracker = VelocityTracker()

    // Pointer events publish directly to the draw/layer readers. Animatables are
    // used only after release, rather than launching snapTo jobs for every event.
    private var position by mutableFloatStateOf(initialIndex.toFloat())
    private var panelOffset by mutableFloatStateOf(0f)
    private var deformationVelocityTarget by mutableFloatStateOf(0f)

    private var motionGeneration = 0
    private var valueJob: Job? = null
    private var velocityJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var offsetJob: Job? = null
    private var desiredValue = initialIndex.toFloat()
    private var pressRequested = false
    private var disposed = false

    val value: Float get() = position
    val targetValue: Float get() = targetIndex.toFloat()
    val deformationVelocityItemsPerSecond: Float get() = velocityAnimation.value
    val pressProgress: Float get() = pressProgressAnimation.value
    val dragOffset: Float get() = panelOffset
    val isRunning: Boolean get() = valueJob?.isActive == true

    var velocityPxPerSecond by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    var targetIndex by mutableIntStateOf(initialIndex)
        private set

    private fun startNewMotion(): Int {
        motionGeneration += 1
        return motionGeneration
    }

    private fun press() {
        if (pressRequested) return
        pressRequested = true
        releaseJob?.cancel()
        pressJob?.cancel()
        pressJob = scope.launch {
            pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
        }
    }

    private fun release(onSettled: (() -> Unit)? = null) {
        pressRequested = false
        releaseJob?.cancel()
        releaseJob = scope.launch {
            awaitReleaseFrame()
            val threshold = ((itemCount - 1).toFloat() * 0.025f).coerceAtLeast(0.001f)
            snapshotFlow { abs(value - targetValue) < threshold }.first { it }
            onSettled?.invoke()
            pressJob?.cancel()
            pressJob = scope.launch {
                pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
            }
        }
    }

    private fun updateDeformationVelocity(position: Float, eventTimeMillis: Long) {
        val valueRange = (itemCount - 1).toFloat().coerceAtLeast(1f)
        deformationVelocityTracker.addPosition(
            eventTimeMillis,
            Offset(position, 0f)
        )
        deformationVelocityTarget = deformationVelocityTracker.calculateVelocity().x / valueRange
    }

    private fun startDeformationVelocityLoop() {
        if (velocityJob?.isActive == true) return
        velocityJob = scope.launch {
            var continuingAnimation = false
            while (isActive) {
                val target = deformationVelocityTarget
                var retargeted = false
                velocityAnimation.animateTo(
                    targetValue = target,
                    animationSpec = velocityAnimationSpec,
                    sequentialAnimation = continuingAnimation
                ) {
                    // Read the latest target at a frame boundary. Pointer events
                    // neither allocate jobs nor repeatedly cancel a coroutine.
                    if (deformationVelocityTarget != target) {
                        retargeted = true
                        cancelAnimation()
                    }
                }
                continuingAnimation = retargeted
                if (!retargeted) {
                    // An idle Dock has no running frame loop. Start a fresh clock
                    // after this wait so a long pause cannot fast-forward a spring.
                    snapshotFlow { deformationVelocityTarget }.first { it != target }
                }
            }
        }
    }

    private fun animateToValue(value: Float, onSettled: (() -> Unit)? = null) {
        val nextTarget = value.fastCoerceIn(0f, (itemCount - 1).toFloat())
        val startPosition = position
        val initialVelocity = if (valueAnimation.isRunning) valueAnimation.velocity else 0f
        targetIndex = nextTarget.roundToInt().coerceIn(0, itemCount - 1)
        valueJob?.cancel()
        valueJob = scope.launch {
            valueAnimation.snapTo(startPosition)
            valueAnimation.animateTo(nextTarget, valueAnimationSpec, initialVelocity) {
                position = this.value
            }
        }
        deformationVelocityTarget = 0f
        press()
        release(onSettled)
    }

    fun onDrag(
        dragAmountPx: Float,
        itemWidthPx: Float,
        gestureVelocityPxPerSecond: Float = 0f,
        eventTimeMillis: Long = SystemClock.uptimeMillis()
    ) {
        if (disposed || itemWidthPx <= 0f || itemCount <= 0) return
        if (!isDragging) {
            isDragging = true
            startNewMotion()
            valueJob?.cancel()
            offsetJob?.cancel()
            desiredValue = position
            velocityPxPerSecond = 0f
            deformationVelocityTracker.resetTracking()
            deformationVelocityTarget = 0f
            startDeformationVelocityLoop()
            press()
        }
        velocityPxPerSecond = gestureVelocityPxPerSecond

        val isOverscrolling = desiredValue < 0f || desiredValue > (itemCount - 1).toFloat()
        val resistance = if (isOverscrolling) {
            dragSpec.overscrollResistance
        } else {
            dragSpec.baseResistance
        }
        desiredValue = (desiredValue + dragAmountPx / itemWidthPx * resistance).fastCoerceIn(
            -dragSpec.overscrollLimitItems,
            (itemCount - 1).toFloat() + dragSpec.overscrollLimitItems
        )

        position = desiredValue.fastCoerceIn(0f, (itemCount - 1).toFloat())
        panelOffset += dragAmountPx
        updateDeformationVelocity(position, eventTimeMillis)
    }

    fun setPressed(pressed: Boolean) {
        if (disposed) return
        if (pressed) {
            press()
        } else if (!isDragging && pressRequested) {
            release()
        }
    }

    fun onDragEnd(velocityX: Float, itemWidthPx: Float) {
        if (disposed || !isDragging || itemWidthPx <= 0f || itemCount <= 0) return
        isDragging = false
        val generation = motionGeneration
        velocityPxPerSecond = velocityX
        val releaseTargetIndex = resolveLiquidGlassDockReleaseTargetIndex(
            currentValue = desiredValue,
            velocityPxPerSecond = velocityX,
            itemWidthPx = itemWidthPx,
            itemCount = itemCount,
            dragSpec = dragSpec
        )
        targetIndex = releaseTargetIndex
        desiredValue = releaseTargetIndex.toFloat()
        animateToValue(releaseTargetIndex.toFloat()) {
            if (generation == motionGeneration) {
                velocityPxPerSecond = 0f
                onIndexChanged(releaseTargetIndex)
            }
        }
        offsetJob?.cancel()
        val startOffset = panelOffset
        offsetJob = scope.launch {
            offsetAnimation.snapTo(startOffset)
            offsetAnimation.animateTo(0f, offsetSnapAnimationSpec) {
                panelOffset = value
            }
        }
    }

    fun updateIndex(index: Int) {
        if (disposed || isDragging || itemCount <= 0) return
        val safeIndex = index.coerceIn(0, itemCount - 1)
        if (
            safeIndex == targetIndex &&
            (
                isRunning ||
                    abs(value - safeIndex.toFloat()) < 0.005f
                )
        ) {
            return
        }
        startNewMotion()
        targetIndex = safeIndex
        desiredValue = safeIndex.toFloat()
        velocityPxPerSecond = 0f
        animateToValue(safeIndex.toFloat())
    }

    internal fun revokePendingSelection() { startNewMotion() }

    /** Revoke a measured selector gesture without publishing its old drag destination. */
    fun cancelInteractionTo(index: Int) {
        if (disposed || itemCount <= 0) return
        startNewMotion()
        isDragging = false
        releaseJob?.cancel()
        offsetJob?.cancel()
        panelOffset = 0f
        velocityPxPerSecond = 0f
        desiredValue = index.coerceIn(0, itemCount - 1).toFloat()
        animateToValue(desiredValue)
    }

    fun dispose() {
        disposed = true
        startNewMotion()
        valueJob?.cancel()
        velocityJob?.cancel()
        pressJob?.cancel()
        releaseJob?.cancel()
        offsetJob?.cancel()
    }
}

private const val REDUCED_MOTION_DURATION_MILLIS = 120

@Composable
internal fun rememberLiquidGlassDockMotionState(
    initialIndex: Int,
    itemCount: Int,
    reduceMotion: Boolean,
    onIndexChanged: (Int) -> Unit
): LiquidGlassDockMotionState {
    val scope = rememberCoroutineScope()
    val currentOnIndexChanged by rememberUpdatedState(onIndexChanged)
    val state = remember(itemCount, reduceMotion) {
        LiquidGlassDockMotionState(
            initialIndex = initialIndex,
            itemCount = itemCount,
            scope = scope,
            onIndexChanged = { currentOnIndexChanged(it) },
            reduceMotion = reduceMotion
        )
    }
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    return state
}

internal fun Modifier.liquidGlassDockHorizontalDrag(
    motionState: LiquidGlassDockMotionState,
    itemWidthPx: Float
): Modifier = pointerInput(motionState, itemWidthPx) {
    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        val down = awaitFirstDown(requireUnconsumed = false)
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            motionState.onDrag(over, itemWidthPx, eventTimeMillis = change.uptimeMillis)
        }

        if (dragStart != null) {
            velocityTracker.addPosition(dragStart.uptimeMillis, dragStart.position)
            var cancelled = false
            try {
                horizontalDrag(dragStart.id) { change ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val dragAmount = change.position.x - change.previousPosition.x
                    motionState.onDrag(
                        dragAmountPx = dragAmount,
                        itemWidthPx = itemWidthPx,
                        gestureVelocityPxPerSecond = velocityTracker.calculateVelocity().x,
                        eventTimeMillis = change.uptimeMillis
                    )
                }
            } catch (_: Exception) {
                cancelled = true
            }
            motionState.onDragEnd(
                velocityX = if (cancelled) 0f else velocityTracker.calculateVelocity().x,
                itemWidthPx = itemWidthPx
            )
        }
    }
}
