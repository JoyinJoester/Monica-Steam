package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationList
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.*
import takagi.ru.monica.steam.friends.voice.ui.SteamVoiceStatusBanner
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamGlassEffectsEnabled
import takagi.ru.monica.ui.LocalReduceAnimations

/** Production inbox/voice controls, synthetic data, no account/network or global display changes. */
class SteamChatGlassPresentationTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun capture(name: String): Bitmap {
        val latch = CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { latch.countDown() }
            rule.activity.window.decorView.invalidate()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        for (root in listOf(rule.activity.filesDir, rule.activity.getExternalFilesDir(null))) {
            val file = File(root, "chat-glass/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }

    private inner class Pointer(private val point: Offset) {
        private val down = SystemClock.uptimeMillis()
        private val origin = IntArray(2)
        init {
            rule.runOnUiThread { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin) }
            send(MotionEvent.ACTION_DOWN)
        }
        private fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                point.x + origin[0], point.y + origin[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        fun cancel() = send(MotionEvent.ACTION_CANCEL)
        fun up() = send(MotionEvent.ACTION_UP)
    }

    @Test fun realInboxLightHeldLensEscapesItsShellAndCancels() = inbox("light", false, false, true)
    @Test fun realInboxDarkLargeFontHeldLensEscapesItsShellAndCancels() = inbox("dark-large", true, true, true)
    @Test fun realInboxWithoutShadersStillFiltersAndCancels() = inbox("fallback", false, false, false)
    @Test fun realInboxDarkFlatHeldLensAndCancel() = inbox("dark-flat", true, false, true, scrolled = false)

    private fun inbox(name: String, dark: Boolean, large: Boolean, shaders: Boolean, scrolled: Boolean = true) {
        val friends = (1..18).map { SteamFriend("fixture-$it", personaName = "Portal friend $it", gameName = "Co-op lobby") }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
                LocalSteamGlassEffectsEnabled provides shaders,
                LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        Column {
                            Text("Steam Chat", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineMedium)
                            SteamConversationList(
                                chatState = SteamChatUiState(sessions = SteamChatSessionsSnapshot("self",
                                    friends.mapIndexed { i, f -> SteamChatSession(f.steamId, 1000L - i, 0, if (i % 2 == 0) 2 else 0) }, 1000)),
                                groupState = SteamGroupChatUiState(), friends = friends, query = "",
                                onOpenDirect = {}, onOpenGroup = { _, _ -> }, onRefresh = {}, onCreateGroup = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        // Put real conversation rows under the glass, not a checkerboard-only component.
        if (scrolled) rule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        val all = rule.onNodeWithText(rule.activity.getString(R.string.steam_chat_filter_all))
        val slot = all.fetchSemanticsNode().boundsInRoot
        val before = capture("$name-rest")
        val pointer = Pointer(slot.center)
        rule.mainClock.advanceTimeBy(350)
        rule.waitForIdle()
        val held = capture("$name-held")
        val origin = IntArray(2)
        rule.runOnUiThread { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin) }
        // 4dp inner + 6dp outer padding; require changes ABOVE the entire filter bar.
        val density = rule.activity.resources.displayMetrics.density
        val top = (slot.top + origin[1] - 10 * density).toInt()
        var changed = 0
        for (x in (slot.left + origin[0]).toInt() until (slot.right + origin[0]).toInt()) {
            for (y in (top - 24).coerceAtLeast(0) until top) {
                if (before.getPixel(x, y) != held.getPixel(x, y)) changed++
            }
        }
        pointer.cancel()
        rule.waitForIdle()
        all.assertIsSelected()
        capture("$name-cancelled").recycle()
        if (shaders) assertTrue("Real inbox lens must escape the shell: $changed pixels", changed > 10)
        rule.onNodeWithText(rule.activity.getString(R.string.steam_chat_unread)).performClick().assertIsSelected()
        capture("$name-unread").recycle()
        before.recycle(); held.recycle()
    }

    @Test fun voiceControlsAnimateWithoutChangingCallActions() = voice(false)
    @Test fun voiceControlsRespectReducedMotion() = voice(true)

    private fun voice(reduced: Boolean) {
        val state = mutableStateOf(SteamVoiceCallState(accountSteamId = "self",
            state = SteamVoiceConnectionState.CONNECTED,
            target = SteamVoiceTarget(SteamVoiceTargetType.DIRECT, "Portal friend", partnerSteamId = "friend")))
        var toggles = 0
        var leaves = 0
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalReduceAnimations provides reduced) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(top = 64.dp)) {
                            SteamVoiceStatusBanner(state.value, "Portal friend",
                                onLeave = { leaves++ },
                                onToggleMicrophone = { toggles++; state.value = state.value.copy(microphoneMuted = !state.value.microphoneMuted) },
                                onToggleOutput = { state.value = state.value.copy(outputMuted = !state.value.outputMuted) })
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        // The Activity's system-window fade is independent of the Compose clock.
        // Let it settle before comparing the resting and held control screenshots.
        SystemClock.sleep(400)
        val before = capture("voice-$reduced-rest")
        val point = rule.onNodeWithContentDescription("静音").fetchSemanticsNode().boundsInRoot.center
        val pointer = Pointer(point)
        rule.mainClock.advanceTimeBy(250)
        rule.waitForIdle()
        val held = capture("voice-$reduced-held")
        // Material's press ripple remains available with reduced motion; do not
        // confuse ripple pixels with the optional scale/icon transition.
        rule.onNodeWithContentDescription("静音").assertIsDisplayed()
        pointer.up()
        rule.waitForIdle()
        assertEquals(1, toggles)
        rule.onNodeWithContentDescription("打开麦克风").assertIsDisplayed()
        capture("voice-$reduced-muted").recycle()
        rule.onNodeWithContentDescription("打开麦克风").performClick()
        assertEquals(2, toggles)
        rule.onNodeWithContentDescription("声音与播放设备").performClick()
        rule.onNodeWithText("静音声音").performClick()
        assertTrue(state.value.outputMuted)
        rule.onNodeWithContentDescription("离开语音").performClick()
        assertEquals(1, leaves)
        before.recycle(); held.recycle()
    }
}
