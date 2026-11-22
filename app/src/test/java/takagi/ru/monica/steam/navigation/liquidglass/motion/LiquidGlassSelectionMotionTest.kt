package takagi.ru.monica.steam.navigation.liquidglass.motion

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiquidGlassSelectionMotionTest {
    private fun TestScope.frameScope() = CoroutineScope(coroutineContext + object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(testScheduler.currentTime * 1_000_000)
        }
    })
    private val slots = (0..4).map { LiquidGlassSelectionTarget(it, Rect(it * 100f, 0f, (it + 1) * 100f, 56f)) }

    @Test fun selectorMatchesDockOnEveryFrameOfTapTravelAndRapidReversal() = runTest {
        val scope = frameScope()
        val releaseFrame: suspend () -> Unit = { withFrameNanos { } }
        val callbacks = mutableListOf<Int>()
        val row = LiquidGlassSelectionMotion(scope, callbacks::add, releaseFrame)
        val dock = LiquidGlassDockMotionState(0, 5, scope, {}, false, awaitReleaseFrame = releaseFrame)
        row.synchronize(0, slots, true, false)
        val token = requireNotNull(row.press(Offset(250f, 28f), 0, true))
        dock.setPressed(true)
        repeat(12) {
            advanceTimeBy(16); runCurrent()
            assertEquals(50f, row.bounds.center.x, .001f)
            assertEquals(dock.pressProgress, row.pressProgress, .0001f)
        }
        row.release(token, true); dock.setPressed(false)
        row.selectFromClick(2); row.synchronize(2, slots, true, false); dock.updateIndex(2)
        repeat(70) { frame ->
            if (frame == 5) {
                row.selectFromClick(4); row.synchronize(4, slots, true, false); dock.updateIndex(4)
            }
            if (frame == 10) {
                row.selectFromClick(0); row.synchronize(0, slots, true, false); dock.updateIndex(0)
            }
            advanceTimeBy(16); runCurrent()
            assertEquals("travel frame $frame", dock.value, (row.bounds.center.x - 50f) / 100f, .0001f)
            assertEquals("press frame $frame", dock.pressProgress, row.pressProgress, .0001f)
            assertEquals(dock.deformationVelocityItemsPerSecond, row.deformationVelocity, .0001f)
        }
        assertEquals(listOf(2, 4, 0), callbacks)
        row.dispose(); dock.dispose()
    }

    @Test fun heldAndCancelledDragUsesDockVelocityAndCannotCommit() = runTest {
        val scope = frameScope()
        val releaseFrame: suspend () -> Unit = { withFrameNanos { } }
        val selected = mutableListOf<Int>()
        val row = LiquidGlassSelectionMotion(scope, selected::add, releaseFrame)
        val dock = LiquidGlassDockMotionState(0, 5, scope, {}, false, awaitReleaseFrame = releaseFrame)
        row.synchronize(0, slots, true, false)
        val token = requireNotNull(row.press(Offset(50f, 28f), 0))
        dock.setPressed(true)
        repeat(10) { i ->
            row.dragTo(token, 50f + (i + 1) * 10f, (i + 1) * 16L)
            dock.onDrag(.1f, 1f, 6.25f, (i + 1) * 16L)
            advanceTimeBy(16); runCurrent()
            assertEquals(dock.value, (row.bounds.center.x - 50f) / 100f, .0001f)
            assertEquals(dock.deformationVelocityItemsPerSecond, row.deformationVelocity, .0001f)
        }
        row.release(token, true)
        advanceTimeBy(1000); runCurrent()
        assertEquals(slots.first().bounds, row.bounds)
        assertTrue(selected.isEmpty())
        row.dispose(); dock.dispose()
    }

    @Test fun childClickBeforeParentReleaseCannotLeaveCurrentLensHeld() = runTest {
        val calls = mutableListOf<Int>()
        val row = LiquidGlassSelectionMotion(frameScope(), calls::add) { withFrameNanos { } }
        row.synchronize(0, slots, true, false)
        val token = requireNotNull(row.press(Offset(50f, 28f), 0))
        advanceTimeBy(300); runCurrent()
        assertTrue(row.pressProgress > .9f)
        row.selectFromClick(0)
        row.release(token, true)
        advanceTimeBy(1000); runCurrent()
        assertEquals(0f, row.pressProgress, .001f)
        assertEquals(listOf(0), calls)
        row.dispose()
    }

    @Test fun measuredWidthsAndPhysicalRtlInterpolateWithoutResizingTheLabels() {
        val measured = listOf(LiquidGlassSelectionTarget("second", Rect(0f, 0f, 80f, 48f)),
            LiquidGlassSelectionTarget("first", Rect(80f, 0f, 200f, 48f)))
        assertEquals(Rect(40f, 0f, 140f, 48f), selectionBoundsAtIndex(measured, .5f))
        assertEquals(measured[1].bounds, selectionBoundsAtIndex(measured, 1f))
    }

    @Test fun reflowStopAndStyleChangeRevokeOldGesture() = runTest {
        for (change in 0..2) {
            val callbacks = mutableListOf<Int>()
            val row = LiquidGlassSelectionMotion(frameScope(), callbacks::add) { withFrameNanos { } }
            row.synchronize(0, slots, true, false)
            val token = requireNotNull(row.press(Offset(50f, 28f), 0))
            row.dragTo(token, 150f, 100)
            val updated = if (change == 0) slots.map { it.copy(bounds = it.bounds.translate(12f, 0f)) } else slots
            row.synchronize(0, updated, change != 1, change == 2)
            row.release(token, false)
            advanceTimeBy(1000); runCurrent()
            assertEquals(updated[0].bounds, row.bounds)
            assertTrue(callbacks.isEmpty())
            assertEquals(0f, row.pressProgress, 0f)
            row.dispose()
        }
    }

    @Test fun reducedClickAndDragKeepCallbacksAndSkipAnimation() = runTest {
        val callbacks = mutableListOf<Int>()
        val row = LiquidGlassSelectionMotion(this, callbacks::add)
        row.synchronize(0, slots, true, true)
        row.selectFromClick(1); row.synchronize(1, slots, true, true)
        assertEquals(slots[1].bounds, row.bounds)
        val token = requireNotNull(row.press(Offset(150f, 28f), 0))
        row.dragTo(token, 250f, 100); row.release(token, false)
        assertEquals(listOf(1, 2), callbacks)
        assertEquals(0f, row.pressProgress, 0f)
        row.dispose()
    }

    @Test fun externalSelectionAndDisposeSuppressPendingDragSelection() = runTest {
        val callbacks = mutableListOf<Int>()
        val row = LiquidGlassSelectionMotion(frameScope(), callbacks::add) { withFrameNanos { } }
        row.synchronize(0, slots, true, false)
        val token = requireNotNull(row.press(Offset(50f, 28f), 0))
        row.dragTo(token, 150f, 100); row.release(token, false)
        row.synchronize(4, slots, true, false)
        advanceTimeBy(1000); runCurrent()
        assertEquals(slots[4].bounds, row.bounds)
        assertTrue(callbacks.isEmpty())
        row.dispose()
        row.release(token, false)
        advanceTimeBy(1000); runCurrent()
        assertTrue(callbacks.isEmpty())
    }
}
