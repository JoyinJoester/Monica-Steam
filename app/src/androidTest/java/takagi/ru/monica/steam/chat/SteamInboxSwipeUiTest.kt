package takagi.ru.monica.steam.chat

import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.upstream.DialogCell
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationList
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.ui.theme.MonicaTheme

@RunWith(AndroidJUnit4::class)
class SteamInboxSwipeUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramSocialFixtureActivity? = null
    private var originalFlags = 0
    @Before fun observeAllWindows() {
        val info = instrumentation.uiAutomation.serviceInfo
        originalFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
    }
    @After fun close() {
        instrumentation.runOnMainSync { activity?.finish() }
        val info = instrumentation.uiAutomation.serviceInfo
        info.flags = originalFlags
        instrumentation.uiAutomation.serviceInfo = info
    }

    private fun launch(dark: Boolean = true) {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "conversations").putExtra("dark", dark)) as TelegramSocialFixtureActivity
        await { rowOrNull("Alex") != null }
    }

    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (check()) return
            Thread.sleep(40)
        }
        if (!check()) {
            save("failure-${SystemClock.uptimeMillis()}")
            val dump = StringBuilder()
            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                dump.append(" ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                    .append(" desc=").append(node.contentDescription).append(" visible=").append(node.isVisibleToUser).append('\n')
                for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, depth + 1) }
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let { visit(it, 0) }
            File(instrumentation.targetContext.getExternalFilesDir(null), "swipe-failure-tree.txt").writeText(dump.toString())
            fail("Timed out waiting for swipe state")
        }
    }

    private fun rowOrNull(title: String): DialogCell? {
        var result: DialogCell? = null
        instrumentation.runOnMainSync {
            fun find(view: View): DialogCell? {
                if (view is DialogCell && view.contentDescription?.toString()?.startsWith("$title,") == true) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            result = activity?.window?.decorView?.let(::find)
        }
        return result
    }

    private fun bounds(title: String): Rect {
        val row = requireNotNull(rowOrNull(title))
        val rect = Rect()
        instrumentation.runOnMainSync {
            val location = IntArray(2)
            row.getLocationOnScreen(location)
            rect.set(location[0], location[1], location[0] + row.width, location[1] + row.height)
        }
        return rect
    }

    private fun label(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isVisibleToUser && (node.text?.toString() == text || node.contentDescription?.toString() == text)) return node
            return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let(::find) }
        }
        // Compose dialogs have a separate window; rootInActiveWindow can still refer to the
        // underlying inbox while its search field owns the accessibility active-window cache.
        val roots = instrumentation.uiAutomation.windows.mapNotNull { it.root }
        return roots.firstNotNullOfOrNull(::find)
            ?: instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }

    // API32 can retain the removed floating toolbar in its accessibility cache. Read only
    // selection presence from mounted semantics; gestures, Back and AX clicks stay unchanged.
    private fun mountedSelectionCount(count: Int): androidx.compose.ui.semantics.SemanticsNode? {
        var node: androidx.compose.ui.semantics.SemanticsNode? = null
        instrumentation.runOnMainSync {
            node = visibleSteamChatDescription(activity!!.window.decorView,
                activity!!.getString(takagi.ru.monica.R.string.steam_chat_selection_count, count))
        }
        return node
    }

    private fun mountedAllFilter(): androidx.compose.ui.semantics.SemanticsNode? {
        var node: androidx.compose.ui.semantics.SemanticsNode? = null
        instrumentation.runOnMainSync {
            node = visibleSteamChatText(activity!!.window.decorView,
                activity!!.getString(takagi.ru.monica.R.string.steam_chat_filter_all))
        }
        return node
    }

    private fun click(text: String) {
        var node = requireNotNull(label(text))
        while (!node.isClickable) node = requireNotNull(node.parent)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun inject(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        event.recycle()
    }

    private fun swipe(title: String, fraction: Float, cancel: Boolean = false, whileHeld: (() -> Unit)? = null) {
        val rect = bounds(title)
        val startX = rect.left + rect.width() * if (fraction > 0) 0.25f else 0.75f
        val y = rect.exactCenterY()
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, startX, y)
        for (step in 1..18) {
            Thread.sleep(16)
            inject(down, MotionEvent.ACTION_MOVE, startX + rect.width() * fraction * step / 18f, y)
        }
        instrumentation.waitForIdleSync()
        try {
            whileHeld?.invoke()
        } finally {
            inject(down, if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, startX + rect.width() * fraction, y)
        }
        instrumentation.waitForIdleSync()
    }

    private fun save(name: String) {
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "inbox-swipe-shots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }

    @Test fun leftSwipeOpensExistingDeleteConfirmationAndCancelKeepsTheConversation() {
        launch()
        swipe("Alex", -0.36f)
        save("after-left-attempt-dark")
        await { label("删除这个会话？") != null }
        assertEquals("A swipe cannot open the chat", "conversations", activity!!.route)
        assertNotNull(label("保留会话内容"))
        assertNotNull(label("删除会话内容"))
        save("delete-confirmation-dark")
        click("取消删除")
        await { label("删除这个会话？") == null && bounds("Alex").left == 0 }
    }

    @Test fun rightSwipeTogglesMultipleSelectionsAndSelectionModeBlocksDeleteSwipe() {
        launch()
        swipe("Alex", 0.36f)
        await { mountedSelectionCount(1) != null }
        swipe("小林", 0.36f)
        await { mountedSelectionCount(2) != null }
        swipe("Alex", -0.36f)
        assertNull("Left swipe must not delete while selecting", label("删除这个会话？"))
        assertNotNull(mountedSelectionCount(2))
        swipe("Alex", 0.36f)
        await { mountedSelectionCount(1) != null }
        assertEquals("Swipes never open the conversation", "conversations", activity!!.route)
        save("selection-dark")
        click("取消选择")
        await { mountedSelectionCount(1) == null }
    }

    @Test fun shortAndCancelledGesturesSpringBackWithoutAction() {
        launch()
        for (fraction in listOf(0.08f, -0.08f)) {
            swipe("Alex", fraction)
            await { bounds("Alex").left == 0 }
            assertNull(mountedSelectionCount(1))
            assertNull(label("删除这个会话？"))
        }
        for (fraction in listOf(0.36f, -0.36f)) {
            swipe("Alex", fraction, cancel = true)
            await { bounds("Alex").left == 0 }
            assertNull(mountedSelectionCount(1))
            assertNull(label("删除这个会话？"))
        }
        assertEquals("conversations", activity!!.route)
    }

    @Test fun actualSystemBackCancelsSelectionInBothThemes() {
        for (dark in listOf(true, false)) {
            launch(dark)
            swipe("Alex", 0.36f)
            await { mountedSelectionCount(1) != null && bounds("Alex").left == 0 }
            val committed = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                activity!!.window.decorView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                activity!!.window.decorView.invalidate()
            }
            assertTrue(committed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            save("selected-before-system-back-$dark")
            await { activity!!.hasWindowFocus() }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            await { mountedSelectionCount(1) == null && mountedAllFilter() != null }
            assertFalse(activity!!.isFinishing)
            assertEquals("Back cancels selection before leaving the inbox", "conversations", activity!!.route)
            save("selection-after-system-back-$dark")
            instrumentation.runOnMainSync { activity!!.finish() }
        }
    }

    @Test fun unreadRippleStaysInsidePillInBothThemes() {
        for (dark in listOf(true, false)) {
            launch(dark)
            assertNotNull("Read row exists before filtering", rowOrNull("明月"))
            var node = requireNotNull(label("未读"))
            while (!node.isClickable) node = requireNotNull(node.parent)
            val rect = Rect().also(node::getBoundsInScreen)
            save("unread-before-$dark")
            val down = SystemClock.uptimeMillis()
            inject(down, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY())
            try {
                Thread.sleep(160)
                save("unread-held-$dark")
            } finally {
                inject(down, MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY())
            }
            await { rowOrNull("Alex") != null && rowOrNull("明月") == null }
            save("unread-after-$dark")
            instrumentation.runOnMainSync { activity!!.finish() }
        }
    }

    @Test fun bothDirectionsFollowTheFingerAndRevealActionsInBothThemes() {
        for (dark in listOf(true, false)) {
            launch(dark)
            val width = bounds("Alex").width()
            for (fraction in listOf(0.36f, -0.36f)) {
                swipe("Alex", fraction, cancel = true) {
                    val actual = bounds("Alex").left
                    save((if (fraction > 0) "right" else "left") + (if (dark) "-dark" else "-light"))
                    assertEquals("The complete row follows the finger", width * fraction, actual.toFloat(), 4f)
                    assertNotNull("An exposed action has a visible label", label(if (fraction > 0) "选择" else "删除"))
                    save((if (fraction > 0) "right" else "left") + (if (dark) "-dark" else "-light"))
                }
                await { bounds("Alex").left == 0 }
            }
            instrumentation.runOnMainSync { activity!!.finish() }
        }
    }

    @Test fun narrowLargeTypeListKeepsVerticalScrollingAndTapAndLongPress() {
        launch()
        val friends = List(20) { SteamFriend("scroll-$it", SteamFriendRelationship.FRIEND, personaName = "Row $it") }
        val sessions = friends.mapIndexed { index, friend -> SteamChatSession(friend.steamId, 1000L - index, unreadCount = 0) }
        instrumentation.runOnMainSync {
            activity!!.setContent {
                MonicaTheme(darkTheme = false) {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.statusBarsPadding()) {
                                SteamConversationList(
                                    chatState = SteamChatUiState(sessions = SteamChatSessionsSnapshot("fixture", sessions, 0)),
                                    groupState = SteamGroupChatUiState(), friends = friends, query = "",
                                    onOpenDirect = { activity!!.lastFriend = it }, onOpenGroup = { _, _ -> },
                                    onRefresh = {}, onCreateGroup = {},
                                    modifier = Modifier.width(320.dp).height(460.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        await { rowOrNull("Row 2") != null }
        val original = bounds("Row 2")
        val down = SystemClock.uptimeMillis()
        val start = bounds("Row 1")
        inject(down, MotionEvent.ACTION_DOWN, start.exactCenterX(), start.exactCenterY())
        for (step in 1..18) {
            Thread.sleep(16)
            inject(down, MotionEvent.ACTION_MOVE, start.exactCenterX() + step * 0.2f,
                start.exactCenterY() - start.height() * 0.7f * step / 18f)
        }
        Thread.sleep(150)
        inject(down, MotionEvent.ACTION_UP, start.exactCenterX() + 3.6f, start.exactCenterY() - start.height() * 0.7f)
        await { bounds("Row 2").top < original.top - 20 }
        assertNull(mountedSelectionCount(1))
        assertNull(label("删除这个会话？"))
        assertEquals("Vertical scrolling cannot open a conversation", "", activity!!.lastFriend)
        save("narrow-large-type-light")
        val target = bounds("Row 2")
        val tapDown = SystemClock.uptimeMillis()
        inject(tapDown, MotionEvent.ACTION_DOWN, target.exactCenterX(), target.exactCenterY())
        Thread.sleep(30)
        inject(tapDown, MotionEvent.ACTION_UP, target.exactCenterX(), target.exactCenterY())
        await { activity!!.lastFriend == "scroll-2" }
        val pressDown = SystemClock.uptimeMillis()
        inject(pressDown, MotionEvent.ACTION_DOWN, target.exactCenterX(), target.exactCenterY())
        Thread.sleep(650)
        inject(pressDown, MotionEvent.ACTION_UP, target.exactCenterX(), target.exactCenterY())
        await { label("置顶") != null && label("删除会话") != null }
    }
}
