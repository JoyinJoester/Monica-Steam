package takagi.ru.monica.steam.chat

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.*
import com.monica.telegramchat.upstream.DialogCell
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.ui.*
import takagi.ru.monica.steam.friends.domain.*
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.ui.theme.MonicaTheme

/** Offline regressions through the real native rows, popup window and chat navigation. */
@RunWith(AndroidJUnit4::class)
class SteamChatHandoffRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: TelegramSocialFixtureActivity
    private val evidence get() = File(instrumentation.targetContext.getExternalFilesDir(null), "chat-handoff").apply { mkdirs() }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun launch(dark: Boolean = true) {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "conversations").putExtra("dark", dark)) as TelegramSocialFixtureActivity
        await { activity.hasWindowFocus() }
    }
    @After fun close() {
        if (::activity.isInitialized) main { activity.finish() }
        instrumentation.waitForIdleSync()
    }
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
        assertTrue("UI condition did not settle", ready)
    }
    private inline fun <reified T : View> views(root: View): List<T> = descendants(root).filterIsInstance<T>()
    private fun descendants(root: View): List<View> {
        val result = mutableListOf<View>()
        fun visit(view: View) {
            result += view
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        return result
    }
    private fun row(name: String): DialogCell? = views<DialogCell>(activity.window.decorView)
        .firstOrNull { it.contentDescription?.startsWith("$name,") == true }
    private fun visible(name: String): Boolean = row(name)?.getGlobalVisibleRect(Rect()) == true
    private fun committedFrame() {
        val committed = java.util.concurrent.CountDownLatch(1)
        main {
            activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            activity.window.decorView.invalidate()
        }
        assertTrue("A rendered frame was submitted", committed.await(5, java.util.concurrent.TimeUnit.SECONDS))
    }
    private fun emptyInboxIsVisible(): Boolean = visibleSteamChatText(
        activity.window.decorView, activity.getString(takagi.ru.monica.R.string.steam_chat_no_conversations)
    ) != null
    private fun save(name: String) {
        // Row position is asserted before this delay. Allow LazyColumn's normal item fade-in
        // to finish for the final visual still; popup intermediate frames are captured separately.
        if (name in setOf("search-top", "return-top", "reload-top")) Thread.sleep(500)
        committedFrame()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(evidence, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun swipeUp() {
        var r = Rect()
        main { r = Rect().also { activity.window.decorView.getGlobalVisibleRect(it) } }
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float) {
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, r.centerX().toFloat(), y, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(e, true)) } finally { e.recycle() }
        }
        val start = r.top + r.height() * .7f
        event(MotionEvent.ACTION_DOWN, start)
        for (step in 1..18) {
            Thread.sleep(16)
            event(MotionEvent.ACTION_MOVE, start - r.height() * .42f * step / 18f)
        }
        Thread.sleep(150)
        event(MotionEvent.ACTION_UP, start - r.height() * .42f)
        instrumentation.waitForIdleSync()
    }

    @Test fun selectingLongPressTogglesWithoutOpeningTheActionMenu() {
        launch()
        var toggles = 0
        val entry = SteamConversationListEntry(SteamConversationType.DIRECT, "peer", title = "Selection", subtitle = "", timestamp = 0, unreadCount = 0, pinned = false)
        main { activity.setContent {
            MonicaTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.statusBarsPadding()) {
                        SteamConversationRow(entry, true, true, SteamConversationActions(), { toggles++ }, {}, {}, {})
                    }
                }
            }
        } }
        await { row("Selection") != null }
        main { assertTrue(row("Selection")!!.performLongClick()) }
        instrumentation.waitForIdleSync()
        assertEquals("Long press in selection must toggle the row, not show destructive actions", 1, toggles)
        save("selection-long-press")
    }

    private val friends = List(40) { SteamFriend("handoff-$it", SteamFriendRelationship.FRIEND, personaName = "Row $it") }
    private val sessions = friends.mapIndexed { i, f -> SteamChatSession(f.steamId, 10000L - i) }
    private val query = mutableStateOf("")
    private val data = mutableStateOf(sessions)
    private val selected = mutableStateOf<String?>(null)
    private fun listAndThread() {
        main { activity.setContent {
            MonicaTheme {
                Surface(Modifier.fillMaxSize()) {
                    BackHandler(enabled = selected.value != null) { selected.value = null }
                    SteamChatNavigation(Triple(selected.value, null, null), false) { target ->
                        if (target.first == null) {
                            Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                                SteamConversationList(
                                    chatState = SteamChatUiState(sessions = SteamChatSessionsSnapshot("fixture", data.value, 0)),
                                    groupState = SteamGroupChatUiState(), friends = friends, query = query.value,
                                    onOpenDirect = { selected.value = it }, onOpenGroup = { _, _ -> }, onRefresh = {}, onCreateGroup = {}
                                )
                            }
                        } else {
                            SteamChatThread(
                                state = SteamChatUiState(accountSteamId = "fixture", selectedPartnerSteamId = target.first),
                                richMediaState = SteamChatRichMediaUiState(), friend = friends.first { it.steamId == target.first },
                                onNavigateBack = { selected.value = null }, onOpenInfo = {}, onRefresh = {}, onLoadOlder = {},
                                onSend = {}, onRetryMessage = {}, onReact = { _, _ -> }, onStickerReply = { _, _ -> }, onReport = { _, _ -> },
                                onAttachmentSelected = {}, onAttachmentSpoilerChanged = {}, onUploadAttachment = {}, onClearAttachment = {},
                                onClearAttachmentFailure = {}, onRefreshCatalogs = {}
                            )
                        }
                    }
                }
            }
        } }
        await { visible("Row 0") }
    }

    @Test fun changingSearchReturnsToTheFirstResult() {
        launch(); listAndThread(); swipeUp(); swipeUp()
        await { !visible("Row 0") }
        main { query.value = "Row" }
        await { visible("Row 0") }
        save("search-top")
    }

    @Test fun incomingSessionUpdatesKeepTheVisibleAnchor() {
        launch(); listAndThread(); swipeUp(); swipeUp()
        await { !visible("Row 0") }
        var anchor = ""
        var before = Rect()
        main {
            val cell = views<DialogCell>(activity.window.decorView).first { it.getGlobalVisibleRect(Rect()) }
            anchor = cell.contentDescription.toString().substringBefore(',')
            cell.getGlobalVisibleRect(before)
            data.value = sessions.map { it.copy(unreadCount = 2) }
        }
        await { row(anchor)?.contentDescription?.contains(activity.getString(takagi.ru.monica.R.string.steam_chat_unread_count, 2)) == true }
        committedFrame()
        main {
            val after = Rect()
            assertTrue(requireNotNull(row(anchor)).getGlobalVisibleRect(after))
            assertEquals("Live updates preserve the reader's position", before.top, after.top)
            assertFalse(visible("Row 0"))
        }
    }

    @Test fun returningFromRealThreadStartsAtTopEvenWhenRowsArriveLater() {
        launch(); listAndThread()
        val initialTop = Rect()
        main { assertTrue(row("Row 0")!!.getGlobalVisibleRect(initialTop)) }
        swipeUp(); swipeUp()
        await { !visible("Row 0") }
        main {
            val cell = views<DialogCell>(activity.window.decorView).first { it.getGlobalVisibleRect(Rect()) }
            assertTrue(cell.performClick())
        }
        await { views<TelegramChatHeaderView>(activity.window.decorView).isNotEmpty() && !visible("Row 0") }
        save("real-thread")
        main { data.value = emptyList() }
        // Deliver an actual Back key after the thread window owns focus.
        await { activity.hasWindowFocus() }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await { selected.value == null && views<TelegramChatHeaderView>(activity.window.decorView).isEmpty() && views<DialogCell>(activity.window.decorView).isEmpty() && emptyInboxIsVisible() }
        save("return-empty")
        main { data.value = sessions }
        await { visible("Row 0") }
        committedFrame()
        main {
            val restoredTop = Rect()
            assertTrue(row("Row 0")!!.getGlobalVisibleRect(restoredTop))
            assertEquals("The complete first row returns to its original position", initialTop, restoredTop)
        }
        save("return-top")
    }

    @Test fun emptyThenRepopulatedListStartsAtTop() {
        launch(); listAndThread(); swipeUp(); swipeUp()
        await { !visible("Row 0") }
        main { data.value = emptyList() }
        await { views<DialogCell>(activity.window.decorView).isEmpty() && emptyInboxIsVisible() }
        save("reload-empty")
        main { data.value = sessions }
        await { visible("Row 0") }
        save("reload-top")
    }

    private fun popupHost(): TelegramChatHeaderView {
        lateinit var header: TelegramChatHeaderView
        main {
            header = TelegramChatHeaderView(activity)
            val root = FrameLayout(activity)
            root.setBackgroundColor(0xff20232a.toInt())
            root.addView(header, FrameLayout.LayoutParams(-1, TelegramUi.dp(56f)).apply { topMargin = TelegramUi.dp(40f) })
            activity.setContentView(root)
        }
        await { header.width > 0 }
        return header
    }
    private fun popup(): TelegramPopupLayout = TelegramPopupLayout(activity, TelegramChatColors.DARK_BLUE).apply {
        addAction("Chat details", 0, TelegramChatColors.DARK_BLUE, true) {}
        addAction("Search messages", 0, TelegramChatColors.DARK_BLUE, true) {}
        addAction("Unavailable", 0, TelegramChatColors.DARK_BLUE, false) {}
    }

    @Test fun popupDrawFramesGrowMonotonicallyFromHiddenToVisible() {
        launch()
        lateinit var header: TelegramChatHeaderView
        lateinit var content: TelegramPopupLayout
        main { content = popup() }
        header = popupHost()
        val samples = mutableListOf<Pair<Float, Int>>()
        val bitmaps = mutableListOf<Bitmap>()
        val listener = ViewTreeObserver.OnPreDrawListener {
            samples += content.backScaleY to content.backAlpha
            if (bitmaps.size < 30 && content.width > 0 && content.height > 0) {
                bitmaps += Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888).also { content.draw(Canvas(it)) }
            }
            true
        }
        main {
            content.viewTreeObserver.addOnPreDrawListener(listener)
            header.showActionMenu(content)
            assertEquals(if (ValueAnimator.areAnimatorsEnabled()) 0f else 1f, content.backScaleY, 0f)
        }
        await { content.backScaleY == 1f && !content.startAnimationPending }
        main { content.viewTreeObserver.removeOnPreDrawListener(listener) }
        File(evidence, "popup-frames.txt").writeText(samples.joinToString("\n") { "${it.first},${it.second}" })
        bitmaps.forEachIndexed { index, bitmap ->
            File(evidence, "popup-frame-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        assertTrue(samples.isNotEmpty())
        samples.zipWithNext().forEach { (a,b) -> assertTrue("Popup cannot flash full then collapse: $samples", b.first >= a.first && b.second >= a.second) }
        if (ValueAnimator.areAnimatorsEnabled()) assertTrue("Must observe an intermediate draw, not just a closed/open still", samples.any { it.first > 0f && it.first < 1f })
        main { assertEquals(.5f, content.getItemAt(2).alpha, .001f) }
        save("popup-open")
        main { header.dismissActionMenu() }
    }

    @Test fun dismissedPopupCanBeShownAgainWithoutStaleAlphaOrCallbacks() {
        launch()
        val header = popupHost()
        lateinit var content: TelegramPopupLayout
        main { content = popup(); header.showActionMenu(content) }
        await { content.backScaleY == 1f && !content.startAnimationPending }
        main { content.dismiss { header.dismissActionMenu() } }
        await { !content.isAttachedToWindow }
        main { header.showActionMenu(content) }
        await { content.isAttachedToWindow && content.alpha == 1f && content.backAlpha == 255 && !content.startAnimationPending }
        save("popup-reopened")
        main { header.dismissActionMenu() }
    }

    @Test fun interruptedDismissCannotDispatchAnActionOnReopenedPopup() {
        launch()
        val header = popupHost()
        lateinit var content: TelegramPopupLayout
        var staleActions = 0
        main { content = popup(); header.showActionMenu(content) }
        await { content.backScaleY == 1f && !content.startAnimationPending }
        main {
            content.dismiss { staleActions++ }
            header.dismissActionMenu()
            header.showActionMenu(content)
        }
        await { content.alpha == 1f && content.backAlpha == 255 && !content.startAnimationPending }
        // Wait past the old exit duration; reduced motion legitimately executes synchronously.
        Thread.sleep(200)
        main {
            assertEquals(if (ValueAnimator.areAnimatorsEnabled()) 0 else 1, staleActions)
            assertTrue(content.isAttachedToWindow)
            assertEquals(1f, content.alpha, 0f)
            header.dismissActionMenu()
        }
    }
}
