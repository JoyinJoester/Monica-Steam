package takagi.ru.monica.steam.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.steam.friends.chat.richmedia.ui.RichPickerPage
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamRichPickerBottomBar
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMediaPickerPanel
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.R
import android.graphics.Bitmap
import android.graphics.Color as PixelColor
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.liquidglass.render.SteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.ui.components.MonicaModalBottomSheet
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFilters
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFilter
import takagi.ru.monica.steam.friends.chat.ui.labelRes

/** Real hardware-window pixels and retained input state; only synthetic in-memory content. */
@OptIn(ExperimentalMaterial3Api::class)
class SteamAppGlassUiTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun filterAndPickerBarsSampleLiveBackgroundInBothStyles() {
        val style = mutableStateOf(SteamDockStyle.LIQUID_GLASS)
        val variant = mutableIntStateOf(0)
        val filter = mutableStateOf(SteamConversationFilter.ALL)
        val page = mutableStateOf(RichPickerPage.EMOJI)
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides style.value) {
                    SteamGlassHost(Modifier.fillMaxSize(), content = { Pattern(variant.intValue) }, overlay = {
                        SteamConversationFilters(filter.value, { filter.value = it },
                            Modifier.align(Alignment.TopCenter).padding(top = 80.dp).testTag("filters"))
                        SteamRichPickerBottomBar(page.value, { page.value = it }, {},
                            Modifier.align(Alignment.Center).testTag("picker-tabs"))
                    })
                }
            }
        }
        for (current in listOf(SteamDockStyle.LIQUID_GLASS, SteamDockStyle.FIXED)) {
            rule.runOnIdle { style.value = current; variant.intValue = 0 }
            val first = listOf("filters", "picker-tabs").map { crop(it, "$it-$current-before") }
            rule.runOnIdle { variant.intValue = 1 }
            val second = listOf("filters", "picker-tabs").map { crop(it, "$it-$current-after") }
            first.zip(second).forEach { (before, after) ->
                // Sample the interior of the leading pill, excluding its label/selected indicator.
                assertTrue("Bar must sample its live sibling source in $current",
                    distance(before.getPixel(before.width / 4, before.height * 3 / 4),
                        after.getPixel(after.width / 4, after.height * 3 / 4)) > 12)
                before.recycle(); after.recycle()
            }
        }
    }

    @Test fun realPickerKeepsExclusiveTabsAndCloseAtLargeFontInBothStyles() {
        val style = mutableStateOf(SteamDockStyle.LIQUID_GLASS)
        var closes = 0
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides style.value,
                    LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        SteamChatRichMediaPickerPanel(SteamChatRichMediaUiState(), { closes++ }, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        for (current in listOf(SteamDockStyle.LIQUID_GLASS, SteamDockStyle.FIXED)) {
            rule.runOnIdle { style.value = current }
            for (page in RichPickerPage.entries) {
                val label = rule.activity.getString(page.tabLabelRes)
                rule.onNodeWithText(label).performClick().assertIsSelected()
                rule.onAllNodes(isSelected()).assertCountEquals(1)
            }
            saveScreen("picker-large-$current")
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.steam_chat_close)).performClick()
        }
        rule.runOnIdle { assertEquals(2, closes) }
    }

    @Test fun sharedPageMotionDisposesOldContentAndSupportsReducedMotion() {
        val page = mutableIntStateOf(0)
        val reduced = mutableStateOf(false)
        val disposed = mutableListOf<Int>()
        rule.setContent {
            MaterialTheme {
                AnimatedContent(page.intValue, transitionSpec = {
                    steamPageTransition(reduced.value, forward = targetState > initialState)
                }) { value ->
                    DisposableEffect(value) { onDispose { disposed += value } }
                    Box(Modifier.fillMaxSize().testTag("page-$value")) { Text("Page $value") }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { page.intValue = 1 }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithTag("page-0").assertDoesNotExist()
        rule.onNodeWithTag("page-1").assertExists()
        val moving = rule.onNodeWithTag("page-1").fetchSemanticsNode().positionInRoot.x
        assertTrue("Incoming page slides, without retaining the outgoing page", moving > 0f)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(0f, rule.onNodeWithTag("page-1").fetchSemanticsNode().positionInRoot.x, 1f)
        assertTrue(disposed.contains(0))
        rule.runOnIdle { reduced.value = true; page.intValue = 0 }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithTag("page-1").assertDoesNotExist()
        assertEquals("Reduced motion has no spatial movement", 0f,
            rule.onNodeWithTag("page-0").fetchSemanticsNode().positionInRoot.x, 1f)
        rule.mainClock.autoAdvance = true
    }

    @Test fun filterIndicatorMovesBetweenExclusiveSelections() {
        val selected = mutableStateOf(SteamConversationFilter.ALL)
        rule.setContent {
            MaterialTheme {
                SteamConversationFilters(selected.value, { selected.value = it },
                    Modifier.testTag("filters"), unreadCount = 3)
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        rule.onNodeWithTag("filters").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        val before = crop("filters", "filters-before")
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.UNREAD.labelRes())).performClick()
        rule.mainClock.advanceTimeBy(80)
        val moving = crop("filters", "filters-moving")
        rule.mainClock.advanceTimeBy(300)
        val after = crop("filters", "filters-after")
        assertTrue("Indicator has an actual intermediate frame", imageDifference(moving, after) > 1_000)
        assertTrue("Indicator moved away from the previous selection", imageDifference(before, moving) > 1_000)
        rule.onAllNodes(isSelected()).assertCountEquals(1)
        rule.onNodeWithText(rule.activity.getString(SteamConversationFilter.UNREAD.labelRes())).assertIsSelected()
        before.recycle(); moving.recycle(); after.recycle()
        rule.mainClock.autoAdvance = true
    }

    @Test fun materialsRefreshFromSiblingAndStyleSwitchPreservesDraft() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val style = mutableStateOf(SteamDockStyle.LIQUID_GLASS)
        val variant = mutableIntStateOf(0)
        var mounts = 0
        var unmounts = 0
        var source: SteamLiquidGlassBackdrop? = null
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSteamDesignStyle provides style.value) {
                    SteamGlassHost(Modifier.fillMaxSize(), content = {
                        DisposableEffect(Unit) { mounts++; onDispose { unmounts++ } }
                        Pattern(variant.intValue)
                        var draft by remember { mutableStateOf("") }
                        TextField(draft, { draft = it }, Modifier.align(Alignment.TopCenter).testTag("draft"))
                    }, overlay = {
                        source = LocalSteamGlassBackdrop.current
                        Box(Modifier.align(Alignment.Center).size(240.dp, 120.dp).testTag("glass")
                            .steamGlassSurface(RoundedCornerShape(24.dp), Color.White))
                    })
                }
            }
        }
        rule.onNodeWithTag("draft").performTextInput("retained draft")
        for (current in listOf(SteamDockStyle.LIQUID_GLASS, SteamDockStyle.FIXED)) {
            rule.runOnIdle { style.value = current; variant.intValue = 0 }
            rule.waitForIdle()
            val first = crop("glass", "material-${current}-red-blue")
            val y = first.height / 2
            val left = first.getPixel(first.width / 4, y)
            val right = first.getPixel(first.width * 3 / 4, y)
            assertTrue("Source visible through $current", distance(left, right) > 20)
            assertTrue("Boundary blurred for $current", distance(left, first.getPixel(first.width / 2 - 2, y)) > 8)
            rule.runOnIdle { variant.intValue = 1 }
            val second = crop("glass", "material-${current}-green-yellow")
            assertTrue("Live source refresh for $current", distance(left, second.getPixel(second.width / 4, y)) > 20)
            first.recycle(); second.recycle()
            rule.onNodeWithTag("draft").assertTextContains("retained draft")
            rule.runOnIdle { assertEquals(1, mounts); assertEquals(0, unmounts); assertEquals(1, source!!.consumers) }
        }
    }

    @Test fun popupSamplesCorrectScreenPositionAndReleasesSourceAfterDismissal() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val opened = mutableStateOf(false)
        val variant = mutableIntStateOf(0)
        var source: SteamLiquidGlassBackdrop? = null
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS) {
                    SteamGlassHost(Modifier.fillMaxSize(), content = { Pattern(variant.intValue) }, overlay = {
                        source = LocalSteamGlassBackdrop.current
                        Box(Modifier.align(Alignment.Center)) {
                            Button({ opened.value = true }, Modifier.testTag("open")) { Text("Menu") }
                            SteamGlassDropdownMenu(opened.value, { opened.value = false },
                                Modifier.width(220.dp).testTag("popup"), containerColor = Color.White) {
                                DropdownMenuItem({ Text("Synthetic action") }, { opened.value = false })
                            }
                        }
                    })
                }
            }
        }
        rule.runOnIdle { assertEquals("Closed menu does not record background", 0, source!!.consumers) }
        rule.onNodeWithTag("open").performClick()
        rule.onNodeWithTag("popup").assertIsDisplayed()
        val first = crop("popup", "popup-before")
        rule.runOnIdle { assertEquals(1, source!!.consumers); variant.intValue = 1 }
        val second = crop("popup", "popup-after")
        // At an interior point, away from the label and border; dialog-window origin must be applied.
        val x = first.width / 4
        val y = first.height - first.height / 5
        assertTrue("Popup renders changing background", distance(first.getPixel(x, y), second.getPixel(x, y)) > 20)
        first.recycle(); second.recycle()
        rule.onNodeWithText("Synthetic action").performClick()
        rule.waitUntil(5_000) { source!!.consumers == 0 }
        rule.onNodeWithTag("popup").assertDoesNotExist()
    }

    @Test fun scaffoldToolbarFabSheetAndDialogKeepActionsInBothThemes() {
        val dark = mutableStateOf(false)
        val liquid = mutableStateOf(false)
        val sheet = mutableStateOf(false)
        val dialog = mutableStateOf(false)
        var clicks = 0
        rule.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalSteamDesignStyle provides if (liquid.value) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED) {
                    SteamGlassScaffold(topBar = {
                        SteamGlassTopAppBar({ Text("Synthetic library") }, actions = {
                            TextButton({ dialog.value = true }) { Text("Details") }
                        })
                    }, floatingActionButton = {
                        SteamGlassFloatingActionButton({ clicks++; sheet.value = true }, Modifier.testTag("fab")) { Text("+") }
                    }) { padding ->
                        Box(Modifier.fillMaxSize()) { Pattern(0); Text("Offline content", Modifier.padding(padding)) }
                        if (sheet.value) MonicaModalBottomSheet({ sheet.value = false }) {
                            Text("Synthetic sheet", Modifier.padding(24.dp))
                            TextButton({ sheet.value = false }) { Text("Close sheet") }
                        }
                        if (dialog.value) SteamGlassAlertDialog({ dialog.value = false },
                            confirmButton = { TextButton({ dialog.value = false }) { Text("Confirm") } },
                            title = { Text("Synthetic dialog") }, text = { Text("No account or network") })
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) for (isLiquid in listOf(false, true)) {
            rule.runOnIdle { dark.value = isDark; liquid.value = isLiquid }
            rule.onNodeWithTag("fab").performClick()
            rule.waitUntil(5_000) { rule.onNodeWithText("Synthetic sheet").isDisplayed() }
            rule.onNodeWithText("Synthetic sheet").assertIsDisplayed()
            saveScreen("sheet-dark-$isDark-liquid-$isLiquid")
            rule.onNodeWithText("Close sheet").performClick()
            rule.onNodeWithText("Details").performClick()
            rule.onNodeWithText("Synthetic dialog").assertIsDisplayed()
            saveScreen("dialog-dark-$isDark-liquid-$isLiquid")
            rule.onNodeWithText("Confirm").performClick()
            saveScreen("scaffold-dark-$isDark-liquid-$isLiquid")
        }
        rule.runOnIdle { assertEquals(4, clicks) }
    }

    @Test fun sheetHandlePreservesExpandCollapseAndDismissVeto() {
        val opened = mutableStateOf(true)
        var allowDismiss = false
        var dismissals = 0
        lateinit var state: SheetState
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS) {
                    state = rememberModalBottomSheetState(confirmValueChange = {
                        it != SheetValue.Hidden || allowDismiss
                    })
                    SteamGlassHost(Modifier.fillMaxSize(), content = { Pattern(0) }, overlay = {
                        if (opened.value) SteamGlassModalBottomSheet(
                            onDismissRequest = { dismissals++; opened.value = false },
                            sheetState = state
                        ) { Box(Modifier.fillMaxWidth().height(700.dp)) { Text("Handle fixture") } }
                    })
                }
            }
        }
        rule.waitUntil(5_000) { state.currentValue == SheetValue.PartiallyExpanded }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand)
        rule.waitUntil(5_000) { state.currentValue == SheetValue.Expanded }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .performSemanticsAction(SemanticsActions.Collapse)
        rule.waitUntil(5_000) { state.currentValue == SheetValue.PartiallyExpanded }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performClick()
        rule.waitUntil(5_000) { state.currentValue == SheetValue.Expanded }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss)
        rule.runOnIdle { assertEquals(0, dismissals); assertTrue(state.isVisible); allowDismiss = true }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performClick()
        rule.waitUntil(5_000) { dismissals == 1 }
        rule.onNodeWithText("Handle fixture").assertDoesNotExist()
    }

    @Composable private fun Pattern(variant: Int) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(if (variant == 0) Color.Red else Color.Green, size = Size(size.width / 2, size.height))
            drawRect(if (variant == 0) Color.Blue else Color.Yellow,
                topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
        }
    }

    private fun crop(tag: String, name: String): Bitmap {
        rule.waitForIdle()
        // Wait for RenderThread to present the latest independently recorded backdrop.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val bounds = node.boundsInWindow
        val presented = java.util.concurrent.CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { presented.countDown() }
            rule.activity.window.decorView.invalidate()
        }
        assertTrue("A hardware frame must be presented before pixel assertions",
            presented.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        // Semantics bounds already include padding/clipping. Translate their window origin,
        // rather than adding layout-node padding a second time (visible on narrow screens).
        val origin = rule.runOnIdle {
            val coordinates = node.layoutInfo.coordinates
            coordinates.localToScreen(Offset.Zero) - coordinates.localToWindow(Offset.Zero)
        }
        val left = (bounds.left + origin.x).toInt().coerceIn(0, screen.width - 1)
        val top = (bounds.top + origin.y).toInt().coerceIn(0, screen.height - 1)
        val right = (bounds.right + origin.x).toInt().coerceIn(left + 1, screen.width)
        val bottom = (bounds.bottom + origin.y).toInt().coerceIn(top + 1, screen.height)
        val image = Bitmap.createBitmap(screen, left, top, right - left, bottom - top)
        save(image, name); screen.recycle(); return image
    }
    private fun saveScreen(name: String) {
        rule.waitForIdle()
        requireNotNull(instrumentation.uiAutomation.takeScreenshot()).also { save(it, name); it.recycle() }
    }
    private fun save(image: Bitmap, name: String) {
        val folder = File(instrumentation.targetContext.filesDir, "app-glass-evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun distance(a: Int, b: Int) = abs(PixelColor.red(a) - PixelColor.red(b)) +
        abs(PixelColor.green(a) - PixelColor.green(b)) + abs(PixelColor.blue(a) - PixelColor.blue(b))
    private fun imageDifference(a: Bitmap, b: Bitmap): Long {
        var difference = 0L
        for (y in 0 until minOf(a.height, b.height) step 3)
            for (x in 0 until minOf(a.width, b.width) step 3) difference += distance(a.getPixel(x, y), b.getPixel(x, y))
        return difference
    }
}
