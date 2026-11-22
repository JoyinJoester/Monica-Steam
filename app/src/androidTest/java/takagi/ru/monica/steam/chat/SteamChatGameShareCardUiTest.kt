package takagi.ru.monica.steam.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamChineseComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile
import takagi.ru.monica.steam.friends.chat.gameinvite.domain.steamGameInviteHeaderUrl
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.ui.SteamChatThread
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.steam.store.data.SteamStoreCache
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.ui.theme.MonicaTheme

/** Cache-backed synthetic data only, including the existing invitation regression fixtures. */
internal fun seedChatGameCardFixture(context: Context, appId: Int, name: String, artworkTitle: String) {
    val url = steamGameInviteHeaderUrl(appId)
    SteamStoreCache(context).writeDetail(null, SteamStoreDetail(appId, name, headerImageUrl = url))
    val image = Bitmap.createBitmap(460, 215, Bitmap.Config.ARGB_8888)
    Canvas(image).apply {
        drawColor(Color.rgb(26, 130, 151))
        drawRect(300f, 0f, 460f, 215f, Paint().apply { color = Color.rgb(26, 58, 110) })
        drawCircle(360f, 97f, 52f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 189, 72) })
        drawText(artworkTitle, 22f, 106f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f; isFakeBoldText = true })
        drawText("OFFLINE ART FIXTURE", 22f, 141f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f })
    }
    steamRemoteImageCacheFile(context, url).apply { parentFile!!.mkdirs() }.outputStream().use {
        image.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    image.recycle()
}

class SteamChatGameShareCardUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamChineseComposeFixtureActivity>()
    private val own = "76561198000000001"
    private val peer = "76561198000000002"
    private val openedGames = mutableListOf<Int>()
    private fun label(id: Int) = compose.activity.getString(id)

    @After fun clearOverrides() {
        SteamChineseComposeFixtureActivity.fontScaleOverride = null
        SteamChineseComposeFixtureActivity.densityDpiOverride = null
    }

    private fun mount(body: String, outgoing: Boolean, large: Boolean, glass: Boolean) {
        SteamChineseComposeFixtureActivity.densityDpiOverride = if (large) 540 else 480
        SteamChineseComposeFixtureActivity.fontScaleOverride = if (large) 1.5f else 1f
        compose.activityRule.scenario.recreate()
        seedChatGameCardFixture(compose.activity, 4001890, "渔力全开", "HOW TO FISH")
        seedChatGameCardFixture(compose.activity, 620, "传送门 2 · 合作关卡合集（合成测试）", "PORTAL 2")
        compose.setContent { MonicaTheme(darkTheme = glass) {
            CompositionLocalProvider(LocalSteamDesignStyle provides if (glass) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED) {
                SteamGlassHost(modifier = androidx.compose.ui.Modifier.fillMaxSize(), overlay = {}, content = {
                    SteamChatThread(
                        state = SteamChatUiState(accountSteamId = own, selectedPartnerSteamId = peer,
                            thread = SteamChatThreadSnapshot(own, peer, listOf(
                                SteamChatMessage(peer, if (outgoing) own else peer, 1795222800, 1, body)), false, 0)),
                        richMediaState = SteamChatRichMediaUiState(), friend = SteamFriend(peer, personaName = "卡片测试好友"),
                        onNavigateBack = {}, onOpenInfo = {}, onRefresh = {}, onLoadOlder = {},
                        onSend = { fail("Card interaction must not send a message") }, onRetryMessage = {},
                        onReact = { _, _ -> }, onStickerReply = { _, _ -> }, onReport = { _, _ -> },
                        onAttachmentSelected = {}, onAttachmentSpoilerChanged = {}, onUploadAttachment = {},
                        onClearAttachment = {}, onClearAttachmentFailure = {}, onRefreshCatalogs = {},
                        onOpenStoreApp = { openedGames.add(it) })
                })
            }
        } }
    }

    private fun card() = compose.onNodeWithTag("steam-chat-game-card")

    private fun assertCardHasNoExtraButtons() {
        compose.onNodeWithContentDescription(label(R.string.more_options)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.steam_chat_game_invite_view_store)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.steam_chat_store_game_view)).assertDoesNotExist()
    }

    private fun verifyArtwork(title: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val art = compose.onNodeWithTag("steam-chat-game-artwork", useUnmergedTree = true)
        art.assertIsDisplayed()
        val cover = art.fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithTag("steam-chat-game-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Cover must lead the title", cover.bottom <= heading.top)
        assertTrue("Share artwork is a wide banner", cover.width / cover.height in 2.1f..2.2f)
        compose.waitUntil(5_000) {
            val bitmap = art.captureToImage().asAndroidBitmap()
            val pixel = bitmap.getPixel(bitmap.width / 10, bitmap.height / 10)
            Color.red(pixel) < 60 && Color.green(pixel) > 100 && Color.blue(pixel) > 130
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(200)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(compose.activity.filesDir, "game-share-cards").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun receivedInviteHasArtworkAndRetainsStoreAndPreviewDetails() {
        mount("steam://joinlobby/4001890/123456789/$peer", outgoing = false, large = false, glass = false)
        verifyArtwork("渔力全开")
        assertCardHasNoExtraButtons()
        compose.onNodeWithText(compose.activity.getString(R.string.steam_chat_game_invite_app_id, 4001890), useUnmergedTree = true).assertDoesNotExist()
        capture("received-invite-m3e")
        card().performClick()
        compose.runOnIdle { assertTrue(openedGames.isEmpty()) }
        compose.onNodeWithTag("steam-card-preview").assertIsDisplayed()
        compose.onNode(hasText(compose.activity.getString(R.string.steam_chat_game_invite_app_id, 4001890)) and
            hasAnyAncestor(hasTestTag("steam-card-preview"))).performScrollTo().assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        card().performTouchInput { longClick() }
        compose.onNodeWithText(label(R.string.steam_chat_game_invite_view_store)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(4001890), openedGames) }
    }

    @Test fun sentStoreShareWithLargeFontRetainsArtworkCopyAndStoreNavigation() {
        val link = "https://store.steampowered.com/app/620/"
        mount(link, outgoing = true, large = true, glass = true)
        verifyArtwork("传送门 2 · 合作关卡合集（合成测试）")
        assertCardHasNoExtraButtons()
        compose.onNodeWithText(compose.activity.getString(R.string.steam_chat_store_game_app_id, 620), useUnmergedTree = true).assertDoesNotExist()
        capture("sent-store-large-glass")
        card().performClick()
        compose.runOnIdle { assertTrue(openedGames.isEmpty()) }
        compose.onNodeWithTag("steam-card-preview").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.steam_card_copy_link)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(link, compose.activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
            assertTrue(openedGames.isEmpty())
        }
        card().performClick()
        compose.onNodeWithText(label(R.string.steam_chat_store_game_view)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(620), openedGames) }
    }
}
