package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.upstream.DialogCell
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationActions
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFlags
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationList
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.ui.theme.MonicaTheme

/** Mounted production list and native rows, with synthetic callbacks and an explicit dock region.
 * Direct semantics inspection avoids replacing the hybrid screen's Compose animation clock.
 */
@RunWith(AndroidJUnit4::class)
class SteamChatFloatingSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramSocialFixtureActivity? = null
    private val query = mutableStateOf("")
    private val pinned = mutableListOf<String>()
    private val archived = mutableListOf<String>()
    private val deleted = mutableListOf<Pair<List<String>, Boolean>>()
    private val opened = mutableListOf<String>()
    private val friends = listOf("Alpha One", "Alpha Two", "Beta Peer", "Alpha Archived").mapIndexed { index, name ->
        SteamFriend("floating-$index", SteamFriendRelationship.FRIEND, personaName = name)
    }
    private val actions = SteamConversationActions(
        onTogglePin = { pinned += it.id },
        onToggleArchive = { archived += it.id },
        onDelete = { entries, content -> deleted += entries.map { it.id } to content }
    )
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun string(id: Int) = activity!!.getString(id)
    private fun countLabel(count: Int) = activity!!.getString(R.string.steam_chat_selection_count, count)

    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = check() }
            if (ready) return
            Thread.sleep(20)
        }
        var ready = false
        main { ready = check() }
        assertTrue("Floating selection did not reach the expected mounted state", ready)
    }

    @After fun close() {
        main { activity?.finish() }
        instrumentation.waitForIdleSync()
    }

    private fun launch(dark: Boolean = true, fontScale: Float = 1f, listedFriends: List<SteamFriend> = friends) {
        main { activity?.finish() }
        instrumentation.waitForIdleSync()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "conversations").putExtra("dark", dark)
        ) as TelegramSocialFixtureActivity
        main {
            activity!!.setContent {
                MonicaTheme(darkTheme = dark) {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(
                        LocalDensity provides Density(density, fontScale),
                        LocalSteamDockContentClearance provides 104.dp
                    ) {
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize()) {
                                Box(Modifier.width(320.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding()
                                    .semantics { contentDescription = "Floating selection viewport" }) {
                                    SteamConversationList(
                                        chatState = SteamChatUiState(
                                            accountSteamId = "floating-account",
                                            sessions = SteamChatSessionsSnapshot("floating-account",
                                                listedFriends.mapIndexed { index, friend -> SteamChatSession(friend.steamId, 10_000L - index) }, 0)
                                        ),
                                        groupState = SteamGroupChatUiState(), friends = listedFriends, query = query.value,
                                        flags = SteamConversationFlags(archivedDirectIds = setOf("floating-3")),
                                        actions = actions, onOpenDirect = { opened += it }, onOpenGroup = { _, _ -> },
                                        onRefresh = {}, onCreateGroup = {}
                                    )
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(104.dp)
                                            .semantics { contentDescription = "Synthetic dock clearance" },
                                        color = MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text("Dock · 104 dp") }
                                    }
                            }
                            }
                        }
                    }
                }
            }
        }
        await { activity!!.hasWindowFocus() && row("Alpha One")?.height?.let { it > 0 } == true }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    private fun row(title: String): DialogCell? = descendants(activity!!.window.decorView)
        .filterIsInstance<DialogCell>().firstOrNull { it.contentDescription?.startsWith("$title,") == true }

    private fun rowBounds(title: String): Rect = Rect().also { bounds ->
        requireNotNull(row(title)).getGlobalVisibleRect(bounds)
        check(!bounds.isEmpty)
    }

    private fun semantics(): List<SemanticsNode> = buildList {
        fun visit(node: SemanticsNode) {
            if (node.boundsInWindow.width > 0 && node.boundsInWindow.height > 0) add(node)
            node.children.forEach(::visit)
        }
        // Dialogs use a separate ViewRoot; inspect those mounted roots as well as the main list.
        WindowInspector.getGlobalWindowViews().asReversed().forEach { root ->
            descendants(root).filterIsInstance<ViewRootForTest>().forEach { visit(it.semanticsOwner.unmergedRootSemanticsNode) }
        }
    }

    private fun node(label: String): SemanticsNode? = semantics().firstOrNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
    }

    private fun actionNode(label: String, matchDescription: Boolean = true): SemanticsNode? {
        // Floating actions expose icon descriptions. A same-named filter or dialog button
        // exposes Text, so matching both would silently invoke a different control.
        var result = semantics().firstOrNull { candidate ->
            if (matchDescription) {
                candidate.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
            } else {
                candidate.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            }
        }
        while (result != null && result.config.getOrNull(SemanticsActions.OnClick) == null) result = result.parent
        return result
    }

    private fun clickText(label: String) = click(label, matchDescription = false)

    private fun click(label: String, matchDescription: Boolean = true) {
        await { actionNode(label, matchDescription) != null }
        main {
            val action = requireNotNull(actionNode(label, matchDescription))
            assertFalse("$label must be enabled", action.config.contains(SemanticsProperties.Disabled))
            assertTrue("$label responds", action.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
        }
    }

    private fun inject(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun select(title: String) {
        await { activity!!.hasWindowFocus() && row(title) != null }
        var bounds = Rect()
        main { bounds = rowBounds(title) }
        val down = SystemClock.uptimeMillis()
        val start = bounds.left + bounds.width() * .2f
        inject(down, MotionEvent.ACTION_DOWN, start, bounds.exactCenterY())
        for (step in 1..18) {
            Thread.sleep(16)
            inject(down, MotionEvent.ACTION_MOVE, start + bounds.width() * .40f * step / 18f, bounds.exactCenterY())
        }
        inject(down, MotionEvent.ACTION_UP, start + bounds.width() * .40f, bounds.exactCenterY())
        await { node(countLabel(1)) != null }
    }

    private fun committedFrame() {
        val committed = CountDownLatch(1)
        main {
            activity!!.window.decorView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            activity!!.window.decorView.invalidate()
        }
        assertTrue("A native frame was committed", committed.await(5, TimeUnit.SECONDS))
    }

    private fun save(label: String) {
        committedFrame()
        val image = captureTelegramUiEvidence(instrumentation)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "chat-floating-selection").apply { mkdirs() }
        File(directory, "$label.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }

    @Test fun floatingBarKeepsFiltersAndRowsStableAboveDockInBothThemesAndLargeType() {
        for (dark in listOf(true, false)) {
            launch(dark, fontScale = 1.5f)
            var before = Rect()
            var filterTop = 0f
            main {
                before = rowBounds("Alpha One")
                filterTop = requireNotNull(node(string(R.string.steam_chat_filter_all))).boundsInWindow.top
            }
            select("Alpha One")
            // A spring can cross its target and then overshoot by a pixel on the next frame.
            // Require a stable exact rectangle instead of treating that crossing as completion.
            var stableSince = 0L
            await {
                val now = SystemClock.uptimeMillis()
                if (rowBounds("Alpha One") == before) {
                    if (stableSince == 0L) stableSince = now
                    now - stableSince >= 200L
                } else {
                    stableSince = 0L
                    false
                }
            }
            main {
                val after = rowBounds("Alpha One")
                assertEquals("Selection does not move the first row", before, after)
                assertEquals(filterTop, requireNotNull(node(string(R.string.steam_chat_filter_all))).boundsInWindow.top, 1f)
                val bar = requireNotNull(node(countLabel(1))).boundsInWindow
                val dock = requireNotNull(node("Synthetic dock clearance")).boundsInWindow
                val viewport = requireNotNull(node("Floating selection viewport")).boundsInWindow
                assertEquals("Fixture uses a real 320dp layout constraint", 320f * activity!!.resources.displayMetrics.density, viewport.width, 1f)
                assertTrue("Selection controls float in the lower half", bar.top > viewport.top + viewport.height / 2)
                assertTrue("Selection stays above the reserved dock region", bar.bottom <= dock.top + 1f)
                for (label in listOf(string(R.string.select_all), string(R.string.steam_chat_pin), string(R.string.steam_chat_archive),
                    string(R.string.steam_chat_delete), string(R.string.steam_chat_selection_cancel))) {
                    val button = requireNotNull(actionNode(label)).boundsInWindow
                    assertTrue("$label fits the 320dp viewport", button.left >= viewport.left - 1f && button.right <= viewport.right + 1f)
                    assertTrue("$label does not overlap the dock", button.bottom <= dock.top + 1f)
                }
            }
            save("floating-${if (dark) "dark" else "light"}-320dp-large")
            click(string(R.string.steam_chat_selection_cancel))
            await { node(countLabel(1)) == null }
        }
    }

    @Test fun selectAllUsesCurrentSearchResultsAndDispatchesOnlyThosePinAndArchiveCallbacks() {
        query.value = "Alpha"
        launch()
        select("Alpha One")
        click(string(R.string.select_all))
        await { node(countLabel(2)) != null }
        click(string(R.string.steam_chat_pin))
        await { node(countLabel(2)) == null }
        main {
            assertEquals(listOf("floating-0", "floating-1"), pinned)
            assertTrue(archived.isEmpty())
            assertTrue(deleted.isEmpty())
            query.value = "Beta"
        }
        await { row("Beta Peer") != null && row("Alpha One") == null }
        select("Beta Peer")
        click(string(R.string.select_all))
        click(string(R.string.steam_chat_archive))
        await { node(countLabel(1)) == null }
        main {
            assertEquals(listOf("floating-2"), archived)
            assertTrue(deleted.isEmpty())
            assertTrue(opened.isEmpty())
        }
    }

    @Test fun selectingOneThousandChatsPreservesAllFiveFullSizeActionsAt320DpAndLargeType() {
        val largeList = List(1_000) { index ->
            SteamFriend("bulk-floating-$index", SteamFriendRelationship.FRIEND,
                personaName = if (index == 0) "Alpha One" else "Bulk peer $index")
        }
        launch(fontScale = 1.5f, listedFriends = largeList)
        select("Alpha One")
        click(string(R.string.select_all))
        await { node(countLabel(1_000)) != null }
        committedFrame()
        main {
            val viewport = requireNotNull(node("Floating selection viewport")).boundsInWindow
            val dock = requireNotNull(node("Synthetic dock clearance")).boundsInWindow
            val density = activity!!.resources.displayMetrics.density
            assertEquals(320f * density, viewport.width, 1f)
            assertNotNull("Full count remains available even if the visual badge must shorten", node(countLabel(1_000)))
            for (label in listOf(string(R.string.select_all), string(R.string.steam_chat_pin),
                string(R.string.steam_chat_archive), string(R.string.steam_chat_delete),
                string(R.string.steam_chat_selection_cancel))) {
                val action = requireNotNull(actionNode(label))
                val bounds = action.boundsInWindow
                assertFalse("$label remains enabled", action.config.contains(SemanticsProperties.Disabled))
                assertNotNull("$label retains its click action", action.config.getOrNull(SemanticsActions.OnClick)?.action)
                assertTrue("$label keeps its 40dp width after a four-digit count", bounds.width >= 40f * density - 1f)
                assertTrue("$label keeps its 40dp height", bounds.height >= 40f * density - 1f)
                assertTrue("$label fits the viewport", bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f)
                assertTrue("$label stays above the dock", bounds.bottom <= dock.top + 1f)
            }
        }
        save("floating-dark-320dp-large-1000")
        click(string(R.string.steam_chat_pin))
        await { pinned.size == 1_000 && node(countLabel(1_000)) == null }
        main {
            assertEquals("Each selected conversation is dispatched exactly once", 1_000, pinned.size)
            assertEquals(largeList.map { it.steamId }.toSet(), pinned.toSet())
            assertTrue(archived.isEmpty() && deleted.isEmpty() && opened.isEmpty())
        }
    }

    @Test fun deleteRequiresConfirmationAndCancelPreservesTheSelection() {
        launch()
        select("Alpha One")
        click(string(R.string.steam_chat_delete))
        await { node(string(R.string.steam_chat_delete_title)) != null }
        main { assertTrue("Opening confirmation never deletes", deleted.isEmpty()) }
        clickText(string(R.string.steam_chat_delete_cancel))
        await { node(string(R.string.steam_chat_delete_title)) == null && node(countLabel(1)) != null }
        main {
            assertTrue("Cancel never calls the delete action", deleted.isEmpty())
            assertNotNull(row("Alpha One"))
        }
        click(string(R.string.steam_chat_delete))
        await { node(string(R.string.steam_chat_delete_title)) != null }
        clickText(string(R.string.steam_chat_delete))
        await { deleted.size == 1 && node(countLabel(1)) == null }
        main { assertEquals(listOf("floating-0") to false, deleted.single()) }
    }

    @Test fun emptySelectionDisablesMutationsAndCloseBackAndFilterCancelSelection() {
        launch()
        select("Alpha One")
        main { assertTrue(requireNotNull(row("Alpha One")).performClick()) }
        await { node(countLabel(0)) != null }
        main {
            for (label in listOf(string(R.string.steam_chat_pin), string(R.string.steam_chat_archive), string(R.string.steam_chat_delete))) {
                assertTrue("$label is disabled at zero selected", requireNotNull(actionNode(label)).config.contains(SemanticsProperties.Disabled))
            }
            assertTrue(pinned.isEmpty() && archived.isEmpty() && deleted.isEmpty() && opened.isEmpty())
        }
        click(string(R.string.steam_chat_selection_cancel))
        await { node(countLabel(0)) == null }
        select("Alpha One")
        await { activity!!.hasWindowFocus() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await { node(countLabel(1)) == null }
        main { assertFalse("Back exits selection before leaving this page", activity!!.isFinishing) }
        select("Alpha One")
        clickText(string(R.string.steam_chat_filter_direct))
        await { node(countLabel(1)) == null && row("Alpha One") != null }
        main {
            assertTrue(pinned.isEmpty() && archived.isEmpty() && deleted.isEmpty() && opened.isEmpty())
            assertNotNull(node(string(R.string.steam_chat_filter_all)))
        }
    }
}
