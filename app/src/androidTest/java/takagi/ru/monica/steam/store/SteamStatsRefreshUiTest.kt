package takagi.ru.monica.steam.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.*
import takagi.ru.monica.steam.friends.chat.ui.SteamInboxTopBar
import takagi.ru.monica.steam.library.*
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.store.points.domain.*
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamStatsRefreshUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val avatar = "https://avatars.fastly.steamstatic.com/stats-refresh-avatar.png"
    private val artwork = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/620/stats-refresh-header.png"
    private var publicArtwork = false
    private var opened = ""
    private var refreshing by mutableStateOf(false)
    private var refreshes = 0
    private fun label(id: Int) = compose.activity.getString(id)
    private fun fixtureImage(url: String, wide: Boolean = false) {
        val file = steamRemoteImageCacheFile(compose.activity, url).apply { parentFile!!.mkdirs() }
        val publicFile = File(compose.activity.filesDir, "stats-refresh-public-${if (wide) "header" else "avatar"}.jpg")
        if (publicArtwork && publicFile.isFile) { file.writeBytes(publicFile.readBytes()); return }
        val bitmap = Bitmap.createBitmap(if (wide) 720 else 96, if (wide) 320 else 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(22, 113, 157))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 181, 64) }
        canvas.drawCircle(if (wide) 540f else 48f, if (wide) 110f else 48f, if (wide) 100f else 30f, paint)
        if (wide) {
            paint.color = Color.rgb(22, 53, 79)
            repeat(7) { canvas.drawRect(it * 90f, 180f + it * 8, it * 90f + 65, 320f, paint) }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Composable private fun Theme(large: Boolean = false, glass: Boolean = false, reduced: Boolean = true, dark: Boolean = glass, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(compose.activity.resources.displayMetrics.widthPixels / 380f, if (large) 1.5f else 1f),
            LocalReduceAnimations provides reduced, LocalSteamDesignStyle provides if (glass) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED) {
            MonicaTheme(darkTheme = dark) { Surface(Modifier.fillMaxSize()) { content() } }
        }
    }
    private fun mountStats(large: Boolean = false, glass: Boolean = false) {
        fixtureImage(avatar); fixtureImage(artwork, true)
        val extras = SteamGameStatsExtras(avatarUrl = avatar,
            friends = SteamGameFriendsActivity(SteamGameFriendTime("76561198000000001", 1740, 1026), listOf(
                SteamGameFriendTime("76561198000000002", 3600, 30, "Atlas", avatar),
                SteamGameFriendTime("76561198000000003", 600, 10, "P-body", avatar))),
            badges = SteamGameBadgeCatalog("Cooperative Testing", avatar, "Level 2", (1..5).map { SteamGameBadgeCard("Card $it", "$avatar?card=$it", it < 3) }),
            points = SteamPointsShopPage(SteamPointsShopCategory.FEATURED,
                listOf(SteamPointsShopItem(620, 123, 1, 4, 100, "Portal", smallImageUrl = avatar))))
        extras.badges!!.cards.forEach { fixtureImage(it.imageUrl) }
        compose.setContent { Theme(large, glass) {
            SteamGamePersonalStatsContent("Portal 2", "Test player", SteamGamePersonalStats(
                game = SteamGame(620, "Portal 2", 1740, 1026), loading = false,
                achievements = SteamGameAchievements(1, 620, "Portal 2", listOf(
                    SteamAchievement("first", "First experiment", "Complete a test chamber", true, null, null, null),
                    SteamAchievement("hidden", "Spoiler name", "Spoiler description", false, null, null, null, hidden = true)), 1)),
                onChooseAccount = {}, onRetry = { refreshes++ }, onOpenGameData = { opened = "data" },
                onOpenScreenshots = { opened = "screenshots" }, extras = extras, heroImageUrl = artwork,
                appId = 620, steamId = "76561198000000001", onOpenWeb = { opened = it }, onOpenPointsShop = { opened = "points" }, onOpenReward = { opened = "reward:${it.definitionId}" })
        } }
        compose.waitForIdle()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.filesDir, "stats-refresh/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun statsShowsActualAvatarAxisAndGameSpecificCollectibles() {
        mountStats()
        compose.waitUntil(5000) {
            val pixels = compose.onNodeWithTag("game_stats_avatar").captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2].red > .8f
        }
        compose.onNodeWithTag("game_friend_time_axis").assertExists()
        shot("stats-light-top")
        compose.onNodeWithTag("game_personal_list").performScrollToKey("collectibles")
        shot("stats-light-collections")
        compose.onNodeWithTag("game_personal_list").performScrollToKey("collectibles")
        compose.onNodeWithText("Portal", substring = false).performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("reward:123", opened)
        compose.onNodeWithTag("game_personal_list").performScrollToNode(hasText(label(R.string.steam_achievement_hidden)))
        compose.onNodeWithText("Spoiler name").assertDoesNotExist()
        shot("stats-light-achievements")
    }
    @Test fun publicArtworkPreviewWithSyntheticStatistics() {
        org.junit.Assume.assumeTrue(File(compose.activity.filesDir, "stats-refresh-public-header.jpg").isFile)
        publicArtwork = true; mountStats(glass = true)
        compose.waitForIdle(); android.os.SystemClock.sleep(700)
        shot("stats-public-artwork-top")
        compose.onNodeWithTag("game_personal_list").performScrollToKey("collectibles")
        shot("stats-public-artwork-collections")
    }
    @Test fun menuIndicatorHasRealMotionAndReducedMotionStaysStill() {
        var reduced by mutableStateOf(false)
        // Compose's infinite-animation policy cancels indeterminate effects when
        // auto-advance is enabled. Start with the manual clock before composition.
        compose.mainClock.autoAdvance = false
        compose.setContent { Theme(reduced = reduced) {
            Box(Modifier.fillMaxWidth()) { SteamPageOverflowMenu(true, {}, {}, {}) }
        } }
        compose.mainClock.autoAdvance = false
        val node = compose.onNodeWithTag("steam_refresh_shape", useUnmergedTree = true)
        compose.mainClock.advanceTimeBy(150)
        val first = node.captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(350)
        val second = node.captureToImage().toPixelMap()
        var differences = 0
        for (y in 0 until first.height) for (x in 0 until first.width) if (first[x,y] != second[x,y]) differences++
        assertTrue("Shape should morph in place", differences > 20)
        compose.runOnUiThread { reduced = true }; compose.mainClock.advanceTimeBy(50)
        val still = node.captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(500)
        val later = node.captureToImage().toPixelMap()
        for (y in 0 until still.height) for (x in 0 until still.width) assertEquals(still[x,y], later[x,y])
        compose.mainClock.autoAdvance = true
    }
    @Test fun statsMenuKeepsGameDataAndScreenshotsReachable() {
        mountStats()
        compose.onNodeWithTag("game_stats_menu").performClick()
        compose.onNodeWithText(label(R.string.steam_game_stats_game_data)).performClick()
        assertEquals("data", opened)
        compose.onNodeWithTag("game_stats_menu").performClick()
        compose.onNodeWithText(label(R.string.steam_game_stats_screenshots)).performClick()
        assertEquals("screenshots", opened)
    }
    @Test fun glassLargeTypeKeepsMetricsAndCollectiblesScrollable() {
        mountStats(large = true, glass = true)
        compose.onNodeWithTag("game_total_playtime").assertIsDisplayed()
        shot("stats-glass-large-top")
        compose.onNodeWithTag("game_personal_list").performScrollToKey("collectibles")
        compose.onNodeWithText("Cooperative Testing").assertIsDisplayed()
        shot("stats-glass-large-collections")
    }
    @Test fun libraryMenuMorphsInPlaceAndStillOpensWhileRefreshing() {
        var notifications = 0
        compose.setContent { Theme {
            Column {
                SteamPageOverflowMenu(refreshing, { refreshes++ }, { notifications++ }, {})
                SteamExpressivePullToRefresh(refreshing, { refreshes++; refreshing = true }, showIndicator = false,
                    modifier = Modifier.testTag("refresh-body")) {
                    LazyColumn(Modifier.fillMaxSize()) { items(30) { Text("Row $it", Modifier.fillMaxWidth().height(56.dp)) } }
                }
            }
        } }
        val before = compose.onNodeWithContentDescription(label(R.string.more_options)).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("refresh-body").performTouchInput { swipeDown(startY = height * .15f, endY = height * .9f) }
        compose.waitUntil(3000) { refreshes == 1 }
        val after = compose.onNodeWithContentDescription(label(R.string.more_options)).fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onAllNodesWithTag("steam_refresh_shape", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).assertCountEquals(0)
        shot("library-menu-refresh")
        compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
        compose.onNodeWithText(label(R.string.refresh)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.steam_notifications_title)).performClick()
        assertEquals(1, notifications)
    }
    @Test fun nativeThreadMenuRemainsClickableWhenGlyphIsReplaced() {
        var invoked = 0
        compose.setContent { Theme(glass = true) {
            Box(Modifier.fillMaxWidth()) {
                takagi.ru.monica.steam.friends.chat.telegram.TelegramChatHeader("Test friend", "Online",
                    onBack = {}, onInfo = {}, actions = listOf(
                        takagi.ru.monica.steam.friends.chat.telegram.TelegramChatAction("Inspect thread", { invoked++ })),
                    sourceView = null, refreshing = true)
            }
        } }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(label(R.string.more_options)))
            .perform(androidx.test.espresso.action.ViewActions.click())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Inspect thread"))
            .perform(androidx.test.espresso.action.ViewActions.click())
        assertEquals(1, invoked)
        shot("native-thread-refresh")
    }
    @Test fun chatMenuRetainsItsActionsDuringRefresh() {
        var spaces = 0
        var glass by mutableStateOf(true)
        compose.setContent { Theme(glass = glass, dark = true) {
            takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost(
                Modifier.fillMaxSize(),
                content = {
                    Column(Modifier.fillMaxSize().padding(top = 112.dp)) {
                        listOf("Atlas", "P-body", "Co-op group").forEach { name ->
                            ListItem(headlineContent = { Text(name) }, supportingContent = { Text("Recent message") })
                        }
                    }
                }, overlay = {
                SteamInboxTopBar(null, true, "", {}, onShowAccounts = {}, onOpenSpace = { spaces++ },
                    onCreateGroup = {}, onRefresh = { refreshes++ }, refreshing = true)
                }
            )
        } }
        for (style in listOf(true, false)) {
            compose.runOnIdle { glass = style }
            compose.onNodeWithContentDescription(label(R.string.steam_chat_options)).performClick()
            compose.onNodeWithText(label(R.string.steam_friends_refresh)).assertIsNotEnabled()
            shot(if (style) "chat-menu-open-glass" else "chat-menu-open-m3e")
            compose.onNodeWithText(label(R.string.steam_space_title)).performClick()
            compose.waitForIdle()
        }
        assertEquals(2, spaces); assertEquals(0, refreshes)
        shot("chat-menu-refresh")
    }
}
