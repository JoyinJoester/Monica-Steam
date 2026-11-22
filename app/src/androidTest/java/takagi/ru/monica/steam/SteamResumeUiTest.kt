package takagi.ru.monica.steam

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.ui.SteamChatFriendPicker
import takagi.ru.monica.steam.friends.domain.*
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.space.ui.*
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.steam.store.points.domain.*
import takagi.ru.monica.steam.store.points.data.*
import takagi.ru.monica.steam.store.points.presentation.*
import takagi.ru.monica.steam.store.points.ui.*
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamResumeUiTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val friends = (1..7).map { SteamFriend("7656119800000080$it", relationship = SteamFriendRelationship.FRIEND,
        personaName = "Friend $it", gameId = "620", gameName = "Portal 2") }
    private fun save(name: String) {
        val file = File(rule.activity.filesDir, "steam-resume/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Composable private fun Theme(large: Boolean = false, content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density *
            (rule.activity.resources.displayMetrics.widthPixels / d.density / 360f), if (large) 1.5f else 1f),
            LocalSteamDesignStyle provides SteamDockStyle.LIQUID_GLASS,
            LocalSteamGlassEffectsEnabled provides true, LocalReduceAnimations provides true) {
            MonicaTheme { Surface(Modifier.fillMaxSize()) { content() } }
        }
    }
    private fun contacts(large: Boolean) {
        var opened = ""
        rule.setContent { Theme(large) { SteamChatFriendPicker(friends, false, "", { opened = it }, {}) } }
        val bounds = rule.onNodeWithTag("steam-contacts-filter").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("Filter starts at the left edge", root.left, bounds.left, 1f)
        if (!large) assertTrue("No unused full-width tail", bounds.width < root.width - 12f)
        rule.onNodeWithTag("steam-glass-selection-lens", useUnmergedTree = true).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_friends_filter_playing)).performClick().assertIsSelected()
        save(if (large) "friends-glass-large" else "friends-glass")
        // Android-native contact rows still dispatch the exact identity through their native view.
        rule.runOnIdle { assertEquals("", opened) }
    }
    @Test fun contactsUseCompactLeftAlignedDockGlass() = contacts(false)
    @Test fun contactsLargeTextStillSelectsLastOption() = contacts(true)

    @Test fun collapsedPresenceUsesCompactSlotsAndExactFriendTargets() {
        lateinit var behavior: SteamSpaceHeaderScrollBehavior
        var opened = ""
        rule.setContent { Theme {
            behavior = rememberSteamSpaceHeaderScrollBehavior(rememberLazyListState())
            SteamGlassHost(Modifier.fillMaxSize(), content = {}, overlay = {
                SteamSpacePlayingHeader(friends, false, false, false, false, { opened = it.steamId }, behavior)
            })
        } }
        val tag = "steam-space-playing-target-"
        val expanded = rule.onNodeWithTag(tag + friends[0].steamId).fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { behavior.nestedScrollConnection.onPreScroll(Offset(0f, -10000f), NestedScrollSource.UserInput) }
        val first = rule.onNodeWithTag(tag + friends[0].steamId).fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag(tag + friends[1].steamId).fetchSemanticsNode().boundsInRoot
        assertTrue("Slots shrink with avatars", first.width < expanded.width * .65f)
        val density = first.width / 48f
        assertEquals("Four dp between compact targets", 4f * density, second.left - first.right, 2f)
        rule.onNodeWithTag(tag + friends[1].steamId).performClick()
        rule.runOnIdle { assertEquals(friends[1].steamId, opened) }
        save("space-compact")
    }

    @Test fun pointsActionsOpenNativeRewardAndCacheIsScopedByGame() {
        val reward = SteamPointsShopItem(620, 123, 1, 4, 100, "Portal reward")
        var selected: SteamPointsShopItem? = null
        var opens = 0
        var external = 0
        rule.setContent { Theme {
            Column { SteamGameStatsCollectibles(SteamGameStatsExtras(
                points = SteamPointsShopPage(SteamPointsShopCategory.FEATURED, listOf(reward))),
                620, "", { external++ }, { opens++ }, { selected = it }) }
        } }
        rule.onNodeWithText("Portal reward").performClick()
        rule.runOnIdle { assertEquals(reward, selected); assertEquals(0, external) }
        rule.onAllNodesWithText(rule.activity.getString(R.string.steam_game_stats_view_all)).onLast().performClick()
        rule.runOnIdle { assertEquals(1, opens); assertEquals(0, external) }
        val cache = SteamPointsShopCache(rule.activity)
        val page = SteamPointsShopPage(SteamPointsShopCategory.FEATURED, listOf(reward))
        cache.write(page, 620)
        assertEquals(listOf(reward), cache.read(SteamPointsShopCategory.FEATURED, 620)!!.items)
        assertNull(cache.read(SteamPointsShopCategory.FEATURED, 621))
    }

    @Test fun personalStatsRoutesThroughNativeGameShopAndReturnsToSameStats() {
        val reward = SteamPointsShopItem(620, 123, 1, 4, 100, "Portal reward")
        val account = takagi.ru.monica.steam.data.SteamAccount(1, "76561198000000001", "player", "Test player",
            "android:test", "test", null, null, null, null, null, null, "{}", true, 0, 0, 0)
        val gateway = object : SteamGameStatsGateway {
            override suspend fun extraDetails(account: takagi.ru.monica.steam.data.SteamAccount, appId: Int,
                publish: (SteamGameStatsExtras) -> Unit) {
                publish(SteamGameStatsExtras(points = SteamPointsShopPage(SteamPointsShopCategory.FEATURED, listOf(reward))))
            }
            override suspend fun cached(accountId: Long, appId: Int) = null to null
            override suspend fun playerGame(account: takagi.ru.monica.steam.data.SteamAccount, appId: Int) =
                takagi.ru.monica.steam.library.SteamLibraryResult.Success(takagi.ru.monica.steam.library.SteamGame(appId, "Portal 2", 60, 0))
            override suspend fun achievements(account: takagi.ru.monica.steam.data.SteamAccount,
                game: takagi.ru.monica.steam.library.SteamGame) =
                takagi.ru.monica.steam.library.SteamLibraryResult.Success(takagi.ru.monica.steam.library.SteamGameAchievements(1, 620, "Portal 2", emptyList(), 1))
        }
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { requests.incrementAndGet(); throw java.io.IOException("Synthetic unavailable shop") }.build()
        val model = SteamPointsShopViewModel(SteamPointsShopCache(rule.activity), SteamPointsShopService(client), appId = 620)
        var external = 0
        rule.setContent { Theme {
            SteamGameDetailTabs(620, "Portal 2", true, account, takagi.ru.monica.steam.data.SteamStorageSource.Local,
                {}, {}, { external++ }, null, statsGateway = gateway, workshopSupport = { false },
                pointsShopContent = { id, item, close ->
                    assertEquals(620, id)
                    SteamPointsShopScreen(account, close, { external++ }, appId = id, initialItem = item, viewModel = model)
                }) { _, _ -> Text("Store fixture") }
        } }
        rule.onNodeWithTag("game_personal_list").performScrollToNode(hasText("Portal reward"))
        rule.onAllNodesWithText(rule.activity.getString(R.string.steam_game_stats_view_all)).onLast().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_points_shop_title)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, external) }
        save("stats-native-points")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("Portal reward").assertIsDisplayed().performClick()
        rule.onNodeWithText("Portal reward").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.steam_points_shop_open_reward)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, external) }
        save("stats-native-reward")
        androidx.test.espresso.Espresso.pressBack()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("game_personal_list").assertExists()
        assertTrue(requests.get() > 0)
    }

    @Test fun activityShowsIndependentAuthorAndAchievementGameName() {
        val event = takagi.ru.monica.steam.space.domain.SteamSpaceEvent("fixture", takagi.ru.monica.steam.space.domain.SteamSpaceEventKind.ACHIEVEMENT,
            "76561198000000801", 1800000000, appIds = listOf(620), sourceUrl = "https://steamcommunity.com/profiles/76561198000000801/",
            actorName = "Feed author", appNames = mapOf(620 to "Portal 2"),
            achievements = listOf(takagi.ru.monica.steam.space.domain.SteamSpaceAchievement("COOP", "Cooperative test")))
        rule.setContent { Theme {
            SteamSpaceScreen(takagi.ru.monica.steam.space.presentation.SteamSpaceUiState(items = listOf(event), loaded = true),
                takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState(), {}, {}, {}, {}, {}, {}, {})
        } }
        rule.onNodeWithText("Feed author").assertIsDisplayed()
        rule.onNodeWithText("Portal 2").assertIsDisplayed()
        rule.onNodeWithText("Cooperative test").assertIsDisplayed()
        save("space-author-game")
    }
}
