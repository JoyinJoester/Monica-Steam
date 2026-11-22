package takagi.ru.monica.steam.navigation

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.SteamListFilterRow
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFilter
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFilters
import takagi.ru.monica.steam.friends.chat.ui.labelRes
import takagi.ru.monica.steam.friends.chat.richmedia.ui.RichPickerPage
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamRichPickerBottomBar
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.store.ui.SteamGameDetailNavigation
import takagi.ru.monica.steam.store.ui.SteamGameDetailSection
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.R

/** Physical down/up events and intermediate native glass positions, not semantic click alone. */
class SteamGlassSelectionTravelDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val selected = mutableIntStateOf(0)
    private val callbacks = mutableListOf<Int>()
    private fun choose(value: Int) { selected.intValue = value; callbacks += value }

    @After fun restoreClock() { rule.mainClock.autoAdvance = true }

    private fun mount(kind: String, rtl: Boolean = false, reduced: Boolean = false) {
        rule.setContent {
            MaterialTheme(colorScheme = if (kind == "list") lightColorScheme() else darkColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
                    LocalReduceAnimations provides reduced,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    SteamGlassHost(Modifier.fillMaxSize(), content = {
                        Canvas(Modifier.fillMaxSize()) { drawRect(if (kind == "list") Color(0xFFE5E8F2) else Color(0xFF101022)) }
                    }, overlay = {
                        Column(Modifier.align(Alignment.Center).width(360.dp),
                            verticalArrangement = Arrangement.spacedBy(48.dp)) {
                            Text("合成测试 · $kind", Modifier.padding(horizontal = 24.dp))
                            when (kind) {
                                "inbox" -> SteamConversationFilters(SteamConversationFilter.entries[selected.intValue],
                                    { choose(it.ordinal) }, unreadCount = 0)
                                "detail" -> SteamGameDetailNavigation(SteamGameDetailSection.entries[selected.intValue],
                                    true, { choose(it.ordinal) }, {})
                                "picker" -> SteamRichPickerBottomBar(RichPickerPage.entries[selected.intValue],
                                    { choose(it.ordinal) }, {})
                                else -> SteamListFilterRow(listOf(0, 1, 2), selected.intValue, ::choose,
                                    label = { listOf("全部", "已解锁成就", "未解锁")[it] })
                            }
                            Text("仅使用本地合成数据", Modifier.padding(horizontal = 24.dp))
                        }
                    })
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5000) { rule.activity.hasWindowFocus() }
        SystemClock.sleep(400)
    }

    private fun label(kind: String, index: Int): String = when (kind) {
        "inbox" -> rule.activity.getString(SteamConversationFilter.entries[index].labelRes())
        "detail" -> rule.activity.getString(listOf(R.string.steam_game_tab_store, R.string.steam_game_tab_stats, R.string.workshop_title)[index])
        "picker" -> rule.activity.getString(RichPickerPage.entries[index].tabLabelRes)
        else -> listOf("全部", "已解锁成就", "未解锁")[index]
    }

    private fun lens() = rule.onNodeWithTag("steam-glass-selection-lens").fetchSemanticsNode().boundsInRoot
    private fun tabCenter(kind: String, index: Int) = rule.onNodeWithText(label(kind, index)).fetchSemanticsNode().boundsInRoot.center

    private inner class Pointer(private val position: Offset) {
        private val origin = IntArray(2)
        private val down = SystemClock.uptimeMillis()
        init {
            rule.runOnUiThread { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin) }
            send(MotionEvent.ACTION_DOWN)
        }
        private fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                position.x + origin[0], position.y + origin[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        fun finish(cancel: Boolean = false) { send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP) }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(80)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(rule.activity.filesDir, "selection-motion").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun verifyTravel(kind: String, rtl: Boolean = false) {
        mount(kind, rtl)
        val source = lens()
        val destination = tabCenter(kind, 1)
        val prefix = kind + if (rtl) "-rtl" else ""
        capture("$prefix-00-rest")
        rule.mainClock.autoAdvance = false
        val pointer = Pointer(destination)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("Down must start at the previous glass position", source.center.x, lens().center.x, 3f)
        capture("$prefix-01-down")
        rule.mainClock.advanceTimeBy(64)
        assertEquals("Dock holds the current lens until click", source.center.x, lens().center.x, 3f)
        capture("$prefix-02-held")
        assertTrue(callbacks.isEmpty())
        pointer.finish()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(64)
        val moving = lens()
        val progress = (moving.center.x - source.center.x) / (destination.x - source.center.x)
        assertTrue("Expected actual travel between options, got $progress", progress in .01f..0.95f)
        assertEquals(listOf(1), callbacks)
        capture("$prefix-03-moving")
        val positions = mutableListOf<Rect>()
        repeat(24) { frame ->
            rule.mainClock.advanceTimeByFrame()
            positions += lens()
            if (kind == "inbox" || frame in listOf(3, 8, 16, 23)) capture("$prefix-frame-${frame.toString().padStart(2, '0')}")
        }
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(destination.x, lens().center.x, 3f)
        rule.runOnIdle { assertEquals(listOf(1), callbacks); assertEquals(1, selected.intValue) }
        capture("$prefix-99-settled")
        assertTrue(positions.any { kotlin.math.abs(it.center.x - destination.x) > 6f })
        println("GLASS_TRAVEL kind=$kind rtl=$rtl source=$source moving=$moving destination=$destination settled=${lens()}")
    }

    @Test fun inboxPhysicalTapTravelsInsteadOfOnlyZooming() = verifyTravel("inbox")
    @Test fun defaultListFilterPhysicalTapTravels() = verifyTravel("list")
    @Test fun gameDetailPhysicalTapTravels() = verifyTravel("detail")
    @Test fun richPickerPhysicalTapTravels() = verifyTravel("picker")
    @Test fun rtlFilterTravelsAlongPhysicalCoordinates() = verifyTravel("list", rtl = true)

    @Test fun cancelledUnselectedHoldKeepsDockOriginWithoutCallback() {
        mount("inbox")
        val source = lens()
        rule.mainClock.autoAdvance = false
        val pointer = Pointer(tabCenter("inbox", 1))
        rule.mainClock.advanceTimeBy(96)
        val moving = lens()
        pointer.finish(cancel = true)
        rule.waitForIdle()
        assertEquals(moving.center.x, lens().center.x, 3f)
        rule.mainClock.advanceTimeBy(96)
        assertEquals("Cancelled hold stays at committed slot", source.center.x, lens().center.x, 3f)
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(source.center.x, lens().center.x, 3f)
        assertTrue(callbacks.isEmpty())
    }

    @Test fun reducedMotionSelectsWithoutIntermediateTravel() {
        mount("inbox", reduced = true)
        rule.mainClock.autoAdvance = false
        val destination = tabCenter("inbox", 1)
        val pointer = Pointer(destination)
        pointer.finish()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(destination.x, lens().center.x, 3f)
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(destination.x, lens().center.x, 3f)
        assertEquals(listOf(1), callbacks)
    }
}
