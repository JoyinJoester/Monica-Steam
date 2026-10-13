package takagi.ru.monica.steam.store

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.steamdb.data.*
import takagi.ru.monica.steam.steamdb.domain.*
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.ui.SteamStoreDetailHeader
import takagi.ru.monica.ui.theme.MonicaTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File

class SteamStorePriceSummaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = CopyOnWriteArrayList<String>()
    private var failed = false
    private var compared = 0
    private var detail by mutableStateOf(SteamStoreDetail(413150, "Stardew Valley",
        currency = "CNY", finalPriceCents = 4800, accountCountryCode = "CN",
        priceCountryCode = "CN", availableInAccountRegion = true))
    private val repository = SteamDbRepository(object : SteamDbGateway {
        override fun info(appId: Int): SteamDbResult<SteamDbAppInfo> = error("Summary must only request price")
        override fun players(appId: Int): SteamDbResult<Long> = error("Summary must only request price")
        override fun rating(appId: Int): SteamDbResult<SteamDbRating> = error("Summary must only request price")
        override fun price(appId: Int, region: String): SteamDbResult<SteamDbLowestPrice> {
            calls += "$appId:$region"
            return if (failed) SteamDbResult.Failed(SteamDbFailure.NETWORK)
            else SteamDbResult.Ready(SteamDbLowestPrice(if (region == "CNY") "¥24.00" else "$2.99",
                50, null, 8, 1778716800L), System.currentTimeMillis())
        }
    }, scope)
    @After fun close() { scope.cancel() }
    private fun launch(large: Boolean = false) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.5f else 1f)) {
            MonicaTheme(darkTheme = large) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                    Column(Modifier.width(320.dp).padding(12.dp)) {
                        SteamStoreDetailHeader(detail, false, { compared++ }, {}, steamDbRepository = repository)
                    }
                    }
                }
            }
        }
    }
    private fun awaitPrice(value: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("store_detail_price_group").captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun currentAndLowestPricesAreAdjacentAndCompareRemainsAvailable() {
        launch()
        awaitPrice("¥24.00")
        compose.onNodeWithText(detail.formattedFinalPrice).assertIsDisplayed()
        compose.onNodeWithText("¥24.00").assertIsDisplayed()
        val current = compose.onNodeWithTag("store_detail_current_price").fetchSemanticsNode().boundsInRoot
        val low = compose.onNodeWithTag("store_detail_history_price").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(current.left, low.left, 1f)
        assertEquals(current.right, low.right, 1f)
        assertEquals(2f, (low.top - current.bottom) / density, 1f)
        assertTrue((low.bottom - current.top) / density <= 180f)
        screenshot("price-summary-light")
        compose.onNodeWithTag("store_detail_current_price").performClick()
        compose.runOnIdle { assertEquals(1, compared); assertEquals(listOf("413150:CNY"), calls.toList()) }
    }
    @Test fun priceFailureDoesNotHideCurrentPriceAndRetryRecovers() {
        failed = true; launch()
        val error = compose.activity.getString(R.string.steamdb_network_error)
        awaitPrice(error)
        compose.onNodeWithText(detail.formattedFinalPrice).assertIsDisplayed()
        compose.runOnIdle { failed = false }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.steamdb_retry)).performClick()
        awaitPrice("¥24.00")
        compose.onNodeWithText("¥24.00").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, calls.size) }
    }
    @Test fun freeUnpricedAndUnavailableGamesDoNotRequestMisleadingHistory() {
        detail = detail.copy(isFree = true, finalPriceCents = 0); launch()
        compose.onNodeWithTag("store_detail_history_price").assertDoesNotExist()
        compose.runOnIdle { detail = detail.copy(isFree = false, finalPriceCents = null) }
        compose.onNodeWithTag("store_detail_history_price").assertDoesNotExist()
        compose.runOnIdle { detail = detail.copy(finalPriceCents = 4800, availableInAccountRegion = false, accountCountryCode = "US") }
        compose.onNodeWithTag("store_detail_history_price").assertDoesNotExist()
        compose.onNodeWithTag("store_detail_current_price").assertIsDisplayed()
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
    }
    @Test fun largeTextKeepsBothPricesAndSwitchingRegionClearsOldHistory() {
        launch(large = true); awaitPrice("¥24.00")
        compose.onNodeWithText(detail.formattedFinalPrice).assertIsDisplayed()
        compose.onNodeWithText("¥24.00").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.steamdb_open)).assertIsDisplayed()
        screenshot("price-summary-large-dark")
        compose.runOnIdle { detail = detail.copy(currency = "USD", accountCountryCode = "PK", priceCountryCode = "PK") }
        awaitPrice("$2.99")
        compose.onNodeWithText("¥24.00").assertDoesNotExist()
        compose.onNodeWithText("$2.99").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("413150:CNY", "413150:USD-SASIA"), calls.toList()) }
    }
}
