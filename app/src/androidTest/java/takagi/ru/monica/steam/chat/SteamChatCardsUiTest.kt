package takagi.ru.monica.steam.chat

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamSpaceFixtureActivity
import takagi.ru.monica.steam.foundation.ui.SteamCardPreviewHost
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamOfficialMessageContent
import takagi.ru.monica.steam.friends.chat.ui.SteamChatComposer
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.ui.theme.MonicaTheme

/** Offline synthetic cards only. These tests never open Steam or send a chat message. */
class SteamChatCardsUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamSpaceFixtureActivity>()
    private val own = "76561198000000001"
    private val url = "https://steamcommunity.com/tradeoffer/new/?partner=123&token=synthetic"
    private fun label(id: Int) = compose.activity.getString(id)

    @org.junit.Before fun seedOfflineInvitation() {
        seedChatGameCardFixture(compose.activity, 730, "Synthetic game", "SYNTHETIC GAME")
    }

    @Test fun unsupportedCardRetainsOriginalMessageWithoutActionButton() {
        val body = "[steam_future url=javascript:bad]Synthetic payload[/steam_future]"
        val message = (SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage).message
        compose.setContent { MonicaTheme {
            SteamCardPreviewHost("synthetic") { SteamOfficialMessageContent(message) }
        } }
        openPreview()
        compose.onNodeWithText(label(R.string.steam_card_open)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.steam_card_details)).performClick()
        compose.onNodeWithText(body).assertIsDisplayed()
    }

    @Test fun actualComposerSendsCardOnlyThroughExistingSendButton() {
        val sent = mutableListOf<String>()
        compose.setContent { MonicaTheme {
            Box(Modifier.fillMaxSize()) {
                SteamChatComposer(
                    draftKey = "synthetic/direct",
                    richMediaState = SteamChatRichMediaUiState(), onSend = { sent.add(it) },
                    onAttachmentSelected = {}, onAttachmentSpoilerChanged = {}, onUploadAttachment = {},
                    onClearAttachment = {}, onClearAttachmentFailure = {}, onRefreshCatalogs = {},
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } }
        assertNoComposerMenu()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.EditText::class.java))
            .perform(androidx.test.espresso.action.ViewActions.replaceText(url))
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Send"))
            .perform(androidx.test.espresso.action.ViewActions.click())
        compose.runOnIdle { assertEquals(listOf(url), sent) }
    }

    private fun openPreview() {
        val gameCards = compose.onAllNodesWithTag("steam-chat-game-card").fetchSemanticsNodes()
        if (gameCards.isNotEmpty()) compose.onNodeWithTag("steam-chat-game-card").performClick()
        else compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
        compose.onNodeWithTag("steam-card-preview").assertIsDisplayed()
    }

    private fun previewAction(id: Int) {
        compose.onNodeWithText(label(id)).performScrollTo().performClick()
    }

    private val instrumentation get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(200) // Allow the committed Surface frame to reach the screenshot buffer.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = java.io.File(compose.activity.filesDir, "chat-cards").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun interceptLinks(block: (MutableList<android.content.Intent>) -> Unit) {
        val opened = mutableListOf<android.content.Intent>()
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action != android.content.Intent.ACTION_VIEW) return null
                opened.add(intent)
                return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try { block(opened) } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun directTradeOpensOnlyCurrentPartnerFormWithoutAddingOrSending() = interceptLinks { opened ->
        mountThread(dark = true, liquid = false)
        androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
            androidx.test.espresso.matcher.ViewMatchers.withContentDescription(label(R.string.more_options)),
            androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA(
                androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(com.monica.telegramchat.TelegramChatHeaderView::class.java))
        )).perform(androidx.test.espresso.action.ViewActions.click())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(label(R.string.steam_card_start_trade)))
            .perform(androidx.test.espresso.action.ViewActions.click())
        compose.runOnIdle {
            assertEquals(listOf("https://steamcommunity.com/tradeoffer/new/?partner=39734274"), opened.map { it.dataString })
        }
    }

    @Test fun tradeOfferOpenAndCopyUseExactParsedOffer() = interceptLinks { opened ->
        val offer = "https://steamcommunity.com/tradeoffer/123456/"
        val message = (SteamChatRichContentParser.parse("[tradeoffer id=123456][/tradeoffer]") as SteamChatRichContent.OfficialMessage).message
        compose.setContent { MonicaTheme {
            SteamCardPreviewHost("synthetic") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SteamOfficialMessageContent(message) }
            }
        } }
        openPreview()
        previewAction(R.string.steam_card_view_trade)
        openPreview()
        previewAction(R.string.steam_card_copy_link)
        compose.runOnIdle {
            assertEquals(listOf(offer), opened.map { it.dataString })
            val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(offer, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test fun gameInvitationKeepsSteamActionSeparateFromStore() = interceptLinks { opened ->
        val invite = "steam://joinlobby/730/123456789/76561198000000002"
        val content = SteamChatRichContentParser.parse(invite) as SteamChatRichContent.GameInvite
        val stores = mutableListOf<Int>()
        var deleted = 0
        compose.setContent { MonicaTheme {
            SteamCardPreviewHost("invite") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    takagi.ru.monica.steam.friends.chat.gameinvite.ui.SteamChatGameInviteCard(
                        content, { stores.add(it) }, onDeleteMessage = { deleted++ }
                    )
                }
            }
        } }
        compose.onNodeWithText(label(R.string.steam_card_copy_link)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.open_link)).assertDoesNotExist()
        openPreview()
        compose.runOnIdle { assertTrue(stores.isEmpty()) }
        previewAction(R.string.steam_chat_game_invite_view_store)
        fun menuAction(id: Int) {
            openPreview()
            previewAction(id)
        }
        menuAction(R.string.open_link)
        menuAction(R.string.steam_card_copy_link)
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(invite, clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertEquals(0, deleted)
        }
        menuAction(R.string.steam_chat_game_invite_view_store)
        menuAction(R.string.delete)
        compose.runOnIdle {
            assertEquals(listOf(invite), opened.map { it.dataString })
            assertEquals(listOf(730, 730), stores)
            assertEquals(1, deleted)
        }
    }

    @Test fun unavailableExternalHandlerIsContainedAndInvalidCommandsAreIgnored() {
        var attempts = 0
        for (denied in listOf(false, true)) {
            val context = object : android.content.ContextWrapper(compose.activity) {
                override fun startActivity(intent: android.content.Intent) {
                    attempts++
                    if (denied) throw SecurityException("Synthetic denied handler")
                    throw android.content.ActivityNotFoundException("Synthetic missing handler")
                }
            }
            compose.runOnIdle {
                takagi.ru.monica.steam.friends.chat.richmedia.ui.openSteamChatCard(context, "steam://install/440")
                takagi.ru.monica.steam.friends.chat.richmedia.ui.openSteamChatCard(context, url)
            }
        }
        assertEquals(2, attempts)
    }

    @Test fun localDeleteRequiresConfirmationAndCancelKeepsMessage() {
        val removed = mutableListOf<takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage>()
        mountThread(dark = true, liquid = false,
            invitationBody = "steam://joinlobby/730/123456789/76561198000000002", onLocalDelete = removed::add)
        fun requestDelete() {
            openPreview()
            previewAction(R.string.delete)
        }
        requestDelete()
        compose.onNodeWithText(label(R.string.steam_chat_local_delete_description)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        requestDelete()
        compose.onNodeWithText(label(R.string.delete)).performClick()
        compose.runOnIdle { assertEquals(1, removed.size) }
    }

    @Test fun localDeleteMarkersSurviveStoreReopenAndStayInTheirConversation() {
        val context = compose.activity.applicationContext
        val account = "synthetic-delete-${java.util.UUID.randomUUID()}"
        val peer = "76561198000000002"
        val message = takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage(peer, peer, 100, 0, "Synthetic")
        val key = takagi.ru.monica.steam.friends.chat.data.SteamChatLocalDeletionStore.conversationKey(account, peer)
        try {
            val store = takagi.ru.monica.steam.friends.chat.data.SteamChatLocalDeletionStore(context)
            val changed = java.util.concurrent.CountDownLatch(1)
            val stop = store.observe(account, peer) { changed.countDown() }
            try {
                val reopened = takagi.ru.monica.steam.friends.chat.data.SteamChatLocalDeletionStore(context)
                val saved = reopened.hide(account, peer, message)
                assertTrue(changed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(saved, store.load(account, peer))
                assertEquals(saved, reopened.load(account, peer))
                assertTrue(store.load(account + "-other", peer).isEmpty())
                assertTrue(store.load(account, peer + "-other").isEmpty())
            } finally { stop() }
        } finally {
            context.getSharedPreferences("steam_chat_local_message_deletions", android.content.Context.MODE_PRIVATE)
                .edit().remove(key).commit()
        }
    }

    private fun mountThread(
        dark: Boolean, liquid: Boolean, peer: String = "76561198000000002",
        invitationBody: String? = null,
        onLocalDelete: ((takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage) -> Unit)? = null,
        outgoing: Boolean = false, fontScale: Float = 1f
    ) {
        val messages = listOf(
            takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage(peer, peer, 1792900800, 1, "一起交换道具\n$url"),
            takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage(peer, peer, 1792900860, 2, "[tradeoffer id=123456][/tradeoffer]")
        ).let { existing -> if (invitationBody == null) existing else listOf(existing.first().copy(
            body = invitationBody, senderSteamId = if (outgoing) own else peer)) }
        compose.setContent { MonicaTheme(darkTheme = dark) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle provides
                    if (liquid) takagi.ru.monica.steam.navigation.SteamDockStyle.LIQUID_GLASS
                    else takagi.ru.monica.steam.navigation.SteamDockStyle.FIXED) {
                takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost(
                    modifier = Modifier.fillMaxSize(), overlay = {}, content = {
                        takagi.ru.monica.steam.friends.chat.ui.SteamChatThread(
                            onDeleteMessageLocally = onLocalDelete,
                            state = takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState(
                                accountSteamId = own, selectedPartnerSteamId = peer,
                                thread = takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot(own, peer, messages, false, 0)),
                            richMediaState = SteamChatRichMediaUiState(),
                            friend = takagi.ru.monica.steam.friends.domain.SteamFriend(peer, personaName = "卡片测试好友"),
                            onNavigateBack = {}, onOpenInfo = {}, onRefresh = {}, onLoadOlder = {},
                            onSend = { fail("Preview must not send") }, onRetryMessage = {},
                            onReact = { _, _ -> }, onStickerReply = { _, _ -> }, onReport = { _, _ -> },
                            onAttachmentSelected = {}, onAttachmentSpoilerChanged = {}, onUploadAttachment = {},
                            onClearAttachment = {}, onClearAttachmentFailure = {}, onRefreshCatalogs = {}
                        )
                    }
                )
            }
        } }
    }

    @Test fun longPressLiftsTheActualChatCardAndBackRestoresTheThread() {
        mountThread(dark = true, liquid = false,
            invitationBody = "steam://joinlobby/730/123456789/76561198000000002")
        val trigger = SemanticsMatcher("Card preview long press") {
            it.config.getOrNull(SemanticsActions.OnLongClick)?.label == label(R.string.steam_preview_open)
        }
        capture("card-before-long-press")
        compose.onNode(trigger).performTouchInput { longClick(percentOffset(.35f, .5f)) }
        compose.onNodeWithTag("steam-card-preview").assertIsDisplayed()
        compose.onNode(hasText(label(R.string.steam_chat_game_invite_view_store)) and
            hasAnyAncestor(hasTestTag("steam-card-preview"))).assertIsDisplayed()
        capture("card-after-long-press")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        compose.onNode(trigger).assertIsDisplayed()
    }

    private fun verifyThreadAndAttachments(dark: Boolean, liquid: Boolean) {
        mountThread(dark, liquid)
        compose.onNodeWithText(label(R.string.steam_card_view_trade)).assertDoesNotExist()
        val theme = "${if (liquid) "glass" else "m3e"}-${if (dark) "dark" else "light"}"
        capture("thread-$theme")
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Attach"))
            .perform(androidx.test.espresso.action.ViewActions.click())
        capture("attachment-$theme")
        assertNoComposerMenu()
    }

    private fun assertNoComposerMenu() {
        androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
            androidx.test.espresso.matcher.ViewMatchers.withContentDescription("More options"),
            androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA(
                androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(com.monica.telegramchat.TelegramChatComposerView::class.java))
        )).check(androidx.test.espresso.assertion.ViewAssertions.matches(org.hamcrest.Matchers.not(
            androidx.test.espresso.matcher.ViewMatchers.isDisplayed())))
    }

    @Test fun m3eLightThreadAndAttachments() = verifyThreadAndAttachments(dark = false, liquid = false)
    @Test fun m3eDarkThreadAndAttachments() = verifyThreadAndAttachments(dark = true, liquid = false)
    @Test fun glassLightThreadAndAttachments() = verifyThreadAndAttachments(dark = false, liquid = true)
    @Test fun glassDarkThreadAndAttachments() = verifyThreadAndAttachments(dark = true, liquid = true)

    private fun verifyOpenGraphProfile(outgoing: Boolean, liquid: Boolean, fontScale: Float = 1f) {
        val profile = "https://steamcommunity.com/profiles/$own/"
        val image = "https://community.steamstatic.com/chat/image/synthetic-og-fixture/share_image.jpg"
        val cache = takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile(compose.activity, image)
        cache.parentFile!!.mkdirs()
        val bitmap = android.graphics.Bitmap.createBitmap(480, 240, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(47, 154, 174))
        cache.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val body = """[og url="$profile" img="$image" title="Steam Community :: Synthetic Profile" desc="About synthetic profile :fixture:"]$profile[/og]"""
        val parsed = (SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage).message
        assertEquals(image, parsed.imageUrl)
        mountThread(dark = true, liquid = liquid, invitationBody = body, outgoing = outgoing, fontScale = fontScale)
        compose.onNodeWithText("Steam Community :: Synthetic Profile").assertIsDisplayed()
        compose.onNodeWithText("About synthetic profile :fixture:").assertIsDisplayed()
        compose.onNodeWithText(body).assertDoesNotExist()
        val trigger = SemanticsMatcher("Profile card preview long press") {
            it.config.getOrNull(SemanticsActions.OnLongClick)?.label == label(R.string.steam_preview_open)
        }
        val name = "og-${if (outgoing) "sent" else "received"}-${if (liquid) "glass" else "m3e"}-$fontScale"
        capture(name)
        compose.onNode(trigger).performTouchInput { longClick(percentOffset(.35f, .5f)) }
        compose.onNodeWithTag("steam-card-preview").assertIsDisplayed()
        compose.onNode(hasText("Steam Community :: Synthetic Profile") and
            hasAnyAncestor(hasTestTag("steam-card-preview"))).assertIsDisplayed()
        capture("$name-preview")
        previewAction(R.string.steam_card_details)
        compose.onNodeWithText(body).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText(body).assertDoesNotExist()
    }

    @Test fun sentOpenGraphProfileIsACardInM3eThread() = verifyOpenGraphProfile(outgoing = true, liquid = false)
    @Test fun sentOpenGraphProfileIsACardInGlassThread() = verifyOpenGraphProfile(outgoing = true, liquid = true)
    @Test fun receivedOpenGraphProfileUsesTheSameParser() = verifyOpenGraphProfile(outgoing = false, liquid = false)
    @Test fun largeFontOpenGraphProfileStillHasReachableDetails() = verifyOpenGraphProfile(outgoing = true, liquid = true, fontScale = 1.5f)

    @Test fun pastedProfileLinkSendsNormallyAndServerEchoBecomesACard() {
        val sent = mutableListOf<String>()
        compose.setContent { MonicaTheme {
            Box(Modifier.fillMaxSize()) {
                SteamChatComposer(draftKey = "synthetic/profile",
                    richMediaState = SteamChatRichMediaUiState(), onSend = { sent.add(it) },
                    onAttachmentSelected = {}, onAttachmentSpoilerChanged = {}, onUploadAttachment = {},
                    onClearAttachment = {}, onClearAttachmentFailure = {}, onRefreshCatalogs = {},
                    modifier = Modifier.align(Alignment.BottomCenter))
            }
        } }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.EditText::class.java))
            .perform(androidx.test.espresso.action.ViewActions.replaceText("https://steamcommunity.com/profiles/$own/"))
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Send"))
            .perform(androidx.test.espresso.action.ViewActions.click())
        compose.runOnIdle {
            val profile = "https://steamcommunity.com/profiles/$own/"
            assertEquals(listOf(profile), sent)
            val echo = """[og url="$profile" title="Synthetic Profile" desc="Bio"]$profile[/og]"""
            assertEquals(takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessageKind.PROFILE,
                (SteamChatRichContentParser.parse(echo) as SteamChatRichContent.OfficialMessage).message.kind)
        }
    }
}
