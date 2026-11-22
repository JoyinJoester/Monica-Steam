package takagi.ru.monica.steam.space.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SteamSpaceHeaderScrollBehaviorTest {
    @Test fun upwardDragConsumesOnlyTheRemainingCollapseHeight() {
        val first = consumeSteamSpaceHeaderScroll(0f, 160f, -100f, canExpand = true)
        assertEquals(.625f, first.fraction, .0001f)
        assertEquals(-100f, first.consumedY, .0001f)
        val second = consumeSteamSpaceHeaderScroll(first.fraction, 160f, -100f, canExpand = false)
        assertEquals(1f, second.fraction, 0f)
        assertEquals(-60f, second.consumedY, .0001f)
        assertEquals("the remaining 40 px can scroll the feed", -40f, -100f - second.consumedY, .0001f)
    }

    @Test fun downwardDragCannotExpandUntilFeedReachesTheTop() {
        val beforeTop = consumeSteamSpaceHeaderScroll(1f, 160f, 90f, canExpand = false)
        assertEquals(1f, beforeTop.fraction, 0f)
        assertEquals(0f, beforeTop.consumedY, 0f)
        val atTop = consumeSteamSpaceHeaderScroll(1f, 160f, 90f, canExpand = true)
        assertEquals(.4375f, atTop.fraction, .0001f)
        assertEquals(90f, atTop.consumedY, .0001f)
    }

    @Test fun excessDownwardMovementRemainsAvailableForPullToRefresh() {
        val result = consumeSteamSpaceHeaderScroll(.5f, 160f, 140f, canExpand = true)
        assertEquals(0f, result.fraction, 0f)
        assertEquals(80f, result.consumedY, .0001f)
        assertEquals(60f, 140f - result.consumedY, .0001f)
    }

    @Test fun progressAndConsumptionRemainBoundedAcrossMixedGestures() {
        var fraction = 0f
        listOf(-5f, -43f, 12f, -250f, 30f, 600f, -80f, 20f).forEach { delta ->
            val result = consumeSteamSpaceHeaderScroll(fraction, 173f, delta, canExpand = true)
            assertTrue(result.fraction in 0f..1f)
            assertTrue(kotlin.math.abs(result.consumedY) <= kotlin.math.abs(delta) + .001f)
            assertTrue(result.consumedY == 0f || result.consumedY * delta >= 0f)
            assertEquals(result.consumedY, (fraction - result.fraction) * 173f, .001f)
            fraction = result.fraction
        }
    }

    @Test fun noHeaderRangeAndInvalidDeltasNeverConsumeFeedMovement() {
        assertEquals(SteamSpaceHeaderScrollResult(.5f, 0f), consumeSteamSpaceHeaderScroll(.5f, 0f, -10f, true))
        assertEquals(SteamSpaceHeaderScrollResult(.5f, 0f), consumeSteamSpaceHeaderScroll(.5f, Float.NaN, -10f, true))
        assertEquals(SteamSpaceHeaderScrollResult(.5f, 0f), consumeSteamSpaceHeaderScroll(.5f, 100f, Float.NaN, true))
    }

    @Test fun settleUsesDirectionOrNearestEndpointWithoutReopeningAboveScrolledFeed() {
        assertEquals(0f, steamSpaceHeaderSettleTarget(.49f, 0f, true), 0f)
        assertEquals(1f, steamSpaceHeaderSettleTarget(.51f, 0f, true), 0f)
        assertEquals(1f, steamSpaceHeaderSettleTarget(.1f, -900f, true), 0f)
        assertEquals(0f, steamSpaceHeaderSettleTarget(.9f, 900f, true), 0f)
        assertEquals(1f, steamSpaceHeaderSettleTarget(.1f, 900f, false), 0f)
    }

    @Test fun horizontalMovementDoesNotFoldHeaderAndDisabledHeaderConsumesNothing() = runTest {
        val behavior = SteamSpaceHeaderScrollBehavior(backgroundScope) { true }
        behavior.configure(true, true)
        behavior.updateRange(220, 56)
        assertEquals(Offset.Zero, behavior.nestedScrollConnection.onPreScroll(Offset(80f, -5f), NestedScrollSource.UserInput))
        assertEquals(0f, behavior.collapsedFraction, 0f)
        behavior.nestedScrollConnection.onPreScroll(Offset(0f, -82f), NestedScrollSource.UserInput)
        assertEquals(.5f, behavior.collapsedFraction, .0001f)
        behavior.configure(false, true)
        assertEquals(0f, behavior.collapsedFraction, 0f)
        assertEquals(Offset.Zero, behavior.nestedScrollConnection.onPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput))
    }

    @Test fun largerNaturalFontHeightKeepsProgressButUpdatesPixelConsumption() = runTest {
        val behavior = SteamSpaceHeaderScrollBehavior(backgroundScope) { true }
        behavior.configure(true, true)
        behavior.updateRange(216, 56)
        behavior.nestedScrollConnection.onPreScroll(Offset(0f, -80f), NestedScrollSource.UserInput)
        behavior.updateRange(296, 56)
        assertEquals(.5f, behavior.collapsedFraction, .0001f)
        val consumed = behavior.nestedScrollConnection.onPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput)
        assertEquals(-120f, consumed.y, .0001f)
        assertEquals(1f, behavior.collapsedFraction, 0f)
    }

    @Test fun unchangedNaturalMeasurementDoesNotInvalidateItsOwnLayout() = runTest {
        val behavior = SteamSpaceHeaderScrollBehavior(backgroundScope) { true }
        behavior.updateRange(216, 56)
        Snapshot.sendApplyNotifications()
        var invalidations = 0
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Unit, { _: Unit -> invalidations++ }) { behavior.collapseRangePx }
            repeat(100) { behavior.updateRange(216, 56) }
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)
            behavior.updateRange(296, 56)
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test fun reducedMotionSettlesImmediatelyAndPostScrollExpandsOnlyAtTop() = runTest {
        var atTop = false
        val behavior = SteamSpaceHeaderScrollBehavior(backgroundScope) { atTop }
        behavior.configure(true, true)
        behavior.updateRange(216, 56)
        behavior.nestedScrollConnection.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, behavior.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput))
        atTop = true
        val expanded = behavior.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput)
        assertEquals(30f, expanded.y, 0f)
        assertEquals(.4375f, behavior.collapsedFraction, .0001f)
        behavior.nestedScrollConnection.onPreFling(Velocity.Zero)
        assertEquals(Velocity.Zero, behavior.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity.Zero))
        assertEquals(0f, behavior.collapsedFraction, 0f)
    }
}
