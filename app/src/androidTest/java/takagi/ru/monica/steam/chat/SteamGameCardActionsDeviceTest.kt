package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamChineseComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.SteamCardPreviewHost
import takagi.ru.monica.steam.friends.chat.gameinvite.ui.SteamChatGameInviteCard
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.ui.theme.MonicaTheme

/** Production invitation card and menu, with cached synthetic artwork and callback counters. */
class SteamGameCardActionsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamChineseComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var stores = 0
    private var messages = 0
    private var deletes = 0
    private fun label(id: Int) = rule.activity.getString(id)
    private fun action(id: Int) = rule.onNodeWithText(label(id))

    @After fun resetOverrides() {
        SteamChineseComposeFixtureActivity.fontScaleOverride = null
        SteamChineseComposeFixtureActivity.densityDpiOverride = null
    }

    private fun mount(dark: Boolean, large: Boolean = false, withMessageActions: Boolean = true) {
        SteamChineseComposeFixtureActivity.densityDpiOverride = if (large) 540 else 480
        SteamChineseComposeFixtureActivity.fontScaleOverride = if (large) 1.5f else 1f
        rule.activityRule.scenario.recreate()
        seedChatGameCardFixture(rule.activity, 4001890, "渔力全开", "HOW TO FISH")
        val invite = SteamChatRichContentParser.parse(
            "steam://joinlobby/4001890/123456789/76561198000000002") as SteamChatRichContent.GameInvite
        rule.setContent { MonicaTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalSteamDesignStyle provides
                if (dark) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED) {
                SteamGlassHost(Modifier.fillMaxSize(), overlay = {}, content = {
                    SteamCardPreviewHost("actions") {
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                                contentAlignment = Alignment.BottomStart) {
                                SteamChatGameInviteCard(invite, { stores++ },
                                    onLongClick = if (withMessageActions) ({ messages++ }) else null,
                                    onDeleteMessage = if (withMessageActions) ({ deletes++ }) else null)
                            }
                        }
                    }
                })
            }
        } }
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("渔力全开", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun open() {
        rule.onNodeWithTag("steam-chat-game-card").performClick()
        rule.onNodeWithTag("steam-card-preview").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, stores + messages + deletes) }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val frame = CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
            rule.activity.window.decorView.invalidate()
        }
        assertTrue(frame.await(5, TimeUnit.SECONDS))
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(rule.activity.filesDir, "game-card-actions/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun verifyLayout(dark: Boolean, large: Boolean) {
        mount(dark, large)
        rule.onNodeWithTag("steam-game-card-more").assertDoesNotExist()
        action(R.string.steam_chat_game_invite_view_store).assertDoesNotExist()
        open()
        val primary = action(R.string.steam_chat_game_invite_view_store).fetchSemanticsNode().boundsInRoot
        val link = action(R.string.open_link).fetchSemanticsNode().boundsInRoot
        if (large) assertTrue("Large text stacks the main destinations", primary.bottom <= link.top)
        else {
            assertTrue("Main destinations sit side by side", primary.right <= link.left)
            assertEquals(primary.top, link.top, 1f)
            assertEquals(primary.width, link.width, 2f)
        }
        val copy = action(R.string.steam_card_copy_link).fetchSemanticsNode().boundsInRoot
        val message = action(R.string.steam_preview_message_actions).fetchSemanticsNode().boundsInRoot
        val delete = action(R.string.delete).fetchSemanticsNode().boundsInRoot
        assertTrue(link.bottom <= copy.top)
        assertEquals("Utility rows align", copy.left, message.left, 1f)
        assertEquals(copy.width, delete.width, 1f)
        val density = rule.activity.resources.displayMetrics.density
        assertEquals("Utility rows form a connected group", 2*density, message.top-copy.bottom, 2f)
        assertTrue(copy.height >= 48*density && message.height >= 48*density && delete.height >= 48*density)
        listOf(R.string.steam_card_copy_link, R.string.open_link).forEach { id ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNode(hasText(label(id)) and hasAnyAncestor(hasTestTag("steam-card-preview")),
                useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            capture("layout-${id}-${if (large) "large" else "normal"}-${if (dark) "dark" else "light"}")
            assertEquals("Short Chinese labels stay on one line", 1, layout.lineCount)
            // Layout rounds to whole pixels; compare the actual paragraph extent with that rounding.
            // MultiParagraph.width is the offered layout width; Text may report its smaller
            // intrinsic size. Check the actual glyph extent against that displayed size.
            assertTrue("No horizontal clipping", layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f)
            assertTrue("No vertical clipping", layout.multiParagraph.height <= layout.size.height + 1f)
        }
        val name = "${if (dark) "glass-dark" else "m3-light"}-${if (large) "large" else "normal"}"
        capture(name)
        action(R.string.steam_preview_message_actions).performScrollTo().performClick()
        rule.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, messages); assertEquals(0, stores + deletes) }
    }

    @Test fun invitationGlassUsesDestinationPillsAndGroupedRows() = verifyLayout(true, false)
    @Test fun invitationM3UsesDestinationPillsAndGroupedRows() = verifyLayout(false, false)
    @Test fun invitationLargeTextKeepsBothLinkActionsReadable() = verifyLayout(false, true)
    @Test fun missingMessageCallbacksOmitManagementRows() {
        mount(dark = true, withMessageActions = false)
        open()
        action(R.string.steam_preview_message_actions).assertDoesNotExist()
        action(R.string.delete).assertDoesNotExist()
        action(R.string.steam_chat_game_invite_view_store).performClick()
        rule.runOnIdle { assertEquals(1, stores); assertEquals(0, messages + deletes) }
        rule.onNodeWithTag("steam-card-preview").assertDoesNotExist()
    }
}
