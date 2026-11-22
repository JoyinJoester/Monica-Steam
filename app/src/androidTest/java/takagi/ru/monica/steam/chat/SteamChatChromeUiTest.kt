package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.TelegramChatComposerView
import com.monica.telegramchat.TelegramChatHeaderView
import com.monica.telegramchat.TelegramEdgeBlurView
import org.hamcrest.Matchers.allOf
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.steam.TelegramChatFixtureActivity
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile

@RunWith(AndroidJUnit4::class)
class SteamChatChromeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramChatFixtureActivity? = null
    private fun text(id: Int) = instrumentation.targetContext.getString(id)
    @After fun close() {
        if (activity != null) save("last-" + if (activity!!.intent.getBooleanExtra("group", false)) "group" else "direct")
        instrumentation.runOnMainSync { activity?.finish() }; activity = null
    }
    private fun launch(group: Boolean = false, dark: Boolean = true, long: Boolean = true) {
        close()
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40, 125, 170))
        val paint = Paint().apply { color = Color.rgb(250, 180, 80) }
        canvas.drawCircle(64f, 64f, 38f, paint)
        steamRemoteImageCacheFile(instrumentation.targetContext, "https://avatars.fastly.steamstatic.com/synthetic-chrome.jpg")
            .apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramChatFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("chrome", true).putExtra("rich", true)
            .putExtra("group", group).putExtra("dark", dark).putExtra("long", long)
            .putExtra("fixtureKey", "chrome-${System.nanoTime()}")) as TelegramChatFixtureActivity
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }
    private inline fun <reified T : View> find(): T? = find(T::class.java)
    private fun <T : View> find(type: Class<T>): T? {
        fun traverse(view: View): T? = if (type.isInstance(view)) type.cast(view) else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { traverse(view.getChildAt(it)) } else null
        return activity?.window?.decorView?.let(::traverse)
    }
    private fun await(condition: () -> Boolean) = compose.waitUntil(10_000, condition)
    private fun save(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(180) // Capture a committed Surface frame, rather than the previous buffer.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "chat-chrome").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val privateDirectory = File(instrumentation.targetContext.filesDir, "chat-chrome").apply { mkdirs() }
        File(privateDirectory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun search(group: Boolean) {
        for (dark in listOf(true, false)) {
            launch(group = group, dark = dark)
            val list = requireNotNull(find<RecyclerView>())
            val editorMatcher = allOf(isAssignableFrom(EditText::class.java), isDescendantOfA(isAssignableFrom(TelegramChatComposerView::class.java)))
            onView(editorMatcher).perform(replaceText("Draft survives search"))
            onView(allOf(withContentDescription("More options"), isDescendantOfA(isAssignableFrom(TelegramChatHeaderView::class.java))))
                .perform(click())
            onView(withText(text(R.string.steam_thread_search))).perform(click())
            val searchMatcher = allOf(isAssignableFrom(EditText::class.java), withHint(text(R.string.steam_thread_search)))
            onView(searchMatcher).perform(replaceText("Portal"))
            await { compose.onAllNodesWithText("1 / 16").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 / 16").assertExists()
            assertSame("Search must retain the original message list", list, find<RecyclerView>())
            compose.onNodeWithText(text(R.string.steam_history_media)).performClick()
            compose.onNodeWithText(text(R.string.steam_thread_no_matches)).assertExists()
            compose.onNodeWithText(text(R.string.steam_history_all)).performClick()
            compose.onNodeWithText("1 / 16").assertExists()
            compose.onNodeWithContentDescription(text(R.string.steam_thread_search_filters)).performClick()
            compose.onNodeWithText(text(R.string.steam_history_today)).performClick()
            compose.onNodeWithText(text(R.string.steam_thread_no_matches)).assertExists()
            compose.onNodeWithContentDescription(text(R.string.steam_thread_search_filters)).performClick()
            compose.onNodeWithText(text(R.string.steam_history_any_time)).performClick()
            compose.onNodeWithText("1 / 16").assertExists()
            compose.onNodeWithContentDescription(text(R.string.steam_thread_search_filters)).performClick()
            compose.onNodeWithText("我").performClick()
            compose.onNodeWithText(text(R.string.steam_thread_no_matches)).assertExists()
            compose.onNodeWithContentDescription(text(R.string.steam_thread_search_filters)).performClick()
            compose.onNodeWithText(text(R.string.steam_thread_all_senders)).performClick()
            compose.onNodeWithText("1 / 16").assertExists()
            compose.onNodeWithContentDescription(text(R.string.steam_thread_older_match)).performClick()
            compose.onNodeWithText("2 / 16").assertExists()
            onView(searchMatcher).perform(pressImeActionButton())
            await { activity!!.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == false }
            save("search-${if (group) "group" else "direct"}-${if (dark) "dark" else "light"}")
            compose.onNodeWithContentDescription(text(R.string.steam_thread_newer_match)).performClick()
            compose.onNodeWithText("1 / 16").assertExists()
            onView(searchMatcher).perform(replaceText("no-such-offline-message"))
            compose.onNodeWithText(text(R.string.steam_thread_no_matches)).assertExists()
            onView(searchMatcher).perform(replaceText("Portal"))
            compose.onNodeWithText(text(R.string.steam_thread_results_list)).performClick()
            File(instrumentation.targetContext.filesDir, "search-before-click.txt").writeText(compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).printToString(maxDepth = 100))
            // Native message cells have separate Compose roots with the same card text.
            // Select the result in the modal window, rather than a covered message cell.
            compose.onAllNodes(hasText("Portal guide") and hasAnyAncestor(isDialog())).onFirst().performClick()
            compose.waitForIdle()
            try {
                await { compose.onAllNodesWithContentDescription(text(R.string.steam_chat_close_search)).fetchSemanticsNodes().isNotEmpty() }
            } catch (error: Throwable) {
                File(instrumentation.targetContext.filesDir, "search-after-click.txt").writeText(compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).printToString(maxDepth = 100))
                save("search-result-failure")
                instrumentation.uiAutomation.rootInActiveWindow?.let { File(instrumentation.targetContext.filesDir, "search-window.txt").writeText(it.toString()) }
                throw error
            }
            compose.onAllNodesWithContentDescription(text(R.string.steam_chat_close_search)).onFirst().performClick()
            onView(searchMatcher).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(editorMatcher).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
            await { find<EditText>()?.text?.toString() == "Draft survives search" }
            save("conversation-${if (group) "group" else "direct"}-${if (dark) "dark" else "light"}")
            assertSame("Closing search keeps the same list", list, find<RecyclerView>())
            assertTrue(activity!!.sent.isEmpty())
            // The controls end above system navigation; the gradient's view extends to the edge.
            val edge = mutableListOf<TelegramEdgeBlurView>()
            fun collect(view: View) {
                if (view is TelegramEdgeBlurView) edge += view
                if (view is ViewGroup) repeat(view.childCount) { collect(view.getChildAt(it)) }
            }
            instrumentation.runOnMainSync { collect(activity!!.window.decorView) }
            val bottom = edge.maxBy { val xy = IntArray(2); it.getLocationOnScreen(xy); xy[1] }
            val location = IntArray(2); bottom.getLocationOnScreen(location)
            val ime = activity!!.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            assertTrue("Backdrop reaches the window/IME edge", kotlin.math.abs(location[1] + bottom.height - (activity!!.window.decorView.height - ime)) <= 3)
        }
    }
    @Test fun directSearchRetainsMessagesAndDraftInBothThemes() = search(false)
    @Test fun groupSearchRetainsMessagesAndDraftInBothThemes() = search(true)

    @Test fun sharedMediaLivesAtTheEndOfPrivateAndGroupProfiles() {
        for ((group, dark) in listOf(false to true, true to true, false to false, true to false)) {
            launch(group = group, dark = dark, long = false)
            onView(withContentDescription(if (group) "Monica Steam 测试群" else "小林")).perform(click())
            compose.onNode(hasScrollToIndexAction()).performScrollToKey("shared-history-header")
            compose.onNodeWithText(text(R.string.steam_thread_shared_content)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.steam_history_media)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.steam_history_links)).assertExists()
            compose.onNodeWithText("查找记录").assertDoesNotExist()
            save("profile-${if (group) "group" else "direct"}-${if (dark) "dark" else "light"}-media")
            compose.onNodeWithText(text(R.string.steam_history_links)).assertIsDisplayed().performClick()
            val link = hasText("Portal guide")
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(link)
            compose.onNode(link).assertIsDisplayed().performClick()
            await { activity!!.sharedOpened != null && !activity!!.fixtureInfoOpen && find<RecyclerView>() != null }
            val linkId = activity!!.sharedOpened
            onView(withContentDescription(if (group) "Monica Steam 测试群" else "小林")).perform(click())
            compose.onNode(hasScrollToIndexAction()).performScrollToKey("shared-history-header")
            val shared = hasContentDescription(text(R.string.steam_thread_open_shared_message).substringBefore('%'), substring = true)
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(shared)
            compose.onAllNodes(shared).onFirst().assertIsDisplayed().performClick()
            await { activity!!.sharedOpened != linkId && !activity!!.fixtureInfoOpen && find<RecyclerView>() != null }
            assertTrue(activity!!.sent.isEmpty())
        }
    }

    @Test fun continuedDraggingAtBothListEdgesKeepsViewportAndTopBlurInSync() {
        launch(long = true)
        val list = requireNotNull(find<RecyclerView>())
        await { list.childCount > 0 }
        fun descendants(view: View): List<View> = buildList {
            add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
        }
        lateinit var top: TelegramEdgeBlurView
        instrumentation.runOnMainSync {
            top = descendants(activity!!.window.decorView).filterIsInstance<TelegramEdgeBlurView>()
                .minBy { val at = IntArray(2); it.getLocationOnScreen(at); at[1] }
            assertEquals(View.OVER_SCROLL_NEVER, list.overScrollMode)
        }
        fun positions(): List<Pair<Int, Int>> {
            var result = emptyList<Pair<Int, Int>>()
            instrumentation.runOnMainSync { result = (0 until list.childCount).map {
                list.getChildAt(it).let { child -> list.getChildAdapterPosition(child) to child.top }
            } }
            return result
        }
        fun capture(name: String): Bitmap {
            val bounds = android.graphics.Rect()
            instrumentation.runOnMainSync { assertTrue(top.getGlobalVisibleRect(bounds)) }
            val full = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val crop = Bitmap.createBitmap(full, bounds.left, bounds.top, bounds.width(), bounds.height())
            full.recycle()
            val dir = File(instrumentation.targetContext.filesDir, "edge-drag").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return crop
        }
        fun difference(a: Bitmap, b: Bitmap): Double {
            var total = 0L
            for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) {
                val first = a.getPixel(x, y); val second = b.getPixel(x, y)
                for (shift in listOf(0, 8, 16)) total += kotlin.math.abs(((first shr shift) and 255) - ((second shr shift) and 255))
            }
            return total.toDouble() / (((a.width + 1) / 2) * ((a.height + 1) / 2) * 3)
        }
        for (direction in listOf(-1, 1)) {
            instrumentation.runOnMainSync { list.stopScroll(); list.scrollBy(0, direction * 100000) }
            compose.waitForIdle(); Thread.sleep(500)
            instrumentation.runOnMainSync { assertFalse("Must reach the actual list edge", list.canScrollVertically(direction)) }
            val beforePositions = positions()
            val name = if (direction < 0) "top" else "bottom"
            val before = capture("$name-before")
            val start = android.os.SystemClock.uptimeMillis()
            val x = list.width * .5f; val y = list.height * .5f
            fun send(action: Int, nextY: Float) {
                val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(), action, x, nextY, 0)
                try { instrumentation.runOnMainSync { list.dispatchTouchEvent(event) } } finally { event.recycle() }
            }
            send(android.view.MotionEvent.ACTION_DOWN, y)
            repeat(12) { index ->
                send(android.view.MotionEvent.ACTION_MOVE, y - direction * (index + 1) * 20f)
                Thread.sleep(20)
                assertEquals("Boundary drag must not move rendered rows", beforePositions, positions())
            }
            Thread.sleep(100)
            val held = capture("$name-held")
            send(android.view.MotionEvent.ACTION_UP, y - direction * 240f)
            compose.waitForIdle(); Thread.sleep(160)
            val released = capture("$name-released")
            assertEquals(beforePositions, positions())
            val heldDifference = difference(before, held); val releasedDifference = difference(before, released)
            println("EDGE_DRAG_PIXELS $name held=$heldDifference released=$releasedDifference")
            assertTrue("Top blur must not drift while its source is stationary", heldDifference < 1.5)
            assertTrue("Top blur must stay aligned after release", releasedDifference < 1.5)
            listOf(before, held, released).forEach { it.recycle() }
        }
    }

    @Test fun gaussianRadiusAndTintFadeTowardsContentFromBothEdges() {
        launch(long = false)
        val density = activity!!.resources.displayMetrics.density
        val stripe = (8 * density).toInt()
        lateinit var source: View
        lateinit var top: TelegramEdgeBlurView
        lateinit var bottom: TelegramEdgeBlurView
        instrumentation.runOnMainSync {
            val frame = FrameLayout(activity!!)
            source = object : View(activity) {
                val paint = Paint()
                override fun onDraw(canvas: Canvas) {
                    for (x in 0..width step stripe) {
                        paint.color = if ((x / stripe) % 2 == 0) Color.WHITE else Color.BLACK
                        canvas.drawRect(x.toFloat(), 0f, (x + stripe).toFloat(), height.toFloat(), paint)
                    }
                }
            }
            frame.addView(source, FrameLayout.LayoutParams(-1, -1))
            top = TelegramEdgeBlurView(activity!!).apply { setSourceView(source); setTopEdge(true); setTintColor(Color.TRANSPARENT) }
            bottom = TelegramEdgeBlurView(activity!!).apply { setSourceView(source); setTopEdge(false); setTintColor(Color.TRANSPARENT) }
            frame.addView(top, FrameLayout.LayoutParams(-1, (150 * density).toInt(), Gravity.TOP))
            frame.addView(bottom, FrameLayout.LayoutParams(-1, (150 * density).toInt(), Gravity.BOTTOM))
            activity!!.setContentView(frame)
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        fun contrast(view: View, fraction: Float): Double {
            val location = IntArray(2); view.getLocationOnScreen(location)
            val y = location[1] + (fraction * view.height).toInt()
            val samples = (2..9).map { index -> Color.red(screenshot.getPixel(location[0] + index * stripe + stripe / 2, y)).toDouble() }
            return samples.max() - samples.min()
        }
        val topValues = listOf(.15f, .5f, .85f).map { contrast(top, it) }
        val bottomValues = listOf(.15f, .5f, .85f).map { contrast(bottom, it) }
        File(instrumentation.targetContext.getExternalFilesDir(null), "edge-contrast.txt")
            .writeText("top=$topValues\nbottom=$bottomValues\n")
        assertTrue("Top strong to weak: $topValues", topValues[0] + 10 < topValues[1] && topValues[1] + 10 < topValues[2])
        assertTrue("Bottom weak to strong: $bottomValues", bottomValues[0] > bottomValues[1] + 10 && bottomValues[1] > bottomValues[2] + 10)
        screenshot.recycle()
        save("gradient-radius-probe")
    }
}
