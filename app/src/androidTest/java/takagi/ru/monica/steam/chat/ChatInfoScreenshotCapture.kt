package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramSocialFixtureActivity

/** Temporary capture harness: renders the real chat info page and saves PNGs for review. */
@RunWith(AndroidJUnit4::class)
class ChatInfoScreenshotCapture {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun captureChatInfoRoutes() {
        val routes = listOf(
            "group" to "info-group-dark",
            "chat-info" to "info-direct-dark",
            "chat-search" to "history-dark"
        )
        for ((route, name) in routes) {
            val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("route", route)
                .putExtra("dark", true)
            val activity = instrumentation.startActivitySync(intent)
            instrumentation.waitForIdleSync()
            Thread.sleep(6_000)
            save(name)
            instrumentation.runOnMainSync { activity.finish() }
            Thread.sleep(500)
        }
    }

    @Test
    fun captureHistoryFilteredByMedia() {
        // Category and grid mode come in as intent extras: shell taps at hardcoded pixels broke
        // as soon as the filter strip moved, and they raced the first frame.
        captureSearch("history-media-dark", category = "MEDIA")
        captureSearch("history-grid-dark", category = "MEDIA", grid = true)
        captureSearch("history-link-dark", category = "LINK")
    }

    @Test
    fun captureHistorySearchExpanded() {
        // The M3E header hides its title behind the expanded field, so this needs its own shot.
        captureSearch("history-expanded-dark", query = "翻盘", expanded = true)
    }

