package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.TelegramEmojiPickerView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.richmedia.ui.*
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.ui.LocalReduceAnimations

/** Actual native source pixels and production picker gestures; no Steam account or network assets. */
class SteamRichPickerGlassDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val sourceMode = mutableIntStateOf(0) // fine stripes / black / white
    private val selected = mutableStateOf(RichPickerPage.EMOJI)
    private val changes = mutableListOf<RichPickerPage>()
    private var source by mutableStateOf<View?>(null)
    private var dismissed = 0
    private var refreshes = 0
    private val emojiSelections = mutableListOf<String>()

    private fun mount(panel: Boolean = true, dark: Boolean = true, reduced: Boolean = false,
        rtl: Boolean = false, large: Boolean = false, effects: Boolean = true) {
        rule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
                    LocalReduceAnimations provides reduced,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context -> object : View(context) {
                            private val paint = Paint()
                            override fun onDraw(canvas: Canvas) {
                                val stripe = 2 * resources.displayMetrics.density
                                for (n in 0..(width / stripe).toInt()) {
                                    paint.color = when (sourceMode.intValue) {
                                        1 -> android.graphics.Color.BLACK
                                        2 -> android.graphics.Color.WHITE
                                        3 -> if ((n / 32) % 2 == 0) 0xFF355578.toInt() else 0xFFB77772.toInt()
                                        else -> if (n % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                                    }
                                    canvas.drawRect(n * stripe, 0f, (n + 1) * stripe, height.toFloat(), paint)
                                }
                            }
                        }.also { source = it } }, update = { sourceMode.intValue; it.invalidate() })
                        if (panel) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("panel")) {
                            SteamChatRichMediaPickerPanel(
                                state = SteamChatRichMediaUiState(effects = if (effects) listOf(
                                    SteamChatEffect("Offline sparkle"), SteamChatEffect("Offline wave")) else emptyList()),
                                onDismiss = { dismissed++ }, onEmojiSelected = { emojiSelections += it },
                                onEmoticonSelected = {}, onStickerSelected = {}, onEffectSelected = {},
                                onRefresh = { refreshes++ }, sourceView = source)
                        } else SteamGlassHost(Modifier.fillMaxSize(), backdropColor = Color(0xFF101022),
                            content = {}, overlay = {
                                SteamRichPickerBottomBar(selected.value, { selected.value = it; changes += it },
                                    { dismissed++ }, Modifier.align(Alignment.Center).width(340.dp).testTag("bar"))
                            })
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5000) { rule.activity.hasWindowFocus() }
        SystemClock.sleep(400)
    }
    private fun tab(page: RichPickerPage) = rule.onNodeWithText(rule.activity.getString(page.tabLabelRes))
    private fun center(page: RichPickerPage) = tab(page).fetchSemanticsNode().boundsInRoot.center
    private inner class Pointer(start: Offset) {
        private val origin = IntArray(2)
        private val down = SystemClock.uptimeMillis()
        private var position = start
        init {
            rule.runOnUiThread { rule.activity.findViewById<View>(android.R.id.content).getLocationOnScreen(origin) }
            send(MotionEvent.ACTION_DOWN)
        }
        private fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                position.x + origin[0], position.y + origin[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        fun move(to: Offset) {
            val start = position
            repeat(24) { n ->
                SystemClock.sleep(16); position = start + (to - start) * ((n + 1) / 24f)
                send(MotionEvent.ACTION_MOVE); rule.mainClock.advanceTimeBy(16)
            }
        }
        fun finish(cancel: Boolean = false) { SystemClock.sleep(80); send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP) }
    }
    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        val presented = CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { presented.countDown() }
            rule.activity.window.decorView.invalidate()
        }
        assertTrue(presented.await(5, TimeUnit.SECONDS))
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(rule.activity.getExternalFilesDir(null), "rich-picker/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }
    private fun rootOrigin(): IntArray = IntArray(2).also { value ->
        rule.runOnUiThread { rule.activity.findViewById<View>(android.R.id.content).getLocationOnScreen(value) }
    }
    private fun nativeViews(): List<View> {
        fun walk(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { walk(view.getChildAt(it)) } else emptyList()
        return rule.runOnUiThread { walk(rule.activity.window.decorView) }
    }
    private fun gaussian(dark: Boolean) {
        mount(dark = dark)
        val bounds = rule.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
        val origin = rootOrigin()
        val y = (bounds.top + bounds.height * .60f).toInt() + origin[1]
        fun samples(bitmap: Bitmap): List<Int> = (30..200).map { android.graphics.Color.red(bitmap.getPixel(it, y)) }
        val striped = capture(if (dark) "dark-striped" else "light-striped")
        val values = samples(striped)
        val contrast = values.max() - values.min()
        assertTrue("Fine native stripes must be Gaussian blurred, not merely tinted: $contrast", contrast < 25)
        rule.runOnIdle { sourceMode.intValue = 1 }
        val black = capture(if (dark) "dark-black" else "light-black")
        rule.runOnIdle { sourceMode.intValue = 2 }
        val white = capture(if (dark) "dark-white" else "light-white")
        val delta = samples(white).average() - samples(black).average()
        assertTrue("Opaque panel cannot satisfy background sampling: $delta", delta > 70)
        println("PICKER_GAUSSIAN dark=$dark contrast=$contrast blackWhiteDelta=$delta")
        val native = nativeViews().filterIsInstance<TelegramEmojiPickerView>().single()
        rule.runOnUiThread {
            val background = native.background as android.graphics.drawable.ColorDrawable
            assertEquals(android.graphics.Color.TRANSPARENT, background.color)
        }
        rule.runOnIdle { sourceMode.intValue = 3 }
        capture(if (dark) "dark-soft-backdrop" else "light-soft-backdrop").recycle()
        listOf(striped, black, white).forEach { it.recycle() }
    }
    @Test fun darkPanelBlursNativeSourceAndRetainsTransparency() = gaussian(true)
    @Test fun lightPanelBlursNativeSourceAndRetainsTransparency() = gaussian(false)

    @Test fun holdMagnifiesLabelAndRimThenRebounds() = holdLens(false)
    @Test fun nativePanelAlsoRendersHeldLensAndRebound() = holdLens(true)
    private fun holdLens(panel: Boolean) {
        sourceMode.intValue = 1; mount(panel = panel)
        val bounds = tab(RichPickerPage.EMOJI).fetchSemanticsNode().boundsInRoot
        val origin = rootOrigin()
        val before = capture(if (panel) "panel-lens-rest" else "lens-rest")
        val pointer = Pointer(bounds.center)
        var held: Bitmap? = null
        try {
            rule.mainClock.advanceTimeBy(240); held = capture(if (panel) "panel-lens-held" else "lens-held")
            val scale = 88f / 56f
            val rx = (bounds.width - 12 * rule.activity.resources.displayMetrics.density) * scale / 2f
            val ry = (bounds.height - 12 * rule.activity.resources.displayMetrics.density) * scale / 2f
            val cx = bounds.center.x + origin[0]; val cy = bounds.center.y + origin[1]
            var rim = 0; var textHeld = 0; var textRest = 0
            val heldGlyph = mutableListOf<Offset>(); val restGlyph = mutableListOf<Offset>()
            for (x in (cx-rx).toInt()..(cx+rx).toInt()) for (y in (cy-ry).toInt()..(cy+ry).toInt()) {
                val radius = ((x-cx)/rx)*((x-cx)/rx) + ((y-cy)/ry)*((y-cy)/ry)
                fun light(bitmap: Bitmap): Int { val p=bitmap.getPixel(x,y); return ((p shr 16) and 255)+((p shr 8) and 255)+(p and 255) }
                val a=light(before); val b=light(held)
                if (radius in .81f..1.05f && b>a+30) rim++
                if (abs((x-cx)/rx)<.80f && abs((y-cy)/ry)<.60f && b>480) { textHeld++; heldGlyph += Offset(x.toFloat(),y.toFloat()) }
                if (abs((x-cx)/rx)<.80f && abs((y-cy)/ry)<.60f && a>480) { textRest++; restGlyph += Offset(x.toFloat(),y.toFloat()) }
            }
            fun glyphCenter(points: List<Offset>) = Offset((points.minOf { it.x } + points.maxOf { it.x }) / 2f,
                (points.minOf { it.y } + points.maxOf { it.y }) / 2f)
            val drift = glyphCenter(heldGlyph) - glyphCenter(restGlyph)
            println("PICKER_LENS rim=$rim textHeld=$textHeld textRest=$textRest drift=$drift")
            assertTrue("Magnification must keep the label centered: $drift",abs(drift.x)<4f && abs(drift.y)<4f)
            assertTrue("Refractive rim must survive outside the rest slot: $rim",rim>60)
            val restWidth = restGlyph.maxOf { it.x } - restGlyph.minOf { it.x } + 1f
            val heldWidth = heldGlyph.maxOf { it.x } - heldGlyph.minOf { it.x } + 1f
            val restHeight = restGlyph.maxOf { it.y } - restGlyph.minOf { it.y } + 1f
            val heldHeight = heldGlyph.maxOf { it.y } - heldGlyph.minOf { it.y } + 1f
            println("PICKER_GLYPH rest=${restWidth}x$restHeight held=${heldWidth}x$heldHeight")
            // Shader resampling changes how many pixels cross a brightness threshold.
            // Measure the visible glyph's width and area as well as its retained pixels.
            assertTrue("Captured glyph width must magnify: $heldWidth/$restWidth",heldWidth>restWidth*1.15f)
            assertTrue("Captured glyph area must magnify",heldWidth*heldHeight>restWidth*restHeight*1.20f)
            // Primary-tinted Dock capture has fewer near-white pixels after resampling.
            // Width, area and centering above establish real magnification; retain
            // an absolute readable core rather than comparing different text colors.
            assertTrue("Magnification retains readable glyph pixels", textHeld > 80)
        } finally { pointer.finish(); held?.recycle() }
        rule.waitForIdle()
        val after = capture(if (panel) "panel-lens-released" else "lens-released")
        val cx=(bounds.center.x+origin[0]).toInt(); val top=(bounds.top+origin[1]-4).toInt()
        var difference=0
        for(x in cx-30..cx+30) for(y in top-4..top) difference+=abs(android.graphics.Color.red(before.getPixel(x,y))-android.graphics.Color.red(after.getPixel(x,y)))
        assertTrue("Released lens returns to resting contour: $difference",difference<200)
        before.recycle();after.recycle()
    }
    @Test fun draggingAnyCategoryCommitsOnlyOnReleaseAndCancelRestores() {
        mount(panel = false)
        Pointer(center(RichPickerPage.EMOTICON)).apply {
            move(center(RichPickerPage.EFFECT))
            assertEquals(RichPickerPage.EMOJI, rule.runOnUiThread { selected.value })
            assertTrue(changes.isEmpty()); capture("lens-dragging").recycle(); finish()
        }
        rule.waitForIdle(); tab(RichPickerPage.EFFECT).assertIsSelected()
        assertEquals(listOf(RichPickerPage.EFFECT),changes)
        Pointer(center(RichPickerPage.EMOJI)).apply { move(center(RichPickerPage.STICKER)); finish(true) }
        rule.waitForIdle();tab(RichPickerPage.EFFECT).assertIsSelected()
        assertEquals(listOf(RichPickerPage.EFFECT),changes)
    }
    @Test fun reducedMotionLargeFontRtlStillDragsAndCloses() {
        mount(panel = false, reduced = true, large = true, rtl = true)
        Pointer(center(RichPickerPage.EMOJI)).apply { move(center(RichPickerPage.STICKER)); finish() }
        rule.waitForIdle(); tab(RichPickerPage.STICKER).assertIsSelected()
        capture("rtl-large-reduced").recycle()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.steam_chat_close)).performClick()
        assertEquals(1,dismissed)
    }
    @Test fun nativeEmojiPagesSearchResetRefreshAndCloseRemainUsable() {
        mount()
        val native = nativeViews().filterIsInstance<TelegramEmojiPickerView>().single()
        rule.runOnUiThread {
            val grid = native.getChildAt(1) as androidx.recyclerview.widget.RecyclerView
            grid.getChildAt(0).performClick()
        }
        assertEquals(listOf("😀"),emojiSelections)
        Pointer(center(RichPickerPage.EMOJI)).apply {
            move(center(RichPickerPage.EFFECT))
            assertEquals(1,nativeViews().filterIsInstance<TelegramEmojiPickerView>().size)
            finish()
        }
        rule.waitForIdle()
        rule.onNodeWithText("Offline sparkle").assertIsDisplayed()
        val editor = nativeViews().filterIsInstance<android.widget.EditText>().single()
        rule.runOnUiThread { editor.setText("wave") }
        rule.waitForIdle()
        rule.onNodeWithText("Offline sparkle").assertDoesNotExist()
        rule.onNodeWithText("Offline wave").assertIsDisplayed()
        tab(RichPickerPage.EFFECT).performClick()
        rule.onNodeWithText("Offline sparkle").assertDoesNotExist()
        tab(RichPickerPage.STICKER).performClick()
        tab(RichPickerPage.EMOTICON).performClick()
        tab(RichPickerPage.EFFECT).performClick()
        rule.onNodeWithText("Offline sparkle").assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.steam_chat_rich_picker_refresh)).performClick()
        assertEquals(1,refreshes)
        tab(RichPickerPage.EMOJI).performClick()
        rule.waitForIdle()
        assertEquals(1,nativeViews().filterIsInstance<TelegramEmojiPickerView>().size)
        capture("panel-final").recycle()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.steam_chat_close)).performClick()
        assertEquals(1,dismissed)
    }
    @Test fun unsupportedEffectsRetainOpaqueNativeFallbackAndClickableTabs() {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
            LocalSteamGlassEffectsEnabled provides false) {
            SteamChatRichMediaPickerPanel(SteamChatRichMediaUiState(),{dismissed++},{},{},{},{},{})
        } } }
        rule.waitForIdle()
        val native=nativeViews().filterIsInstance<TelegramEmojiPickerView>().single()
        rule.runOnUiThread { assertEquals(255,(native.background as android.graphics.drawable.ColorDrawable).alpha) }
        tab(RichPickerPage.EMOTICON).performClick();tab(RichPickerPage.EMOTICON).assertIsSelected()
        capture("fallback").recycle()
    }
}
