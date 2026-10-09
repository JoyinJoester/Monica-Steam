package takagi.ru.monica.steam.store

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.ui.SteamStoreHomeActions
import takagi.ru.monica.steam.store.ui.SteamStoreNavigationDrawer

class SteamStoreDrawerDismissalTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var drawer: DrawerState
    private lateinit var scope: CoroutineScope
    private var dismissals = 0
    private var actions = 0
    private var hostClicks = 0
    private var visible by mutableStateOf(true)

    private fun launch() {
        compose.setContent {
            MaterialTheme {
                drawer = rememberDrawerState(DrawerValue.Closed)
                scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = { hostClicks++ }, modifier = Modifier.testTag("host")) { Text("Home") }
                    if (visible) SteamStoreNavigationDrawer(
                        refreshing = false,
                        actions = SteamStoreHomeActions(
                            query = {}, search = {}, filter = {}, clearFilters = {}, advancedFilters = {},
                            refresh = {}, loadMore = {}, game = {}, event = {}, account = {}, cart = {},
                            wishlist = { actions++ }, freebies = {}, points = {}, activateProduct = {},
                            workshopImport = {}, notifications = {}, settings = {}
                        ),
                        drawer = drawer,
                        onDismiss = { dismissals++; visible = false }
                    )
                }
            }
        }
    }

    private fun assertDismissed() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("store_home_drawer").assertDoesNotExist()
        compose.onNodeWithTag("host").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(1, hostClicks) }
    }

    @Test fun closeWhileOpeningRemovesTheDialogWindow() {
        compose.mainClock.autoAdvance = false
        launch()
        compose.mainClock.advanceTimeBy(80)
        // A competing close uses the same anchored-drag mutation that cancels open during a swipe.
        compose.runOnIdle { scope.launch { drawer.close() } }
        assertDismissed()
    }

    @Test fun tapScrimRemovesWindowAndUnblocksHome() {
        launch()
        compose.onNode(isDialog()).performTouchInput { click(Offset(width - 4f, centerY)) }
        assertDismissed()
    }

    @Test fun swipeClosedRemovesWindowAndUnblocksHome() {
        launch()
        compose.onNodeWithTag("store_home_drawer").performTouchInput { swipeLeft(durationMillis = 200) }
        assertDismissed()
    }

    @Test fun interruptedButtonCloseStillRemovesWindow() {
        launch()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("store_drawer_close").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { scope.launch { drawer.snapTo(DrawerValue.Closed) } }
        assertDismissed()
    }

    private fun clickWishlist() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.store_home_wishlist)
        compose.onNodeWithText(label).performClick()
    }

    @Test fun interruptedActionDismissesAndNavigatesExactlyOnce() {
        launch()
        compose.mainClock.autoAdvance = false
        clickWishlist()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { scope.launch { drawer.snapTo(DrawerValue.Closed) } }
        assertDismissed()
        compose.runOnIdle { assertEquals(1, actions) }
    }

    @Test fun disposingDrawerDuringCloseDoesNotRunStaleNavigation() {
        launch()
        compose.mainClock.autoAdvance = false
        clickWishlist()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { visible = false }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, actions); assertEquals(0, dismissals) }
    }
}
