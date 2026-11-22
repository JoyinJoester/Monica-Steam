package takagi.ru.monica.steam.store

import android.content.res.Configuration
import android.graphics.Bitmap
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.*
import takagi.ru.monica.steam.library.*
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.steam.workshop.*
import takagi.ru.monica.steam.steamdb.data.*
import takagi.ru.monica.steam.steamdb.domain.*
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamGameDetailTabsUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val originalLocale = Locale.getDefault()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val account = SteamAccount(1, "76561198000000001", "player", "Test player", "android:test", "test",
        null, null, null, "fixture-token", null, null, "{}", true, 0, 0, 0)
    private var selected by mutableStateOf<SteamAccount?>(account)
    private var appId by mutableIntStateOf(620)
    private var closed = 0
    private var chosen = 0
    private var shares = 0
    private var official = 0
    private var purchaseBar = false
    private var requestPurchase by mutableStateOf(false)
    private var supportsWorkshop = false
    private var realWorkshop = false
    private val workshopGateway = object : SteamWorkshopGateway {
        override suspend fun supportsWorkshop(appId: Int) = true
        override suspend fun browse(account: SteamAccount?, appId: Int, query: WorkshopQuery, page: Int) =
            WorkshopBatch(listOf(WorkshopItem("10001", appId, "Cooperative maps")), page = 1, hasMore = false, total = 1)
        override suspend fun details(account: SteamAccount, appId: Int, id: String) = WorkshopItem(id, appId, "Cooperative maps")
        override suspend fun publicDetails(appId: Int, id: String) = WorkshopItem(id, appId, "Cooperative maps")
        override suspend fun setSubscription(account: SteamAccount, item: WorkshopItem, subscribe: Boolean, dependencies: Boolean) = Unit
    }
    private var storeUnavailable by mutableStateOf(false)
    private val gateway = Gateway()
    private class Gateway : SteamGameStatsGateway {
        var calls = 0
        var waitForFirst: CompletableDeferred<Unit>? = null
        var firstEntered = CompletableDeferred<Unit>()
        var noActivity = false
        var fail = false
        override suspend fun cached(accountId: Long, appId: Int): Pair<SteamLibrarySnapshot?, SteamGameAchievements?> = null to null
        override suspend fun playerGame(account: SteamAccount, appId: Int): SteamLibraryResult<SteamGame?> {
            calls++
            if (account.id == 1L && waitForFirst != null) withContext(NonCancellable) {
                firstEntered.complete(Unit); waitForFirst!!.await()
            }
            return if (fail) SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
                else SteamLibraryResult.Success(if (noActivity) null else SteamGame(appId, "Portal 2",
                    if (account.id == 1L) 1740 else 120, 1026, lastPlayedAt = 1794902400))
        }
        override suspend fun achievements(account: SteamAccount, game: SteamGame): SteamLibraryResult<SteamGameAchievements> =
            if (fail) SteamLibraryResult.Failure(SteamLibraryFailureReason.PRIVATE_PROFILE)
            else SteamLibraryResult.Success(SteamGameAchievements(account.id, game.appId, "Portal 2", (0..35).map {
                SteamAchievement("a$it", "Achievement $it", "Complete experiment $it", it % 2 == 0,
                    if (it % 2 == 0) 1794902400 else null, null, null, hidden = it == 1)
            }, 1))
    }
    private val db = SteamDbRepository(object : SteamDbGateway {
        override fun info(appId: Int): SteamDbResult<SteamDbAppInfo> = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        override fun players(appId: Int): SteamDbResult<Long> = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        override fun rating(appId: Int): SteamDbResult<SteamDbRating> = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        override fun price(appId: Int, region: String): SteamDbResult<SteamDbLowestPrice> = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
    }, scope)
    @After fun cleanup() { compose.mainClock.autoAdvance = true; gateway.waitForFirst?.complete(Unit); scope.cancel(); Locale.setDefault(originalLocale) }
    private fun label(id: Int) = compose.activity.getString(id)
    private fun launch(personal: Boolean = false, large: Boolean = false, reduced: Boolean = true, glass: Boolean = false) {
        Locale.setDefault(Locale.ENGLISH)
        compose.activityRule.scenario.onActivity {
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(Configuration(it.resources.configuration).apply { setLocale(Locale.ENGLISH) }, it.resources.displayMetrics)
            it.enableEdgeToEdge()
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * (compose.activity.resources.displayMetrics.widthPixels / density.density / 360f), if (large) 1.5f else 1f),
                LocalReduceAnimations provides reduced,
                LocalSteamDesignStyle provides if (glass) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED) {
                MonicaTheme(darkTheme = large) {
                    Surface(Modifier.fillMaxSize()) {
                        key(selected?.id, appId) {
                            SteamGameDetailTabs(appId, "Portal 2", personal, selected, SteamStorageSource.Local,
                                onBack = { closed++ }, onChooseAccount = { chosen++ },
                                onOpenWeb = { official++ }, onShare = { shares++ }, statsGateway = gateway,
                                workshopSupport = { supportsWorkshop },
                                bottomBar = if (purchaseBar) { openStore ->
                                    SteamGamePurchaseBar(SteamStoreDetail(appId,"Portal 2",finalPriceCents=420),
                                        false,false,false,{}, { requestPurchase = true; openStore() })
                                } else null,
                                workshopContent = { nav ->
                                    if (realWorkshop) SteamWorkshopScreen(appId, "Portal 2", null, SteamStorageSource.Local,
                                        { closed++ }, gameHeader = nav, gateway = workshopGateway)
                                    else SteamGameDetailSectionHost(header = { nav { closed++ } }) { top ->
                                        Box(Modifier.padding(top = top)) { Text("Workshop fixture") }
                                    }
                                }) { state, header ->
                                if (storeUnavailable) Column { header(null, null); Text("Store unavailable") }
                                else StoreFixture(state, header)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
    }
    @Composable private fun StoreFixture(state: LazyListState, header: @Composable ((() -> Unit)?, (() -> Unit)?) -> Unit) {
        SteamStoreDetailContent(
            detail = SteamStoreDetail(appId, "Portal 2", about = "Cooperative portal experiments. ".repeat(30),
                initialPriceCents = 4200, finalPriceCents = 420, currency = "CNY", discountPercent = 90,
                accountCountryCode = "CN", priceCountryCode = "CN", developers = listOf("Valve")),
            hints = emptyList(), showTags = false, filterableTags = emptySet(), loading = false, cached = false,
            purchaseContext = null, purchaseContextFromCache = false, loadingPurchaseContext = false, purchaseContextFailure = null,
            alreadyOwned = false, freeLicenseOption = null, freeLicenseClaiming = false, freeLicenseClaimResult = null,
            onBack = { closed++ }, onOpenWorkshop = {}, onOpenOfficial = { official++ }, onOpenOfficialReviews = { official++ },
            onShare = { shares++ }, onOpenWebsite = {}, reviewFilters = SteamReviewFilterSelection(), loadingMoreReviews = false,
            reviewLoadError = null, onReviewFiltersChanged = {}, onLoadMoreReviews = {}, cartItem = null, inWishlist = false,
            wishlistAvailable = false, wishlistMutating = false, wishlistError = null, ignored = false, ignoreAvailable = false,
            ignoreMutating = false, ignoreSyncState = null, ignoredError = null, regionalPrices = emptyList(), regionalPricesFromCache = false,
            loadingRegionalPrices = false, regionalPriceFailure = null, showRegionalPrices = false, onAddToCart = {}, onAddAsGift = {},
            onClaimFreeLicense = {}, onRemoveFromCart = {}, onOpenCart = {}, onToggleWishlist = {}, onToggleIgnored = {},
            onOpenRegionalPrices = {}, onCloseRegionalPrices = {}, onRetryRegionalPrices = {}, onOpenRelatedApp = {},
            onOpenBundle = {}, onFilterByTag = { false }, modifier = Modifier.fillMaxSize(), steamDbRepository = db,
            listState = state, header = header, purchaseSheetRequested = requestPurchase,
            onPurchaseSheetDismissed = { requestPurchase = false })
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val target = File(compose.activity.filesDir, "game-detail/$name.png").apply { parentFile!!.mkdirs() }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(compose.activity.getExternalFilesDir(null), "game-detail/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    private fun statsTab() { compose.onNodeWithText(label(R.string.steam_game_tab_stats)).performClick(); compose.waitForIdle() }
    private fun storeTab() { compose.onNodeWithText(label(R.string.steam_game_tab_store)).performClick(); compose.waitForIdle() }
    @Test fun m3eHeaderSurvivesRealPageSwitchAndIndicatorTravels() = verifyPageTabTravel(glass = false)
    @Test fun glassHeaderSurvivesRealPageSwitchAndLensTravels() = verifyPageTabTravel(glass = true)

    private fun verifyPageTabTravel(glass: Boolean) {
        supportsWorkshop = true; realWorkshop = true
        launch(reduced = false, glass = glass)
        val prefix = if (glass) "glass-page" else "m3e-page"
        val headerId = compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().id
        val tabs = compose.onNodeWithTag("game_detail_tabs")
        val bounds = tabs.fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithText(label(R.string.steam_game_tab_store)).fetchSemanticsNode().boundsInRoot.center.x
        val destination = compose.onNodeWithText(label(R.string.steam_game_tab_stats)).fetchSemanticsNode().boundsInRoot.center.x
        val reference = tabs.captureToImage().toPixelMap()
        val scanY = (reference.height * .2f).toInt()
        val fill = reference[(start - bounds.left).toInt(), scanY]
        fun center(): Float {
            if (glass) return compose.onNode(hasTestTag("steam-glass-selection-lens") and
                hasAnyAncestor(hasTestTag("game_detail_tabs"))).fetchSemanticsNode().boundsInRoot.center.x
            val pixels = tabs.captureToImage().toPixelMap()
            val xs = (0 until pixels.width).filter { x ->
                val c = pixels[x, scanY]
                kotlin.math.abs(c.red - fill.red) + kotlin.math.abs(c.green - fill.green) +
                    kotlin.math.abs(c.blue - fill.blue) < .045f
            }
            assertTrue("Selected pill must be visible", xs.size > 20)
            return xs.average().toFloat() + bounds.left
        }
        val origin = center()
        screenshot("$prefix-00-rest")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(label(R.string.steam_game_tab_stats)).performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        compose.onAllNodesWithTag("game_detail_navigation").assertCountEquals(1)
        assertEquals("The real page switch must retain the navigation instance", headerId,
            compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().id)
        val moving = center()
        val progress = (moving - origin) / (destination - origin)
        assertTrue("Selected pill must travel through intermediate positions: $progress", progress in .01f..0.95f)
        screenshot("$prefix-01-moving")
        repeat(12) { frame ->
            compose.mainClock.advanceTimeByFrame()
            screenshot("$prefix-frame-${frame.toString().padStart(2, '0')}")
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(destination, center(), 4f)
        screenshot("$prefix-99-settled")
        // Reverse an in-flight transition without replacing or duplicating the header.
        compose.onNodeWithText(label(R.string.steam_game_tab_store)).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText(label(R.string.steam_game_tab_stats)).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(destination, center(), 4f)
        assertEquals(headerId, compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().id)
        compose.onNodeWithText(label(R.string.workshop_title)).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(64)
        compose.onAllNodesWithTag("game_detail_navigation").assertCountEquals(1)
        assertEquals(headerId, compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().id)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Cooperative maps").assertIsDisplayed().performClick()
        compose.onNodeWithTag("game_detail_navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.workshop_back)).performClick()
        compose.onNodeWithTag("game_detail_navigation").assertIsDisplayed()
        println("PAGE_TAB_TRAVEL glass=$glass origin=$origin moving=$moving destination=$destination progress=$progress")
    }
    @Test fun purchaseBarOnlyAppearsOnStoreAndPreservesGlassTabState() = verifyPurchaseScope(glass = true)
    @Test fun purchaseBarOnlyAppearsOnStoreAndPreservesM3eTabState() = verifyPurchaseScope(glass = false)
    private fun verifyPurchaseScope(glass: Boolean) {
        purchaseBar = true; supportsWorkshop = true; launch(personal = true, large = true, glass = glass)
        val headerId = compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().id
        compose.onNodeWithTag("game_purchase_bar").assertDoesNotExist()
        compose.onNodeWithTag("game_purchase_wishlist").assertDoesNotExist()
        compose.onNodeWithText("29.0 h").assertIsDisplayed()
        screenshot("stats-no-purchase-${if (glass) "glass" else "m3e"}")
        compose.onNodeWithTag("game_personal_list").performScrollToNode(hasText("Achievement 3"))
        compose.onNodeWithText("Achievement 3").assertIsDisplayed()
        storeTab()
        assertEquals("Purchase visibility must not remount persistent tab navigation", headerId,
            compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().id)
        compose.onNodeWithTag("game_purchase_open").assertIsDisplayed().performClick()
        compose.onNodeWithTag("game_purchase_sheet").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.steam_store_open_official)).performScrollTo().assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("game_purchase_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("game_purchase_bar").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.steam_store_information)))
        val last = compose.onNodeWithText(label(R.string.steam_store_information)).fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("game_purchase_bar").fetchSemanticsNode().boundsInRoot
        assertTrue("Last list section clears measured purchase bar", last.bottom <= bar.top)
        statsTab()
        compose.onNodeWithTag("game_purchase_bar").assertDoesNotExist()
        compose.onNodeWithText("Achievement 3").assertIsDisplayed()
        assertEquals(headerId, compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().id)
        compose.onNodeWithText(label(R.string.workshop_title)).performClick()
        compose.onNodeWithText("Workshop fixture").assertIsDisplayed()
        compose.onNodeWithTag("game_purchase_bar").assertDoesNotExist()
        compose.onNodeWithTag("game_purchase_wishlist").assertDoesNotExist()
        storeTab()
        compose.onNodeWithTag("game_purchase_open").assertIsDisplayed()
    }
    @Test fun nativeWorkshopUsesCombinedHeaderAndKeepsBrowseAndBack() {
        supportsWorkshop = true; realWorkshop = true; launch(glass = true)
        compose.onNodeWithText(label(R.string.workshop_title)).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Cooperative maps").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cooperative maps").assertIsDisplayed()
        compose.onAllNodesWithTag("game_detail_navigation").assertCountEquals(1)
        screenshot("native-workshop")
        compose.onNodeWithText("Cooperative maps").performClick()
        compose.onNodeWithContentDescription(label(R.string.workshop_back)).performClick()
        compose.onNodeWithTag("game_detail_navigation").assertIsDisplayed()
        statsTab(); compose.onNodeWithText("29.0 h").assertIsDisplayed()
    }
    @Test fun darkGlassHeaderPressRendersLensAndCancelKeepsSection() {
        launch(glass = true, large = true, reduced = false)
        screenshot("dark-rest")
        val rest = compose.onNodeWithTag("game_detail_navigation").captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(label(R.string.steam_game_tab_store)).performTouchInput { down(center); advanceEventTime(240) }
        compose.mainClock.advanceTimeBy(240)
        screenshot("dark-held")
        val held = compose.onNodeWithTag("game_detail_navigation").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until rest.height) for (x in 0 until rest.width) {
            val a = rest[x,y]; val b = held[x,y]
            if (kotlin.math.abs(a.red-b.red)+kotlin.math.abs(a.green-b.green)+kotlin.math.abs(a.blue-b.blue) > .1f) changed++
        }
        assertTrue("Held glass must visibly magnify/refract the actual labels: $changed", changed > 100)
        compose.onNodeWithText(label(R.string.steam_game_tab_store)).performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("store_detail_current_price").assertIsDisplayed()
        statsTab(); screenshot("dark-stats")
    }
    @Test fun compactHeaderIsOneRowAndDoesNotStretchTheTabs() {
        launch(glass = true)
        val back = compose.onNodeWithTag("game_detail_back").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("game_detail_tabs").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(back.center.y, tabs.center.y, 2f)
        assertTrue(tabs.left > back.right)
        assertTrue(tabs.right < root.right - 24f)
        compose.onNodeWithTag("store_detail_topbar").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.workshop_title)).assertDoesNotExist()
        screenshot("compact-store-glass")
    }
    @Test fun restoredToolbarSharesOpensReviewsAndDragsToOtherEdge() {
        launch(glass = true)
        compose.onNodeWithContentDescription(label(R.string.steam_store_detail_share)).performClick()
        assertEquals(1, shares)
        compose.onNodeWithContentDescription(label(R.string.steam_store_detail_jump_to_reviews)).performClick()
        assertEquals(1, official)
        val before = compose.onNodeWithTag("game_detail_tools").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("game_detail_tools").performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(-before.left + 8f, 70f), 500); up()
        }
        val after = compose.onNodeWithTag("game_detail_tools").fetchSemanticsNode().boundsInRoot
        assertTrue(after.left < before.left / 2)
        assertTrue(after.top >= compose.onNodeWithTag("game_detail_navigation").fetchSemanticsNode().boundsInRoot.bottom)
        screenshot("toolbar-dragged-left")
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithContentDescription(label(R.string.steam_store_detail_toolbar_expand)).performClick()
        compose.onNodeWithContentDescription(label(R.string.steam_store_detail_share)).assertIsDisplayed()
    }
    @Test fun workshopAppearsOnlyForSupportedGameAndKeepsSameHeader() {
        supportsWorkshop = true; launch(glass = true)
        compose.onNodeWithText(label(R.string.workshop_title)).performClick()
        compose.onNodeWithText("Workshop fixture").assertIsDisplayed()
        compose.onNodeWithTag("game_detail_navigation").assertExists()
        statsTab(); compose.onNodeWithText("29.0 h").assertIsDisplayed()
        storeTab(); compose.onNodeWithTag("store_detail_current_price").assertIsDisplayed()
    }
    @Test fun liquidGlassTabsKeepBothSectionsReachable() {
        launch(glass = true, reduced = false)
        statsTab()
        compose.onNodeWithText("29.0 h").assertIsDisplayed()
        screenshot("stats-liquid-glass")
        storeTab()
        compose.onNodeWithTag("store_detail_current_price").assertIsDisplayed()
        screenshot("store-liquid-glass")
    }
    @Test fun storeAndPersonalStatsRetainIndependentScrollAndFilter() {
        launch()
        screenshot("m3e-detail-store")
        assertEquals(0, gateway.calls)
        screenshot("store-light")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.steam_store_information)))
        compose.onNodeWithText(label(R.string.steam_store_information)).assertIsDisplayed()
        statsTab()
        compose.onNodeWithTag("game_personal_list").performScrollToNode(hasText("Achievement 20"))
        compose.onNodeWithText("Achievement 20").assertIsDisplayed()
        storeTab()
        compose.onNodeWithText(label(R.string.steam_store_information)).assertIsDisplayed()
        statsTab()
        compose.onNodeWithText("Achievement 20").assertIsDisplayed()
        assertEquals(1, gateway.calls)
    }
    @Test fun personalEntryRemainsUsableWhenStoreUnavailable() {
        storeUnavailable = true; launch(personal = true)
        compose.onNodeWithText("29.0 h").assertIsDisplayed()
        storeTab(); compose.onNodeWithText("Store unavailable").assertIsDisplayed()
        statsTab(); compose.onNodeWithText("29.0 h").assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        assertEquals(1, closed)
    }
    @Test fun signedOutHasAccountActionAndNoInventedHours() {
        selected = null; launch(personal = true)
        compose.onNodeWithText(label(R.string.steam_game_stats_sign_in)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.steam_switch_account)).performClick()
        assertEquals(1, chosen); assertEquals(0, gateway.calls)
        compose.onNodeWithTag("game_total_playtime").assertDoesNotExist()
        screenshot("signed-out")
    }
    @Test fun noActivityShowsUnavailableWhileAchievementsRemainUsable() {
        gateway.noActivity = true; launch(personal = true)
        compose.onNodeWithTag("game_total_playtime").assertTextEquals(label(R.string.steam_game_stats_unknown))
        compose.onNodeWithTag("game_personal_list").performScrollToNode(hasText("Achievement 0"))
        compose.onNodeWithText("Achievement 0").assertIsDisplayed()
    }
    @Test fun lateOldAccountCannotReplaceCurrentPlayersStats() {
        gateway.waitForFirst = CompletableDeferred(); launch(personal = true)
        compose.waitUntil(5000) { gateway.firstEntered.isCompleted }
        compose.runOnIdle { selected = account.copy(id = 2, steamId = "76561198000000002", displayName = "Second player") }
        compose.onNodeWithText("Second player").assertIsDisplayed()
        compose.onNodeWithText("2.0 h").assertIsDisplayed()
        compose.runOnIdle { gateway.waitForFirst!!.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText("29.0 h").assertDoesNotExist()
        compose.onNodeWithText("2.0 h").assertIsDisplayed()
    }
    @Test fun failureSupportsExplicitRetryAndDoesNotShowZero() {
        gateway.fail = true; launch(personal = true)
        compose.onNodeWithTag("game_total_playtime").assertTextEquals(label(R.string.steam_game_stats_unknown))
        gateway.fail = false
        compose.onNodeWithTag("game_stats_menu").performClick()
        compose.onNodeWithText(label(R.string.refresh)).performClick()
        compose.onNodeWithText("29.0 h").assertIsDisplayed()
        assertEquals(2, gateway.calls)
    }
    @Test fun darkLargeFontKeepsHeaderMetricsAndControlsReachable() {
        supportsWorkshop = true
        launch(personal = true, large = true)
        compose.onNodeWithText("Test player").assertIsDisplayed()
        compose.onNodeWithText("29.0 h").assertIsDisplayed()
        screenshot("stats-dark-large")
        compose.onNodeWithTag("game_stats_menu").performClick()
        compose.onNodeWithText(label(R.string.steam_switch_account)).performClick()
        assertEquals(1, chosen)
        compose.onNodeWithText(label(R.string.workshop_title)).performScrollTo().performClick()
        compose.onNodeWithText("Workshop fixture").assertIsDisplayed()
        screenshot("stats-dark-large-actions")
    }
    @Test fun achievementFilterSurvivesTabRoundTripAndConcealsHiddenNames() {
        launch(personal = true, reduced = false)
        screenshot("stats-light")
        compose.onNodeWithTag("game_personal_list").performScrollToNode(hasText(label(R.string.steam_game_stats_incomplete)))
        compose.onNodeWithText(label(R.string.steam_game_stats_incomplete)).performClick()
        compose.onNodeWithTag("game_personal_list").performScrollToNode(hasText("Achievement 3"))
        compose.onNodeWithText("Achievement 1").assertDoesNotExist()
        storeTab(); statsTab()
        compose.onNodeWithText("Achievement 3").assertIsDisplayed()
        compose.onNodeWithText("Achievement 0").assertDoesNotExist()
        screenshot("stats-filtered")
    }
}