    private fun captureSearch(
        name: String,
        category: String? = null,
        grid: Boolean = false,
        query: String? = null,
        expanded: Boolean = false,
        dark: Boolean = true
    ) {
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "chat-search")
            .putExtra("dark", dark)
            .apply {
                if (category != null) putExtra("historyCategory", category)
                if (grid) putExtra("historyGrid", true)
                if (query != null) putExtra("historyQuery", query)
                if (expanded) putExtra("historySearchExpanded", true)
            }
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(5_000)
        save(name)
        instrumentation.runOnMainSync { activity.finish() }
        Thread.sleep(500)
    }

    @Test
    fun captureThreadJump() {
        // Both themes: the controls' translucent bars are graded per theme, and the backdrop
        // blur is the thing being judged here.
        for (dark in listOf(true, false)) {
            val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("route", "thread-jump")
                .putExtra("dark", dark)
            val activity = instrumentation.startActivitySync(intent)
            instrumentation.waitForIdleSync()
            Thread.sleep(3_000)
            save(if (dark) "thread-jump-dark" else "thread-jump-light")
            instrumentation.runOnMainSync { activity.finish() }
            Thread.sleep(400)
        }
    }

    @Test
    fun captureChatInfoLight() {
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "chat-info")
            .putExtra("dark", false)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(6_000)
        save("info-direct-light")
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test
    fun captureSearchLight() {
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "chat-search")
            .putExtra("dark", false)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(5_000)
        save("history-light")
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test
    fun captureCompositeGroupAvatars() {
        // One row per member count (1/2/3/4/6) at 40/56/88dp, so the disc sectors and the
        // initial-only tile can be judged at the sizes the app actually uses them.
        for (dark in listOf(true, false)) {
            val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("route", "group-avatar")
                .putExtra("dark", dark)
            val activity = instrumentation.startActivitySync(intent)
            instrumentation.waitForIdleSync()
            Thread.sleep(7_000)
            save(if (dark) "group-avatar-dark" else "group-avatar-light")
            instrumentation.runOnMainSync { activity.finish() }
            Thread.sleep(500)
        }
    }

    @Test
    fun captureInboxTopBar() {
        // The hamburger is gone: the title should now sit flush at the leading edge, and the
        // create-group entry moved into the new-chat contact sheet.
        for (dark in listOf(true, false)) {
            saveInbox(if (dark) "inbox-dark" else "inbox-light", dark = dark, friends = false)
        }
        saveInbox("inbox-friends-dark", dark = true, friends = true)
        saveInbox("inbox-friends-light", dark = false, friends = true)
        saveInbox("inbox-search-dark", dark = true, friends = false, search = true)
        saveInbox("inbox-friends-search-dark", dark = true, friends = true, search = true)
    }

    @Test
    fun captureStoreStyleInbox() {
        // The chat list now borrows the store header: one rounded search field plus tabs.
        for (dark in listOf(true, false)) {
            val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("route", "conversations")
                .putExtra("dark", dark)
            val activity = instrumentation.startActivitySync(intent)
            instrumentation.waitForIdleSync()
            Thread.sleep(5_000)
            save(if (dark) "inbox-store-dark" else "inbox-store-light")
            instrumentation.runOnMainSync { activity.finish() }
            Thread.sleep(400)
        }
    }

    @Test
    fun captureInboxGestures() {
        // Drives the new affordances with real touch events: the archive chip, the long-press menu
        // and the right-swipe multi-selection that ends in the delete dialog.
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "conversations")
            .putExtra("dark", true)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(5_000)
        tap(863f, 352f)
        Thread.sleep(1_500)
        save("inbox-archive-dark")
        tap(80f, 352f)
        Thread.sleep(1_200)
        longPress(400f, 560f)
        Thread.sleep(900)
        save("inbox-menu-dark")
        tap(540f, 1_700f)
        Thread.sleep(700)
        swipeRight(560f)
        Thread.sleep(1_000)
        save("inbox-selection-dark")
        tap(992f, 352f)
        Thread.sleep(1_000)
        save("inbox-delete-dark")
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test
    fun captureUnreadPress() {
        // Holds the unread pill down so the tap ripple is on screen; an unclipped indication shows
        // up here as a translucent rectangle with sharp corners around the capsule.
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "conversations")
            .putExtra("dark", true)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(5_000)
        val now = android.os.SystemClock.uptimeMillis()
        inject(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 166f, 1_975f, 0))
        Thread.sleep(160)
        save("inbox-unread-press")
        inject(android.view.MotionEvent.obtain(now, now + 200, android.view.MotionEvent.ACTION_UP, 166f, 1_975f, 0))
        Thread.sleep(1_200)
        save("inbox-unread-after")
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test
    fun captureInboxScrollReset() {
        // The inbox must land on its newest row: after scrolling down, changing the filter has to
        // bring the list back to the top instead of keeping a now meaningless offset.
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "conversations")
            .putExtra("dark", true)
            .putExtra("many", true)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(5_000)
        save("inbox-scroll-top")
        swipeUp()
        Thread.sleep(900)
        save("inbox-scrolled")
        tap(863f, 352f)
        Thread.sleep(1_200)
        tap(80f, 352f)
        Thread.sleep(1_200)
        save("inbox-after-filter")
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test
    fun captureSelectionBackToFilters() {
        // Back has to end the multi-selection before it leaves the page, so this captures the
        // screen right before and right after the system back key.
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "conversations")
            .putExtra("dark", true)
        val activity = instrumentation.startActivitySync(intent) as androidx.activity.ComponentActivity
        instrumentation.waitForIdleSync()
        Thread.sleep(5_000)
        swipeRight(560f)
        Thread.sleep(1_200)
        save("inbox-selection-before-back")
        // Drive the activity's dispatcher directly: key injection depends on the window focus and
        // tells us nothing about whether the screen registered a back callback.
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        Thread.sleep(1_500)
        save("inbox-selection-after-back")
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test
    fun captureHeaderMenu() {
        // Opening the overflow menu used to paint one fully expanded frame before the cascade
        // started, which read as a flash; keep a still of the settled menu for comparison.
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "thread-jump")
            .putExtra("dark", true)
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(4_000)
        save("thread-menu-closed")
        tap(1_004f, 202f)
        Thread.sleep(900)
        save("thread-menu-open")
        instrumentation.runOnMainSync { activity.finish() }
    }

    private fun inject(event: android.view.MotionEvent) {
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    private fun tap(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        inject(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        inject(android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun longPress(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        inject(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        Thread.sleep(900)
        inject(android.view.MotionEvent.obtain(now, now + 900, android.view.MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun swipeRight(y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        inject(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 120f, y, 0))
        for (step in 1..12) {
            Thread.sleep(16)
            inject(
                android.view.MotionEvent.obtain(
                    now, now + step * 20L, android.view.MotionEvent.ACTION_MOVE,
                    120f + step * 46f, y, 0
                )
            )
        }
        inject(android.view.MotionEvent.obtain(now, now + 320, android.view.MotionEvent.ACTION_UP, 672f, y, 0))
    }

    private fun swipeUp() {
        val now = android.os.SystemClock.uptimeMillis()
        inject(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 540f, 1_450f, 0))
        for (step in 1..10) {
            Thread.sleep(16)
            inject(
                android.view.MotionEvent.obtain(
                    now, now + step * 20L, android.view.MotionEvent.ACTION_MOVE,
                    540f, 1_450f - step * 70f, 0
                )
            )
        }
        inject(android.view.MotionEvent.obtain(now, now + 260, android.view.MotionEvent.ACTION_UP, 540f, 750f, 0))
    }

    private fun saveInbox(
        name: String,
        dark: Boolean,
        friends: Boolean,
        search: Boolean = false
    ) {
        val intent = Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("route", "conversations")
            .putExtra("dark", dark)
            .putExtra("rootFriends", friends)
            .apply {
                if (search) {
                    putExtra("rootSearch", true)
                    putExtra("rootQuery", if (friends) "明" else "月")
                }
            }
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        Thread.sleep(4_000)
        save(name)
        instrumentation.runOnMainSync { activity.finish() }
        Thread.sleep(400)
    }

    private fun save(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "chat-info-shots")
            .apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
