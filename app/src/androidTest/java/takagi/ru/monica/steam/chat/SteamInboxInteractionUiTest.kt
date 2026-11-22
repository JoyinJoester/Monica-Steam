package takagi.ru.monica.steam.chat

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.upstream.DialogCell
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationType
import takagi.ru.monica.steam.friends.chat.ui.SteamChatNavigation
import takagi.ru.monica.steam.friends.chat.ui.SteamChatSubpage
import takagi.ru.monica.steam.friends.chat.ui.SteamChatConversationPreferencesState
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationActions
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationListEntry
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationRow
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationType
import takagi.ru.monica.steam.friends.chat.ui.rememberSteamChatConversationPreferences
import takagi.ru.monica.ui.theme.MonicaTheme

@RunWith(AndroidJUnit4::class)
class SteamInboxInteractionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramSocialFixtureActivity? = null
    private var flags = 0
    private var probe: PreferenceProbe? = null
    private val entry = SteamConversationListEntry(SteamConversationType.DIRECT, "fixture-peer",
        title = "Tap timing", subtitle = "Offline fixture", timestamp = 0, unreadCount = 0, pinned = false)

    @Before fun observeWindows() {
        val info = instrumentation.uiAutomation.serviceInfo
        flags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
    }

    @After fun close() {
        probe?.release?.countDown()
        instrumentation.runOnMainSync { activity?.finish() }
        val info = instrumentation.uiAutomation.serviceInfo
        info.flags = flags
        instrumentation.uiAutomation.serviceInfo = info
    }

    private fun launch(dark: Boolean = true) {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "conversations").putExtra("dark", dark)) as TelegramSocialFixtureActivity
        await { row("小林") != null }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) Thread.sleep(10)
        assertTrue("Timed out waiting for inbox interaction", condition())
    }

    private fun row(title: String): DialogCell? {
        val result = AtomicReference<DialogCell?>()
        instrumentation.runOnMainSync {
            fun find(view: View): DialogCell? {
                if (view is DialogCell && view.contentDescription?.toString()?.startsWith("$title,") == true) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            result.set(activity?.window?.decorView?.let(::find))
        }
        return result.get()
    }

    private fun rowBounds(title: String): Rect {
        val cell = requireNotNull(row(title))
        val result = Rect()
        instrumentation.runOnMainSync {
            val location = IntArray(2)
            cell.getLocationOnScreen(location)
            result.set(location[0], location[1], location[0] + cell.width, location[1] + cell.height)
        }
        return result
    }

    private fun find(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (root.isVisibleToUser && root.text?.toString() == label) return root
        return (0 until root.childCount).firstNotNullOfOrNull { root.getChild(it)?.let { child -> find(child, label) } }
    }

    private fun labelBounds(label: String): Rect? = instrumentation.uiAutomation.windows
        // The inbox also has a "置顶" filter. Only a menu window carries "删除会话".
        .mapNotNull { it.root }.filter { find(it, "删除会话") != null }.firstNotNullOfOrNull { find(it, label) }
        ?.let { Rect().apply { it.getBoundsInScreen(this) } }

    private fun event(down: Long, action: Int, x: Float, y: Float) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
    }

    private fun press(title: String, xFraction: Float, yFraction: Float) {
        val bounds = rowBounds(title)
        val x = bounds.left + bounds.width() * xFraction
        val y = bounds.top + bounds.height() * yFraction
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, x, y)
        try { Thread.sleep(650) } finally { event(down, MotionEvent.ACTION_UP, x, y) }
        await { labelBounds("置顶") != null }
    }

    private fun dismissMenu() {
        // A touch outside the popup exercises its real dismiss path without relying on
        // a keyboard event which can be rejected before the popup gains key focus.
        val dimensions = AtomicReference<android.util.DisplayMetrics>()
        instrumentation.runOnMainSync { dimensions.set(activity!!.resources.displayMetrics) }
        val metrics = dimensions.get()
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, 4f, metrics.heightPixels * 0.5f)
        event(down, MotionEvent.ACTION_UP, 4f, metrics.heightPixels * 0.5f)
        await { labelBounds("置顶") == null }
    }

    private fun save(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "inbox-interaction").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun record(name: String, value: String) {
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.txt").writeText(value)
    }

    @Test fun longPressMenuFollowsTheTouchPointInBothThemes() {
        for (dark in listOf(true, false)) {
            launch(dark)
            val height = rowBounds("小林").height()
            press("小林", 0.2f, 0.15f)
            val first = requireNotNull(labelBounds("置顶"))
            save("menu-low-x-${if (dark) "dark" else "light"}")
            dismissMenu()
            press("小林", 0.65f, 0.85f)
            val second = requireNotNull(labelBounds("置顶"))
            save("menu-high-x-${if (dark) "dark" else "light"}")
            record("menu-position-${if (dark) "dark" else "light"}", "first=$first\nsecond=$second\nrowHeight=$height")
            assertTrue("Menu must move with the horizontal touch position", second.left > first.left + 80)
            assertTrue("Menu must follow the vertical touch point inside the same row", second.top > first.top + height * 0.4f)
            assertEquals("A long press cannot open the conversation", "conversations", activity!!.route)
            dismissMenu()
            tap("小林")
            await { activity!!.route == "friend" }
            instrumentation.runOnMainSync { activity!!.finish() }
        }
    }

    @Test fun bottomEdgeLargeTypeAndKeyboardMenusRemainInsideTheWindow() {
        launch(false)
        for (fontScale in listOf(1f, 1.5f)) {
            instrumentation.runOnMainSync {
                activity!!.setContent {
                    MonicaTheme(darkTheme = false) {
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                                SteamConversationRow(entry, false, false, SteamConversationActions(), {}, {}, {}, {},
                                    Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
            await { row("Tap timing") != null }
            val bounds = rowBounds("Tap timing")
            press("Tap timing", 0.95f, 0.85f)
            val delete = requireNotNull(labelBounds("删除会话"))
            val pin = requireNotNull(labelBounds("置顶"))
            assertTrue("Near the bottom, the menu must open above the finger", pin.bottom < bounds.top)
            assertTrue(delete.right <= activity!!.resources.displayMetrics.widthPixels)
            assertTrue(delete.bottom <= activity!!.resources.displayMetrics.heightPixels)
            assertTrue(pin.left >= 0 && pin.top >= 0)
            save("menu-bottom-light-$fontScale")
            dismissMenu()
            val nativeRow = requireNotNull(row("Tap timing"))
            instrumentation.runOnMainSync { assertTrue(nativeRow.performLongClick()) }
            await { labelBounds("置顶") != null }
            assertTrue(requireNotNull(labelBounds("置顶")).bottom < bounds.top)
            save("menu-keyboard-light-$fontScale")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("input keyevent 4")
            ).use { it.readBytes() }
            await { labelBounds("置顶") == null }
        }
    }

    private fun tap(title: String): Long {
        val bounds = rowBounds(title)
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
        Thread.sleep(30)
        val up = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY())
        return up
    }

    @Test fun nativeTapDispatchHasNoGestureTimeout() {
        launch()
        val clicked = AtomicLong()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                MonicaTheme {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        SteamConversationRow(entry, false, false, SteamConversationActions(), {}, {}, {},
                            onClick = { clicked.set(SystemClock.uptimeMillis()) })
                    }
                }
            }
        }
        await { row("Tap timing") != null }
        val timings = (0 until 8).map {
            clicked.set(0)
            val up = tap("Tap timing")
            await { clicked.get() != 0L }
            clicked.get() - up
        }
        record("native-tap-ms", timings.joinToString())
        assertTrue("Tap callback must not wait for a long-press/swipe timeout: $timings", timings.max() < 140)
    }

    @Test fun conversationTransitionSettlesWithoutTheLongScalePause() {
        launch()
        val clicked = AtomicLong()
        val firstDraw = AtomicLong()
        val settled = AtomicLong()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                MonicaTheme {
                    var peer by remember { mutableStateOf<String?>(null) }
                    SteamChatNavigation(Triple(peer, null, null), false, Modifier.fillMaxSize()) { target ->
                        if (target.first == null) {
                            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                                SteamConversationRow(entry, false, false, SteamConversationActions(), {}, {}, {}, onClick = {
                                    clicked.set(SystemClock.uptimeMillis()); peer = entry.id
                                })
                            }
                        } else {
                            SideEffect { firstDraw.compareAndSet(0, SystemClock.uptimeMillis()) }
                            LaunchedEffect(Unit) {
                                snapshotFlow { transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible }
                                    .first { it }
                                settled.compareAndSet(0, SystemClock.uptimeMillis())
                            }
                            Surface(Modifier.fillMaxSize(), color = Color(0xffcceedd)) { Text("Conversation fixture") }
                        }
                    }
                }
            }
        }
        await { row("Tap timing") != null }
        tap("Tap timing")
        await { settled.get() != 0L }
        record("navigation-ms", "firstComposition=${firstDraw.get() - clicked.get()}\nsettled=${settled.get() - clicked.get()}")
        assertTrue("Conversation should settle within 320 ms; actual=${settled.get() - clicked.get()}", settled.get() - clicked.get() < 320)
        save("conversation-transition-settled")
    }

    private class PreferenceProbe(context: Context) : ContextWrapper(context) {
        val mainReads = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        @Volatile var enabled = false
        @Volatile var blockFirst = false
        val reads = AtomicInteger()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val delegate = super.getSharedPreferences(name, mode)
            if (name != "steam_chat_conversation_preferences") return delegate
            return object : SharedPreferences by delegate {
                override fun getString(key: String?, defValue: String?): String? {
                    if (enabled) {
                        if (Looper.myLooper() == Looper.getMainLooper()) mainReads.incrementAndGet()
                        val ordinal = reads.incrementAndGet()
                        started.countDown()
                        if (blockFirst && ordinal == 1) release.await(3, TimeUnit.SECONDS) else SystemClock.sleep(120)
                    }
                    return delegate.getString(key, defValue)
                }
            }
        }
    }

    @Test fun preferenceReadRunsOffTheUiThread() {
        launch()
        val context = PreferenceProbe(instrumentation.targetContext).also { probe = it }
        val store = SteamChatInfoPreferencesStore(context)
        var open: (() -> Unit)? = null
        val observedState = AtomicReference<SteamChatConversationPreferencesState?>()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                var id by remember { mutableStateOf<SteamChatConversationId?>(null) }
                val state = rememberSteamChatConversationPreferences(store, id)
                val preferences by state
                SideEffect { observedState.set(state) }
                SideEffect { open = { id = SteamChatConversationId("fixture-account", SteamChatConversationType.DIRECT, "probe-a") } }
                Text("pinned=${preferences.pinned}")
            }
        }
        await { open != null }
        context.blockFirst = true
        context.enabled = true
        instrumentation.runOnMainSync { open!!() }
        assertTrue(context.started.await(3, TimeUnit.SECONDS))
        record("preference-main-reads", "mainReads=${context.mainReads.get()}")
        assertEquals("Encrypted preference lookup must not block a navigation frame", 0, context.mainReads.get())
        await { observedState.get()?.loaded == false }
        assertFalse("Editable preferences are not ready while the read is blocked", requireNotNull(observedState.get()).loaded)
        context.release.countDown()
        await { observedState.get()?.loaded == true }
    }

    @Test fun cancelledPreferenceReadCannotRestoreThePreviousConversation() {
        launch()
        val context = PreferenceProbe(instrumentation.targetContext).also { probe = it }
        val store = SteamChatInfoPreferencesStore(context)
        val first = SteamChatConversationId("fixture-account", SteamChatConversationType.DIRECT, "probe-a")
        val second = SteamChatConversationId("fixture-account", SteamChatConversationType.DIRECT, "probe-b")
        val preferences = context.getSharedPreferences("steam_chat_conversation_preferences", Context.MODE_PRIVATE)
        val observed = AtomicReference("")
        var select: ((SteamChatConversationId) -> Unit)? = null
        preferences.edit().putString(store.key(first), "{\"pinned\":true}").putString(store.key(second), "{\"muted\":true}").commit()
        instrumentation.runOnMainSync {
            activity!!.setContent {
                var id by remember { mutableStateOf<SteamChatConversationId?>(null) }
                val current by rememberSteamChatConversationPreferences(store, id)
                // Read snapshot state during composition, then publish that committed value.
                // Reading it only inside SideEffect would not subscribe this UI to preference changes.
                val rendered = "${id?.peerOrGroupId}:${current.pinned}:${current.muted}"
                SideEffect { select = { id = it }; observed.set(rendered) }
                Text(rendered)
            }
        }
        await { select != null }
        context.blockFirst = true
        context.enabled = true
        instrumentation.runOnMainSync { select!!(first) }
        assertTrue(context.started.await(3, TimeUnit.SECONDS))
        activity!!.runOnUiThread { select!!(second) }
        try {
            val deadline = SystemClock.uptimeMillis() + 700
            while (context.reads.get() < 2 && SystemClock.uptimeMillis() < deadline) Thread.sleep(10)
            assertTrue("Switching conversations must not wait for the old preference read", context.reads.get() >= 2)
            await { observed.get() == "probe-b:false:true" }
        } finally { context.release.countDown() }
        Thread.sleep(250)
        assertEquals("probe-b:false:true", observed.get())
        record("preference-cancellation", observed.get())
    }
}
