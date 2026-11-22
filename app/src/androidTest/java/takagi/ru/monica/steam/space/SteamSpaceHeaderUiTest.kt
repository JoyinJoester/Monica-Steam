package takagi.ru.monica.steam.space

import android.graphics.Bitmap
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamChineseComposeFixtureActivity
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamGlassEffectsEnabled
import takagi.ru.monica.steam.space.domain.SteamSpaceAchievement
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.presentation.SteamSpaceUiState
import takagi.ru.monica.steam.space.ui.SteamSpaceChrome
import takagi.ru.monica.steam.space.ui.SteamSpaceScreen
import takagi.ru.monica.steam.space.ui.SteamSpaceTopBar
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

@RunWith(AndroidJUnit4::class)
class SteamSpaceHeaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamChineseComposeFixtureActivity>()
    private var state by mutableStateOf(SteamSpaceUiState(loaded = true, stale = true, items = (0..3).map {
        SteamSpaceEvent("space-header-$it", SteamSpaceEventKind.STATUS, "76561198000000901", 1795222800L - it * 3600,
            body = listOf("合成动态：合作关卡完成，下一站继续冒险。", "今天发现了一个新的隐藏房间。", "一起玩 Portal 2 的伙伴们，明天见！", "新一轮挑战开始了。")[it],
            sourceUrl = "https://steamcommunity.com/my/home/")
    }))
    private var glass by mutableStateOf(false)
    private var dark by mutableStateOf(false)
    private var large by mutableStateOf(false)
    private var effects by mutableStateOf(true)
    private var back = 0
    private var refresh = 0
    private var publish = 0

    private fun host(content: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = LocalDensity.current
            val widthDp = if (large) 320f else 360f
            val scale = compose.activity.resources.displayMetrics.widthPixels / density.density / widthDp
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, if (large) 1.5f else 1f),
                LocalSteamDesignStyle provides if (glass) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED,
                LocalSteamGlassEffectsEnabled provides effects, LocalReduceAnimations provides true) {
                MonicaTheme(darkTheme = dark) { Surface(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    private fun screen() = host {
        SteamSpaceScreen(state, SteamFriendsUiState(), onBack = { back++ },
            onRefresh = { refresh++; state = state.copy(refreshing = true) },
            onLoadMore = {}, onOpenOriginal = {}, onOpenGame = {}, onMessage = {}, onPublish = { publish++ })
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(compose.activity.getExternalFilesDir(null), "space-header").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun floatingHeaderKeepsBackFiltersRefreshAndPublishInBothStyles() {
        screen()
        for (liquid in listOf(false, true)) {
            compose.runOnIdle { glass = liquid; state = state.copy(refreshing = false) }
            compose.onNodeWithTag("steam-space-filters").performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.steam_space_content_settings)).assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("steam-space-publish").performClick()
            compose.onNodeWithTag("steam-space-refresh").performClick().assertIsNotEnabled()
            compose.runOnIdle { assertEquals(if (liquid) 2 else 1, refresh); assertEquals(if (liquid) 2 else 1, publish) }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.steam_space_refresh)).assertExists()
            save(if (liquid) "glass-busy" else "m3-busy")
        }
        compose.onNodeWithTag("steam-space-back").performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test fun narrowLargeTextMeasuresHeaderAndKeepsAllActionsClearOfFeed() {
        large = true; dark = true
        screen()
        for (liquid in listOf(false, true)) {
            compose.runOnIdle { glass = liquid }
            compose.onNodeWithTag("steam-space-back").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("steam-space-filters").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            compose.onNodeWithTag("steam-space-refresh").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            compose.onNodeWithTag("steam-space-publish").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val title = compose.onNodeWithText(compose.activity.getString(R.string.steam_space_title)).fetchSemanticsNode().boundsInRoot
            val filters = compose.onNodeWithTag("steam-space-filters").fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("steam-space-top-bar").fetchSemanticsNode().boundsInRoot
            val recent = compose.onNodeWithText(compose.activity.getString(R.string.steam_space_recent)).fetchSemanticsNode().boundsInRoot
            assertTrue("title has its own space", title.width > 0 && title.right <= filters.left)
            assertTrue("feed clears actual large header", recent.top >= bar.bottom)
            save(if (liquid) "glass-dark-large" else "m3-dark-large")
        }
    }

    @Test fun activeFilterIsAnnouncedAndLoadingNeverDispatchesRefresh() {
        var filtered by mutableStateOf(true)
        var busy by mutableStateOf(true)
        host { SteamSpaceChrome(header = {
            SteamSpaceTopBar(filtered, busy, {}, { filtered = !filtered }, { refresh++ })
        }, content = {}) }
        compose.onNodeWithTag("steam-space-filters").assertIsSelected().performClick().assertIsNotSelected()
        compose.onNodeWithTag("steam-space-refresh").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, refresh); busy = false }
        compose.onNodeWithTag("steam-space-refresh").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refresh) }
    }

    @Test fun cachedAchievementOldUrlLoadsActualImageThroughCanonicalCacheKey() {
        val oldUrl = "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/620/monica-space-header-fixture.png"
        val resolved = requireNotNull(takagi.ru.monica.steam.space.data.SteamSpaceLinks.achievementIcon(620, oldUrl))
        val file = takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile(compose.activity, resolved)
        check(!file.exists())
        file.parentFile!!.mkdirs()
        val source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(245, 189, 71)) }
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }; source.recycle()
        try {
            state = state.copy(items = listOf(state.items.first().copy(kind = SteamSpaceEventKind.ACHIEVEMENT,
                appIds = listOf(620), body = null, achievements = listOf(SteamSpaceAchievement("COOP", "默契搭档", iconUrl = oldUrl)))))
            screen()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("steam-space-achievement-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("steam-space-achievement-image", useUnmergedTree = true).assertIsDisplayed()
            val image = compose.onNodeWithTag("steam-space-achievement-image", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            val pixel = image.getPixel(image.width / 2, image.height / 2)
            assertTrue(android.graphics.Color.red(pixel) > 220 && android.graphics.Color.green(pixel) > 150 && android.graphics.Color.blue(pixel) < 100)
            save("cached-achievement-corrected")
            compose.runOnIdle { dark = true; large = true }
            compose.onNodeWithTag("steam-space-achievement-image", useUnmergedTree = true).assertIsDisplayed()
            save("cached-achievement-dark-large")
        } finally { file.delete() }
    }

    @Test fun liveFeedColorReachesBothGlassMaterialsAndFallbackRemainsUsable() {
        var red by mutableStateOf(true)
        host { SteamSpaceChrome(header = { SteamSpaceTopBar(false, false, {}, {}, {}) },
            content = { Box(Modifier.fillMaxSize().background(if (red) Color.Red else Color.Blue)) }) }
        for (liquid in listOf(false, true)) {
            compose.runOnIdle { glass = liquid; red = true }
            val first = compose.onNodeWithTag("steam-space-title-capsule").captureToImage().asAndroidBitmap()
            compose.runOnIdle { red = false }
            val second = compose.onNodeWithTag("steam-space-title-capsule").captureToImage().asAndroidBitmap()
            val x = first.width / 2; val y = first.height - 12
            assertNotEquals("live recorded feed must change the material", first.getPixel(x, y), second.getPixel(x, y))
        }
        compose.runOnIdle { effects = false }
        compose.onNodeWithTag("steam-space-back").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("steam-space-refresh").assertIsEnabled()
    }
}
