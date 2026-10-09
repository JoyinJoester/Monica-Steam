package takagi.ru.monica.steam.steamdb

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.steamdb.data.*
import takagi.ru.monica.steam.steamdb.domain.*
import takagi.ru.monica.steam.steamdb.ui.*
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamDbUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = CountDownLatch(1)
    private val priceCalls = AtomicInteger()
    private var query by mutableStateOf(SteamDbQuery(620, "CN", "CNY"))
    private var fontScale by mutableFloatStateOf(1f)
    private var dark by mutableStateOf(false)
    private var fail = false
    private var delayed = false
    private val originalLocale = Locale.getDefault()
    private val api = object : SteamDbGateway {
        override fun info(appId: Int): SteamDbResult<SteamDbAppInfo> = if (fail) SteamDbResult.Failed(SteamDbFailure.SERVICE)
            else SteamDbResult.Ready(SteamDbAppInfo(12L, 8120L, 98460L, 624180L, 1790726400L), System.currentTimeMillis())
        override fun players(appId: Int): SteamDbResult<Long> = SteamDbResult.Ready(5420L, System.currentTimeMillis())
        override fun rating(appId: Int): SteamDbResult<SteamDbRating> = SteamDbResult.Ready(SteamDbRating(400000, 2100), System.currentTimeMillis())
        override fun price(appId: Int, region: String): SteamDbResult<SteamDbLowestPrice> {
            priceCalls.incrementAndGet()
            if (delayed && appId == 620) check(gate.await(20, TimeUnit.SECONDS))
            return if (fail) SteamDbResult.Failed(SteamDbFailure.RATE_LIMITED, System.currentTimeMillis()+60_000)
            else SteamDbResult.Ready(SteamDbLowestPrice(if (appId == 620) "¥ 4.20" else "$ 0.99", 90, "¥ 6.00".takeIf { appId == 620 }, 12, 1750809600), System.currentTimeMillis())
        }
    }
    @After fun close() { gate.countDown(); scope.cancel(); Locale.setDefault(originalLocale) }
    private fun label(id: Int): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }).getString(id)
    }
    private fun launch(priceOnly: Boolean = false, settings: Boolean = false) {
        val repository = SteamDbRepository(api, scope)
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        compose.activityRule.scenario.onActivity {
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(Configuration(it.resources.configuration).apply {
                setLocale(Locale.SIMPLIFIED_CHINESE)
                this.fontScale = this@SteamDbUiTest.fontScale
            }, it.resources.displayMetrics)
            it.enableEdgeToEdge()
        }
        compose.setContent {
            val base = LocalContext.current
            val config = remember { Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) } }
            val context = remember { base.createConfigurationContext(config) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MonicaTheme(darkTheme = dark) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("steamdb_preview")) {
                        if (settings) SteamDbSettingsScreen({})
                        else if (priceOnly) Column(Modifier.verticalScroll(rememberScrollState())) { SteamDbPriceSection(query, repository = repository) }
                        else SteamDbDetailsEntry(query, "Portal 2", repository = repository)
                    }
                }
            }
        }
    }
    private fun open() = compose.onNodeWithTag("steamdb_entry").performClick()
    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun entryLoadsOnDemandAndDisplaysPublicData() {
        launch()
        compose.runOnIdle { assertEquals(0, priceCalls.get()) }
        open()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("¥ 4.20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("5,420").assertExists()
        compose.onNodeWithText("近两年史低：¥ 6.00").assertExists()
        compose.onNodeWithText(label(R.string.steamdb_players_official)).assertExists()
        screenshot("steamdb-data")
        compose.onNodeWithText(label(R.string.steamdb_open)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.steamdb_attribution)).performScrollTo().assertIsDisplayed()
    }
    @Test fun partialFailureKeepsSteamPlayersAndCanRefresh() {
        fail = true
        launch()
        open()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("5,420").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label(R.string.steamdb_rate_limited)).assertExists()
        compose.onNodeWithContentDescription(label(R.string.steamdb_refresh)).assertIsEnabled().performClick()
        compose.waitForIdle()
        screenshot("steamdb-partial")
        compose.runOnIdle { assertEquals(1, priceCalls.get()) }
    }
    @Test fun darkLargeTextRetainsPriceAndSourceActions() {
        fontScale = 1.5f; dark = true
        launch()
        open()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("¥ 4.20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("¥ 4.20").assertIsDisplayed()
        screenshot("steamdb-large-dark")
        compose.onNodeWithText(label(R.string.steamdb_open)).performScrollTo().assertIsDisplayed()
    }
    @Test fun switchingGameAndRegionCannotDisplayLateOldPrice() {
        delayed = true
        launch(priceOnly = true)
        compose.waitUntil(5_000) { priceCalls.get() == 1 }
        compose.runOnIdle { query = SteamDbQuery(440, "PK", "USD") }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("$ 0.99").fetchSemanticsNodes().isNotEmpty() }
        gate.countDown()
        compose.waitForIdle()
        compose.onNodeWithText("¥ 4.20").assertDoesNotExist()
        compose.onNodeWithText("$ 0.99").assertIsDisplayed()
        screenshot("steamdb-region")
    }
    @Test fun freeGameAndSettingsDoNotRequireKeys() {
        query = SteamDbQuery(570, "CN", "CNY", true)
        launch(priceOnly = true)
        compose.onNodeWithText(label(R.string.steamdb_free)).assertExists()
        compose.runOnIdle { assertEquals(0, priceCalls.get()) }
    }
    @Test fun settingsExplainsBuiltInSourceWithoutCredentialInput() {
        launch(settings = true)
        compose.onNodeWithText(label(R.string.steamdb_settings_description)).assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        screenshot("steamdb-settings")
    }
}
