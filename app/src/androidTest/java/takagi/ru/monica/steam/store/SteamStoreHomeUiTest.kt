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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
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
    private fun launch(
        initial: SteamStoreUiState = SteamStoreUiState(home = home),
        viewport: DpSize? = null
    ) {
        state = initial
        compose.setContent {
            val content: @Composable () -> Unit = {
                val base = LocalContext.current
                val config = remember { Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) } }
                val context = remember { base.createConfigurationContext(config) }
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    MonicaTheme(darkTheme = dark) {
                        list = rememberLazyListState()
                        Box(Modifier.fillMaxSize().testTag("store_preview")) {
                            if (visible) SteamStoreHomePage(
                                state, list, actions(),
                                account = state.accounts.firstOrNull { it.id == state.selectedAccountId }
                            ) else Text("detail fixture")
                        }
                    }
                }
            }
            if (viewport != null) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport), content)
            } else {
                content()
            }
        }
    }
    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("store_preview").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun searchAvatarUsesCurrentAccountCacheAndStaysCircularWhenSwitching() {
        val first = SteamAccount(904011, "76561198000904011", "avatar-fixture", "Moon", "", "",
            null, null, null, null, null, null, "{}", false, 0, 0, 0)
        val second = first.copy(id = 904012, steamId = "76561198000904012", displayName = "Violet")
        val colors = listOf(0xFF365B96.toInt(), 0xFF81549B.toInt())
        val cache = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "steam_avatars")
        cache.mkdirs()
        val files = listOf(first, second).map { File(cache, "${it.steamId}.png") }
        try {
            files.forEachIndexed { index, file ->
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(colors[index])
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.color = 0xFFF3D4A1.toInt()
                canvas.drawCircle(32f, 32f, 19f, paint)
                paint.color = colors[index]
                canvas.drawCircle(42f, 23f, 19f, paint)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            dark = true
            launch(SteamStoreUiState(home = home, accounts = listOf(first, second), selectedAccountId = first.id),
                viewport = DpSize(412.dp, 892.dp))
            val button = compose.onNodeWithTag("store_home_account")
            listOf(first, second).forEachIndexed { index, account ->
                compose.runOnIdle { state = state.copy(selectedAccountId = account.id) }
                compose.waitUntil(10_000) {
                    val bitmap = button.captureToImage().asAndroidBitmap()
                    bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == colors[index]
                }
                button.assertContentDescriptionEquals(account.displayName)
                val bitmap = button.captureToImage().asAndroidBitmap()
                screenshot("store-search-avatar-$index")
                val avatarPixels = (0 until bitmap.width).filter { x ->
                    bitmap.getPixel(x, bitmap.height / 2) == colors[index]
                }
                val cornerOffset = ((avatarPixels.last() - avatarPixels.first() + 1) * 0.4f).toInt()
                // Inside the square image's corner, outside a circle: the search surface must show.
                assertNotEquals(colors[index], bitmap.getPixel(bitmap.width / 2 + cornerOffset, bitmap.height / 2 + cornerOffset))
                button.performClick()
                compose.runOnIdle { assertEquals("account", events.last()) }
            }
            compose.runOnIdle { state = state.copy(accounts = emptyList(), selectedAccountId = null) }
            button.assertContentDescriptionEquals(label(R.string.steam_store_account)).performClick()
            compose.runOnIdle { assertEquals("account", events.last()) }
        } finally {
            files.forEach { it.delete() }
        }
    }

    private fun specialCard(index: Int) = compose.onAllNodes(
        hasClickAction() and hasText(home.specials[index].name)
    )[0]

    private fun assertHeroWidth(index: Int) {
        val viewport = compose.onNodeWithTag("store_preview").getUnclippedBoundsInRoot()
        val card = specialCard(index).getUnclippedBoundsInRoot()
        val viewportWidth = (viewport.right - viewport.left).value
        val cardWidth = (card.right - card.left).value
        assertTrue("The main offer must use most of the row, not one of four narrow columns", cardWidth > viewportWidth * 0.6f)
    }

    @Test fun narrowPhoneHasOneLargeOfferAndSwipeOpensTheNextGame() {
        launch(viewport = DpSize(360.dp, 800.dp))
        assertHeroWidth(0)
        specialCard(0).assertIsDisplayed()
        screenshot("store-hero-360-start")
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Carousel))[0]
            .performTouchInput {
                // A full-viewport drag can cross more than one page before fling starts.
                swipe(Offset(width * 0.8f, centerY), Offset(width * 0.4f, centerY), 450)
            }
        compose.waitForIdle()
        assertHeroWidth(1)
        screenshot("store-hero-360-next")
        specialCard(1).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("game:${home.specials[1].appId}", events.last()) }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Carousel))[0]
            .performTouchInput {
                swipe(Offset(width * 0.4f, centerY), Offset(width * 0.8f, centerY), 450)
            }
        compose.waitForIdle()
        specialCard(0).performClick()
        compose.runOnIdle { assertEquals("game:${home.specials.first().appId}", events.last()) }
    }

    @Test fun phoneHeroKeepsLongTitleAndPriceReachableWithLargeText() {
        fontScale = 1.5f
        dark = true
        launch(viewport = DpSize(412.dp, 892.dp))
        assertHeroWidth(0)
        compose.onAllNodesWithText(home.specials.first().formattedFinalPrice)[0].assertIsDisplayed()
        specialCard(0).performClick()
        compose.runOnIdle { assertEquals("game:${home.specials.first().appId}", events.last()) }
        screenshot("store-hero-412-large-text")
    }

    @Test fun widerViewportKeepsOneHeroInsteadOfAddingMoreLargeColumns() {
        launch(viewport = DpSize(840.dp, 800.dp))
        assertHeroWidth(0)
        specialCard(0).assertIsDisplayed()
        screenshot("store-hero-840-start")
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

    @Test fun repeatedScrimAndSwipeDismissalsLeaveHomeTouchable() {
        launch()
        repeat(16) { iteration ->
            openMenu()
            if (iteration % 2 == 0) {
                compose.onNode(isDialog()).performTouchInput { click(Offset(width - 4f, centerY)) }
            } else {
                compose.onNodeWithTag("store_home_drawer").performTouchInput { swipeLeft(durationMillis = 200) }
            }
            compose.onNodeWithTag("store_home_drawer").assertDoesNotExist()
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.onNodeWithTag("store_home_account").performTouchInput { click() }
            compose.runOnIdle { assertEquals(iteration + 1, events.count { it == "account" }) }
        }
        screenshot("store-home-after-drawer-dismissal")
    }
}
