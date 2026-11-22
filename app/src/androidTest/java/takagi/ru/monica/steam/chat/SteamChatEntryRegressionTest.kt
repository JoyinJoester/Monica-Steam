package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.navigation.*
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.navigation.liquidglass.render.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.TelegramChatHeaderView
import com.monica.telegramchat.TelegramRecyclerView
import com.monica.telegramchat.TelegramWallpaperDrawable
import com.monica.telegramchat.TelegramChatColors
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
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.ui.*
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.*
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.ui.theme.MonicaTheme

/** Real production list/thread controls with offline synthetic data and the normal frame clock. */
@RunWith(AndroidJUnit4::class)
class SteamChatEntryRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: TelegramSocialFixtureActivity
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = check() }
            if (ready) return
            Thread.sleep(20)
        }
        main { assertTrue("Mounted UI did not settle", check()) }
    }
    private fun launch() {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "conversations")) as TelegramSocialFixtureActivity
        await { activity.hasWindowFocus() }
    }
    @After fun close() {
        if (::activity.isInitialized) main { activity.finish() }
        instrumentation.waitForIdleSync()
    }
    private fun descendants(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(descendants(root.getChildAt(i)))
    }
    private inline fun <reified T : View> views() = descendants(activity.window.decorView).filterIsInstance<T>()
    private fun row(title: String) = views<DialogCell>().firstOrNull { it.contentDescription?.startsWith("$title,") == true }
    private fun clickFilter(label: String) {
        main {
            var node = requireNotNull(visibleSteamChatText(activity.window.decorView, label))
            while (node.config.getOrNull(SemanticsActions.OnClick) == null) node = requireNotNull(node.parent)
            assertTrue(requireNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
        }
        instrumentation.waitForIdleSync()
    }
    private fun assertSelected(label: String, selected: Boolean) {
        // waitForIdleSync only drains the Android message queue; Compose can still have
        // a frame pending. Observe the mounted selection before making the assertions.
        await {
            var node = visibleSteamChatText(activity.window.decorView, label) ?: return@await false
            while (node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent ?: return@await false
            node.config.getOrNull(SemanticsProperties.Selected) == selected
        }
        main {
            var node = requireNotNull(visibleSteamChatText(activity.window.decorView, label))
            while (node.config.getOrNull(SemanticsActions.OnClick) == null) node = requireNotNull(node.parent)
            assertEquals("$label must share the tab selection model", selected, node.config.getOrNull(SemanticsProperties.Selected))
            assertNull("Filters are tabs, not independent checkboxes", node.config.getOrNull(SemanticsProperties.ToggleableState))
        }
    }
    private val friends = List(40) { SteamFriend("entry-$it", personaName = "Entry $it") }
    private val sessions = friends.mapIndexed { i, f -> SteamChatSession(f.steamId, 10000L - i, unreadCount = if (i == 0) 2 else 0) }

    @Test fun wallpaperMountCostSample() {
        val costs = mutableListOf<Long>()
        repeat(8) {
            main {
                val start = System.nanoTime()
                val wallpaper = TelegramWallpaperDrawable(instrumentation.targetContext)
                wallpaper.setBounds(0, 0, 1080, 2200)
                costs += System.nanoTime() - start
            }
        }
        println("CHAT_WALLPAPER_MOUNT_NS $costs")
    }

    @Test fun wallpaperSharesBoundedMaskAndAppliesCurrentBoundsAndTheme() {
        lateinit var first: TelegramWallpaperDrawable
        lateinit var second: TelegramWallpaperDrawable
        main {
            first = TelegramWallpaperDrawable(instrumentation.targetContext)
            second = TelegramWallpaperDrawable(instrumentation.targetContext)
            first.setBounds(0, 0, 1080, 2200)
            second.setBounds(0, 0, 720, 1280)
            // These changes can precede the asynchronous result. It must use current state.
            first.setBounds(10, 12, 330, 652)
            first.setColors(TelegramChatColors.DAY)
            second.setPatternStrength(0f)
        }
        val tileField = TelegramWallpaperDrawable::class.java.getDeclaredField("tile").apply { isAccessible = true }
        await { tileField.get(first) != null && tileField.get(second) != null }
        main {
            val tile = tileField.get(first) as android.graphics.Bitmap
            assertSame("Visible and glass-source wallpapers share one immutable mask", tile, tileField.get(second))
            assertTrue("Decoration cache is bounded to 5 MiB", tile.allocationByteCount <= 5 * 1024 * 1024)
            val image = android.graphics.Bitmap.createBitmap(340, 664, android.graphics.Bitmap.Config.ARGB_8888)
            first.draw(android.graphics.Canvas(image))
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            assertEquals("Resizing keeps the current origin and clipping", 0, image.getPixel(0, 0))
            assertTrue("Pattern keeps its current light theme", pixels.count { it != 0 && it != TelegramChatColors.DAY.background } > 100)
            image.eraseColor(0)
            second.setBounds(0, 0, 340, 664)
            second.draw(android.graphics.Canvas(image))
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            assertTrue("Late pattern delivery must not replace plain wallpaper", pixels.all { it == TelegramChatColors.DARK_BLUE.background })
            image.recycle()
        }
    }

    @Test fun unreadAndCategoriesAreMutuallyExclusive() {
        launch()
        val group = SteamGroupChatSummary("entry-group", "Read group", defaultChatId = "room",
            rooms = listOf(SteamGroupChatRoom("room", "General")))
        main { activity.setContent { MonicaTheme { Surface(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) {
                SteamConversationList(SteamChatUiState(sessions = SteamChatSessionsSnapshot("entry", sessions, 0)),
                    SteamGroupChatUiState(groups = listOf(group)), friends, "", onOpenDirect = {},
                    onOpenGroup = { _, _ -> }, onRefresh = {}, onCreateGroup = {})
            }
        } } } }
        await { row("Entry 0") != null }
        val all = activity.getString(R.string.steam_chat_filter_all)
        val unread = activity.getString(R.string.steam_chat_unread)
        val direct = activity.getString(R.string.steam_chat_filter_direct)
        val groups = activity.getString(R.string.steam_chat_filter_group)
        clickFilter(unread)
        await { row("Entry 1") == null }
        assertSelected(all, false)
        assertSelected(unread, true)
        clickFilter(unread)
        assertSelected(unread, true)
        assertSelected(all, false)
        clickFilter(groups)
        await { row("Read group") != null && row("Entry 0") == null }
        assertSelected(unread, false)
        assertSelected(groups, true)
        clickFilter(unread)
        await { row("Entry 0") != null && row("Read group") == null }
        clickFilter(direct)
        await { row("Entry 1") != null }
        assertSelected(unread, false)
        assertSelected(direct, true)
        clickFilter(all)
        assertSelected(all, true)
        assertSelected(direct, false)
    }

    @Test fun inboxBottomBoundaryKeepsTheFilterBackdropAndRowsStationary() {
        launch()
        main { activity.setContent { MonicaTheme {
            CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS) {
                SteamConversationList(SteamChatUiState(sessions = SteamChatSessionsSnapshot("entry", sessions, 0)),
                    SteamGroupChatUiState(), friends, "", onOpenDirect = {}, onOpenGroup = { _, _ -> },
                    onRefresh = {}, onCreateGroup = {}, modifier = Modifier.fillMaxSize().statusBarsPadding())
            }
        } } }
        await { row("Entry 0") != null }
        fun scrollingNode(): androidx.compose.ui.semantics.SemanticsNode? {
            fun search(node: androidx.compose.ui.semantics.SemanticsNode): androidx.compose.ui.semantics.SemanticsNode? =
                if (node.config.getOrNull(SemanticsActions.ScrollToIndex) != null) node else node.children.firstNotNullOfOrNull(::search)
            return descendants(activity.window.decorView).filterIsInstance<androidx.compose.ui.platform.ViewRootForTest>()
                .firstNotNullOfOrNull { search(it.semanticsOwner.unmergedRootSemanticsNode) }
        }
        main { assertTrue(requireNotNull(scrollingNode()?.config?.getOrNull(SemanticsActions.ScrollToIndex)?.action).invoke(39)) }
        await { row("Entry 39") != null }
        main { assertTrue(requireNotNull(scrollingNode()?.config?.getOrNull(SemanticsActions.ScrollBy)?.action).invoke(0f, 100000f)) }
        await { scrollingNode()?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { it.maxValue() - it.value() < 1f } == true }
        Thread.sleep(500)
        fun positions(): List<Pair<String, Int>> {
            var result = emptyList<Pair<String, Int>>()
            main { result = views<DialogCell>().map { val xy = IntArray(2); it.getLocationOnScreen(xy); it.contentDescription.toString() to xy[1] } }
            return result
        }
        val first = positions()
        val density = activity.resources.displayMetrics.density
        var top = 0
        main { top = requireNotNull(visibleSteamChatText(activity.window.decorView, activity.getString(R.string.steam_chat_filter_all))).boundsInWindow.top.toInt() }
        fun capture(name: String): android.graphics.Bitmap {
            val full = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val image = android.graphics.Bitmap.createBitmap(full, 0, (top - 8 * density).toInt().coerceAtLeast(0), full.width, (400 * density).toInt())
            full.recycle()
            val dir = File(instrumentation.targetContext.filesDir, "inbox-edge").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            return image
        }
        val before = capture("before")
        val start = SystemClock.uptimeMillis()
        val x = activity.resources.displayMetrics.widthPixels * .5f
        val y = activity.resources.displayMetrics.heightPixels * .55f
        fun send(action: Int, nextY: Float) {
            val event = android.view.MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, nextY, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        send(android.view.MotionEvent.ACTION_DOWN, y)
        repeat(12) { index -> send(android.view.MotionEvent.ACTION_MOVE, y - (index + 1) * 20f); Thread.sleep(20) }
        Thread.sleep(150)
        val held = capture("held")
        send(android.view.MotionEvent.ACTION_UP, y - 240f)
        Thread.sleep(300)
        val released = capture("released")
        assertEquals("Continued inbox boundary drag must not move its rows", first, positions())
        fun difference(a: android.graphics.Bitmap, b: android.graphics.Bitmap): Double {
            var total = 0L; var count = 0
            for (yy in 0 until a.height step 3) for (xx in 0 until a.width step 3) {
                val p = a.getPixel(xx, yy); val q = b.getPixel(xx, yy)
                for (shift in listOf(0, 8, 16)) { total += kotlin.math.abs(((p shr shift) and 255) - ((q shr shift) and 255)); count++ }
            }
            return total.toDouble() / count
        }
        val heldDifference = difference(before, held); val releasedDifference = difference(before, released)
        File(instrumentation.targetContext.filesDir, "inbox-edge/pixels.txt").writeText("held=$heldDifference released=$releasedDifference")
        assertTrue("Filter backdrop must follow the stationary inbox", heldDifference < 1.5)
        assertTrue("Released filter backdrop must stay aligned", releasedDifference < 1.5)
        listOf(before, held, released).forEach { it.recycle() }
    }

    @Test fun actualDockSwitchesToPopulatedInboxWithoutRemountingNativeRows() {
        launch()
        val tabs = SteamDockTab.entries.toList()
        val current = mutableStateOf(tabs.first())
        val mounts = mutableMapOf<SteamDockTab, Int>()
        main { activity.setContent { MonicaTheme {
            CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS) {
                val backdrop = rememberSteamLiquidGlassBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().steamLiquidGlassBackdropSource(backdrop, true)) {
                        SteamTabPageHost(current.value, tabs.toSet(), true, modifier = Modifier.fillMaxSize(),
                            transitionSpec = { _, _ -> steamPageTransition(false) }) { page ->
                            DisposableEffect(Unit) { mounts[page] = (mounts[page] ?: 0) + 1; onDispose {} }
                            if (page == SteamDockTab.CHAT) Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 100.dp)) {
                                SteamConversationList(SteamChatUiState(sessions = SteamChatSessionsSnapshot("entry", sessions, 0)),
                                    SteamGroupChatUiState(), friends, "", onOpenDirect = {}, onOpenGroup = { _, _ -> },
                                    onRefresh = {}, onCreateGroup = {})
                            } else androidx.compose.material3.Text("Synthetic $page", Modifier.fillMaxSize())
                        }
                    }
                    SteamLiquidGlassDock(tabs, current.value, backdrop, { current.value = it },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        } } }
        val labelIds = mapOf(SteamDockTab.TOKEN to R.string.steam_dock_token, SteamDockTab.LIBRARY to R.string.steam_dock_library_label,
            SteamDockTab.STORE to R.string.steam_store_title, SteamDockTab.CHAT to R.string.steam_dock_chat_label,
            SteamDockTab.SETTINGS to R.string.settings_title)
        // setContent can reuse semantics nodes before their replacement layout is placed.
        // Wait for the new frame, then read coordinates from the actual mounted Dock.
        val mountedFrame = CountDownLatch(1)
        main { activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { mountedFrame.countDown() }; activity.window.decorView.invalidate() }
        assertTrue("Replacement Dock layout must commit before touch coordinates are read", mountedFrame.await(5, TimeUnit.SECONDS))
        await {
            val nodes = tabs.map { visibleSteamChatDescription(activity.window.decorView, activity.getString(labelIds.getValue(it))) }
            nodes.all { it != null } && nodes.filterNotNull().zipWithNext().all { (a, b) -> a.boundsInWindow.center.x < b.boundsInWindow.center.x }
        }
        var phase = "mount"
        val trace = mutableListOf<String>()
        fun tapDock(tab: SteamDockTab) {
            phase = "locate $tab (current=${current.value})"
            val label = activity.getString(labelIds.getValue(tab))
            await { visibleSteamChatDescription(activity.window.decorView, label) != null }
            var center = androidx.compose.ui.geometry.Offset.Zero
            main { center = requireNotNull(visibleSteamChatDescription(activity.window.decorView, label)).boundsInWindow.center }
            trace += "$phase at $center"
            val down = SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, center.x, center.y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
            }
            phase = "commit selection $tab (current=${current.value})"
            await { current.value == tab }
        }
        val handlerThread = HandlerThread("dock-inbox-frames").apply { start() }
        val frames = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val samples = org.json.JSONArray()
        val listener = Window.OnFrameMetricsAvailableListener { _, metric, _ -> frames += metric.getMetric(FrameMetrics.TOTAL_DURATION) }
        main { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(handlerThread.looper)) }
        var nativeRows: List<DialogCell>? = null
        try {
            repeat(3) { round -> for (from in tabs.filter { it != SteamDockTab.CHAT }) {
                tapDock(from); Thread.sleep(250)
                val start = SystemClock.elapsedRealtime()
                tapDock(SteamDockTab.CHAT)
                phase = "visible inbox from=$from round=$round current=${current.value}"
                await { row("Entry 0")?.getGlobalVisibleRect(Rect()) == true }
                val committed = CountDownLatch(1)
                main { activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }; activity.window.decorView.invalidate() }
                assertTrue(committed.await(5, TimeUnit.SECONDS))
                val elapsed = SystemClock.elapsedRealtime() - start
                samples.put(org.json.JSONObject().put("from", from.name).put("round", round).put("ms", elapsed))
                assertTrue("Dock must commit its populated inbox within 1 second", elapsed < 1000)
                main {
                    val actual = views<DialogCell>()
                    if (nativeRows == null) nativeRows = actual else {
                        assertEquals(nativeRows!!.size, actual.size)
                        nativeRows!!.zip(actual).forEach { (old, new) -> assertSame("Warm Dock return keeps the actual native row", old, new) }
                    }
                    assertEquals(1, mounts[SteamDockTab.CHAT])
                }
                Thread.sleep(200)
            } }
        } catch (error: Throwable) {
            main {
                trace += "FAILED $phase focus=${activity.hasWindowFocus()} mounts=$mounts rows=${views<DialogCell>().map { it.contentDescription }}"
                fun dump(node: androidx.compose.ui.semantics.SemanticsNode, depth: Int) {
                    trace += "${" ".repeat(depth)}${node.boundsInWindow} ${node.config}"
                    node.children.forEach { dump(it, depth + 1) }
                }
                descendants(activity.window.decorView).filterIsInstance<androidx.compose.ui.platform.ViewRootForTest>()
                    .forEach { dump(it.semanticsOwner.unmergedRootSemanticsNode, 0) }
            }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(instrumentation.targetContext.filesDir, "dock-inbox-failure.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            throw error
        } finally {
            File(instrumentation.targetContext.filesDir, "dock-inbox-trace.txt").writeText(trace.joinToString("\n"))
            main { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            handlerThread.quitSafely(); handlerThread.join(2000)
        }
        val result = org.json.JSONObject().put("switches", samples).put("frameNs", org.json.JSONArray(frames.toList()))
        File(instrumentation.targetContext.filesDir, "dock-inbox-frames.json").writeText(result.toString())
        println("DOCK_INBOX_METRICS $result")
    }

    @Test fun realListToPopulatedThreadFrameSample() {
        launch()
        val selected = mutableStateOf<String?>(null)
        val messages = List(80) { i -> SteamChatMessage("entry-0", if (i % 3 == 0) "entry" else "entry-0",
            10000L + i, i, "Synthetic chat message $i, with enough text to measure the real message rows.") }
        main { activity.setContent { MonicaTheme { Surface(Modifier.fillMaxSize()) {
            BackHandler(enabled = selected.value != null) { selected.value = null }
            SteamChatNavigation(Triple(selected.value, null, null), false, Modifier.fillMaxSize()) { target ->
                if (target.first == null) Box(Modifier.statusBarsPadding()) {
                    SteamConversationList(SteamChatUiState(sessions = SteamChatSessionsSnapshot("entry", sessions, 0)),
                        SteamGroupChatUiState(), friends, "", onOpenDirect = { selected.value = it },
                        onOpenGroup = { _, _ -> }, onRefresh = {}, onCreateGroup = {})
                } else SteamChatThread(
                    state = SteamChatUiState(accountSteamId = "entry", selectedPartnerSteamId = target.first,
                        thread = SteamChatThreadSnapshot("entry", target.first!!, messages, false, 0)),
                    richMediaState = SteamChatRichMediaUiState(), friend = friends.first(),
                    onNavigateBack = { selected.value = null }, onOpenInfo = {}, onRefresh = {}, onLoadOlder = {},
                    onSend = {}, onRetryMessage = {}, onReact = { _, _ -> }, onStickerReply = { _, _ -> }, onReport = { _, _ -> },
                    onAttachmentSelected = {}, onAttachmentSpoilerChanged = {}, onUploadAttachment = {}, onClearAttachment = {},
                    onClearAttachmentFailure = {}, onRefreshCatalogs = {})
            }
        } } } }
        await { row("Entry 0")?.getGlobalVisibleRect(Rect()) == true }
        val handlerThread = HandlerThread("chat-entry-frame-metrics").apply { start() }
        val frames = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val entries = mutableListOf<Long>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) }
        main { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(handlerThread.looper)) }
        try {
            repeat(8) {
                Thread.sleep(400)
                val start = SystemClock.elapsedRealtime()
                main { assertTrue(requireNotNull(row("Entry 0")).performClick()) }
                await { views<TelegramRecyclerView>().singleOrNull()?.childCount?.let { it > 0 } == true }
                val committed = CountDownLatch(1)
                main { activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }; activity.window.decorView.invalidate() }
                assertTrue(committed.await(5, TimeUnit.SECONDS))
                entries += SystemClock.elapsedRealtime() - start
                Thread.sleep(350)
                main { assertEquals(1, views<TelegramChatHeaderView>().size); assertTrue(views<DialogCell>().isEmpty()) }
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                await { row("Entry 0")?.getGlobalVisibleRect(Rect()) == true && views<TelegramChatHeaderView>().isEmpty() }
            }
        } finally {
            main { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            handlerThread.quitSafely(); handlerThread.join(2000)
        }
        val result = org.json.JSONObject().put("entryMs", org.json.JSONArray(entries))
            .put("frameNs", org.json.JSONArray(frames.toList()))
        File(instrumentation.targetContext.filesDir, "chat-entry-frames.json").writeText(result.toString())
        println("CHAT_ENTRY_METRICS $result")
    }
}
