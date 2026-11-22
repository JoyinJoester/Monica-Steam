package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamChineseComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.SteamCardPreviewHost
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.steamGameInviteHeaderUrl
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatCardAction
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichCard
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

/** Observe the transformed native bounds and display-list pixels at paused animation frames. */
class SteamGameCardExpansionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SteamChineseComposeFixtureActivity>()
    private val scope = mutableStateOf("first")
    private var actions = 0

    @After fun restoreClock() { compose.mainClock.autoAdvance = true }

    private fun mount(reduced: Boolean = false, manyActions: Boolean = false) {
        seedChatGameCardFixture(compose.activity, 620, "传送门 2", "PORTAL 2")
        compose.setContent {
            MonicaTheme(darkTheme = true) {
                CompositionLocalProvider(LocalReduceAnimations provides reduced) {
                    SteamCardPreviewHost(scope.value) {
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                                contentAlignment = Alignment.BottomStart) {
                                SteamChatRichCard(
                                    kind = "游戏分享", title = "传送门 2", icon = Icons.Default.SportsEsports,
                                    artwork = steamGameInviteHeaderUrl(620), metadata = "App 620", description = null,
                                    primary = SteamChatCardAction("查看商店") { actions++ },
                                    // Keep the cover fully offscreen even on a tall emulator.
                                    actions = if (manyActions) List(60) { index ->
                                        SteamChatCardAction("操作 $index") { actions++ }
                                    } else listOf(SteamChatCardAction("复制链接") { actions++ }),
                                    modifier = Modifier.width(240.dp), featuredArtwork = true
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("steam-chat-game-artwork", useUnmergedTree = true).assertIsDisplayed()
        compose.waitUntil(5_000) { hasCoverPixels() }
    }

    private fun artwork(inPreview: Boolean = false) = compose.onNode(
        hasTestTag("steam-chat-game-artwork") and hasAnyAncestor(hasTestTag(
            if (inPreview) "steam-card-preview" else "steam-chat-game-card"
        )), useUnmergedTree = true
    )

    private fun hasCoverPixels(inPreview: Boolean = false): Boolean {
        val image = artwork(inPreview).captureToImage().asAndroidBitmap()
        val pixel = image.getPixel(image.width / 5, image.height / 5)
        return Color.red(pixel) < 60 && Color.green(pixel) > 100 && Color.blue(pixel) > 130
    }

    private fun previewBounds() = compose.onNodeWithTag("steam-card-preview").fetchSemanticsNode().boundsInRoot

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // The Compose clock remains frozen; allow the committed frame to reach SurfaceFlinger.
        Thread.sleep(80)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(compose.activity.filesDir, "game-card-motion").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun near(expected: Rect, actual: Rect, tolerance: Float = 3f) {
        assertEquals("left", expected.left, actual.left, tolerance)
        assertEquals("top", expected.top, actual.top, tolerance)
        assertEquals("right", expected.right, actual.right, tolerance)
        assertEquals("bottom", expected.bottom, actual.bottom, tolerance)
    }

    @Test fun tapExpandsFromExactCardBoundsAndCloseReturnsContinuously() {
        mount()
        val source = compose.onNodeWithTag("steam-chat-game-card").fetchSemanticsNode().boundsInRoot
        capture("01-card")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("steam-chat-game-card").performClick()
        compose.mainClock.advanceTimeByFrame()
        near(source, previewBounds(), 5f)
        capture("02-opening-origin")
        assertTrue("The very first expansion frame must retain the decoded cover", hasCoverPixels(inPreview = true))
        val opening = (1..27).map { frame ->
            compose.mainClock.advanceTimeByFrame()
            val bounds = previewBounds()
            capture("open-${frame.toString().padStart(2, '0')}")
            assertTrue("Cover must stay decoded throughout expansion", hasCoverPixels(inPreview = true))
            bounds
        }
        val early = opening[5]
        val middle = opening[11]
        val expanded = previewBounds()
        capture("05-expanded")
        assertTrue(early.width > source.width && early.width < expanded.width)
        assertTrue(middle.width > early.width && middle.width < expanded.width)
        assertTrue(early.height > source.height && middle.height > early.height && expanded.height > middle.height)
        compose.runOnIdle { assertEquals(0, actions) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.steam_preview_close)).performClick()
        compose.mainClock.advanceTimeByFrame()
        near(expanded, previewBounds(), 5f)
        val closing = (1..24).map { frame ->
            compose.mainClock.advanceTimeByFrame()
            val bounds = compose.onAllNodesWithTag("steam-card-preview").fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            capture("close-${frame.toString().padStart(2, '0')}")
            bounds
        }
        val returning = requireNotNull(closing[6])
        assertTrue(returning.width < expanded.width && returning.width > source.width)
        assertTrue(returning.height < expanded.height && returning.height > source.height)
        val nearOrigin = requireNotNull(closing[16])
        assertTrue(nearOrigin.width < returning.width)
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        near(source, compose.onNodeWithTag("steam-chat-game-card").fetchSemanticsNode().boundsInRoot)
        capture("08-restored")
        println("CARD_EXPANSION source=$source early=$early middle=$middle expanded=$expanded returning=$returning nearOrigin=$nearOrigin")
        compose.mainClock.autoAdvance = true
    }

    @Test fun scrolledPreviewReturnsToItsCoverWhenClosing() {
        mount(manyActions = true)
        compose.onNodeWithTag("steam-chat-game-card").performClick()
        compose.onNodeWithText("操作 59").performScrollTo().assertIsDisplayed()
        capture("scroll-before-close")
        artwork(inPreview = true).assertIsNotDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.steam_preview_close)).performClick()
        compose.mainClock.advanceTimeBy(280)
        artwork(inPreview = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        assertTrue("Closing must restore the original cover", hasCoverPixels())
    }

    @Test fun backDuringOpeningReversesWithoutJumpAndScopeChangeDropsPreview() {
        mount()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("steam-chat-game-card").performClick()
        compose.mainClock.advanceTimeBy(144)
        val beforeBack = previewBounds()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        near(beforeBack, previewBounds())
        compose.mainClock.advanceTimeByFrame()
        val reversal = previewBounds()
        compose.mainClock.advanceTimeBy(96)
        assertTrue("Closing must reverse the current expansion", previewBounds().width < reversal.width)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        compose.onNodeWithTag("steam-chat-game-card").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { scope.value = "second" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        compose.onNodeWithTag("steam-chat-game-card").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun reducedMotionHasNoIntermediateExpansionAndBackdropClosesImmediately() {
        mount(reduced = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("steam-chat-game-card").performClick()
        compose.mainClock.advanceTimeByFrame()
        val first = previewBounds()
        compose.mainClock.advanceTimeBy(160)
        near(first, previewBounds())
        compose.onNodeWithTag("steam-preview-backdrop").performTouchInput { click(Offset(4f, 4f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        compose.onNodeWithTag("steam-chat-game-card").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }
}
