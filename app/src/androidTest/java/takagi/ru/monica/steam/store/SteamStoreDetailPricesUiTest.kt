package takagi.ru.monica.steam.store

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import java.security.MessageDigest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.SteamRegionalPrice
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.steam.steamdb.data.*
import takagi.ru.monica.steam.steamdb.domain.*
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamStoreDetailPricesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = CopyOnWriteArrayList<String>()
    private val originalLocale = Locale.getDefault()
    private var current by mutableStateOf("US")
    private var appId by mutableIntStateOf(1091500)
    private var refreshed = 0
    private var dismissed = 0
    private var shared = 0
    private var official = 0
    private var compared = 0
    private var large = false
    private val api = object : SteamDbGateway {
        override fun info(appId: Int): SteamDbResult<SteamDbAppInfo> = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        override fun players(appId: Int): SteamDbResult<Long> = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        override fun rating(appId: Int): SteamDbResult<SteamDbRating> = SteamDbResult.Failed(SteamDbFailure.NO_DATA)
        override fun price(appId: Int, region: String): SteamDbResult<SteamDbLowestPrice> {
            calls += "$appId:$region"
            return SteamDbResult.Ready(SteamDbLowestPrice("$ 8.99", 70, null, 3, 1790812800), System.currentTimeMillis())
        }
    }
    private val repository = SteamDbRepository(api, scope)
    private fun price(country: String, currency: String, final: Long, original: Long, cny: Long, cnyOriginal: Long) =
        SteamRegionalPrice(country, currency, final, original, true, cnyFinalPriceMinor = cny, cnyOriginalPriceMinor = cnyOriginal)
    private val prices = listOf(
        price("CN", "CNY", 8940, 29800, 8940, 29800),
        price("US", "USD", 1499, 4999, 10710, 35700),
        price("PK", "USD", 899, 2999, 6033, 20124),
        price("JP", "JPY", 198000, 660000, 7216, 24053),
        price("HK", "HKD", 8250, 27500, 7620, 25400),
        price("TW", "TWD", 45000, 150000, 9660, 32200),
        price("BR", "BRL", 5997, 19990, 11640, 38800),
        price("DE", "EUR", 0, 0, 0, 0).copy(isAvailable = false),
        price("PH", "PHP", 0, 0, 0, 0)
    )
    @After fun close() { scope.cancel(); Locale.setDefault(originalLocale) }
    private fun label(id: Int): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }).getString(id)
    }
    private fun launch(detail: Boolean = false, unavailable: Boolean = false) {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        compose.activityRule.scenario.onActivity {
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(Configuration(it.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }, it.resources.displayMetrics)
            it.enableEdgeToEdge()
        }
        val coverUrl = "https://shared.steamstatic.com/monica-ui-fixture/detail-header.png"
        val key = MessageDigest.getInstance("SHA-256").digest(coverUrl.toByteArray()).joinToString("") { "%02x".format(it) }
        val cover = File(compose.activity.cacheDir, "steam_library_images/$key.img").also { it.parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(460, 215, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(240, 218, 42))
            drawText("CYBERPUNK 2077", 22f, 110f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 36f; isFakeBoldText = true })
            drawText("OFFLINE UI FIXTURE", 22f, 144f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 15f })
        }
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        compose.setContent {
            val base = LocalContext.current
            val config = remember { Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) } }
            val context = remember { base.createConfigurationContext(config) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, if (large) 1.5f else 1f)) {
                MonicaTheme(darkTheme = large) {
                    Surface(Modifier.fillMaxSize()) {
                    if (detail) DetailFixture(coverUrl, unavailable)
                    else SteamStoreRegionalPriceContent(appId, "赛博朋克 2077", current, prices, false, false, null,
                        { refreshed++ }, { dismissed++ }, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), repository)
                    }
                }
            }
        }
    }
    @Composable private fun DetailFixture(cover: String, unavailable: Boolean) {
        SteamStoreDetailContent(
            detail = SteamStoreDetail(1091500, "赛博朋克 2077", headerImageUrl = cover, about = "在夜之城探索开放世界。".repeat(20),
                tags = listOf("开放世界", "角色扮演", "科幻", "单人"), releaseDate = "2020年12月10日", windows = true,
                currency = "CNY", initialPriceCents = 29800, finalPriceCents = 8940, discountPercent = 70,
                accountCountryCode = if (unavailable) "US" else "CN", priceCountryCode = "CN", availableInAccountRegion = !unavailable,
                packageId = 1, packageOptions = listOf(SteamStorePackageOption(1, "标准版", priceCents = 8940)),
                reviews = SteamStoreReviews(overall = SteamReviewSummary(8, 90, 10))),
            hints = emptyList(), showTags = true, filterableTags = setOf("开放世界"), loading = false, cached = false,
            purchaseContext = null, purchaseContextFromCache = false, loadingPurchaseContext = false, purchaseContextFailure = null,
            alreadyOwned = false, freeLicenseOption = null, freeLicenseClaiming = false, freeLicenseClaimResult = null,
            onBack = { dismissed++ }, onOpenWorkshop = {}, onOpenOfficial = { official++ }, onOpenOfficialReviews = { official++ },
            onShare = { shared++ }, onOpenWebsite = {}, reviewFilters = SteamReviewFilterSelection(), loadingMoreReviews = false,
            reviewLoadError = null, onReviewFiltersChanged = {}, onLoadMoreReviews = {}, cartItem = null, inWishlist = false,
            wishlistAvailable = true, wishlistMutating = false, wishlistError = null, ignored = false, ignoreAvailable = true,
            ignoreMutating = false, ignoreSyncState = null, ignoredError = null, regionalPrices = prices, regionalPricesFromCache = false,
            loadingRegionalPrices = false, regionalPriceFailure = null, showRegionalPrices = false, onAddToCart = {}, onAddAsGift = {},
            onClaimFreeLicense = {}, onRemoveFromCart = {}, onOpenCart = {}, onToggleWishlist = {}, onToggleIgnored = {},
            onOpenRegionalPrices = { compared++ }, onCloseRegionalPrices = {}, onRetryRegionalPrices = {}, onOpenRelatedApp = {},
            onOpenBundle = {}, onFilterByTag = { true }, modifier = Modifier.fillMaxSize())
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(600)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun collapsedRowsPinAccountAndKeepPricesCompact() {
        launch()
        compose.runOnIdle { assertEquals(0, calls.size) }
        val us = compose.onNodeWithTag("regional_row_US").fetchSemanticsNode().boundsInRoot
        val cn = compose.onNodeWithTag("regional_row_CN").fetchSemanticsNode().boundsInRoot
        assertTrue(us.top < cn.top)
        assertTrue(us.height / compose.activity.resources.displayMetrics.density <= 100f)
        compose.onNodeWithText("中国香港").assertIsDisplayed()
        compose.onNodeWithText("中国台湾").assertIsDisplayed()
        screenshot("prices-collapsed")
        compose.onNodeWithContentDescription(label(R.string.refresh)).performClick()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        compose.runOnIdle { assertEquals(1, refreshed); assertEquals(1, dismissed) }
    }
    @Test fun historyLoadsOnlyExpandedRegionAndResetsForAccountOrGame() {
        launch()
        compose.onNodeWithTag("regional_row_PK").performClick()
        compose.waitUntil(5_000) { calls.size == 1 }
        compose.onNodeWithText("$ 8.99").assertIsDisplayed()
        assertEquals("1091500:USD-SASIA", calls.single())
        screenshot("prices-expanded")
        compose.onNodeWithTag("regional_row_US").performClick()
        compose.waitUntil(5_000) { calls.size == 2 }
        compose.onNodeWithTag("regional_history_PK").assertDoesNotExist()
        compose.runOnIdle { current = "JP" }
        compose.onNodeWithTag("regional_history_US").assertDoesNotExist()
        compose.onNodeWithTag("regional_row_JP").performClick()
        compose.waitUntil(5_000) { calls.size == 3 }
        compose.runOnIdle { appId = 620 }
        compose.onNodeWithTag("regional_history_JP").assertDoesNotExist()
    }
    @Test fun unavailableAndFreeRegionsDoNotRequestHistory() {
        current = "DE"; launch()
        compose.onNodeWithTag("regional_row_DE").assertIsNotEnabled()
        compose.onNodeWithTag("regional_row_PH").performClick()
        compose.onNodeWithText(label(R.string.steamdb_free)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls.size) }
    }
    @Test fun largeFontDarkRowsKeepLocalCurrenciesAndDisclosureAccessible() {
        large = true; current = "HK"; launch()
        compose.onNodeWithText("中国香港").assertIsDisplayed()
        compose.onNodeWithTag("regional_row_HK").performClick()
        compose.waitUntil(5_000) { calls.size == 1 }
        compose.onNodeWithText("$ 8.99").assertIsDisplayed()
        val original = compose.onNodeWithTag("regional_original_HK", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Original amount must remain on one line", original.height / compose.activity.resources.displayMetrics.density <= 26f)
        screenshot("prices-large-dark")
    }
    @Test fun detailShowsGameBeforePriceAndKeepsNavigationActionsReachable() {
        launch(detail = true)
        compose.onNodeWithTag("store_detail_current_price").assertIsDisplayed().performClick()
        val price = compose.onNodeWithTag("store_detail_current_price").fetchSemanticsNode().boundsInRoot
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(label(R.string.steam_store_header_image_description)).fetchSemanticsNodes().isNotEmpty() }
        val cover = compose.onNodeWithContentDescription(label(R.string.steam_store_header_image_description)).fetchSemanticsNode().boundsInRoot
        assertTrue(cover.bottom <= price.top)
        screenshot("detail-light")
        compose.onNodeWithContentDescription(label(R.string.share)).performClick()
        compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
        compose.onNodeWithText(label(R.string.store_detail_reviews)).performClick()
        compose.onNodeWithText(label(R.string.steam_store_reviews_title)).assertIsDisplayed()
        compose.onNodeWithTag("store_detail_topbar").assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
        compose.onNodeWithText(label(R.string.store_detail_purchase)).performClick()
        compose.onNodeWithText("标准版").assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
        compose.onNodeWithText(label(R.string.store_detail_official)).performClick()
        compose.runOnIdle { assertEquals(1, compared); assertEquals(1, shared); assertEquals(1, official) }
    }
    @Test fun unavailableDetailSeparatesReferencePriceInLargeDarkLayout() {
        large = true; launch(detail = true, unavailable = true)
        compose.onNodeWithText(label(R.string.steam_store_unavailable_account_region)).assertIsDisplayed()
        screenshot("detail-large-dark")
    }
}
