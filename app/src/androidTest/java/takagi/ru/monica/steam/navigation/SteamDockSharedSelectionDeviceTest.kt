package takagi.ru.monica.steam.navigation

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** Actual production Dock and measured selector receive the same clicks on the same frame. */
class SteamDockSharedSelectionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    @After fun resetClock() { rule.mainClock.autoAdvance = true }
    private val tabs = SteamDockTab.completeLiquidGlassOrder(emptyList())
    private val dockSelected = mutableStateOf(tabs[0])
    private val rowSelected = mutableIntStateOf(0)
    private val dockCalls = mutableListOf<Int>()
    private val rowCalls = mutableListOf<Int>()

    private fun mount() {
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS) {
                    SteamGlassHost(Modifier.fillMaxSize(), content = {
                        Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xFF101022)) }
                    }, overlay = {
                        val page = LocalSteamGlassBackdrop.current!!
                        Column(Modifier.align(Alignment.Center).width(368.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(32.dp)) {
                            Text("Dock · 原组件")
                            SteamLiquidGlassDock(tabs, dockSelected.value, page, {
                                dockSelected.value = it; dockCalls += tabs.indexOf(it)
                            }, modifier = Modifier.testTag("comparison-dock"))
                            Text("筛选栏 · 共用 Dock 动画和镜片")
                            val surface = rememberLayerBackdrop()
                            Box(Modifier.width(328.dp).height(64.dp)) {
                                Box(Modifier.matchParentSize().layerBackdrop(surface)
                                    .steamGlassSurface(CircleShape, MaterialTheme.colorScheme.surfaceContainer,
                                        role = SteamGlassRole.FILTER))
                                SteamGlassSelectionRow((0..4).toList(), rowSelected.intValue, {
                                    rowSelected.intValue = it; rowCalls += it
                                }, MaterialTheme.colorScheme.primaryContainer, Modifier.padding(4.dp),
                                    dragFromAnyOption = true, surfaceBackdrop = surface) { item, active, modifier, click ->
                                    Box(modifier.width(64.dp).height(56.dp).testTag("comparison-tab-$item")
                                        .selectable(active, role = Role.Tab, onClick = click),
                                        contentAlignment = Alignment.Center) { Text(listOf("全部", "未读", "私聊", "群聊", "置顶")[item]) }
                                }
                            }
                        }
                    })
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5000) { rule.activity.hasWindowFocus() }
        SystemClock.sleep(400)
    }

    private fun lens(dock: Boolean): Rect = rule.onNodeWithTag(
        if (dock) "steam-dock-selection-lens" else "steam-glass-selection-lens").fetchSemanticsNode().boundsInRoot

    private fun capture(name: String) {
        rule.waitForIdle()
        SystemClock.sleep(60)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val folder = File(rule.activity.filesDir, "selection-motion").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun clickBoth(index: Int) {
        rule.onAllNodes(hasAnyAncestor(hasTestTag("comparison-dock")) and hasClickAction(), useUnmergedTree = true)[index]
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithTag("comparison-tab-$index").performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    @Test fun actualDockAndSelectorHaveMatchingTravelScaleAndReleaseFrames() {
        mount()
        val dockOrigin = lens(true)
        val rowOrigin = lens(false)
        capture("comparison-00-rest")
        rule.mainClock.autoAdvance = false
        clickBoth(2)
        val trace = StringBuilder("frame,dockX,rowX,dockScale,rowScale\n")
        repeat(50) { frame ->
            if (frame == 7) clickBoth(4)
            if (frame == 13) clickBoth(0)
            rule.mainClock.advanceTimeByFrame()
            val dock = lens(true); val row = lens(false)
            val dockX = (dock.center.x - dockOrigin.center.x) / dockOrigin.width
            val rowX = (row.center.x - rowOrigin.center.x) / rowOrigin.width
            val dockScale = dock.height / dockOrigin.height
            val rowScale = row.height / rowOrigin.height
            trace.append("$frame,$dockX,$rowX,$dockScale,$rowScale\n")
            assertEquals("travel frame $frame", dockX, rowX, .025f)
            assertEquals("scale/release frame $frame", dockScale, rowScale, .025f)
            if (frame in listOf(1, 3, 6, 9, 12, 15, 20, 30, 49)) capture("comparison-frame-${frame.toString().padStart(2, '0')}")
        }
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(listOf(2, 4, 0), dockCalls)
        assertEquals(dockCalls, rowCalls)
        assertEquals(dockOrigin.height, lens(true).height, 1f)
        assertEquals(rowOrigin.height, lens(false).height, 1f)
        println("DOCK_SHARED_FRAME_TRACE\n$trace")
        capture("comparison-99-settled")
    }
}
