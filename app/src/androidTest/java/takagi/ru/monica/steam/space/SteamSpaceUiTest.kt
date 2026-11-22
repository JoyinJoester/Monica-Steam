package takagi.ru.monica.steam.space

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamSpaceFixtureActivity
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile
import takagi.ru.monica.steam.friends.chat.ui.SteamInboxTopBar
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.friends.presentation.SteamFriendsFailureReason
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.space.domain.SteamSpaceCursor
import takagi.ru.monica.steam.space.domain.SteamSpaceAchievement
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.domain.SteamSpaceMedia
import takagi.ru.monica.steam.space.presentation.SteamSpaceFailureReason
import takagi.ru.monica.steam.space.presentation.SteamSpaceUiState
import takagi.ru.monica.steam.space.ui.SteamSpaceScreen
import takagi.ru.monica.steam.space.ui.SteamSpaceHeaderCollapsedFraction
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

/** Real native gestures and window frames; no Compose test clock, services, account reads or sends. */
@RunWith(AndroidJUnit4::class)
class SteamSpaceUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: SteamSpaceFixtureActivity? = null
    private var state by mutableStateOf(SteamSpaceUiState())
    private var friends by mutableStateOf(SteamFriendsUiState())
    private var inbox by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var searchExpanded by mutableStateOf(false)
    private var showPublish by mutableStateOf(false)
    private val publishRequests = AtomicInteger()
    private var dark by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)
    private var reduceAnimations by mutableStateOf(false)
    private var hiddenKinds by mutableStateOf<Set<SteamSpaceEventKind>>(emptySet())
    private var contentSettingsSaving by mutableStateOf(false)
    private var contentSettingsFailed by mutableStateOf(false)
    private val filterChanges = AtomicInteger()
    private val showAllChanges = AtomicInteger()
    private val contentRetries = AtomicInteger()
    private val refreshes = AtomicInteger()
    private val pages = AtomicInteger()
    private val backs = AtomicInteger()
    private val entries = AtomicInteger()
    private val accounts = AtomicInteger()
    private val game = AtomicInteger()
    private val message = AtomicReference<String?>(null)
    private val original = AtomicReference<String?>(null)
    private val surfaceColor = AtomicInteger()
    private var seededImage: File? = null
    private var seededAchievementImage: File? = null
    private val accountSteamId = "76561198000000901"
    private val peerSteamId = "76561198000000800"
    private val offlineImageUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/620/monica-space-offline-fixture-20261107.png"
    private val offlineAchievementUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/620/monica-achievement-fixture-20261107.png"
    private val firstBody = "合成测试：合作关卡完成，下一站继续冒险。"
    private val collapseFrameRecords = mutableListOf<String>()

    private fun event(id: String = "fixture-1", body: String = firstBody, media: List<SteamSpaceMedia> = emptyList()) =
        SteamSpaceEvent(id, SteamSpaceEventKind.SCREENSHOT, peerSteamId, 1_794_016_800,
            appIds = listOf(620), body = body, media = media,
            sourceUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=123456")

    private fun fixtureFriends(): SteamFriendsUiState = SteamFriendsUiState(
        accountId = 901,
        snapshot = SteamFriendsSnapshot(friends = listOf("Alex", "Blair", "Casey", "Devon", "Ellis", "Finley", "Gale", "Harper").mapIndexed { i, name ->
            SteamFriend("7656119800000080$i", SteamFriendRelationship.FRIEND, personaName = name,
                personaState = SteamPersonaState.ONLINE,
                gameId = if (i == 1) "9223372036854775807" else "620",
                gameName = if (i == 1) "自定义游戏" else "Portal 2")
        }, fetchedAt = System.currentTimeMillis())
    )

    private fun launch(showInbox: Boolean = false, image: Boolean = false) {
        if (image) seedOfflineImage()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, SteamSpaceFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SteamSpaceFixtureActivity
        main {
            state = SteamSpaceUiState(accountId = 901, accountSteamId = accountSteamId,
                items = listOf(event(media = if (image) listOf(SteamSpaceMedia(offlineImageUrl, 640, 360)) else emptyList())),
                nextCursor = SteamSpaceCursor(901, accountSteamId, 1_794_016_799), loaded = true)
            friends = fixtureFriends()
            inbox = showInbox
            activity!!.setContent {
                MonicaTheme(darkTheme = dark, colorScheme = takagi.ru.monica.data.ColorScheme.OCEAN_BLUE) {
                    val currentSurface = MaterialTheme.colorScheme.background.toArgb()
                    SideEffect { surfaceColor.set(currentSurface) }
                    CompositionLocalProvider(
                        LocalDensity provides Density(LocalDensity.current.density, fontScale),
                        LocalReduceAnimations provides reduceAnimations
                    ) {
                        Surface(Modifier.fillMaxSize().navigationBarsPadding()) {
                            if (inbox) Column {
                                SteamInboxTopBar(null, true, query, { query = it },
                                    searchExpanded = searchExpanded,
                                    onSearchExpandedChange = { searchExpanded = it },
                                    onShowAccounts = { accounts.incrementAndGet() },
                                    onOpenSpace = { entries.incrementAndGet(); inbox = false })
                                Text("离线会话夹具")
                            } else SteamSpaceScreen(state, friends,
                                onBack = { backs.incrementAndGet(); inbox = true },
                                onRefresh = { refreshes.incrementAndGet(); state = state.copy(refreshing = true) },
                                onLoadMore = { pages.incrementAndGet(); state = state.copy(loadingMore = true) },
                                onOpenOriginal = { original.set(it) },
                                onOpenGame = { game.set(it) }, onMessage = { message.set(it) },
                                onManageAccounts = { accounts.incrementAndGet() },
                                onPublish = if (showPublish) ({ publishRequests.incrementAndGet(); Unit }) else null,
                                hiddenKinds = hiddenKinds,
                                onSetKindVisible = { kind, visible ->
                                    filterChanges.incrementAndGet()
                                    hiddenKinds = if (visible) hiddenKinds - kind else hiddenKinds + kind
                                },
                                onShowAllKinds = {
                                    showAllChanges.incrementAndGet()
                                    hiddenKinds = emptySet()
                                    contentSettingsFailed = false
                                },
                                contentSettingsSaving = contentSettingsSaving,
                                contentSettingsFailed = contentSettingsFailed,
                                onRetryContentSettings = {
                                    contentRetries.incrementAndGet()
                                    contentSettingsFailed = false
                                })
                        }
                    }
                }
            }
        }
        await("fixture visible") { activity!!.hasWindowFocus() && textVisible(if (showInbox) "离线会话夹具" else label(R.string.steam_space_recent)) }
    }

    @After fun close() {
        main { activity?.finish() }
        seededImage?.delete()
        seededAchievementImage?.delete()
    }

    @Test fun chatTopEntryOpensSpaceAndKeepsAccountAndSearchActions() {
        launch(showInbox = true)
        tap(label(R.string.steam_switch_account), description = true)
        assertEquals(1, accounts.get())
        tap(label(R.string.topbar_search_hint), description = true)
        await("library-style search editor mounted") { main { mountedNodes().any { it.node.config.getOrNull(SemanticsActions.SetText) != null } } }
        // Drive the library-style editor's actual editing action and value callback.
        main {
            val editor = mountedNodes().first { it.node.config.getOrNull(SemanticsActions.SetText) != null }.node
            assertTrue(editor.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(AnnotatedString("Portal")) == true)
        }
        await("search query visible") { main { query == "Portal" } && textVisible("Portal") }
        tap(label(R.string.topbar_close_search), description = true)
        await("search cleared") { main { query.isEmpty() } && !textVisible("Portal") }
        save("inbox-entry-light")
        assertFalse("Space has one menu entry instead of a standalone button", textVisible(label(R.string.steam_space_title)))
        tap(label(R.string.steam_chat_options), description = true)
        tap(label(R.string.steam_space_title))
        await("space opened") { entries.get() == 1 && textVisible(firstBody) }
        back()
        await("back returns to inbox") { backs.get() == 1 && textVisible("离线会话夹具") }
    }

    @Test fun playingStripScrollsAndSheetUsesExactGameAndMessageTargets() {
        launch()
        tapTag("steam-space-playing-target-$peerSteamId")
        await("playing sheet") { textVisible(label(R.string.steam_space_view_game)) }
        tap(label(R.string.steam_space_view_game))
        await("valid app routed") { game.get() == 620 && !textVisible(label(R.string.steam_space_message)) }
        tapTag("steam-space-playing-target-76561198000000801")
        await("non Steam game sheet") { textVisible(label(R.string.steam_space_message)) }
        assertFalse("Large non-Steam game IDs must never become store app IDs", textVisible(label(R.string.steam_space_view_game)))
        tap(label(R.string.steam_space_message))
        await("exact friend routed") { message.get() == "76561198000000801" }
        val strip = requireNotNull(tagBounds("steam-space-playing-row"))
        repeat(5) {
            if (!textVisible("Harper")) swipe(.84f, .18f, strip.exactCenterY())
        }
        await("last friend reached by horizontal gesture") { textVisible("Harper") }
        tapTag("steam-space-playing-target-76561198000000807")
        await("last friend sheet") { textVisible(label(R.string.steam_space_message)) }
        tap(label(R.string.steam_space_message))
        await("horizontal list retained friend identity") { message.get() == "76561198000000807" }
    }

    @Test fun backDismissesSheetFirstAndAccountSwitchRevokesIt() {
        launch()
        tap("Alex")
        await("sheet opened") { textVisible(label(R.string.steam_space_message)) }
        back()
        await("sheet dismissed before page") { !textVisible(label(R.string.steam_space_message)) }
        assertEquals(0, backs.get())
        tap("Alex")
        await("sheet reopened") { textVisible(label(R.string.steam_space_message)) }
        main {
            state = SteamSpaceUiState(accountId = 902, accountSteamId = "76561198000000902", loaded = true)
            friends = SteamFriendsUiState(accountId = 902, snapshot = SteamFriendsSnapshot())
        }
        await("old account sheet revoked") { !textVisible(label(R.string.steam_space_message)) && textVisible(label(R.string.steam_space_empty)) }
        assertNull(message.get())
        assertEquals(0, game.get())
        back()
        await("page back") { backs.get() == 1 }
    }

    @Test fun refreshAndPaginationFailuresKeepExistingActivityAndAllowRetry() {
        launch()
        tap(label(R.string.steam_space_refresh), description = true)
        await("refresh invoked") { refreshes.get() == 1 }
        main { state = state.copy(refreshing = false, stale = true, failure = SteamSpaceFailureReason.NETWORK) }
        await("refresh failure is visible") { textVisible(label(R.string.steam_space_unavailable)) }
        assertTrue(textVisible(firstBody))
        assertTrue(textVisible(label(R.string.steam_space_previous)))
        main { state = state.copy(failure = null) }
        scrollTo(label(R.string.steam_space_load_more))
        tap(label(R.string.steam_space_load_more))
        await("next page invoked") { pages.get() == 1 }
        main { state = state.copy(loadingMore = false, loadMoreFailure = SteamSpaceFailureReason.NETWORK) }
        scrollTo(label(R.string.steam_space_load_more_failed))
        assertEquals("Previously loaded event remains in supplied UI state", "fixture-1", main { state.items.single().id })
        scrollTo(label(R.string.steam_space_retry))
        save("pagination-failure-light")
        tap(label(R.string.steam_space_retry))
        await("page retry invoked") { pages.get() == 2 }
        main { state = state.copy(loadingMore = false, loadMoreFailure = null, nextCursor = null,
            items = state.items + event("fixture-2", "合成测试：第二条动态")) }
        scrollTo("合成测试：第二条动态")
        assertTrue(textVisible("合成测试：第二条动态"))
        scrollTo(label(R.string.steam_space_end))
        assertTrue(textVisible(label(R.string.steam_space_end)))
    }

    @Test fun updatedPresenceRevokesSheetWhenFriendStopsPlayingOrDisappears() {
        launch()
        for (removeFriend in listOf(false, true)) {
            main { inbox = false; friends = fixtureFriends() }
            await("playing friend available") { textVisible("Alex") }
            tapTag("steam-space-playing-target-$peerSteamId")
            await("playing sheet open") { textVisible(label(R.string.steam_space_message)) }
            main {
                val updated = friends.snapshot!!.friends.mapNotNull { friend ->
                    when {
                        friend.steamId != peerSteamId -> friend
                        removeFriend -> null
                        else -> friend.copy(gameId = "", gameName = "")
                    }
                }
                friends = friends.copy(snapshot = SteamFriendsSnapshot(updated, System.currentTimeMillis()))
            }
            await("presence update closes the old sheet") {
                !textVisible(label(R.string.steam_space_message)) && !textVisible(label(R.string.steam_space_view_game))
            }
            val before = backs.get()
            back()
            await("first Back leaves the page after sheet revocation") {
                backs.get() == before + 1 && textVisible("离线会话夹具")
            }
        }
        assertEquals(2, backs.get())
        assertNull(message.get())
        assertEquals(0, game.get())

        // Once the last playing friend leaves, the whole section relinquishes its height.
        main {
            inbox = false
            friends = fixtureFriends().let { current -> current.copy(snapshot = current.snapshot!!.copy(
                friends = current.snapshot!!.friends.map { it.copy(gameId = "", gameName = "") }
            )) }
        }
        await("no playing header or placeholder remains") {
            tagBounds("steam-space-playing-header") == null &&
                !textVisible(label(R.string.steam_space_no_playing)) && textVisible(firstBody)
        }
        fun assertFeedStartsWithRecentActivity() {
            val header = requireNotNull(tagBounds("steam-space-top-bar"))
            val heading = requireNotNull(bounds(label(R.string.steam_space_recent)))
            assertTrue("No empty playing section before recent activity", heading.top - header.bottom in 0..dp(40))
        }
        assertFeedStartsWithRecentActivity()
        awaitTopBarSurface("no-playing-light")
        save("no-playing-light-normal")
        val normalTitleHeight = requireNotNull(bounds(label(R.string.steam_space_title))).height()
        main { dark = true; fontScale = 1.5f }
        await("no-playing preview uses the larger theme before sampling its color") {
            (bounds(label(R.string.steam_space_title))?.height() ?: 0) > normalTitleHeight
        }
        awaitTopBarSurface("no-playing-dark-large")
        assertFeedStartsWithRecentActivity()
        assertNull(tagBounds("steam-space-playing-header"))
        save("no-playing-dark-large")
    }

    @Test fun publishingRequiresExplicitClickAndKeepsTheCurrentFeedInBothSizes() {
        main { showPublish = true }
        launch()
        Thread.sleep(350) // Let the Activity surface finish its window transition before the normal-size capture.
        val normalTitleHeight = requireNotNull(bounds(label(R.string.steam_space_title))).height()
        for (large in listOf(false, true)) {
            main { fontScale = if (large) 1.5f else 1f }
            if (large) await("Large publishing entry is laid out before capture") {
                (bounds(label(R.string.steam_space_title))?.height() ?: 0) > normalTitleHeight
            }
            await("Publish FAB is visible and labelled") {
                bounds(label(R.string.steam_space_publish), description = true, clickable = true) != null
            }
            val before = if (large) 1 else 0
            assertEquals("Viewing the feed must not publish", before, publishRequests.get())
            save("publish-entry-${if (large) "large" else "normal"}")
            tap(label(R.string.steam_space_publish), description = true)
            await("Explicit click opens the publishing route") { publishRequests.get() == before + 1 }
            assertEquals("fixture-1", main { state.items.single().id })
            assertEquals(0, refreshes.get())
            assertNull("The entry must not send an external activity link", original.get())
        }
    }

    @Test fun expiredPaginationSessionOffersAccountLoginInsteadOfPageRetry() {
        launch()
        main { state = state.copy(loadMoreFailure = SteamSpaceFailureReason.SESSION_REQUIRED) }
        scrollTo(label(R.string.steam_space_session_required))
        assertTrue(textVisible(label(R.string.steam_space_session_required)))
        scrollTo(label(R.string.steam_add_method_login))
        tap(label(R.string.steam_add_method_login))
        await("pagination login opens account management") { accounts.get() == 1 }
        assertEquals("Expired pagination must not repeat the same request", 0, pages.get())
        assertEquals("Existing activity is retained", "fixture-1", main { state.items.single().id })
    }

    @Test fun partialFeedWithoutCursorNeverClaimsAllActivityWasShown() {
        launch()
        main { state = state.copy(partial = true, nextCursor = null, loaded = true) }
        scrollTo(label(R.string.steam_space_partial))
        assertTrue(textVisible(label(R.string.steam_space_partial)))
        scrollTo(label(R.string.steam_space_open_feed))
        assertFalse("A partial page is not the end of all available activity", textVisible(label(R.string.steam_space_end)))
        assertEquals("Partial response retains the readable item", "fixture-1", main { state.items.single().id })
    }

    @Test fun loginEmptyAndNetworkStatesRemainDistinct() {
        launch()
        main { friends = SteamFriendsUiState(snapshot = SteamFriendsSnapshot()); state = state.copy(items = emptyList(), nextCursor = null) }
        await("empty state") { textVisible(label(R.string.steam_space_empty)) }
        assertFalse(textVisible(label(R.string.steam_space_no_playing)))
        assertNull(tagBounds("steam-space-playing-header"))
        val emptyFeedHeadingTop = requireNotNull(bounds(label(R.string.steam_space_recent))).top
        for (presence in listOf(
            SteamFriendsUiState(loading = true),
            SteamFriendsUiState(refreshing = true),
            SteamFriendsUiState(failure = SteamFriendsFailureReason.NETWORK)
        )) {
            main { friends = presence }
            instrumentation.waitForIdleSync()
            assertFalse(textVisible(label(R.string.steam_space_presence_loading)))
            assertFalse(textVisible(label(R.string.steam_space_presence_unavailable)))
            assertNull(tagBounds("steam-space-playing-header"))
            assertEquals("No empty presence row during loading or failure", emptyFeedHeadingTop,
                requireNotNull(bounds(label(R.string.steam_space_recent))).top)
        }
        for ((failure, resource) in listOf(
            SteamSpaceFailureReason.ACCOUNT_REQUIRED to R.string.steam_space_account_required,
            SteamSpaceFailureReason.SESSION_REQUIRED to R.string.steam_space_session_required,
            SteamSpaceFailureReason.NETWORK to R.string.steam_space_unavailable
        )) {
            main { state = state.copy(failure = failure) }
            await("$failure message") { textVisible(label(resource)) }
            assertFalse("Failure must not masquerade as an empty feed", textVisible(label(R.string.steam_space_empty)))
        }
        save("network-unavailable-light")
    }

    @Test fun originalActivityAndSensitiveMediaUseReadOnlyActions() {
        launch()
        main { state = state.copy(items = listOf(event(media = listOf(SteamSpaceMedia(
            "https://example.invalid/must-not-load.png", sensitive = true)))), nextCursor = null) }
        scrollTo(label(R.string.steam_space_sensitive_media))
        assertFalse("Sensitive media stays covered", descriptionVisible(label(R.string.steam_space_media)))
        val card = requireNotNull(tagBounds("steam-space-event-fixture-1"))
        val down = android.os.SystemClock.uptimeMillis()
        inject(down, android.view.MotionEvent.ACTION_DOWN, card.centerX().toFloat(), card.top + dp(18).toFloat())
        Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 160)
        inject(down, android.view.MotionEvent.ACTION_UP, card.centerX().toFloat(), card.top + dp(18).toFloat())
        await("long press opens focused activity") { tagBounds("steam-card-preview") != null }
        save("space-long-press-preview")
        tap(label(R.string.steam_space_view_original))
        await("original link returned") { original.get() == "https://steamcommunity.com/sharedfiles/filedetails/?id=123456" }
        scrollTo(label(R.string.steam_space_open_feed))
        tap(label(R.string.steam_space_open_feed))
        await("full Steam feed link returned") { original.get() == "https://steamcommunity.com/my/home/" }
        assertNull(message.get())
        assertEquals(0, game.get())
    }

    @Test fun cachedPresenceIsMarkedAsLastKnownWhenRefreshFails() {
        launch()
        main { friends = friends.copy(fromCache = true, failure = SteamFriendsFailureReason.NETWORK) }
        await("stale status caption") { textVisible(label(R.string.steam_space_last_known)) }
        assertTrue(textVisible(label(R.string.steam_space_presence_unavailable)))
        assertFalse(textVisible(label(R.string.steam_space_no_playing)))
        tap("Alex")
        await("stale friend sheet") { textVisible(label(R.string.steam_space_message)) }
        assertTrue(textVisible(label(R.string.steam_space_last_known)))
        save("last-known-presence-light")
    }

    @Test fun lightNormalAndDarkLargeTextRenderActualFramesAndSheetControls() {
        launch(image = true)
        await("offline synthetic image decoded") { descriptionVisible(label(R.string.steam_space_media)) }
        // Match what is mounted, independently of the production formatter. The fixture context
        // is Chinese even when the device-wide default locale remains English.
        val chineseDate = Regex("^2026年11月\\d{1,2}日\\s+\\d{1,2}:\\d{2}$")
        await("event date follows the Chinese page locale") {
            main {
                mountedNodes().flatMap { it.node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                    .any { chineseDate.matches(it) && descriptionVisible(it) }
            }
        }
        awaitTopBarSurface("light-normal")
        save("space-light-normal")
        val normalTitleHeight = requireNotNull(bounds(label(R.string.steam_space_title))).height()
        main { dark = true; fontScale = 1.5f }
        await("large theme recomposed") {
            (bounds(label(R.string.steam_space_title))?.height() ?: 0) > normalTitleHeight
        }
        awaitTopBarSurface()
        save("space-dark-large")
        tap("Alex")
        await("large sheet content") { textVisible(label(R.string.steam_space_message)) }
        val button = requireNotNull(bounds(label(R.string.steam_space_message), clickable = true))
        val width = main { activity!!.resources.displayMetrics.widthPixels }
        assertTrue("Message action is within the display", button.left >= 0 && button.right <= width)
        save("playing-sheet-dark-large")
        tap(label(R.string.steam_space_message))
        await("large text action responds") { message.get() == peerSteamId }

        seedOfflineAchievementImage()
        main {
            dark = false; fontScale = 1f
            state = state.copy(items = listOf(event().copy(
                kind = SteamSpaceEventKind.ACHIEVEMENT, title = "Portal 2", body = null,
                achievements = listOf(
                    SteamSpaceAchievement("COOP", "默契搭档", "与好友一起完成合作关卡。", offlineAchievementUrl),
                    SteamSpaceAchievement("EXPLORE", "继续探索", "图标暂不可用，仍可查看成就详情。")
                ), sourceUrl = "https://steamcommunity.com/profiles/$peerSteamId/"
            )), nextCursor = null)
        }
        await("achievement preview replaces the sheet") { textVisible("默契搭档") && !textVisible(label(R.string.steam_space_message)) }
        awaitTopBarSurface("achievement-light")
        save("achievement-icons-light-normal")
        val achievementTitleHeight = requireNotNull(bounds(label(R.string.steam_space_title))).height()
        main { dark = true; fontScale = 1.5f }
        await("achievement preview uses the larger theme before sampling its color") {
            (bounds(label(R.string.steam_space_title))?.height() ?: 0) > achievementTitleHeight
        }
        awaitTopBarSurface("achievement-dark-large")
        save("achievement-icons-dark-large")
        tap(label(R.string.steam_space_event_details), description = true)
        await("Achievement actions open in the card preview") { textVisible(label(R.string.steam_space_view_original)) }
        save("achievement-icons-dark-large-scrolled")
    }

    @Test fun headerDragShrinksContinuouslyThenKeepsOnlyClickableAvatarsAtTheTop() {
        launch(image = true)
        main { state = state.copy(items = state.items + (2..10).map { event("scroll-$it", "离线动态 $it") }) }
        awaitTopBarSurface("expanded-initial")
        val expanded = headerSample()
        captureCollapseFrame(0, "expanded")
        val drag = beginHeaderDrag(upward = true)
        val samples = mutableListOf(expanded)
        try {
            for ((index, fraction) in listOf(.15f, .35f, .55f, .75f, 1f).withIndex()) {
                moveHeaderDrag(drag, fraction)
                samples += headerSample()
                captureCollapseFrame(index + 1, "drag-up-$fraction")
            }
        } finally { endHeaderDrag(drag) }
        await("header fully compact") { headerFraction() >= .999f }
        val compact = headerSample()
        assertTrue("Drag produces several actual intermediate heights", samples.map { it.header.height() }.distinct().size >= 5)
        samples.zipWithNext().forEach { (before, after) ->
            assertTrue("Each further upward move decreases the visible header height", after.header.height() < before.header.height())
            assertTrue("Header top remains fixed while shrinking", kotlin.math.abs(after.header.top - expanded.header.top) <= 1)
        }
        // Semantics reports clipped layout bounds, not graphicsLayer's painted circle diameter.
        // The actual shrinking circles are preserved in the native screenshot sequence.
        assertTrue("Avatar layout is clipped inside the compact strip", compact.avatar.height() < expanded.avatar.height() - dp(12))
        assertTrue("Collapsed avatar remains a minimum 48 dp touch target", compact.target.height() >= dp(48) - 1)
        assertFalse("Heading text is hidden in the compact strip", headerHasText(label(R.string.steam_space_playing)))
        assertFalse("Game labels are hidden in the compact strip", headerHasText("Portal 2"))
        assertFalse("Friend names are hidden in the compact strip", headerHasText("Alex"))
        val belowHeaderDown = SystemClock.uptimeMillis()
        val belowHeaderY = compact.header.bottom + dp(16).toFloat()
        inject(belowHeaderDown, MotionEvent.ACTION_DOWN, compact.target.exactCenterX(), belowHeaderY)
        inject(belowHeaderDown, MotionEvent.ACTION_UP, compact.target.exactCenterX(), belowHeaderY)
        Thread.sleep(180)
        assertFalse("Clipped name/game area cannot intercept a feed touch", textVisible(label(R.string.steam_space_message)))
        val feed = requireNotNull(tagBounds("steam-space-feed"))
        gesture(feed.exactCenterX(), feed.bottom - dp(36).toFloat(), feed.exactCenterX(), feed.top + dp(36).toFloat())
        val whileScrolling = headerSample()
        assertEquals("Header stays pinned as the feed moves", compact.header, whileScrolling.header)
        assertFalse("The feed actually moved away from its top", textVisible(label(R.string.steam_space_recent)))
        captureCollapseFrame(6, "compact-feed-scrolled")
        tapTag("steam-space-playing-target-$peerSteamId")
        await("compact avatar opens the exact friend") { textVisible(label(R.string.steam_space_message)) }
        tap(label(R.string.steam_space_message))
        await("compact avatar message target") { message.get() == peerSteamId }
        returnFeedToTop()
        val reverse = beginHeaderDrag(upward = false)
        try {
            moveHeaderDrag(reverse, .5f)
            captureCollapseFrame(7, "drag-down-half-expanded")
            moveHeaderDrag(reverse, 0f)
            captureCollapseFrame(8, "drag-down-expanded")
        } finally { endHeaderDrag(reverse) }
        await("header expanded at feed top") { headerFraction() <= .001f }
        assertEquals("Downward drag restores the natural header height", expanded.header.height(), headerSample().header.height())
        assertTrue(headerHasText("Portal 2"))
    }

    @Test fun partialHeaderReleaseSettlesUsingRealFramesAndCanExpandAgain() {
        launch()
        val drag = beginHeaderDrag(upward = true)
        try {
            moveHeaderDrag(drag, .62f)
            // Keep the pointer still so this exercises the partial-position settle, not fling velocity.
            Thread.sleep(160)
        } finally { endHeaderDrag(drag) }
        val started = SystemClock.uptimeMillis()
        val observed = mutableListOf<Pair<Long, HeaderSample>>()
        while (SystemClock.uptimeMillis() - started < 1_500) {
            observed += SystemClock.uptimeMillis() to headerSample()
            if (observed.last().second.fraction >= .999f) break
            Thread.sleep(16)
        }
        assertTrue("Release reaches compact state", observed.last().second.fraction >= .999f)
        assertTrue("Normal motion supplies an intermediate release frame", observed.any { it.second.fraction in 0.64f..0.98f })
        assertTrue("The 220 ms settle must not remain stuck", observed.last().first - started < 1_200)
        writeCollapseTrace("collapse-settle-normal.txt", observed.map { (time, sample) -> sample.describe(time) })
        returnFeedToTop()
        val reverse = beginHeaderDrag(upward = false)
        try { moveHeaderDrag(reverse, .25f); Thread.sleep(160) } finally { endHeaderDrag(reverse) }
        await("partial downward release expands fully") { headerFraction() <= .001f }
        assertTrue(headerHasText(label(R.string.steam_space_playing)))
    }

    @Test fun largeTextAndReducedMotionKeepContinuousDragAndAccessibleCompactTargets() {
        launch()
        val normalHeight = headerSample().header.height()
        main { fontScale = 1.5f; reduceAnimations = true }
        await("header naturally accommodates larger text") { headerSample().header.height() > normalHeight }
        val largeHeight = headerSample().header.height()
        val drag = beginHeaderDrag(upward = true)
        try {
            moveHeaderDrag(drag, .3f)
            val first = headerSample()
            moveHeaderDrag(drag, .65f)
            val second = headerSample()
            assertTrue("Reduced motion still follows the finger continuously", second.header.height() < first.header.height())
            assertTrue(first.header.height() < largeHeight)
            assertTrue("The drag has not jumped directly to compact", second.fraction in 0.5f..0.85f)
            Thread.sleep(160)
        } finally { endHeaderDrag(drag) }
        await("reduced motion release reaches compact") { headerFraction() >= .999f }
        val compact = headerSample()
        assertTrue(compact.target.height() >= dp(48) - 1)
        save("collapse-large-reduced-compact")
        tapTag("steam-space-playing-target-$peerSteamId")
        await("large compact avatar responds") { textVisible(label(R.string.steam_space_message)) }
        back()
        await("sheet closes") { !textVisible(label(R.string.steam_space_message)) }
        returnFeedToTop()
        val reverse = beginHeaderDrag(upward = false)
        try { moveHeaderDrag(reverse, .2f); Thread.sleep(160) } finally { endHeaderDrag(reverse) }
        await("reduced motion expansion completes") { headerFraction() <= .001f }
        assertEquals(largeHeight, headerSample().header.height())
        save("collapse-large-reduced-expanded")
    }

    @Test fun contentFilters_hideOnlyAchievementsAndRestoreAll() {
        launch()
        val mixed = listOf(
            event("achievement", "成就测试内容").copy(kind = SteamSpaceEventKind.ACHIEVEMENT),
            event("game", "新增游戏测试内容").copy(kind = SteamSpaceEventKind.GAME_ADDED,
                appNames = mapOf(620 to "Portal 2")),
            event("status", "朋友主动发布的测试动态").copy(kind = SteamSpaceEventKind.STATUS)
        )
        main {
            friends = friends.copy(snapshot = friends.snapshot!!.copy(friends = emptyList()))
            state = state.copy(items = mixed)
        }
        await("achievement initially shown") { textVisible("成就测试内容") }
        tap(label(R.string.steam_space_content_settings), description = true)
        await("content switches shown") { textVisible(label(R.string.steam_space_content_achievements)) }
        main { contentSettingsSaving = true }
        await("saving disables the achievement switch") {
            main { mountedNodes().any {
                it.node.config.getOrNull(SemanticsProperties.TestTag) == "steam-space-kind-ACHIEVEMENT" &&
                    it.node.config.contains(SemanticsProperties.Disabled)
            } }
        }
        tapTag("steam-space-kind-ACHIEVEMENT")
        assertEquals("A disabled switch must not request another save", 0, filterChanges.get())
        main { contentSettingsSaving = false }
        await("save finished") {
            main { mountedNodes().any {
                it.node.config.getOrNull(SemanticsProperties.TestTag) == "steam-space-kind-ACHIEVEMENT" &&
                    !it.node.config.contains(SemanticsProperties.Disabled)
            } }
        }
        tap(label(R.string.steam_space_content_achievements))
        await("only achievement category hidden") { main { hiddenKinds == setOf(SteamSpaceEventKind.ACHIEVEMENT) } }
        save("content-filters-light-normal")
        back()
        await("game remains after dismissing settings") {
            !textVisible(label(R.string.steam_space_content_achievements)) && textVisible("新增游戏测试内容")
        }
        assertFalse(textVisible("成就测试内容"))
        scrollTo("朋友主动发布的测试动态")
        assertEquals("Filtering keeps every original cached event", mixed, main { state.items })
        tap(label(R.string.steam_space_content_settings), description = true)
        tap(label(R.string.steam_space_content_show_all))
        await("all categories restored") { main { hiddenKinds.isEmpty() } }
        back()
        // Off-screen sheet semantics precede removal of its dialog window. Wait until the
        // Activity owns input again before issuing a second, page-level Back event.
        await("settings dismissed and page owns input") {
            main { activity!!.hasWindowFocus() } && !textVisible(label(R.string.steam_space_content_achievements))
        }
        back()
        await("returned to inbox") { textVisible("离线会话夹具") }
        tap(label(R.string.steam_chat_options), description = true)
        tap(label(R.string.steam_space_title))
        await("space route reopened with its feed") { main { !inbox } && tagBounds("steam-space-feed") != null }
        main {
            val list = mountedNodes().first { it.node.config.getOrNull(SemanticsProperties.TestTag) == "steam-space-feed" }.node
            assertTrue(list.config.getOrNull(SemanticsActions.ScrollToIndex)?.action?.invoke(0) == true)
        }
        await("restored achievement survives reopening the page") { textVisible("成就测试内容") }
        assertEquals(1, showAllChanges.get())
        assertEquals(mixed, main { state.items })
    }

    @Test fun contentFilters_allHiddenRetainPaginationAndLargeFontControls() {
        launch()
        main {
            friends = friends.copy(snapshot = friends.snapshot!!.copy(friends = emptyList()))
            hiddenKinds = SteamSpaceEventKind.entries.toSet()
            state = state.copy(items = listOf(event("other", "其他类型测试动态").copy(kind = SteamSpaceEventKind.UNKNOWN)))
        }
        await("filtered empty notice shown") { textVisible(label(R.string.steam_space_content_no_match)) }
        assertFalse("Filtering is distinct from an actually empty feed", textVisible(label(R.string.steam_space_empty)))
        tap(label(R.string.steam_space_load_more))
        await("all hidden still allows the next page") { pages.get() == 1 }
        val normalTitleHeight = requireNotNull(bounds(label(R.string.steam_space_title))).height()
        main { state = state.copy(loadingMore = false); dark = true; fontScale = 1.5f }
        await("large font applied") { (bounds(label(R.string.steam_space_title))?.height() ?: 0) > normalTitleHeight }
        tap(label(R.string.steam_space_content_settings), description = true)
        await("large settings open") { textVisible(label(R.string.steam_space_content_achievements)) }
        save("content-filters-dark-large-top")
        scrollTo(label(R.string.steam_space_content_other))
        // scrollTo may reveal only the label at the viewport edge. Finish the real scroll so
        // the last row's complete touch target is visible before measuring or tapping it.
        val contentList = requireNotNull(tagBounds("steam-space-content-kinds"))
        gesture(contentList.exactCenterX(), contentList.bottom - dp(24).toFloat(),
            contentList.exactCenterX(), contentList.top + dp(24).toFloat())
        val lastControl = requireNotNull(bounds(label(R.string.steam_space_content_other), clickable = true))
        val metrics = main { activity!!.resources.displayMetrics }
        assertTrue(lastControl.left >= 0 && lastControl.right <= metrics.widthPixels)
        assertTrue(lastControl.height() >= dp(48))
        save("content-filters-dark-large-bottom")
        tap(label(R.string.steam_space_content_other))
        await("last switch reachable with large text") { main { SteamSpaceEventKind.UNKNOWN !in hiddenKinds } }
        back()
        await("other activity revealed") { textVisible("其他类型测试动态") }
        assertFalse(textVisible(label(R.string.steam_space_content_no_match)))
        val retainedChoices = main { hiddenKinds }
        assertTrue(retainedChoices.isNotEmpty())
        main { contentSettingsFailed = true }
        tap(label(R.string.steam_space_content_settings), description = true)
        await("settings read or save error shown") { textVisible(label(R.string.steam_space_content_settings_failed)) }
        tap(label(R.string.steam_space_retry))
        await("retry saves the current choices without restoring hidden categories") {
            contentRetries.get() == 1 && main { !contentSettingsFailed && hiddenKinds == retainedChoices }
        }
        main { contentSettingsFailed = true; hiddenKinds = emptySet() }
        tap(label(R.string.steam_space_content_show_all))
        await("show all retries after failure even when all were already visible") {
            showAllChanges.get() == 1 && main { !contentSettingsFailed }
        }
    }

    @Test fun gameActivityPreservesNamesAndMissingAppIds() {
        launch()
        main { friends = friends.copy(snapshot = friends.snapshot!!.copy(friends = emptyList())) }
        val missingName = main { activity!!.getString(R.string.steam_space_app_id, 999998) }
        for (kind in listOf(SteamSpaceEventKind.GAME_ADDED, SteamSpaceEventKind.WISHLISTED,
            SteamSpaceEventKind.FIRST_PLAY, SteamSpaceEventKind.REVIEW)) {
            main { state = state.copy(items = listOf(event("game-mapping").copy(kind = kind,
                appIds = listOf(620, 999998), appNames = mapOf(620 to "Portal 2"),
                title = "旧缓存合并标题")), nextCursor = null) }
            await("$kind keeps resolved and missing games") { textVisible("Portal 2") && textVisible(missingName) }
            assertFalse("A legacy combined title must not replace the complete mapping", textVisible("旧缓存合并标题"))
        }
        save("game-activity-mixed-names-light")
        main { state = state.copy(items = listOf(state.items.single().copy(appNames = emptyMap()))) }
        val knownIdFallback = main { activity!!.getString(R.string.steam_space_app_id, 620) }
        await("legacy cache retains its title and every game ID") {
            textVisible("旧缓存合并标题") && textVisible(knownIdFallback) && textVisible(missingName)
        }
    }

    private fun label(id: Int): String = main { activity!!.getString(id) }

    private fun <T> main(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun await(reason: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) Thread.sleep(20)
        assertTrue("Timed out: $reason", condition())
    }

    private data class MountedNode(val node: SemanticsNode, val root: View)

    private fun mountedNodes(): List<MountedNode> = buildList {
        fun semantics(node: SemanticsNode, root: View) {
            val bounds = node.boundsInWindow
            if (bounds.width <= 0 || bounds.height <= 0) return
            add(MountedNode(node, root))
            // Unmerged children can still contain text removed by clearAndSetSemantics.
            // Respect the real semantics boundary instead of treating hidden descendants as visible.
            if (!node.config.isClearingSemantics) node.children.forEach { semantics(it, root) }
        }
        fun views(view: View, root: View) {
            if (view is ViewRootForTest) semantics(view.semanticsOwner.unmergedRootSemanticsNode, root)
            if (view is ViewGroup) repeat(view.childCount) { views(view.getChildAt(it), root) }
        }
        WindowInspector.getGlobalWindowViews().asReversed().filter { it.isShown }.forEach { views(it, it) }
    }

    private fun bounds(label: String, description: Boolean = false, clickable: Boolean = false): Rect? = main {
        mountedNodes().firstNotNullOfOrNull { mounted ->
            var node: SemanticsNode? = mounted.node
            val matches = if (description) node!!.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
                else node!!.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
                    node.config.getOrNull(SemanticsProperties.EditableText)?.text == label
            if (!matches) return@firstNotNullOfOrNull null
            if (clickable) while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
            node ?: return@firstNotNullOfOrNull null
            val box = node.boundsInWindow
            val screen = IntArray(2); val window = IntArray(2)
            mounted.root.getLocationOnScreen(screen); mounted.root.getLocationInWindow(window)
            val rect = Rect((box.left + screen[0] - window[0]).toInt(), (box.top + screen[1] - window[1]).toInt(),
                (box.right + screen[0] - window[0]).toInt(), (box.bottom + screen[1] - window[1]).toInt())
            // Compose menus use an independently positioned popup window. Convert its
            // visible root rectangle to display coordinates before intersecting the target.
            val visible = Rect()
            val shown = mounted.root.getLocalVisibleRect(visible)
            visible.offset(screen[0], screen[1])
            if (!shown || !rect.intersect(visible) || rect.isEmpty) null else rect
        } ?: nativeViews().firstNotNullOfOrNull { view ->
            val matches = if (description) view.contentDescription?.toString() == label
                else (view as? android.widget.TextView)?.text?.toString() == label ||
                    (view as? android.widget.EditText)?.hint?.toString() == label
            if (!matches || !view.isShown) return@firstNotNullOfOrNull null
            var target = view
            if (clickable) {
                while (!target.isClickable && target.parent is View) target = target.parent as View
                if (!target.isClickable) return@firstNotNullOfOrNull null
            }
            Rect().takeIf { target.getGlobalVisibleRect(it) && !it.isEmpty }?.apply {
                // Native popup windows have their own origin. GlobalVisibleRect is relative
                // to that root, while injected input uses physical display coordinates.
                val origin = IntArray(2)
                target.rootView.getLocationOnScreen(origin)
                offset(origin[0], origin[1])
            }
        }
    }

    private fun nativeViews(): List<View> = buildList {
        fun visit(view: View) {
            add(view)
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        WindowInspector.getGlobalWindowViews().asReversed().filter { it.isShown }.forEach(::visit)
    }

    private fun textVisible(text: String) = bounds(text) != null
    private fun descriptionVisible(text: String) = bounds(text, description = true) != null

    private fun tagBounds(tag: String): Rect? = main {
        mountedNodes().firstOrNull { it.node.config.getOrNull(SemanticsProperties.TestTag) == tag }?.let { mounted ->
            val box = mounted.node.boundsInWindow
            val screen = IntArray(2); val window = IntArray(2)
            mounted.root.getLocationOnScreen(screen); mounted.root.getLocationInWindow(window)
            val rect = Rect((box.left + screen[0] - window[0]).toInt(), (box.top + screen[1] - window[1]).toInt(),
                (box.right + screen[0] - window[0]).toInt(), (box.bottom + screen[1] - window[1]).toInt())
            // Compose menus use an independently positioned popup window. Convert its
            // visible root rectangle to display coordinates before intersecting the target.
            val visible = Rect()
            val shown = mounted.root.getLocalVisibleRect(visible)
            visible.offset(screen[0], screen[1])
            if (!shown || !rect.intersect(visible) || rect.isEmpty) null else rect
        }
    }

    private fun headerFraction(): Float = main {
        requireNotNull(mountedNodes().firstOrNull {
            it.node.config.getOrNull(SemanticsProperties.TestTag) == "steam-space-playing-header"
        }?.node?.config?.getOrNull(SteamSpaceHeaderCollapsedFraction))
    }

    private data class HeaderSample(val fraction: Float, val header: Rect, val avatar: Rect, val target: Rect) {
        fun describe(time: Long) = "uptimeMs=$time fraction=$fraction header=$header avatar=$avatar target=$target"
    }

    private fun headerSample() = HeaderSample(headerFraction(),
        requireNotNull(tagBounds("steam-space-playing-header")),
        requireNotNull(tagBounds("steam-space-playing-avatar-$peerSteamId")),
        requireNotNull(tagBounds("steam-space-playing-target-$peerSteamId")))

    private fun headerHasText(text: String): Boolean = main {
        val header = mountedNodes().first {
            it.node.config.getOrNull(SemanticsProperties.TestTag) == "steam-space-playing-header"
        }.node
        val visibleHeader = header.boundsInWindow
        fun contains(node: SemanticsNode): Boolean {
            val bounds = node.boundsInWindow
            if (bounds.width <= 0 || bounds.height <= 0 || !bounds.overlaps(visibleHeader)) return false
            if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) return true
            return !node.config.isClearingSemantics && node.children.any(::contains)
        }
        contains(header)
    }

    private fun dp(value: Int): Int = main { (activity!!.resources.displayMetrics.density * value).toInt() }

    private data class HeaderDrag(val down: Long, val x: Float, var y: Float, var failure: Throwable? = null)
    private var measuredCollapseRange = 0f

    private fun beginHeaderDrag(upward: Boolean): HeaderDrag {
        // A ModalBottomSheet can remove its composition before its native window returns focus.
        // Wait for the actual host window before injecting the next drag.
        await("current fixture window owns the next header gesture") {
            main { activity?.let { !it.isDestroyed && !it.isFinishing && it.hasWindowFocus() && it.window.decorView.isShown } == true }
        }
        val sample = headerSample()
        if (sample.fraction < .99f) {
            measuredCollapseRange = (sample.header.height() - dp(56)) / (1f - sample.fraction)
        }
        check(measuredCollapseRange > 0f)
        val feed = requireNotNull(tagBounds("steam-space-feed"))
        val y = if (upward) feed.bottom - dp(32).toFloat() else maxOf(feed.top, sample.header.bottom) + dp(32).toFloat()
        return HeaderDrag(SystemClock.uptimeMillis(), feed.exactCenterX(), y).also {
            inject(it.down, MotionEvent.ACTION_DOWN, it.x, it.y)
        }
    }

    private fun moveHeaderDrag(drag: HeaderDrag, targetFraction: Float) {
        try {
        val tolerance = if (targetFraction == 0f || targetFraction == 1f) .001f else .02f
        var attempts = 0
        while (kotlin.math.abs(headerFraction() - targetFraction) > tolerance && attempts++ < 100) {
            val delta = targetFraction - headerFraction()
            val distance = (kotlin.math.abs(delta) * measuredCollapseRange + 1f).coerceAtMost(dp(12).toFloat())
            drag.y += if (delta > 0f) -distance else distance
            val feed = requireNotNull(tagBounds("steam-space-feed"))
            check(drag.y in (feed.top + 1f)..(feed.bottom - 1f)) { "Header gesture must stay in the real feed" }
            inject(drag.down, MotionEvent.ACTION_MOVE, drag.x, drag.y)
            Thread.sleep(32)
        }
        assertTrue("Native drag reaches $targetFraction, actual ${headerFraction()}",
            kotlin.math.abs(headerFraction() - targetFraction) <= tolerance)
        } catch (failure: Throwable) {
            drag.failure = failure
            throw failure
        }
    }

    private fun endHeaderDrag(drag: HeaderDrag) {
        try { inject(drag.down, MotionEvent.ACTION_UP, drag.x, drag.y) }
        catch (releaseFailure: Throwable) {
            // Releasing after a failed MOVE must not replace the first, diagnostic failure.
            val originalFailure = drag.failure
            if (originalFailure != null) originalFailure.addSuppressed(releaseFailure) else throw releaseFailure
        }
    }

    private fun feedScrollValue(): Float = main {
        requireNotNull(mountedNodes().firstOrNull {
            it.node.config.getOrNull(SemanticsProperties.TestTag) == "steam-space-feed"
        }?.node?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)).value()
    }

    private fun returnFeedToTop() {
        repeat(12) {
            if (feedScrollValue() <= .01f) return@repeat
            val feed = requireNotNull(tagBounds("steam-space-feed"))
            val drag = HeaderDrag(SystemClock.uptimeMillis(), feed.exactCenterX(), maxOf(feed.top, headerSample().header.bottom) + dp(24).toFloat())
            inject(drag.down, MotionEvent.ACTION_DOWN, drag.x, drag.y)
            try {
                while (feedScrollValue() > .01f && drag.y + dp(12) < feed.bottom - dp(24)) {
                    drag.y += dp(12)
                    inject(drag.down, MotionEvent.ACTION_MOVE, drag.x, drag.y)
                    Thread.sleep(24)
                }
                Thread.sleep(160)
            } finally { endHeaderDrag(drag) }
        }
        await("feed returned to its actual scroll origin") { feedScrollValue() <= .01f }
        // A small final overscroll can partially expand the header. Let the real settle finish.
        await("header settle after reaching the feed top") { headerFraction() <= .001f || headerFraction() >= .999f }
        if (headerFraction() <= .001f) {
            val drag = beginHeaderDrag(upward = true)
            try { moveHeaderDrag(drag, 1f); Thread.sleep(160) } finally { endHeaderDrag(drag) }
        }
        await("compact header ready at feed origin") { headerFraction() >= .999f }
    }

    private fun tapTag(tag: String) {
        await("tagged target $tag") { tagBounds(tag) != null }
        val rect = requireNotNull(tagBounds(tag))
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY())
        inject(down, MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY())
    }

    private fun captureCollapseFrame(sequence: Int, phase: String) {
        val time = save("collapse-frame-${sequence.toString().padStart(2, '0')}")
        collapseFrameRecords += "sequence=$sequence phase=$phase ${headerSample().describe(time)}"
        writeCollapseTrace("collapse-frame-times.txt", collapseFrameRecords)
    }

    private fun writeCollapseTrace(name: String, lines: List<String>) {
        val directory = File(instrumentation.targetContext.filesDir, "steam-space-evidence").apply { mkdirs() }
        File(directory, name).writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun inject(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            val accepted = instrumentation.uiAutomation.injectInputEvent(event, true)
            if (!accepted) {
                val windowState = main {
                    "activity=${activity?.javaClass?.simpleName} destroyed=${activity?.isDestroyed} finishing=${activity?.isFinishing} " +
                        "focus=${activity?.hasWindowFocus()} configuration=${activity?.resources?.configuration}\n" +
                        WindowInspector.getGlobalWindowViews().joinToString("\n") {
                            "root=${it.javaClass.simpleName} shown=${it.isShown} focus=${it.hasWindowFocus()} size=${it.width}x${it.height}"
                        }
                }
                writeCollapseTrace("input-rejection-${SystemClock.uptimeMillis()}.txt",
                    listOf("action=$action down=$down x=$x y=$y", windowState))
                fail("Native input rejected: action=$action x=$x y=$y; $windowState")
            }
        } finally { event.recycle() }
    }

    private fun tap(label: String, description: Boolean = false) {
        await("tap target $label") { bounds(label, description, clickable = true) != null }
        // A sheet spring can cross its destination before settling. Do not tap a moving target.
        var last: Rect? = null
        var stableSince = SystemClock.uptimeMillis()
        await("stable tap target $label") {
            val current = bounds(label, description, clickable = true)
            if (current != last) { last = current; stableSince = SystemClock.uptimeMillis() }
            current != null && SystemClock.uptimeMillis() - stableSince >= 200
        }
        val rect = requireNotNull(last)
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY())
        inject(down, MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY())
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun swipe(fromX: Float, toX: Float, y: Float) {
        val width = main { activity!!.resources.displayMetrics.widthPixels }
        gesture(width * fromX, y, width * toX, y)
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float) {
        val down = SystemClock.uptimeMillis()
        inject(down, MotionEvent.ACTION_DOWN, x1, y1)
        repeat(18) { i ->
            Thread.sleep(16)
            val fraction = (i + 1) / 18f
            inject(down, MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * fraction, y1 + (y2 - y1) * fraction)
        }
        inject(down, MotionEvent.ACTION_UP, x2, y2)
        Thread.sleep(250)
    }

    private fun scrollTo(label: String) {
        repeat(8) {
            if (textVisible(label)) return
            val metrics = main { activity!!.resources.displayMetrics }
            gesture(metrics.widthPixels * .5f, metrics.heightPixels * .79f,
                metrics.widthPixels * .5f, metrics.heightPixels * .35f)
        }
        assertTrue("Scrolled to $label", textVisible(label))
    }

    private fun save(label: String): Long {
        // Wait for actual submitted window frames, including the independent sheet window.
        val roots = main { WindowInspector.getGlobalWindowViews().filter { it.isShown } }
        for (root in roots) {
            val committed = CountDownLatch(1)
            main { root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }; root.invalidate() }
            assertTrue("Frame committed for $label", committed.await(5, TimeUnit.SECONDS))
        }
        val captureUptime = SystemClock.uptimeMillis()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.filesDir, "steam-space-evidence").apply { mkdirs() }
        File(directory, "$label.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return captureUptime
    }

    private fun awaitTopBarSurface(evidenceLabel: String = "dark") {
        val expected = surfaceColor.get()
        val sampleY = main {
            val statusInset = requireNotNull(activity!!.window.decorView.rootWindowInsets)
                .getInsets(android.view.WindowInsets.Type.statusBars()).top
            statusInset + (activity!!.resources.displayMetrics.density * 10).toInt()
        }
        var stableSince = 0L
        val samples = mutableListOf<Int>()
        await("exposed margin beside the floating header reaches the current theme background") {
            val hostFocused = main {
                activity?.let { !it.isDestroyed && !it.isFinishing && it.hasWindowFocus() && it.window.decorView.isShown } == true
            }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val actual = try { bitmap.getPixel(1, sampleY) } finally { bitmap.recycle() }
            samples += actual
            val matches = hostFocused && kotlin.math.abs(Color.red(actual) - Color.red(expected)) <= 1 &&
                kotlin.math.abs(Color.green(actual) - Color.green(expected)) <= 1 &&
                kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) <= 1
            if (!matches) stableSince = 0L
            else if (stableSince == 0L) stableSince = SystemClock.uptimeMillis()
            matches && SystemClock.uptimeMillis() - stableSince >= 200
        }
        val directory = File(instrumentation.targetContext.filesDir, "steam-space-evidence").apply { mkdirs() }
        File(directory, "$evidenceLabel-top-app-bar-surface.txt").writeText(
            "point=(1,$sampleY)\nexpected=${Integer.toHexString(expected)}\n" +
                "samples=${samples.joinToString { Integer.toHexString(it) }}\n"
        )
    }

    private fun seedOfflineImage() {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(27, 42, 65))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 18f; color = Color.rgb(68, 174, 237) }
        canvas.drawOval(75f, 55f, 210f, 310f, paint)
        paint.color = Color.rgb(255, 157, 57)
        canvas.drawOval(430f, 55f, 565f, 310f, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textSize = 25f
        canvas.drawText("CO-OP", 274f, 178f, paint)
        paint.textSize = 14f
        canvas.drawText("OFFLINE TEST IMAGE", 250f, 211f, paint)
        val file = steamRemoteImageCacheFile(instrumentation.targetContext, offlineImageUrl)
        check(!file.exists()) { "Own fixture image cache must not overwrite existing data" }
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        seededImage = file
    }

    private fun seedOfflineAchievementImage() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(29, 52, 78))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 189, 71) }
        canvas.drawCircle(48f, 43f, 27f, paint)
        canvas.drawRect(43f, 68f, 53f, 78f, paint)
        canvas.drawRoundRect(29f, 77f, 67f, 84f, 3f, 3f, paint)
        paint.color = Color.rgb(29, 52, 78); paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(35f, 43f, 44f, 52f, paint)
        canvas.drawLine(44f, 52f, 61f, 34f, paint)
        val file = steamRemoteImageCacheFile(instrumentation.targetContext,
            requireNotNull(takagi.ru.monica.steam.space.data.SteamSpaceLinks.achievementIcon(620, offlineAchievementUrl)))
        check(!file.exists()) { "Own achievement fixture must not overwrite cached data" }
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        seededAchievementImage = file
    }
}
