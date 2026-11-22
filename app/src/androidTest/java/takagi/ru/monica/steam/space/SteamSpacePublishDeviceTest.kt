package takagi.ru.monica.steam.space

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.SteamFixedBottomBar
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.space.data.*
import takagi.ru.monica.steam.space.domain.*
import takagi.ru.monica.steam.space.presentation.*
import takagi.ru.monica.steam.space.ui.*
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import takagi.ru.monica.steam.network.SteamProtoReader

class SteamSpacePublishDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val account = SteamAccount(7, "76561198000000001", "fixture", "Fixture", "test", "",
        null, null, null, "synthetic-token", null, null, "{}", true, 0, 0, 0)

    private fun event() : List<SteamSpaceEvent> {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val payload = if (request.url.host == "api.steampowered.com") ByteArray(0) else {
                assertNull(request.url.queryParameter("access_token")); assertNull(request.header("Cookie"))
                val id = request.url.queryParameter("appids")!!.toInt()
                val name = if (id == 815370) "Green Hell" else "Cyberpunk 2077"
                """{"$id":{"success":true,"data":{"steam_appid":$id,"name":"$name"}}}""".toByteArray()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(payload.toResponseBody()).build()
        }.build()
        val cm = object : SteamCmGateway {
            override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
                assertEquals(SteamSpaceService.NEWS_METHOD, method)
                return SteamProtoWriter().apply {
                    for ((index, id) in listOf(815370,1091500).withIndex()) writeMessage(1, SteamProtoWriter().apply {
                        writeVarint(1,2); writeVarint(2,1800000000L-index); writeFixed64(3,account.steamId.toLong())
                        writeFixed64(5,id.toLong()); writeString(8,"progress")
                    })
                }.toByteArray()
            }
            override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray) = error("Unrelated")
        }
        return SteamSpaceService(cm, actorLookup = { _, _ -> mapOf(account.steamId to SteamSpaceActor("Fixture friend", "")) },
            appLookup = SteamSpaceAppNames(SteamApiClient(client),client)::load).fetchPage(account,"english",null).events
    }

    private fun save(name: String) {
        val file = File(rule.activity.filesDir,"space-post/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    private fun mount(large: Boolean, liquid: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(rule.activity.resources.displayMetrics.widthPixels / 360f, if(large)1.5f else 1f),
                LocalSteamDesignStyle provides if(liquid) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED,
                LocalReduceAnimations provides true) {
                MonicaTheme(darkTheme = liquid) { Surface(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    private fun feed(large: Boolean, liquid: Boolean) {
        val events = event()
        var clicks = 0
        mount(large,liquid) {
            val density = LocalDensity.current
            var dockHeight by remember { mutableStateOf(100.dp) }
            CompositionLocalProvider(LocalSteamDockContentClearance provides dockHeight) {
                Box(Modifier.fillMaxSize()) {
                    SteamSpaceScreen(SteamSpaceUiState(items=events,loaded=true),SteamFriendsUiState(),{}, {}, {}, {}, {}, {}, {},
                        onPublish = { clicks++ })
                    SteamFixedBottomBar(Modifier.align(Alignment.BottomCenter).testTag("fixture-dock")
                        .onSizeChanged { dockHeight=with(density){it.height.toDp()} },
                        order=SteamDockTab.FIXED_DEFAULT_ORDER,selected=SteamDockTab.CHAT,onSelected={})
                }
            }
        }
        rule.onNodeWithText("Green Hell").assertIsDisplayed()
        rule.onNodeWithText("Cyberpunk 2077").assertIsDisplayed()
        val fab=rule.onNodeWithTag("steam-space-publish")
        fab.assertIsDisplayed()
        val fabBounds=fab.fetchSemanticsNode().boundsInRoot
        val dockBounds=rule.onNodeWithTag("fixture-dock").fetchSemanticsNode().boundsInRoot
        assertTrue("FAB must clear the dock",fabBounds.bottom < dockBounds.top)
        assertTrue("Use a square FAB", kotlin.math.abs(fabBounds.width-fabBounds.height)<2f)
        fab.performClick(); rule.runOnIdle { assertEquals(1,clicks) }
        save(if(large)"feed-large" else if(liquid)"feed-glass" else "feed-m3e")
    }
    @Test fun screenshotIdsResolveThroughPublicFallbackAndM3eFabClearsDock() = feed(false,false)
    @Test fun glassFabClearsDockAndUsesAccessiblePublishLabel() = feed(false,true)
    @Test fun largeTextStillShowsNamesAndKeepsPublishFabReachable() = feed(true,true)

    @Test fun nativeDraftSubmitsOnlyOnUserClickAndKeepsExactText() {
        val sent = CopyOnWriteArrayList<String>()
        val release = CountDownLatch(1)
        val cm = object : SteamCmGateway {
            override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
                assertEquals(SteamSpacePostService.METHOD,method)
                val fields=SteamProtoReader(request).parse()
                assertEquals(0L,fields.getValue(1).asLong)
                sent+=fields.getValue(2).asString
                assertTrue(release.await(20,TimeUnit.SECONDS))
                return ByteArray(0)
            }
            override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray)=error("Unrelated")
        }
        val model=SteamSpaceComposeViewModel(SteamSpacePostService(cm))
        mount(false,false) {
            val state by model.state.collectAsState()
            LaunchedEffect(model){model.selectAccount(account)}
            DisposableEffect(model){onDispose{model.clear()}}
            SteamSpaceComposeScreen(account,state,model::updateText,model::publish,{}, {})
        }
        try {
            rule.waitUntil(5_000){model.state.value.accountId==account.id}
            rule.onNodeWithTag("steam-space-post-send").assertIsNotEnabled()
            rule.onNodeWithTag("steam-space-post-text").performTextInput("Hello\nSteam 😃")
            rule.runOnIdle { assertTrue(sent.isEmpty()) }
            rule.onNodeWithTag("steam-space-post-send").performScrollTo().performClick()
            rule.waitUntil(5_000){sent.size==1}
            rule.onNodeWithTag("steam-space-post-send").assertIsNotEnabled()
            rule.onNodeWithTag("steam-space-post-web").assertIsNotEnabled()
            rule.runOnIdle { assertEquals(listOf("Hello\nSteam 😃"),sent) }
            save("native-sending")
            release.countDown()
            rule.waitUntil(5_000){model.state.value.phase==SteamSpaceComposePhase.SUCCEEDED}
            assertEquals(1,sent.size)
        } finally { release.countDown(); rule.runOnIdle{model.clear()} }
    }

    @Test fun unknownOutcomeKeepsDraftPreventsResubmitAndOffersOfficialVerificationInLargeText() {
        var checks=0
        val draft="Retained draft\nCheck before retry"
        val calls=AtomicInteger()
        val model=SteamSpaceComposeViewModel(SteamSpacePostGateway{_,_->calls.incrementAndGet();throw IOException("Lost response fixture")})
        mount(true,true) {
            val state by model.state.collectAsState()
            LaunchedEffect(model){model.selectAccount(account);model.updateText(draft)}
            DisposableEffect(model){onDispose{model.clear()}}
            SteamSpaceComposeScreen(account,state,model::updateText,model::publish,{}, {checks++})
        }
        rule.waitUntil(5_000){model.state.value.canSubmit}
        rule.onNodeWithTag("steam-space-post-send").performScrollTo().performClick()
        rule.waitUntil(5_000){model.state.value.phase==SteamSpaceComposePhase.UNKNOWN}
        rule.onNodeWithTag("steam-space-post-text").assertTextContains(draft)
        rule.onNodeWithTag("steam-space-post-unknown").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("steam-space-post-send").assertIsNotEnabled()
        rule.onNodeWithTag("steam-space-post-web").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,checks) }
        assertEquals(1,calls.get())
        save("native-unknown-large")
    }

    @Test fun leavingUnsentDraftRequiresExplicitDiscardAndCancelRetainsIt() {
        var closes=0
        mount(false,false) {
            SteamSpaceComposeScreen(account,SteamSpaceComposeState(account.id,account.steamId,"Draft"),{}, {}, {closes++}, {})
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.steam_space_back)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_space_post_keep_editing)).performClick()
        rule.runOnIdle { assertEquals(0,closes) }
        rule.onNodeWithTag("steam-space-post-text").assertTextContains("Draft")
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.steam_space_back)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_space_post_discard)).performClick()
        rule.runOnIdle { assertEquals(1,closes) }
    }
}
