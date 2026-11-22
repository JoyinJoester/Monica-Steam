package takagi.ru.monica.steam.space.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import takagi.ru.monica.ui.LocalReduceAnimations

/** Attach to the feed LazyColumn inside PullToRefreshBox, so expansion consumes its top remainder. */
@Composable
internal fun rememberSteamSpaceHeaderScrollBehavior(
    feedState: LazyListState,
    enabled: Boolean = true,
    reduceAnimations: Boolean = LocalReduceAnimations.current
): SteamSpaceHeaderScrollBehavior {
    val scope = rememberCoroutineScope()
    val behavior = remember(feedState, scope) {
        SteamSpaceHeaderScrollBehavior(scope, canExpand = { !feedState.canScrollBackward })
    }
    SideEffect { behavior.configure(enabled, reduceAnimations) }
    DisposableEffect(behavior) { onDispose { behavior.cancelSettle() } }
    return behavior
}

/** One progress value owns height, avatar scale and text visibility. Dragging never starts an animation. */
@Stable
internal class SteamSpaceHeaderScrollBehavior(
    private val scope: CoroutineScope,
    private val canExpand: () -> Boolean
) {
    var collapsedFraction by mutableFloatStateOf(0f)
        private set
    var collapseRangePx by mutableFloatStateOf(0f)
        private set
    private var enabled = true
    private var reduceAnimations = false
    private var settleJob: Job? = null
    private var flingVelocityY = 0f

    internal fun configure(enabled: Boolean, reduceAnimations: Boolean) {
        this.enabled = enabled
        this.reduceAnimations = reduceAnimations
        if (!enabled) {
            cancelSettle()
            collapsedFraction = 0f
            collapseRangePx = 0f
        }
    }

    /** The expanded content is measured naturally, including the user's font scale. */
    internal fun updateRange(expandedHeightPx: Int, collapsedHeightPx: Int) {
        val measuredRange = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(0).toFloat()
        if (collapseRangePx != measuredRange) collapseRangePx = measuredRange
    }

    internal fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
    }

    private fun consume(delta: Offset, source: NestedScrollSource): Offset {
        if (!enabled || abs(delta.x) > abs(delta.y)) return Offset.Zero
        if (source == NestedScrollSource.UserInput) cancelSettle()
        val result = consumeSteamSpaceHeaderScroll(collapsedFraction, collapseRangePx, delta.y, canExpand())
        collapsedFraction = result.fraction
        return Offset(0f, result.consumedY)
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = consume(available, source)

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f) consume(available, source) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            cancelSettle()
            flingVelocityY = if (abs(available.y) >= abs(available.x)) available.y else 0f
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!enabled || collapseRangePx <= 0f || collapsedFraction == 0f || collapsedFraction == 1f) return Velocity.Zero
            val target = steamSpaceHeaderSettleTarget(collapsedFraction, flingVelocityY, canExpand())
            flingVelocityY = 0f
            if (reduceAnimations) collapsedFraction = target
            else {
                val job = scope.launch {
                    animate(collapsedFraction, target, animationSpec = tween(220, easing = FastOutSlowInEasing)) { value, _ ->
                        collapsedFraction = value.coerceIn(0f, 1f)
                    }
                }
                settleJob = job
                job.join()
                if (settleJob === job) settleJob = null
            }
            // Keep unconsumed velocity available to the pull-to-refresh parent.
            return Velocity.Zero
        }
    }
}

internal data class SteamSpaceHeaderScrollResult(val fraction: Float, val consumedY: Float)

internal fun consumeSteamSpaceHeaderScroll(
    fraction: Float,
    rangePx: Float,
    deltaY: Float,
    canExpand: Boolean
): SteamSpaceHeaderScrollResult {
    val current = fraction.coerceIn(0f, 1f)
    if (rangePx <= 0f || !rangePx.isFinite() || !deltaY.isFinite() || deltaY == 0f || (deltaY > 0f && !canExpand)) {
        return SteamSpaceHeaderScrollResult(current, 0f)
    }
    val before = current * rangePx
    val after = (before - deltaY).coerceIn(0f, rangePx)
    return SteamSpaceHeaderScrollResult(after / rangePx, before - after)
}

internal fun steamSpaceHeaderSettleTarget(fraction: Float, velocityY: Float, canExpand: Boolean): Float = when {
    !canExpand -> 1f
    velocityY < -600f -> 1f
    velocityY > 600f -> 0f
    fraction >= .5f -> 1f
    else -> 0f
}
