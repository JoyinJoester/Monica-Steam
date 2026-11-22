package takagi.ru.monica.steam.space

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.space.data.SteamSpaceActor
import takagi.ru.monica.steam.space.data.SteamSpaceAppNames
import takagi.ru.monica.steam.space.data.SteamSpaceService
import takagi.ru.monica.steam.space.presentation.SteamSpaceUiState
import takagi.ru.monica.steam.space.ui.*
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamSpaceGameBrowserDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val account = SteamAccount(7, "76561198000000001", "fixture", "Fixture", "test", "",
        null, null, null, "synthetic-token", null, null, "{}", true, 0, 0, 0)

    private fun parsedEvent(withName: Boolean = true) = run {
        val cm = object : SteamCmGateway {
            override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
                assertEquals(SteamSpaceService.NEWS_METHOD, method)
                return SteamProtoWriter().apply {
                    writeMessage(1, SteamProtoWriter().apply {
                        writeVarint(1, 2); writeVarint(2, 1800000000); writeFixed64(3, account.steamId.toLong())
                        writeFixed64(5, 1091500); writeString(8, "WORLD")
                    })
                    writeMessage(2, SteamProtoWriter().apply {
                        writeVarint(1, 1091500)
                        writeMessage(2, SteamProtoWriter().apply {
                            writeString(1, "WORLD"); writeString(2, "The World"); writeString(3, "Complete the main storyline.")
                        })
                    })
                }.toByteArray()
            }
            override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray) = error("Unrelated CM request")
        }
        val api = SteamApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("api.steampowered.com", request.url.host)
            assertEquals("/IStoreBrowseService/GetItems/v1/", request.url.encodedPath)
            val fields = SteamProtoReader(Base64.getDecoder().decode(request.url.queryParameter("input_protobuf_encoded"))).parseAll()
            assertEquals(1091500, SteamProtoReader(fields.single { it.number == 1 }.bytes!!).parse().getValue(1).asInt)
            val response = SteamProtoWriter().apply {
                if (withName) writeMessage(1, SteamProtoWriter().apply {
                    writeVarint(1, 0); writeVarint(2, 1091500); writeVarint(3, 1)
                    writeString(6, "Cyberpunk 2077"); writeVarint(9, 0)
                })
            }.toByteArray()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toResponseBody()).build()
        }.build())
        SteamSpaceService(cm, actorLookup = { _, _ -> mapOf(account.steamId to SteamSpaceActor("Feed author", "")) },
            appLookup = SteamSpaceAppNames(api, OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("Unavailable fixture")
                    .body(ByteArray(0).toResponseBody()).build()
            }.build())::load).fetchPage(account, "english", null).events.single()
    }

    private fun mount(large: Boolean, withName: Boolean = true) {
        val event = parsedEvent(withName)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density *
                (rule.activity.resources.displayMetrics.widthPixels / density.density / 360f), if (large) 1.5f else 1f),
                LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS, LocalReduceAnimations provides true) {
                MonicaTheme(darkTheme = large) { Surface(Modifier.fillMaxSize()) {
                    SteamSpaceScreen(SteamSpaceUiState(items = listOf(event), loaded = true), SteamFriendsUiState(),
                        onBack = {}, onRefresh = {}, onLoadMore = {},
                        onOpenOriginal = { assertTrue(openSteamSpaceOriginalInBrowser(rule.activity, it)) },
                        onOpenGame = {}, onMessage = {}, onManageAccounts = {})
                } }
            }
        }
    }

    private fun save(name: String) {
        val file = File(rule.activity.filesDir, "space-game-browser/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun expanded(large: Boolean) {
        val opened = mutableListOf<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                opened += Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            mount(large)
            rule.onNodeWithText("Cyberpunk 2077").assertIsDisplayed()
            rule.onNodeWithText("Cyberpunk 2077").performTouchInput { longClick() }
            rule.onNodeWithText(rule.activity.getString(R.string.steam_space_view_original)).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Cyberpunk 2077").assertIsDisplayed()
            save(if (large) "achievement-dark-large" else "achievement-light")
            rule.onNodeWithText(rule.activity.getString(R.string.steam_space_view_original)).performClick()
            rule.runOnIdle {
                assertEquals(1, opened.size)
                val intent = opened.single()
                assertEquals("https://steamcommunity.com/profiles/${account.steamId}/", intent.dataString)
                assertEquals(Intent.ACTION_MAIN, intent.selector!!.action)
                assertTrue(intent.selector!!.hasCategory(Intent.CATEGORY_APP_BROWSER))
                assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
                assertNull(intent.extras)
                assertNull(intent.component)
                assertNull(intent.`package`)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun officialMetadataAppearsInFeedAndExpandedAchievementThenOpensBrowser() = expanded(false)
    @Test fun largeTextKeepsResolvedGameNameAndBrowserActionReachable() = expanded(true)

    @Test fun absentMetadataRetainsAnExplicitIdInsteadOfInventingAName() {
        mount(false, withName = false)
        rule.onNodeWithText(rule.activity.getString(R.string.steam_space_app_id, 1091500)).assertIsDisplayed()
        rule.onNodeWithText("Cyberpunk 2077").assertDoesNotExist()
    }

    @Test fun actualOriginalOpenLeavesMonicaAndResumesInInstalledBrowser() {
        try {
            rule.runOnIdle {
                assertTrue("Android must accept the browser-selected original URL",
                    openSteamSpaceOriginalInBrowser(rule.activity, "https://steamcommunity.com/my/home/"))
            }
            rule.waitUntil(15_000) {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.chromium"
            }
            assertEquals("com.android.chromium", instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        } finally {
            // Back closes the task's external browsing surface; retain the browser's user data.
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    @Test fun everyAllowedSourceUsesBrowserSelectionAndUnsafeUrlsNeverDispatch() {
        for (url in listOf("https://steamcommunity.com/my/home/", "https://steamcommunity.com/profiles/${account.steamId}/",
            "https://steamcommunity.com/profiles/${account.steamId}/status/1800000000/",
            "https://steamcommunity.com/profiles/${account.steamId}/recommended/1091500/",
            "https://steamcommunity.com/sharedfiles/filedetails/?id=123")) {
            val intent = steamSpaceOriginalBrowserIntent(url)!!
            assertEquals(url, intent.dataString)
            assertTrue(intent.selector!!.hasCategory(Intent.CATEGORY_APP_BROWSER))
        }
        var attempts = 0
        val blocked = object : ContextWrapper(rule.activity) {
            override fun startActivity(intent: Intent) { attempts++; throw ActivityNotFoundException("No browser fixture") }
        }
        for (url in listOf("https://steamcommunity.com.evil.test/my/home/", "https://steamcommunity.com/my/home/?access_token=secret",
            "steam://openurl/https://steamcommunity.com/my/home/", "https://steamcommunity.com/linkfilter/?url=https://evil.test")) {
            assertFalse(openSteamSpaceOriginalInBrowser(blocked, url))
        }
        assertEquals(0, attempts)
        assertFalse(openSteamSpaceOriginalInBrowser(blocked, "https://steamcommunity.com/my/home/"))
        assertEquals("Unavailable browser must never retry with a self-routing VIEW intent", 1, attempts)
    }
}
