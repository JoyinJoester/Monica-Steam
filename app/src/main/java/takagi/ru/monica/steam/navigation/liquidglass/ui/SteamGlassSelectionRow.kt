package takagi.ru.monica.steam.navigation.liquidglass.ui

import android.os.SystemClock
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.motion.LiquidGlassSelectionMotion
import takagi.ru.monica.steam.navigation.liquidglass.motion.LiquidGlassSelectionTarget
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamCombinedBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mutually exclusive tabs. Dragging starts on the current glass block; other starts can scroll. */
@Composable
internal fun <T : Any> SteamGlassSelectionRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    dragFromAnyOption: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    surfaceBackdrop: Backdrop? = null,
    indicatorInset: Dp = 0.dp,
    content: @Composable RowScope.(T, Boolean, Modifier, () -> Unit) -> Unit
) {
    val indicatorInsetPx = with(LocalDensity.current) { indicatorInset.toPx() }
    // Shrink only the drawn lens; labels, touch targets and drag destinations keep their bounds.
    fun indicatorBounds(rect: Rect): Rect {
        val inset = indicatorInsetPx.coerceIn(0f,
            ((minOf(rect.width, rect.height) - 1f) / 2f).coerceAtLeast(0f))
        return rect.inflate(-inset)
    }
    val positions = remember(options) { mutableStateMapOf<T, Rect>() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val currentSelect = rememberUpdatedState(onSelect)
    val motion = remember(scope) { LiquidGlassSelectionMotion<T>(scope, { currentSelect.value(it) }) }
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val pageBackdrop = LocalSteamGlassBackdrop.current?.takeIf { steamGlassSupported() }
    val labelsBackdrop = rememberLayerBackdrop()
    val lensBackdrop = pageBackdrop?.let {
        val base = surfaceBackdrop?.let { surface -> rememberSteamCombinedBackdrop(it.delegate, surface) } ?: it.delegate
        rememberSteamCombinedBackdrop(base, labelsBackdrop)
    }
    val highlights = rememberSteamGlassHighlights(lensBackdrop != null)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    DisposableEffect(pageBackdrop) {
        pageBackdrop?.let { it.consumers++ }
        onDispose { pageBackdrop?.let { it.consumers-- } }
    }
    val reduced = LocalReduceAnimations.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var viewportWidth by remember { mutableIntStateOf(0) }
    val slots = options.mapNotNull { key -> positions[key]?.let { LiquidGlassSelectionTarget(key, it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val active = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val synchronize = rememberUpdatedState {
        motion.synchronize(selected, slots,
            liquid && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), reduced)
    }
    SideEffect { synchronize.value() }
    DisposableEffect(lifecycle, motion) {
        // Cached tabs remain composed. Revoke pending releases and edge scroll immediately
        // at ON_STOP, including when Activity recomposition is paused in the background.
        val observer = LifecycleEventObserver { _, _ -> synchronize.value() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(motion) { onDispose { motion.dispose() } }
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    val scrollSpeed = with(LocalDensity.current) { 320.dp.toPx() }
    fun visibleStart(): Float = if (rtl) (scroll.maxValue - scroll.value).toFloat() else scroll.value.toFloat()
    fun edgeDirection(): Float {
        val local = motion.pointerX - visibleStart()
        val direction = when {
            local < edge -> -1f
            local > viewportWidth - edge -> 1f
            else -> 0f
        }
        val amount = direction * if (rtl) -1f else 1f
        return if ((amount > 0 && scroll.canScrollForward) || (amount < 0 && scroll.canScrollBackward)) direction else 0f
    }
    fun updateEdgeScroll(token: Int) {
        if (!scrollable || edgeDirection() == 0f) {
            motion.stopEdgeScroll()
            return
        }
        motion.startEdgeScroll(token) {
            scroll.scroll(MutatePriority.UserInput) {
                var previous = withFrameNanos { it }
                while (motion.isCurrent(token) && motion.dragging) {
                    val now = withFrameNanos { it }
                    val direction = edgeDirection()
                    if (direction == 0f) break
                    val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, .05f)
                    previous = now
                    val sign = if (rtl) -1f else 1f
                    val physical = scrollBy(direction * sign * scrollSpeed * seconds) * sign
                    if (abs(physical) < .01f) break
                    motion.compensateScroll(token, physical, SystemClock.uptimeMillis())
                }
            }
        }
    }
    val input = if (!liquid || !active) Modifier else Modifier.pointerInput(
        motion, selected, rtl, scrollable, dragFromAnyOption, viewportWidth, edge, scrollSpeed
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val token = motion.press(down.position, down.uptimeMillis, dragFromAnyOption) ?: return@awaitEachGesture
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            try {
                val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    val delta = change.position - down.position
                    if (abs(delta.x) >= abs(delta.y)) {
                        change.consume()
                        motion.dragTo(token, change.position.x, change.uptimeMillis)
                        updateEdgeScroll(token)
                    }
                }
                if (start == null) {
                    motion.release(token, cancelled = true)
                    synchronize.value()
                    return@awaitEachGesture
                }
                velocity.addPosition(start.uptimeMillis, start.position)
                val complete = horizontalDrag(start.id) { change ->
                    change.consume()
                    velocity.addPosition(change.uptimeMillis, change.position)
                    motion.dragTo(token, change.position.x, change.uptimeMillis)
                    updateEdgeScroll(token)
                }
                currentEvent.changes.firstOrNull { it.id == start.id }?.let {
                    velocity.addPosition(it.uptimeMillis, it.position)
                }
                motion.release(token, cancelled = !complete, velocityX = velocity.calculateVelocity().x)
            } catch (cancelled: CancellationException) {
                motion.release(token, cancelled = true)
                throw cancelled
            }
        }
    }
    val dragScaleProgress = rememberIndicatorDragScaleProgress(motion.dragging, reduced)
    Box(modifier.onSizeChanged { viewportWidth = it.width }) {
        if (liquid) {
            Box(Modifier.matchParentSize().layout { measurable, constraints ->
                val rect = indicatorBounds(motion.bounds)
                val width = rect.width.roundToInt().coerceAtLeast(1)
                val height = rect.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((rect.left - visibleStart()).roundToInt(), rect.top.roundToInt())
                }
            }.steamGlassSelectionLens(lensBackdrop, indicatorColor, highlights, dark, layerBlock = {
                val transform = resolveIndicatorTransform(
                    maxOf(dragScaleProgress.value, motion.pressProgress), motion.deformationVelocity)
                scaleX = transform.scaleX
                scaleY = transform.scaleY
                alpha = if (motion.bounds.width > 0f) 1f else 0f
            }) { motion.pressProgress }
                // Draw over the recorded labels, just like the Dock's content lens.
                // Without a source, keep the solid fallback underneath the real labels.
                .zIndex(if (lensBackdrop != null) 2f else 0f)
                .testTag("steam-glass-selection-lens"))
        }
        if (liquid && lensBackdrop != null) {
            // Keep each selected content's colors: tinting this entire layer would paint
            // badge numerals and their backgrounds identically. The Dock's per-slot scale
            // and moving content lens still apply to the complete captured content.
            Row(Modifier.matchParentSize().clearAndSetSemantics {}
                .liquidGlassCaptureLayer().layerBackdrop(labelsBackdrop)
                .then(if (scrollable) Modifier.horizontalScroll(scroll, enabled = false) else Modifier),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = Alignment.CenterVertically) {
                options.forEach { key ->
                    content(key, true, Modifier.graphicsLayer {
                        val scale = lerp(1f, 1.2f, motion.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }) {}
                }
            }
        }
        Row(
            modifier = (if (scrollable) Modifier.horizontalScroll(scroll) else Modifier)
                .selectableGroup()
                .then(input)
                .then(if (liquid) Modifier else Modifier.steamSelectionIndicator(
                    positions[selected]?.let { indicatorBounds(it) }, indicatorColor))
                .zIndex(1f),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { key ->
                content(key, key == selected, Modifier.onPlaced { coordinates ->
                    val origin = coordinates.positionInParent()
                    val rect = Rect(origin.x, origin.y, origin.x + coordinates.size.width,
                        origin.y + coordinates.size.height)
                    if (positions[key] != rect) positions[key] = rect
                }) { motion.selectFromClick(key) }
            }
        }
    }
}
