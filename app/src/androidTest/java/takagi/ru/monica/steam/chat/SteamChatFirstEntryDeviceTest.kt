package takagi.ru.monica.steam.chat

import android.content.Intent
import android.os.*
import android.view.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.library.ui.SteamLibraryScreen
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.ui.*
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.navigation.*
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.navigation.liquidglass.render.*
import takagi.ru.monica.ui.theme.MonicaTheme

/** Run this method in a fresh instrumentation process for each cold-entry sample. */
class SteamChatFirstEntryDeviceTest {
    @Test fun firstAndWarmDockEntry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val mode = arguments.getString("chatMode") ?: "screen"
        val origin = SteamDockTab.valueOf(arguments.getString("origin") ?: "LIBRARY")
        val traced = arguments.getString("sampleTrace") == "true"
        val chatStyle = if (arguments.getString("chatStyle") == "fixed") SteamDockStyle.FIXED else SteamDockStyle.LIQUID_GLASS
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
            SteamComposeFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SteamComposeFixtureActivity
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(check: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < end) {
                var ready = false
                main { ready = check() }
                if (ready) return
                Thread.sleep(15)
            }
            main { assertTrue("UI did not settle", check()) }
        }
        val current = mutableStateOf(origin)
        val tabs = SteamDockTab.entries.toList()
        val friends = List(100) { SteamFriend("cold-$it", personaName = "Cold player $it") }
        val sessions = friends.mapIndexed { i, f -> SteamChatSession(f.steamId, 10000L - i, unreadCount = i % 4) }
        var pending: CountDownLatch? = null
        var selectedAt = 0L
        var committedAt = 0L
        var mounts = 0
        val frames = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        val collector = HandlerThread("chat-first-frames").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metric, dropped ->
            frames += JSONObject().put("totalNs", metric.getMetric(FrameMetrics.TOTAL_DURATION))
                .put("layoutNs", metric.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
                .put("drawNs", metric.getMetric(FrameMetrics.DRAW_DURATION))
                .put("syncNs", metric.getMetric(FrameMetrics.SYNC_DURATION)).put("dropped", dropped)
        }
        val result = JSONObject().put("mode", mode).put("origin", origin.name).put("pid", Process.myPid()).put("chatStyle", chatStyle.name)
        val samples = JSONArray()
        try {
            await { activity.hasWindowFocus() }
            main {
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(collector.looper))
                activity.setContent { MonicaTheme {
                    CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS) {
                        val backdrop = rememberSteamLiquidGlassBackdrop()
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().steamLiquidGlassBackdropSource(backdrop, true)) {
                                SteamTabPageHost(current.value, tabs.toSet(), true, modifier = Modifier.fillMaxSize(),
                                    transitionSpec = { _, _ -> steamPageTransition(false) }) { page ->
                                    if (page == SteamDockTab.CHAT) {
                                        DisposableEffect(Unit) { mounts++; onDispose {} }
                                        CompositionLocalProvider(LocalSteamDesignStyle provides chatStyle) {
                                        if (mode.endsWith("screen")) SteamChatScreen(standalone = true, modifier = Modifier.fillMaxSize())
                                        else Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 100.dp)) {
                                            SteamConversationList(SteamChatUiState(sessions = SteamChatSessionsSnapshot("cold", sessions, 0)),
                                                SteamGroupChatUiState(), friends, "", onOpenDirect = {}, onOpenGroup = { _, _ -> },
                                                onRefresh = {}, onCreateGroup = {})
                                        }
                                        }
                                    } else if (mode.startsWith("library-") && page == SteamDockTab.LIBRARY) {
                                        SteamLibraryScreen(onNavigateBack = {}, modifier = Modifier.fillMaxSize())
                                    } else Text("Synthetic $page", Modifier.fillMaxSize())
                                }
                            }
                            SteamLiquidGlassDock(tabs, current.value, backdrop, {
                                selectedAt = System.nanoTime()
                                current.value = it
                            }, modifier = Modifier.align(Alignment.BottomCenter))
                            val selection = current.value
                            SideEffect {
                                if (selection == SteamDockTab.CHAT) pending?.let { latch ->
                                    pending = null
                                    activity.window.decorView.viewTreeObserver.registerFrameCommitCallback {
                                        committedAt = System.nanoTime(); latch.countDown()
                                    }
                                }
                            }
                        }
                    }
                } }
            }
            val labels = mapOf(SteamDockTab.TOKEN to R.string.steam_dock_token,
                SteamDockTab.LIBRARY to R.string.steam_dock_library_label, SteamDockTab.STORE to R.string.steam_store_title,
                SteamDockTab.CHAT to R.string.steam_dock_chat_label, SteamDockTab.SETTINGS to R.string.settings_title)
            fun tap(tab: SteamDockTab) {
                val label = activity.getString(labels.getValue(tab))
                await { visibleSteamChatDescription(activity.window.decorView, label) != null }
                var point = androidx.compose.ui.geometry.Offset.Zero
                main { point = requireNotNull(visibleSteamChatDescription(activity.window.decorView, label)).boundsInWindow.center }
                val down = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
            }
            await { visibleSteamChatDescription(activity.window.decorView, activity.getString(R.string.steam_dock_chat_label)) != null }
            Thread.sleep(500)
            if (traced) Debug.startMethodTracingSampling(File(activity.filesDir, "chat-first.trace").absolutePath, 16 * 1024 * 1024, 500)
            repeat(4) { round ->
                val committed = CountDownLatch(1)
                main { pending = committed; selectedAt = 0; committedAt = 0 }
                frames.clear()
                val touchedAt = System.nanoTime()
                tap(SteamDockTab.CHAT)
                assertTrue("First selected chat frame must commit", committed.await(15, TimeUnit.SECONDS))
                Thread.sleep(250)
                val sample = JSONObject().put("round", round).put("selectToCommitMs", (committedAt - selectedAt) / 1e6)
                    .put("touchToCommitMs", (committedAt - touchedAt) / 1e6)
                    .put("frames", JSONArray(synchronized(frames) { frames.toList() }))
                assertTrue(selectedAt > 0 && committedAt >= selectedAt)
                samples.put(sample)
                if (traced && round == 0) Debug.stopMethodTracing()
                main { assertEquals("Warm entry must reuse the chat page", 1, mounts) }
                tap(origin)
                await { current.value == origin }
                Thread.sleep(250)
            }
            result.put("samples", samples).put("mounts", mounts)
            File(activity.filesDir, "chat-first.json").writeText(result.toString(2))
            println("CHAT_FIRST_ENTRY $result")
        } finally {
            main { activity.window.removeOnFrameMetricsAvailableListener(listener); activity.finish() }
            collector.quitSafely(); collector.join(2000)
        }
    }
}
