package takagi.ru.monica.steam.navigation.liquidglass.motion

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.floor

internal data class LiquidGlassSelectionTarget<T : Any>(val key: T, val bounds: Rect)

/** Map Dock index space to measured physical slots, including unequal widths and RTL. */
internal fun <T : Any> selectionBoundsAtIndex(targets: List<LiquidGlassSelectionTarget<T>>, index: Float): Rect {
    if (targets.isEmpty()) return Rect.Zero
    val position = index.coerceIn(0f, targets.lastIndex.toFloat())
    val lower = floor(position).toInt()
    val a = targets[lower].bounds
    val b = targets[(lower + 1).coerceAtMost(targets.lastIndex)].bounds
    val t = position - lower
    fun mix(a: Float, b: Float) = a + (b - a) * t
    return Rect(mix(a.left, b.left), mix(a.top, b.top), mix(a.right, b.right), mix(a.bottom, b.bottom))
}

/** Geometry/lifecycle adapter only. All animated values come from the actual Dock engine. */
internal class LiquidGlassSelectionMotion<T : Any>(
    private val scope: CoroutineScope,
    private val onSelected: (T) -> Unit,
    private val awaitReleaseFrame: suspend () -> Unit = { awaitFrame() }
) {
    private var engine by mutableStateOf<LiquidGlassDockMotionState?>(null)
    private var restingBounds by mutableStateOf(Rect.Zero)
    private var targets = emptyList<LiquidGlassSelectionTarget<T>>()
    private var selected: T? = null
    private var enabled = false
    private var reduced = false
    private var disposed = false
    private var generation = 0
    private var edgeJob: Job? = null
    private var lastTime = 0L
    private var grabOffset = 0f
    private var dragIndex = 0f
    private var reducedDragging = false
    var pointerX = 0f
        private set

    val bounds: Rect get() = engine?.let { selectionBoundsAtIndex(targets, it.value) } ?: restingBounds
    val dragging: Boolean get() = engine?.isDragging ?: reducedDragging
    val pressProgress: Float get() = engine?.pressProgress ?: 0f
    val deformationVelocity: Float get() = engine?.deformationVelocityItemsPerSecond ?: 0f

    fun synchronize(value: T, slots: List<LiquidGlassSelectionTarget<T>>, active: Boolean, reduceMotion: Boolean) {
        val valid = slots.filter { it.bounds.width > 0f && it.bounds.height > 0f &&
            it.bounds.left.isFinite() && it.bounds.right.isFinite() &&
            it.bounds.top.isFinite() && it.bounds.bottom.isFinite() }.sortedBy { it.bounds.center.x }
        if (disposed || (value == selected && valid == targets && active == enabled && reduceMotion == reduced)) return
        val reconfigure = valid != targets || active != enabled || reduceMotion != reduced
        selected = value
        enabled = active
        reduced = reduceMotion
        if (reconfigure) {
            retire()
            engine?.dispose()
            engine = null
            targets = valid
            val index = valid.indexOfFirst { it.key == value }
            restingBounds = valid.getOrNull(index)?.bounds ?: Rect.Zero
            if (active && !reduceMotion && index >= 0) {
                engine = LiquidGlassDockMotionState(index, valid.size, scope, { next ->
                    if (enabled && !disposed) targets.getOrNull(next)?.let {
                        if (it.key != selected) onSelected(it.key)
                    }
                }, false, awaitReleaseFrame = awaitReleaseFrame)
            }
        } else {
            val index = targets.indexOfFirst { it.key == value }
            if (index < 0) return
            // A matching click recomposition must not restart the moving Dock lens.
            if (engine?.targetIndex != index || dragging) {
                retire()
                if (engine?.isDragging == true) engine?.cancelInteractionTo(index)
                else engine?.updateIndex(index)
            }
            restingBounds = targets[index].bounds
        }
    }

    fun press(position: Offset, timeMillis: Long, fromAnyOption: Boolean = false): Int? {
        if (!enabled || disposed || bounds.width <= 0f) return null
        val touched = targets.firstOrNull { it.bounds.contains(position) }
        if (!bounds.contains(position) && (!fromAnyOption || touched == null)) return null
        retire()
        pointerX = position.x
        grabOffset = position.x - (touched?.bounds ?: bounds).center.x
        dragIndex = engine?.value ?: indexAt(bounds.center.x)
        lastTime = timeMillis
        // Like Dock, down lifts the current selection; click commits and starts travel.
        engine?.setPressed(true)
        return generation
    }

    private fun indexAt(x: Float): Float {
        if (targets.size < 2) return 0f
        val upper = targets.indexOfFirst { it.bounds.center.x >= x }
        if (upper == 0) return 0f
        if (upper < 0) return targets.lastIndex.toFloat()
        val a = targets[upper - 1].bounds.center.x
        val b = targets[upper].bounds.center.x
        return upper - 1 + ((x - a) / (b - a)).coerceIn(0f, 1f)
    }

    fun dragTo(token: Int, positionX: Float, timeMillis: Long) {
        if (!isCurrent(token)) return
        pointerX = positionX
        val nextIndex = indexAt(positionX - grabOffset)
        val delta = nextIndex - dragIndex
        val velocity = delta / ((timeMillis - lastTime).coerceAtLeast(1L) / 1000f)
        engine?.onDrag(delta, 1f, velocity, timeMillis)
        if (reduced) {
            reducedDragging = true
            restingBounds = selectionBoundsAtIndex(targets, nextIndex)
        }
        dragIndex = nextIndex
        lastTime = timeMillis
    }

    fun compensateScroll(token: Int, physicalPixels: Float, timeMillis: Long) {
        if (isCurrent(token) && dragging) dragTo(token, pointerX + physicalPixels, timeMillis)
    }

    fun release(token: Int, cancelled: Boolean, velocityX: Float = 0f) {
        if (!isCurrent(token)) return
        stopEdgeScroll()
        val index = targets.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        if (cancelled) {
            if (dragging) engine?.cancelInteractionTo(index) else engine?.setPressed(false)
            if (reduced) restingBounds = targets.getOrNull(index)?.bounds ?: Rect.Zero
        } else if (dragging) {
            val physicalWidth = bounds.width.coerceAtLeast(1f)
            if (reduced) {
                val next = resolveLiquidGlassDockReleaseTargetIndex(dragIndex, velocityX, physicalWidth, targets.size)
                restingBounds = targets[next].bounds
                if (targets[next].key != selected) onSelected(targets[next].key)
            } else engine?.onDragEnd(velocityX / physicalWidth, 1f)
        } else engine?.setPressed(false)
        reducedDragging = false
    }

    fun selectFromClick(value: T) {
        if (disposed) return
        val index = targets.indexOfFirst { it.key == value }
        retire()
        // Click may arrive before the parent's up/cancel handler. Retiring its token
        // must still release the Dock press, including a tap on the current selection.
        engine?.setPressed(false)
        if (index >= 0) {
            if (engine?.isDragging == true) engine?.cancelInteractionTo(index)
            else engine?.updateIndex(index)
            restingBounds = targets[index].bounds
        }
        onSelected(value)
    }

    fun isCurrent(token: Int): Boolean = !disposed && enabled && token == generation
    fun startEdgeScroll(token: Int, block: suspend () -> Unit) {
        if (isCurrent(token) && dragging && edgeJob?.isActive != true) edgeJob = scope.launch { block() }
    }
    fun stopEdgeScroll() { edgeJob?.cancel(); edgeJob = null }
    private fun retire() { generation++; engine?.revokePendingSelection(); stopEdgeScroll(); reducedDragging = false }
    fun dispose() { disposed = true; retire(); engine?.dispose(); engine = null }
}
