package takagi.ru.monica.steam.store

import android.os.SystemClock
import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.ui.LocalReduceAnimations

class SteamToolbarTensionUiTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val background = Color(0xFF111122)
    private val gap = mutableFloatStateOf(0f)
    private val radius = mutableFloatStateOf(5f)
    private val edge = mutableStateOf(SteamStoreDetailToolbarEdge.RIGHT)
    private val reduced = mutableStateOf(false)

    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        SystemClock.sleep(200)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "toolbar-tension").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val origin = IntArray(2)
        var width = 0
        var height = 0
        rule.runOnUiThread {
            val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
            view.getLocationOnScreen(origin)
            width = view.width
            height = view.height
        }
        return Bitmap.createBitmap(bitmap, origin[0], origin[1], width, height)
    }

    private fun mountContour() {
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SteamGlassHost(Modifier.fillMaxSize(), background,
                        content = {}, overlay = {
                            val d = LocalDensity.current
                            val shape = SteamToolbarTensionShape(edge.value, with(d) { gap.floatValue.dp.toPx() },
                                with(d) { radius.floatValue.dp.toPx() }, with(d) { 28.dp.toPx() })
                            Box(Modifier.padding(40.dp).width((52 + gap.floatValue).dp).height(100.dp)
                                .testTag("contour").steamGlassSurface(shape, Color(0xFF666688), SteamGlassRole.TOOLBAR))
                        })
                }
            }
        }
        rule.waitForIdle(); SystemClock.sleep(400)
    }

    private fun difference(bitmap: Bitmap, x: Float, y: Float): Int {
        val pixel = bitmap.getPixel(x.toInt(), y.toInt())
        return maxOf(kotlin.math.abs(android.graphics.Color.red(pixel) - 17),
            kotlin.math.abs(android.graphics.Color.green(pixel) - 17),
            kotlin.math.abs(android.graphics.Color.blue(pixel) - 34))
    }

    @Test fun allFourCornersMaskTheGlassBaseOnBothEdges() {
        mountContour()
        for (side in SteamStoreDetailToolbarEdge.entries) {
            rule.runOnIdle { edge.value = side }
            val image = capture("corners-${side.name.lowercase()}")
            val rect = rule.onNodeWithTag("contour").fetchSemanticsNode().boundsInRoot
            val inset = with(rule.density) { .5.dp.toPx() }
            for (x in listOf(rect.left + inset, rect.right - inset)) {
                for (y in listOf(rect.top + inset, rect.bottom - inset)) {
                    assertTrue("Glass escaped rounded corner $side at $x,$y", difference(image, x, y) < 12)
                }
            }
            assertTrue(difference(image, rect.center.x, rect.center.y) > 25)
        }
    }

    @Test fun contactFlattensThenRoundsWithoutMovingOrResizing() {
        mountContour()
        rule.runOnIdle { radius.floatValue = 24f }
        val rounded = capture("contact-approach")
        val rect = rule.onNodeWithTag("contour").fetchSemanticsNode().boundsInRoot
        val inset = with(rule.density) { .5.dp.toPx() }
        val x = rect.right - inset
        val y = rect.top + inset
        assertTrue(difference(rounded, x, y) < 12)
        rule.runOnIdle { radius.floatValue = 0f }
        val flat = capture("contact-flat")
        assertEquals(rect, rule.onNodeWithTag("contour").fetchSemanticsNode().boundsInRoot)
        assertTrue("Contact did not flatten the actual glass mask", difference(flat, x, y) > 25)
        rule.runOnIdle { radius.floatValue = 5f }
        val tension = capture("contact-tension")
        assertEquals(rect, rule.onNodeWithTag("contour").fetchSemanticsNode().boundsInRoot)
        assertTrue("Meniscus did not round the actual mask", difference(tension, x, y) < 12)
    }

    @Test fun stretchedTetherKeepsMiddleConnectedWhileEndsClearThenBreaks() {
        mountContour()
        for (side in SteamStoreDetailToolbarEdge.entries) {
            rule.runOnIdle { edge.value = side; gap.floatValue = 12f; radius.floatValue = 20f }
            val image = capture("stretch-${side.name.lowercase()}")
            val rect = rule.onNodeWithTag("contour").fetchSemanticsNode().boundsInRoot
            val middle = with(rule.density) { 6.dp.toPx() }
            val x = if (side == SteamStoreDetailToolbarEdge.LEFT) rect.left + middle else rect.right - middle
            assertTrue("Neck disconnected before break", difference(image, x, rect.center.y) > 25)
            assertTrue("Upper slit did not clear", difference(image, x, rect.top + middle) < 12)
            assertTrue("Lower slit did not clear", difference(image, x, rect.bottom - middle) < 12)
            rule.runOnIdle { gap.floatValue = 28f; radius.floatValue = 24f }
            val broken = capture("break-${side.name.lowercase()}")
            val finalRect = rule.onNodeWithTag("contour").fetchSemanticsNode().boundsInRoot
            val finalX = if (side == SteamStoreDetailToolbarEdge.LEFT) finalRect.left + middle else finalRect.right - middle
            assertTrue("Neck still present after break", difference(broken, finalX, finalRect.center.y) < 12)
        }
    }

    @Test fun actualToolbarRetainsActionsAfterDragCancelCollapseAndReducedMotion() {
        var shares = 0
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
                    LocalReduceAnimations provides reduced.value,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SteamGlassHost(Modifier.fillMaxSize(), background, content = {}, overlay = {
                        SteamStoreDetailActionToolbar(null, {}, null, { shares++ }, Modifier.padding(24.dp))
                    })
                }
            }
        }
        rule.waitForIdle(); SystemClock.sleep(400)
        val toolbar = rule.onNodeWithTag("game_detail_tools")
        toolbar.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(-40f, 0f), 200); cancel() }
        rule.waitForIdle()
        capture("actual-toolbar-cancel")
        val share = instrumentation.targetContext.getString(takagi.ru.monica.R.string.steam_store_detail_share)
        rule.onNodeWithContentDescription(share).performClick()
        assertEquals(1, shares)
        rule.runOnIdle { reduced.value = true }
        toolbar.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(share).performClick()
        assertEquals(2, shares)
        rule.mainClock.advanceTimeBy(3600)
        rule.waitForIdle()
        val expand = instrumentation.targetContext.getString(takagi.ru.monica.R.string.steam_store_detail_toolbar_expand)
        rule.onNodeWithContentDescription(expand).performClick()
        rule.onNodeWithContentDescription(share).assertExists()
        capture("actual-toolbar-reexpanded")
    }

    @Test fun actualLiftRetainsTetherBeforeBreakingAndCanCancel() {
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SteamGlassHost(Modifier.fillMaxSize(), background, content = {}, overlay = {
                        SteamStoreDetailActionToolbar(null, {}, null, {}, Modifier.padding(24.dp))
                    })
                }
            }
        }
        rule.waitForIdle(); SystemClock.sleep(400)
        rule.mainClock.autoAdvance = false
        val toolbar = rule.onNodeWithTag("game_detail_tools")
        val initial = toolbar.fetchSemanticsNode().boundsInRoot
        capture("lift-00-contact")
        val origin = IntArray(2)
        rule.runOnUiThread {
            rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin)
        }
        val downTime = SystemClock.uptimeMillis()
        var pointerActive = false
        fun send(action: Int, point: Offset) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                point.x + origin[0], point.y + origin[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                pointerActive = action != MotionEvent.ACTION_CANCEL && action != MotionEvent.ACTION_UP
            }
            finally { event.recycle() }
        }
        val medium = initial.center - Offset(with(rule.density) { 24.dp.toPx() }, 0f)
        send(MotionEvent.ACTION_DOWN, initial.center)
        send(MotionEvent.ACTION_MOVE, medium)
        try {
            repeat(6) { frame ->
                rule.mainClock.advanceTimeBy(32)
                capture("lift-${(frame + 1).toString().padStart(2, '0')}-stretch")
            }
            val stretched = toolbar.fetchSemanticsNode().boundsInRoot
            assertTrue("No retained tether during actual lift", stretched.width > initial.width + 2f)
            val image = capture("lift-07-neck")
            assertTrue("Actual tether lost its center before break",
                difference(image, initial.right - 2f, initial.center.y) > 2)
            val far = initial.center - Offset(with(rule.density) { 90.dp.toPx() }, 0f)
            send(MotionEvent.ACTION_MOVE, far)
            repeat(8) { frame ->
                rule.mainClock.advanceTimeBy(32)
                capture("lift-${(frame + 8).toString().padStart(2, '0')}-break")
            }
            val broken = toolbar.fetchSemanticsNode().boundsInRoot
            assertTrue("Lift never separated", broken.right < initial.right - with(rule.density) { 28.dp.toPx() })
            assertEquals("Tether did not retire", initial.width, broken.width, 2f)
            send(MotionEvent.ACTION_CANCEL, far)
            rule.mainClock.advanceTimeBy(1000)
            assertEquals(initial.right, toolbar.fetchSemanticsNode().boundsInRoot.right, 2f)
            capture("lift-16-return")
        } finally {
            if (pointerActive) send(MotionEvent.ACTION_CANCEL, medium)
            rule.mainClock.autoAdvance = true
        }
    }
}
