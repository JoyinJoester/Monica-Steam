package takagi.ru.monica.steam.store

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.data.SteamStoreParser
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.presentation.SteamStoreUiState
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.ui.theme.MonicaTheme

/** Uses public store fixtures, never account credentials or a live purchase endpoint. */
class SteamStoreHomeUiTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<String>()
    private val home by lazy {
        SteamStoreParser.parseFeatured(InstrumentationRegistry.getInstrumentation().context.assets
            .open("store-home-public.json").bufferedReader().use { it.readText() }, "CN")
    }
    private var state by mutableStateOf(SteamStoreUiState())
    private var dark by mutableStateOf(false)
    private var fontScale by mutableFloatStateOf(1f)
    private var visible by mutableStateOf(true)
    private lateinit var list: LazyListState
    private fun label(id: Int): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
        return base.createConfigurationContext(config).getString(id)
    }
    private fun openMenu() = compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
    private fun actions() = SteamStoreHomeActions(
        query = { state = state.copy(query = it, searching = it.isNotBlank(), searchResults = emptyList()) },
        search = { events += "search" },
        filter = { events += "filter:$it"; state = state.copy(query = "", searching = false, browseFilter = it) },
        clearFilters = { events += "clear" }, advancedFilters = { events += "filters" },
        refresh = { events += "refresh" }, loadMore = { events += "more" },
        game = { events += "game:${it.appId}" }, event = { events += "event:$it" },
        account = { events += "account" }, cart = { events += "cart" }, wishlist = { events += "wishlist" },
        freebies = { events += "freebies" }, points = { events += "points" },
        activateProduct = { events += "activate" }, workshopImport = { events += "workshop" },
        notifications = { events += "notifications" }, settings = { events += "settings" }
    )
    private fun launch(initial: SteamStoreUiState = SteamStoreUiState(home = home)) {
        state = initial
        compose.setContent {
            val base = LocalContext.current
            val config = remember { Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) } }
            val context = remember { base.createConfigurationContext(config) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MonicaTheme(darkTheme = dark) {
                    list = rememberLazyListState()
                    Box(Modifier.fillMaxSize().testTag("store_preview")) {
                        if (visible) SteamStoreHomePage(state, list, actions()) else Text("detail fixture")
                    }
                }
            }
        }
    }
    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("store_preview").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun integratedSearchBarKeepsCartAccountAndFiltersVisible() {
        launch()
        compose.onNodeWithTag("store_home_search").assertIsDisplayed()
        for ((tag, action) in listOf("cart" to "cart", "account" to "account", "filters" to "filters")) {
            compose.onNodeWithTag("store_home_$tag").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(action, events.last()) }
        }
        for ((id, action) in listOf(R.string.store_home_wishlist to "wishlist", R.string.steam_store_freebies to "freebies")) {
            openMenu()
            compose.onNodeWithText(label(id)).performClick()
            compose.runOnIdle { assertEquals(action, events.last()) }
        }
        compose.runOnIdle { assertEquals(5, events.size) }
        val searchTop = compose.onNodeWithTag("store_home_search").getUnclippedBoundsInRoot().top
        val categoriesBottom = compose.onNodeWithTag("store_home_categories").getUnclippedBoundsInRoot().bottom
        assertTrue("The header must fit in two compact rows", (categoriesBottom - searchTop).value <= 128f)
        screenshot("store-home-light")
    }

    @Test fun categorySelectionAndSeeAllLeadToCatalogAndGameCardsRemainClickable() {
        launch()
        val first = home.specials.first()
        compose.onAllNodesWithText(first.name)[0].performScrollTo().performClick()
        compose.runOnIdle { assertEquals("game:${first.appId}", events.last()) }
        compose.onNodeWithTag("store_home_list").performScrollToNode(hasText("查看全部"))
        compose.onAllNodesWithText("查看全部")[0].performClick()
        compose.runOnIdle { assertEquals("filter:SPECIALS", events.last()) }
        compose.onNodeWithTag("store_category_TOP_SELLERS").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("filter:TOP_SELLERS", events.last()) }
    }

    @Test fun searchNeverFallsBackToHomeWhileWaitingAndClearRestoresDiscovery() {
        launch()
        compose.onNodeWithTag("store_home_search").performTextInput("Portal")
        compose.onNodeWithTag("store_home_search_progress").assertIsDisplayed()
        compose.onNodeWithText(home.specials.first().name).assertDoesNotExist()
        compose.onNodeWithTag("store_home_search").performImeAction()
        compose.runOnIdle {
            assertEquals("search", events.last())
            state = state.copy(searching = false, searchResults = home.topSellers.take(3))
        }
        compose.onNodeWithTag("store_home_search_progress").assertDoesNotExist()
        screenshot("store-home-search")
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.onNodeWithTag("store_home_cart").assertIsDisplayed()
    }

    @Test fun networkFailureRetainsNavigationAndRetriesOnlyOnDemand() {
        launch(SteamStoreUiState(error = "Synthetic offline failure"))
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithTag("store_home_cart").assertIsDisplayed()
        compose.onNodeWithTag("store_home_search").assertIsDisplayed()
        compose.onNodeWithTag("store_home_retry").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("refresh"), events) }
        screenshot("store-home-offline")
    }

    @Test fun returningFromDetailRetainsHomeScrollPosition() {
        launch()
        compose.onNodeWithTag("store_home_list").performScrollToIndex(4)
        var before = 0
        compose.runOnIdle { before = list.firstVisibleItemIndex; assertTrue(before > 0); visible = false }
        compose.waitForIdle()
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(before, list.firstVisibleItemIndex) }
    }

    @Test fun largeTextAndDarkThemeKeepPricesAndToolsReachable() {
        fontScale = 1.5f
        dark = true
        launch()
        compose.onNodeWithTag("store_home_search").assertIsDisplayed()
        compose.onNodeWithTag("store_home_cart").assertIsDisplayed()
        compose.onNodeWithTag("store_home_list").performScrollToNode(hasText(home.specials.first().formattedFinalPrice))
        compose.onAllNodesWithText(home.specials.first().formattedFinalPrice)[0].assertIsDisplayed()
        screenshot("store-home-dark-large")
        compose.onNodeWithTag("store_home_list").performScrollToIndex(0)
        openMenu()
        compose.onNodeWithText(label(R.string.steam_store_points_shop)).performClick()
        compose.runOnIdle { assertEquals("points", events.last()) }
    }

    @Test fun menuIsAnEdgeDrawerWithScrollableSettingsAndDismissal() {
        fontScale = 1.5f
        launch()
        openMenu()
        val bounds = compose.onNodeWithTag("store_home_drawer").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Drawer must attach to the start edge", bounds.left.value <= 1f)
        compose.onNodeWithText(label(R.string.settings_title)).performScrollTo().assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Wait for the platform Dialog fade as well as Compose's drawer motion.
        Thread.sleep(500)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(instrumentation.targetContext.filesDir, "store-home-drawer.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithText(label(R.string.settings_title)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("settings"), events) }
        compose.onNodeWithTag("store_home_drawer").assertDoesNotExist()
        openMenu()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        compose.onNodeWithTag("store_home_drawer").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("settings"), events) }
    }
}
