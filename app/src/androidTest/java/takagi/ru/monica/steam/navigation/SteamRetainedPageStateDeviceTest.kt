package takagi.ru.monica.steam.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.ui.SteamChatScreen
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.SteamLibraryUiState
import takagi.ru.monica.steam.library.SteamLibraryViewModel
import takagi.ru.monica.steam.library.ui.SteamLibraryScreen
import takagi.ru.monica.steam.design.telegram.SteamTelegramGlassHost

/** Actual screen wrappers in an empty fixture account store. State injection is synthetic;
 * no credentials, network requests or production database mutations are needed. */
class SteamRetainedPageStateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val selected = mutableStateOf(SteamDockTab.LIBRARY)
    private var renderedUnread = -1

    private fun mount() {
        compose.setContent {
            MaterialTheme {
                SteamTabPageHost(
                    targetPage = selected.value,
                    retainedPages = setOf(SteamDockTab.LIBRARY, SteamDockTab.CHAT),
                    retentionEnabled = true,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { _, _ -> steamPageTransition(true) }
                ) { page ->
                    if (page == SteamDockTab.LIBRARY) {
                        SteamLibraryScreen(onNavigateBack = {}, showNavigationBack = false,
                            modifier = Modifier.fillMaxSize())
                    } else {
                        SteamChatScreen(standalone = true, onUnreadCountChange = { renderedUnread = it },
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun select(page: SteamDockTab) {
        compose.runOnIdle { selected.value = page }
        compose.waitForIdle()
    }

    // StateFlow's read-only facade intentionally hides subscriptionCount. Inspect only
    // this fixture's backing flow so the test detects a live hidden UI subscription.
    @Suppress("UNCHECKED_CAST")
    private fun <T> backingFlow(model: Any): MutableStateFlow<T> =
        model.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }
            .get(model) as MutableStateFlow<T>

    @Test fun hiddenLibraryStopsCollectingAndRendersLatestGamesOnReturn() {
        mount()
        lateinit var state: MutableStateFlow<SteamLibraryUiState>
        compose.runOnIdle {
            val model = ViewModelProvider(compose.activity, SteamLibraryViewModel.factory(compose.activity))
                .get(SteamLibraryViewModel::class.java)
            state = backingFlow(model)
        }
        compose.waitUntil(5_000) { state.subscriptionCount.value == 1 }
        select(SteamDockTab.CHAT)
        compose.waitUntil(5_000) { state.subscriptionCount.value == 0 }
        compose.runOnIdle {
            state.value = SteamLibraryUiState(
                selectedAccountId = 1,
                snapshot = SteamLibrarySnapshot(
                    1, listOf(SteamGame(1, "Latest fixture game", 20, 10)), 1_700_000_000
                )
            )
        }
        select(SteamDockTab.LIBRARY)
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Latest fixture game").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Latest fixture game").assertIsDisplayed()
        assertEquals(1, state.subscriptionCount.value)
    }

    @Test fun nestedContentViewUsesTheRetainedPageLifecycle() {
        val owners = mutableMapOf<String, LifecycleOwner>()
        val outerOwners = mutableMapOf<String, LifecycleOwner>()
        compose.setContent {
            MaterialTheme {
                SteamTabPageHost(selected.value, setOf(SteamDockTab.LIBRARY, SteamDockTab.CHAT), true,
                    modifier = Modifier.fillMaxSize(), transitionSpec = { _, _ -> steamPageTransition(true) }) { page ->
                    val outer = LocalLifecycleOwner.current
                    SideEffect { outerOwners[page.name] = outer }
                    SteamTelegramGlassHost(modifier = Modifier.fillMaxSize(), content = {
                        val owner = LocalLifecycleOwner.current
                        SideEffect { owners[page.name] = owner }
                        Text("Synthetic nested ${page.name}")
                    }, overlay = {})
                }
            }
        }
        compose.waitUntil(5_000) { owners.containsKey(SteamDockTab.LIBRARY.name) }
        select(SteamDockTab.CHAT)
        compose.waitUntil(5_000) { owners.containsKey(SteamDockTab.CHAT.name) }
        assertEquals(Lifecycle.State.CREATED, outerOwners.getValue(SteamDockTab.LIBRARY.name).lifecycle.currentState)
        assertEquals("Nested content must stop with its hidden page", Lifecycle.State.CREATED,
            owners.getValue(SteamDockTab.LIBRARY.name).lifecycle.currentState)
        org.junit.Assert.assertSame(outerOwners.getValue(SteamDockTab.CHAT.name), owners.getValue(SteamDockTab.CHAT.name))
    }
}
