package takagi.ru.monica.steam.navigation

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatViewModel
import takagi.ru.monica.steam.friends.chat.ui.SteamChatScreen
import takagi.ru.monica.steam.library.ui.SteamLibraryScreen
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.ui.LocalReduceAnimations

/** Nested native ComposeViews require the actual Android frame clock for visibility. */
class SteamRetainedNativeChatDeviceTest {
    @get:Rule val rule = ActivityScenarioRule(SteamComposeFixtureActivity::class.java)
    private val selected = mutableStateOf(SteamDockTab.CHAT)
    @Volatile private var renderedUnread = -1
    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!predicate() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertTrue(message, predicate())
    }

    @Test fun hiddenChatStopsCollectingAndDisplaysLatestInboxOnReturn() {
        lateinit var state: MutableStateFlow<SteamChatUiState>
        rule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
                        LocalReduceAnimations provides false) {
                        SteamTabPageHost(selected.value, setOf(SteamDockTab.LIBRARY, SteamDockTab.CHAT), true,
                            modifier = Modifier.fillMaxSize(), transitionSpec = { _, _ -> steamPageTransition(false) }) { page ->
                            if (page == SteamDockTab.CHAT) SteamChatScreen(standalone = true,
                                modifier = Modifier.fillMaxSize(), onUnreadCountChange = { renderedUnread = it })
                            else SteamLibraryScreen({}, showNavigationBack = false, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
            val model = ViewModelProvider(activity, SteamChatViewModel.factory(activity))[SteamChatViewModel::class.java]
            @Suppress("UNCHECKED_CAST")
            state = model.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }
                .get(model) as MutableStateFlow<SteamChatUiState>
        }
        await("Chat must collect when visible") { state.subscriptionCount.value == 1 }
        rule.scenario.onActivity { selected.value = SteamDockTab.LIBRARY }
        await("Hidden chat must stop UI collection") { state.subscriptionCount.value == 0 }
        rule.scenario.onActivity {
            state.value = SteamChatUiState(accountId = 1, accountSteamId = "fixture",
                sessions = SteamChatSessionsSnapshot("fixture",
                    listOf(SteamChatSession("Latest synthetic inbox", 1_700_000_000, 0, 7)), 1_700_000_000))
            selected.value = SteamDockTab.CHAT
        }
        await("Returning chat must publish the latest unread count") { renderedUnread == 7 }
        await("Latest inbox row must be visible in the actual native tree") { nativeTextIsVisible("Latest synthetic inbox") }
        assertEquals(1, state.subscriptionCount.value)
    }

    private fun nativeTextIsVisible(text: String): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun search(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            try {
                if ((node.text?.toString()?.contains(text) == true ||
                    node.contentDescription?.toString()?.contains(text) == true) && node.isVisibleToUser) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    val display = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
                    if (bounds.intersect(0, 0, display.widthPixels, display.heightPixels)) return true
                }
                for (index in 0 until node.childCount) if (search(node.getChild(index))) return true
                return false
            } finally { node.recycle() }
        }
        return search(automation.rootInActiveWindow)
    }
}
