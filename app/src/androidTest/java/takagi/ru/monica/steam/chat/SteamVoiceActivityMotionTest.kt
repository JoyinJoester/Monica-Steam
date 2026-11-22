package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.voice.domain.*
import takagi.ru.monica.steam.friends.voice.ui.*
import takagi.ru.monica.ui.LocalReduceAnimations

class SteamVoiceActivityMotionTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()

    private fun image(tag: String) = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
    private fun changed(a: Bitmap, b: Bitmap): Int {
        if (a.width != b.width || a.height != b.height) return Int.MAX_VALUE
        var count = 0
        for (x in 0 until a.width) for (y in 0 until a.height) if (a.getPixel(x, y) != b.getPixel(x, y)) count++
        return count
    }
    private fun save(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(rule.activity.filesDir, "voice-motion/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun statusHasIntermediateFrameAndLatestAccessibleLabel() = status(false)
    @Test fun reducedStatusSnapsWithoutOldAccessibleText() = status(true)

    private fun status(reduced: Boolean) {
        val label = mutableStateOf("Connecting voice chat")
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalReduceAnimations provides reduced) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(24.dp)) {
                            SteamVoiceStatusText(label.value, Modifier.width(260.dp).testTag("status"))
                        }
                    }
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.autoAdvance = false
        val before = image("status")
        rule.runOnUiThread { label.value = "Waiting for an answer" }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithContentDescription("Waiting for an answer").assertExists()
        rule.onNodeWithContentDescription("Connecting voice chat").assertDoesNotExist()
        val middle = image("status")
        rule.mainClock.advanceTimeBy(400)
        val after = image("status")
        assertTrue(changed(before, after) > 30)
        if (reduced) assertEquals(0, changed(middle, after)) else assertTrue(changed(middle, after) > 20)
        // Interrupt an in-flight transition twice; only the newest status is announced.
        rule.runOnUiThread { label.value = "Reconnecting" }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnUiThread { label.value = "Voice chat connected" }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithContentDescription("Voice chat connected").assertExists()
        rule.onNodeWithContentDescription("Reconnecting").assertDoesNotExist()
        before.recycle(); middle.recycle(); after.recycle()
    }

    @Test fun wavesFollowLevelsThenReturnToSilence() = waves(false)
    @Test fun reducedWavesShowStaticSpeakingRing() = waves(true)
    private fun waves(reduced: Boolean) {
        val levels = MutableStateFlow<Map<String, Float>>(emptyMap())
        val enabled = mutableStateOf(true)
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamVoiceAudioLevels provides levels, LocalReduceAnimations provides reduced) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(24.dp)) {
                            SteamVoiceAvatar("peer", SteamFriend("peer", personaName = "Atlas"), enabled.value,
                                modifier = Modifier.testTag("avatar"))
                        }
                    }
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.autoAdvance = false
        val silent = image("avatar")
        rule.runOnUiThread { levels.value = mapOf("unrelated" to 1f) }
        rule.mainClock.advanceTimeBy(300)
        val unrelated = image("avatar"); assertEquals(0, changed(silent, unrelated))
        rule.runOnUiThread { levels.value = mapOf("peer" to .9f) }
        rule.mainClock.advanceTimeBy(320)
        val speaking = image("avatar")
        assertTrue("Speaking must visibly surround avatar", changed(silent, speaking) > 80)
        rule.mainClock.advanceTimeBy(500)
        val later = image("avatar")
        if (reduced) assertEquals(0, changed(speaking, later)) else assertTrue(changed(speaking, later) > 20)
        rule.runOnUiThread { enabled.value = false }
        rule.mainClock.advanceTimeBy(400)
        val muted = image("avatar"); assertEquals(0, changed(silent, muted))
        rule.runOnUiThread { enabled.value = true; levels.value = emptyMap() }
        rule.mainClock.advanceTimeBy(400)
        val ended = image("avatar"); assertEquals(0, changed(silent, ended))
        listOf(silent, unrelated, speaking, later, muted, ended).forEach(Bitmap::recycle)
    }

    @Test fun directAndGroupProductionViewsFitLargeTextAndGateMutedMembers() {
        val levels = MutableStateFlow(mapOf("peer" to .8f, "self" to .6f, "muted" to 1f))
        val state = mutableStateOf(SteamVoiceCallState(accountSteamId = "self",
            target = SteamVoiceTarget(SteamVoiceTargetType.DIRECT, "Atlas", partnerSteamId = "peer"),
            state = SteamVoiceConnectionState.CONNECTED,
            participants = listOf(SteamVoiceParticipant("peer"), SteamVoiceParticipant("muted", micMuted = true))))
        val friends = listOf(SteamFriend("peer", personaName = "Atlas"), SteamFriend("muted", personaName = "P-body"))
        rule.setContent {
            val density = LocalDensity.current
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalSteamVoiceAudioLevels provides levels,
                    LocalSteamVoiceFriends provides friends.associateBy { it.steamId },
                    LocalDensity provides Density(density.density, 1.5f)) {
                    Surface(Modifier.fillMaxSize()) {
                        if (state.value.target?.type == SteamVoiceTargetType.DIRECT) Column {
                            Text("Portal co-op", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
                            SteamVoiceStatusBanner(state.value, "Atlas", onLeave = {}, onToggleMicrophone = {}, onToggleOutput = {})
                        } else SteamVoiceChannelPanel(
                            SteamGroupChatRoom("room", "Portal co-op", voiceAllowed = true,
                                voiceMemberSteamIds = listOf("peer", "muted", "self")),
                            state.value, friends, "self", {}, {}, {}, {}, {})
                    }
                }
            }
        }
        rule.waitForIdle(); SystemClock.sleep(400)
        rule.onNodeWithContentDescription("离开语音").assertIsDisplayed()
        save("direct-speaking-large")
        rule.runOnUiThread {
            state.value = state.value.copy(target = SteamVoiceTarget(SteamVoiceTargetType.GROUP, "Portal co-op", groupId = "group", chatId = "room"))
        }
        rule.waitForIdle()
        rule.onNodeWithText("Atlas").assertIsDisplayed(); rule.onNodeWithText("P-body").assertIsDisplayed()
        save("group-speaking-large")
        rule.runOnUiThread { state.value = state.value.copy(state = SteamVoiceConnectionState.RECONNECTING) }
        rule.waitForIdle(); save("group-reconnecting-large")
        rule.runOnUiThread { state.value = state.value.copy(state = SteamVoiceConnectionState.CONNECTED, microphoneMuted = true, outputMuted = true) }
        rule.waitForIdle(); save("group-muted-large")
    }
}
