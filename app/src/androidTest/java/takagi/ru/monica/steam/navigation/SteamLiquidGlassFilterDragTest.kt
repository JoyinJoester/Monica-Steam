package takagi.ru.monica.steam.navigation

import android.os.SystemClock
import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFilter
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFilters
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationListEntry
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationType
import takagi.ru.monica.steam.friends.chat.ui.filterSteamConversationEntries
import takagi.ru.monica.steam.friends.chat.ui.labelRes
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.ui.LocalReduceAnimations

/** Real pointer input through the inbox filter; selection assertions use the public tab semantics. */
class SteamLiquidGlassFilterDragTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val selected = mutableStateOf(SteamConversationFilter.ALL)
    private val changes = mutableListOf<SteamConversationFilter>()
    private val style = mutableStateOf(SteamDockStyle.LIQUID_GLASS)
    private val reduced = mutableStateOf(false)
    private val large = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val unread = mutableStateOf(3)
    private val visible = mutableStateOf(true)
    private val page = mutableStateOf("filters")
    private val frostBackground = mutableStateOf(Color.Black)
    private var filterBounds = Rect.Zero
    private var parentScroll: ScrollState? = null
    private val entries = listOf(
        SteamConversationListEntry(SteamConversationType.DIRECT, "unread-direct", title = "Unread friend",
            subtitle = "Synthetic message", timestamp = 3L, unreadCount = 3, pinned = true),
        SteamConversationListEntry(SteamConversationType.DIRECT, "read-direct", title = "Read friend",
            subtitle = "Synthetic message", timestamp = 2L, unreadCount = 0, pinned = false),
        SteamConversationListEntry(SteamConversationType.GROUP, "unread-group", title = "Unread group",
            subtitle = "Synthetic message", timestamp = 1L, unreadCount = 2, pinned = false),
        SteamConversationListEntry(SteamConversationType.DIRECT, "archived", title = "Archived friend",
            subtitle = "Synthetic message", timestamp = 1L, unreadCount = 0, pinned = false, archived = true)
    )

    private fun mount(verticalParent: Boolean = false, flatDark: Boolean = false, finePattern: Boolean = false) {
        rule.setContent {
            MaterialTheme(colorScheme = if (flatDark) darkColorScheme() else lightColorScheme()) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalSteamDesignStyle provides style.value,
                    LocalReduceAnimations provides reduced.value,
                    LocalLayoutDirection provides direction.value,
                    LocalDensity provides Density(density.density, if (large.value) 1.5f else 1f)) {
                    SteamGlassHost(Modifier.fillMaxSize(), content = {
                        Canvas(Modifier.fillMaxSize()) {
                            if (finePattern) {
                                val stripe = 2.dp.toPx()
                                for (x in 0..(size.width / stripe).toInt()) drawRect(
                                    if (x % 2 == 0) Color.White else frostBackground.value,
                                    Offset(x * stripe, 0f), Size(stripe, size.height))
                            } else if (flatDark) drawRect(Color(0xFF101022)) else
                            for (x in 0..15) for (y in 0..25) drawRect(
                                if ((x + y) % 2 == 0) Color(0xFF93C5FD) else Color(0xFFFFDCA8),
                                Offset(x * size.width / 16f, y * size.height / 26f),
                                Size(size.width / 16f, size.height / 26f))
                        }
                    }, overlay = {
                        val scroll = rememberScrollState()
                        parentScroll = scroll
                        SteamTabPageHost(page.value, setOf("filters", "other"), true,
                            transitionSpec = { _, _ -> steamPageTransition(true) }) { current ->
                        if (current == "other") Text("Other page") else Column(Modifier.fillMaxSize().then(if (verticalParent) Modifier.verticalScroll(scroll) else Modifier)) {
                            Spacer(Modifier.height(64.dp))
                            if (visible.value) SteamConversationFilters(selected.value, {
                                selected.value = it
                                changes += it
                            }, Modifier.width(320.dp).testTag("filters"), unreadCount = unread.value)
                            filterSteamConversationEntries(entries, selected.value).forEach { entry ->
                                Text(entry.title, Modifier.padding(16.dp).testTag(entry.id))
                            }
                            if (verticalParent) Spacer(Modifier.height(1_600.dp))
                        }
                        }
                    })
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        // Activity window fade runs on the platform clock, outside the Compose test clock.
        // Settle it before baseline pixels; otherwise the whole screenshot is initially dim.
        SystemClock.sleep(400)
        filterBounds = rule.onNodeWithTag("filters").fetchSemanticsNode().boundsInRoot
    }

    private fun center(filter: SteamConversationFilter): Offset = rule.onNodeWithText(
        rule.activity.getString(filter.labelRes())
    ).fetchSemanticsNode().boundsInRoot.center

    private inner class Pointer(start: Offset) {
        private val origin = IntArray(2)
        private val down = SystemClock.uptimeMillis()
        private var position = start
        init {
            rule.runOnUiThread {
                rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin)
            }
            send(MotionEvent.ACTION_DOWN)
        }
        private fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                position.x + origin[0], position.y + origin[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                check(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        fun moveTo(destination: Offset, steps: Int = 32) {
            val start = position
            repeat(steps) { step ->
                SystemClock.sleep(16)
                position = start + (destination - start) * ((step + 1) / steps.toFloat())
                send(MotionEvent.ACTION_MOVE)
                rule.mainClock.advanceTimeBy(16)
            }
        }
        fun finish(cancelled: Boolean = false, delayMillis: Long = 80) {
            SystemClock.sleep(delayMillis)
            send(if (cancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP)
        }
    }

    private fun drag(from: Offset, to: Offset) {
        Pointer(from).apply { moveTo(to); finish() }
    }

    private fun verifySelection(value: SteamConversationFilter, callbacks: List<SteamConversationFilter>) {
        rule.waitForIdle()
        assertEquals(value, rule.runOnIdle { selected.value })
        assertEquals(callbacks, rule.runOnIdle { changes.toList() })
        rule.onAllNodes(isSelected()).assertCountEquals(1)
    }

    private fun capture(name: String): Bitmap {
        val presented = CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { presented.countDown() }
            rule.activity.window.decorView.invalidate()
        }
        assertTrue(presented.await(5, TimeUnit.SECONDS))
        val origin = IntArray(2)
        rule.runOnUiThread {
            rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin)
        }
        val screen = instrumentation.uiAutomation.takeScreenshot()
        // Include the area OUTSIDE the filter container. Cropping to the slot/container
        // accepted a clipped highlight as a floating lens in the previous regression.
        val margin = 32
        val cropped = Bitmap.createBitmap(screen, filterBounds.left.toInt() + origin[0],
            filterBounds.top.toInt() + origin[1] - margin,
            filterBounds.width.toInt(), filterBounds.height.toInt() + margin * 2)
        screen.recycle()
        // The shell's emulated-storage namespace cannot read a secondary user's external files.
        for (root in listOf(rule.activity.getExternalFilesDir(null), rule.activity.filesDir)) {
            val file = File(root, "filter-drag/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return cropped
    }

    private fun difference(a: Bitmap, b: Bitmap): Long {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var value = 0L
        for (x in 0 until a.width) for (y in 0 until a.height) {
            val left = a.getPixel(x, y)
            val right = b.getPixel(x, y)
            for (shift in listOf(0, 8, 16)) value += abs(((left shr shift) and 255) - ((right shr shift) and 255))
        }
        return value
    }

    @Test fun draggingSelectedGlassBlockCommitsUnreadExactlyOnce() {
        mount()
        drag(center(SteamConversationFilter.ALL), center(SteamConversationFilter.UNREAD))
        verifySelection(SteamConversationFilter.UNREAD, listOf(SteamConversationFilter.UNREAD))
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.UNREAD.labelRes())).assertIsSelected()
        rule.onNodeWithText("Read friend").assertDoesNotExist()
        rule.onNodeWithText("Unread friend").assertIsDisplayed()
        rule.onNodeWithText("Unread group").assertIsDisplayed()
    }

    @Test fun cachedPageCancelsPendingReleaseAndCanDragAfterReturn() {
        verifyCachedCancellation(releaseBeforeLeaving = true)
    }

    @Test fun cachedPageCancelsHeldPointerAndCanDragAfterReturn() {
        verifyCachedCancellation(releaseBeforeLeaving = false)
    }

    private fun verifyCachedCancellation(releaseBeforeLeaving: Boolean) {
        mount()
        rule.mainClock.autoAdvance = false
        val start = center(SteamConversationFilter.ALL)
        val destination = center(SteamConversationFilter.UNREAD)
        val pointer = Pointer(start)
        pointer.moveTo(start + (destination - start) * .78f)
        if (releaseBeforeLeaving) pointer.finish()
        rule.runOnUiThread { page.value = "other" }
        rule.mainClock.advanceTimeBy(32)
        if (!releaseBeforeLeaving) pointer.finish()
        rule.mainClock.advanceTimeBy(1600)
        assertEquals(SteamConversationFilter.ALL, rule.runOnUiThread { selected.value })
        assertTrue(rule.runOnUiThread { changes.isEmpty() })
        rule.runOnUiThread { page.value = "filters" }
        rule.mainClock.advanceTimeBy(64)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        drag(center(SteamConversationFilter.ALL), center(SteamConversationFilter.UNREAD))
        verifySelection(SteamConversationFilter.UNREAD, listOf(SteamConversationFilter.UNREAD))
    }

    @Test fun draggedBlockMovesBeforeReleaseWithoutFilteringTheList() {
        mount()
        val start = center(SteamConversationFilter.ALL)
        val destination = center(SteamConversationFilter.UNREAD)
        val before = capture("before")
        val pointer = Pointer(start)
        pointer.moveTo(destination)
        assertEquals(SteamConversationFilter.ALL, rule.runOnUiThread { selected.value })
        assertTrue(rule.runOnUiThread { changes.isEmpty() })
        val moving = capture("dragging")
        pointer.finish()
        verifySelection(SteamConversationFilter.UNREAD, listOf(SteamConversationFilter.UNREAD))
        val after = capture("settled")
        assertTrue("The held pointer must move the visible block", difference(before, moving) > 1_000)
        assertTrue("Release presents the settled selection", difference(moving, after) > 1_000)
        listOf(before, moving, after).forEach { it.recycle() }
    }

    @Test fun cancelledDragRestoresCommittedFilter() {
        mount()
        Pointer(center(SteamConversationFilter.ALL)).apply {
            moveTo(center(SteamConversationFilter.UNREAD))
            finish(cancelled = true)
        }
        verifySelection(SteamConversationFilter.ALL, emptyList())
        capture("cancelled").recycle()
    }

    @Test fun holdingGlassExpandsBeyondTheSlotAndReleaseRestoresIt() {
        mount()
        val slot = rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.ALL.labelRes()))
            .fetchSemanticsNode().boundsInRoot
        val before = capture("hold-before")
        val pointer = Pointer(slot.center)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        val held = capture("hold-pressed")
        var outsideDifference = 0L
        for (x in (slot.left-filterBounds.left).toInt().coerceAtLeast(0) until
            (slot.right-filterBounds.left).toInt().coerceAtMost(before.width)) {
            for (y in 0 until before.height) {
                val rootY = y + filterBounds.top - 32
                if (rootY >= filterBounds.top && rootY < filterBounds.bottom) continue
                val a = before.getPixel(x,y); val b = held.getPixel(x,y)
                for (shift in listOf(0,8,16)) outsideDifference += abs(((a shr shift) and 255)-((b shr shift) and 255))
            }
        }
        pointer.finish()
        verifySelection(SteamConversationFilter.ALL, listOf(SteamConversationFilter.ALL))
        val released = capture("hold-released")
        assertTrue("Held glass must grow visibly outside the entire filter container: $outsideDifference", outsideDifference > 1000)
        assertTrue("Release restores the resting glass", difference(before,released) < difference(before,held))
        listOf(before,held,released).forEach { it.recycle() }
    }

    private fun verifyQuickTapExpansion(target: SteamConversationFilter, reduceMotion: Boolean = false) {
        reduced.value = reduceMotion
        mount(flatDark = true)
        val slot = rule.onNodeWithText(rule.activity.getString(target.labelRes()))
            .fetchSemanticsNode().boundsInRoot
        val prefix = "quick-tap-${target.name.lowercase()}-${if (reduceMotion) "reduced" else "animated"}"
        val before = capture("$prefix-before")
        rule.mainClock.autoAdvance = false
        Pointer(slot.center).finish(delayMillis = 10)
        rule.mainClock.advanceTimeBy(176)
        rule.waitForIdle()
        rule.runOnUiThread {
            assertEquals(target, selected.value)
            assertEquals(listOf(target), changes)
        }
        val expanded = capture("$prefix-expanded")
        fun outsideDifference(bitmap: Bitmap): Long {
            var total = 0L
            for (x in (slot.left-filterBounds.left).toInt().coerceAtLeast(0) until
                (slot.right-filterBounds.left).toInt().coerceAtMost(bitmap.width)) {
                for (y in 0 until bitmap.height) {
                    val rootY = y + filterBounds.top - 32
                    if (rootY >= filterBounds.top && rootY < filterBounds.bottom) continue
                    val a = before.getPixel(x,y); val b = bitmap.getPixel(x,y)
                    for (shift in listOf(0,8,16)) total += abs(((a shr shift) and 255)-((b shr shift) and 255))
                }
            }
            return total
        }
        val expandedOutside = outsideDifference(expanded)
        rule.mainClock.advanceTimeBy(1_000)
        rule.mainClock.autoAdvance = true
        verifySelection(target, listOf(target))
        val settled = capture("$prefix-settled")
        val settledOutside = outsideDifference(settled)
        println("FILTER_QUICK_TAP target=$target reduced=$reduceMotion expanded=$expandedOutside settled=$settledOutside")
        if (reduceMotion) assertTrue("Reduced motion must not expand outside the shell: $expandedOutside", expandedOutside < 1000)
        else if (target != SteamConversationFilter.ALL) {
            assertTrue("A 10ms tap must still expand beyond the entire filter shell: $expandedOutside", expandedOutside > 1000)
            assertTrue("The quick-tap lens must return inside the shell: $settledOutside", settledOutside < expandedOutside / 4)
        }
        listOf(before, expanded, settled).forEach { it.recycle() }
    }

    @Test fun quickTapOnSelectedGlassUsesDockReleaseWithoutDelayedPulse() =
        verifyQuickTapExpansion(SteamConversationFilter.ALL)

    @Test fun quickTapOnUnselectedGlassExpandsAfterImmediateSelection() =
        verifyQuickTapExpansion(SteamConversationFilter.UNREAD)

    @Test fun reducedMotionQuickTapSelectsWithoutExpansion() =
        verifyQuickTapExpansion(SteamConversationFilter.UNREAD, reduceMotion = true)

    @Test fun heldLensHasVisibleGlassRimOnFlatDarkChatBackground() {
        mount(flatDark = true)
        val slot = rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.ALL.labelRes()))
            .fetchSemanticsNode().boundsInRoot
        val before = capture("flat-before")
        val pointer = Pointer(slot.center)
        try {
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            val held = capture("flat-held")
            var brightRimPixels = 0
            var centralTextPixels = 0
            var restingTextPixels = 0
            val heldGlyph = mutableListOf<Offset>()
            val restGlyph = mutableListOf<Offset>()
            for (x in 0 until held.width) for (y in 0 until held.height) {
                val rootX = x + filterBounds.left
                val rootY = y + filterBounds.top - 32
                val dx = (rootX - slot.center.x) / (slot.width * (88f / 56f) / 2)
                val dy = (rootY - slot.center.y) / (slot.height * (88f / 56f) / 2)
                val radiusSquared = dx * dx + dy * dy
                val pixel = held.getPixel(x, y)
                val old = before.getPixel(x, y)
                val light = ((pixel shr 16) and 255) + ((pixel shr 8) and 255) + (pixel and 255)
                val oldLight = ((old shr 16) and 255) + ((old shr 8) and 255) + (old and 255)
                // Compare the actual expanded rim to its dark baseline, not an arbitrary
                // absolute white threshold above the bar. Also reject misaligned text.
                if (radiusSquared in .81f..1.05f && light > oldLight + 30) brightRimPixels++
                if (radiusSquared < .30f && light > 480) { centralTextPixels++; heldGlyph += Offset(x.toFloat(), y.toFloat()) }
                if (radiusSquared < .30f && oldLight > 480) { restingTextPixels++; restGlyph += Offset(x.toFloat(), y.toFloat()) }
            }
            println("FILTER_LENS flatDark rimPixels=$brightRimPixels centralTextPixels=$centralTextPixels")
            File(rule.activity.getExternalFilesDir(null), "filter-drag/flat-metrics.txt").writeText(
                "rimPixels=$brightRimPixels\ncentralTextPixels=$centralTextPixels\nrestingTextPixels=$restingTextPixels\n")
            held.recycle()
            assertTrue("A held lens needs a visible refractive rim on a flat dark page, not just a larger dark disk: $brightRimPixels",
                brightRimPixels > 80)
            assertTrue("The refracted label must remain in the middle of its lens: $centralTextPixels",
                centralTextPixels > 200)
            // Dock tints the capture primary; threshold pixel counts change with tint
            // and filtering. Measure glyph extent to verify magnification instead.
            fun width(points: List<Offset>) = points.maxOf { it.x } - points.minOf { it.x } + 1f
            fun height(points: List<Offset>) = points.maxOf { it.y } - points.minOf { it.y } + 1f
            assertTrue("Holding magnifies glyph width", width(heldGlyph) > width(restGlyph) * 1.10f)
            assertTrue("Holding magnifies glyph area", width(heldGlyph) * height(heldGlyph) > width(restGlyph) * height(restGlyph) * 1.20f)
        } finally {
            pointer.finish()
            before.recycle()
        }
        verifySelection(SteamConversationFilter.ALL, listOf(SteamConversationFilter.ALL))
        capture("flat-released").recycle()
    }

    @Test fun largeFontBadgeAndReducedMotionStillSelectCorrectSlot() {
        large.value = true
        reduced.value = true
        unread.value = 99
        mount()
        drag(center(SteamConversationFilter.ALL), center(SteamConversationFilter.UNREAD))
        verifySelection(SteamConversationFilter.UNREAD, listOf(SteamConversationFilter.UNREAD))
        capture("large-reduced").recycle()
    }

    @Test fun rtlDragUsesPhysicalMeasuredSlots() {
        direction.value = LayoutDirection.Rtl
        mount()
        drag(center(SteamConversationFilter.ALL), center(SteamConversationFilter.UNREAD))
        verifySelection(SteamConversationFilter.UNREAD, listOf(SteamConversationFilter.UNREAD))
        capture("rtl").recycle()
    }

    @Test fun draggingAnUnselectedOptionCommitsTheReleasedFilter() {
        mount()
        drag(center(SteamConversationFilter.UNREAD), center(SteamConversationFilter.DIRECT))
        verifySelection(SteamConversationFilter.DIRECT, listOf(SteamConversationFilter.DIRECT))
    }

    @Test fun cancellingADragFromAnUnselectedOptionRestoresTheCommittedFilter() {
        mount()
        Pointer(center(SteamConversationFilter.UNREAD)).apply {
            moveTo(center(SteamConversationFilter.DIRECT)); finish(cancelled = true)
        }
        verifySelection(SteamConversationFilter.ALL, emptyList())
    }

    @Test fun edgeScrollReachesOffscreenArchivedFilter() {
        mount()
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.ARCHIVED.labelRes())).assertIsNotDisplayed()
        val destination = Offset(filterBounds.right - 28f, center(SteamConversationFilter.ALL).y)
        Pointer(center(SteamConversationFilter.ALL)).apply {
            moveTo(destination)
            rule.mainClock.advanceTimeBy(1_200)
            rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.ARCHIVED.labelRes())).assertIsDisplayed()
            finish()
        }
        verifySelection(SteamConversationFilter.ARCHIVED, listOf(SteamConversationFilter.ARCHIVED))
        rule.onNodeWithText("Archived friend").assertIsDisplayed()
        capture("edge-archived").recycle()
    }

    @Test fun badgeReflowRevokesAnInProgressDrag() {
        mount()
        val pointer = Pointer(center(SteamConversationFilter.ALL))
        pointer.moveTo(center(SteamConversationFilter.UNREAD))
        rule.runOnUiThread { unread.value = 99 }
        // Native pointer injection does not advance the Compose test clock. Present the reflow first.
        rule.waitForIdle()
        rule.onNodeWithText("99").assertIsDisplayed()
        pointer.finish()
        verifySelection(SteamConversationFilter.ALL, emptyList())
    }

    @Test fun externalSelectionCannotBeOverwrittenByReleasedOldGesture() {
        mount()
        val pointer = Pointer(center(SteamConversationFilter.ALL))
        pointer.moveTo(center(SteamConversationFilter.UNREAD))
        rule.runOnUiThread { selected.value = SteamConversationFilter.DIRECT }
        rule.waitForIdle()
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.DIRECT.labelRes())).assertIsSelected()
        pointer.finish()
        verifySelection(SteamConversationFilter.DIRECT, emptyList())
    }

    @Test fun leavingTheFilterCancelsLateSelectionAndCanRemount() {
        mount()
        val pointer = Pointer(center(SteamConversationFilter.ALL))
        pointer.moveTo(center(SteamConversationFilter.UNREAD))
        rule.runOnUiThread { visible.value = false }
        rule.waitForIdle()
        rule.onNodeWithTag("filters").assertDoesNotExist()
        pointer.finish()
        rule.runOnUiThread { visible.value = true }
        verifySelection(SteamConversationFilter.ALL, emptyList())
    }

    @Test fun m3eIgnoresDragAndSupportsClickSelection() {
        style.value = SteamDockStyle.FIXED
        mount(flatDark = true)
        drag(center(SteamConversationFilter.ALL), center(SteamConversationFilter.UNREAD))
        verifySelection(SteamConversationFilter.ALL, emptyList())
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.UNREAD.labelRes())).performClick()
        verifySelection(SteamConversationFilter.UNREAD, listOf(SteamConversationFilter.UNREAD))
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.ALL.labelRes())).performClick()
        verifySelection(SteamConversationFilter.ALL, listOf(SteamConversationFilter.UNREAD, SteamConversationFilter.ALL))
        capture("m3e-dark").recycle()
    }

    @Test fun m3eLargeFontKeepsOneRowAndScrollsToEveryFilter() {
        style.value = SteamDockStyle.FIXED
        large.value = true
        mount()
        val density = rule.activity.resources.displayMetrics.density
        assertTrue("Large type must keep a compact single row", filterBounds.height / density < 88f)
        SteamConversationFilter.entries.forEach {
            rule.onNodeWithText(rule.activity.getString(it.labelRes())).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.ARCHIVED.labelRes())).performClick()
        verifySelection(SteamConversationFilter.ARCHIVED, listOf(SteamConversationFilter.ARCHIVED))
        capture("m3e-large").recycle()
    }

    @Test fun m3eFrostBlursLiveBackgroundInLightTheme() { verifyFrost(false) }

    @Test fun m3eFrostBlursLiveBackgroundInDarkTheme() { verifyFrost(true) }

    private fun verifyFrost(dark: Boolean) {
        style.value = SteamDockStyle.FIXED
        mount(flatDark = dark, finePattern = true)
        val before = capture(if (dark) "m3e-dark-frost" else "m3e-light-frost")
        val inset = (rule.activity.resources.displayMetrics.density * 42).toInt()
        val bandY = before.height - 32 - (rule.activity.resources.displayMetrics.density * 8).toInt()
        fun range(bitmap: Bitmap, y: Int): Int {
            val values = (inset until bitmap.width - inset).map { bitmap.getPixel(it, y) and 255 }
            return values.max() - values.min()
        }
        val raw = range(before, 8)
        val frosted = range(before, bandY)
        assertTrue("Fine backdrop stripes must exist in the control sample: $raw", raw > 180)
        assertTrue("Gaussian blur must remove fine contrast beyond tint alone: $frosted/$raw", frosted < raw * .25f)
        val beforeBounds = rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.UNREAD.labelRes()))
            .fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { frostBackground.value = Color(0xFF00CCFF) }
        rule.waitForIdle()
        instrumentation.waitForIdleSync()
        val after = capture(if (dark) "m3e-dark-frost-updated" else "m3e-light-frost-updated")
        val sourceDelta = (inset until before.width - inset).map {
            abs((before.getPixel(it, 8) and 255) - (after.getPixel(it, 8) and 255))
        }.average()
        assertTrue("The background update must be presented before checking its blur: $sourceDelta", sourceDelta > 100)
        val delta = (inset until before.width - inset).map {
            abs((before.getPixel(it, bandY) and 255) - (after.getPixel(it, bandY) and 255))
        }.average()
        assertTrue("The frost must show the changed backdrop, not an opaque fill: $delta", delta > 12)
        assertEquals(beforeBounds, rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.UNREAD.labelRes()))
            .fetchSemanticsNode().boundsInRoot)
        before.recycle(); after.recycle()
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.UNREAD.labelRes())).performClick()
        verifySelection(SteamConversationFilter.UNREAD, listOf(SteamConversationFilter.UNREAD))
    }

    @Test fun themeChangeCancelsHeldGlassSelection() {
        mount()
        val pointer = Pointer(center(SteamConversationFilter.ALL))
        pointer.moveTo(center(SteamConversationFilter.UNREAD))
        rule.runOnUiThread { style.value = SteamDockStyle.FIXED }
        rule.waitForIdle()
        pointer.finish()
        verifySelection(SteamConversationFilter.ALL, emptyList())
    }

    @Test fun verticalParentScrollingKeepsTheFilterUnchanged() {
        mount(verticalParent = true)
        val start = center(SteamConversationFilter.ALL)
        drag(start, start - Offset(0f, 120f))
        verifySelection(SteamConversationFilter.ALL, emptyList())
        assertTrue("The vertical ancestor must receive its gesture", rule.runOnIdle { parentScroll!!.value > 0 })
    }
}
