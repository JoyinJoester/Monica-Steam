package takagi.ru.monica.steam.friends.chat

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.ui.*
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.ui.theme.MonicaTheme

/** Synthetic conversations only: callbacks never reach Steam. */
class SteamChatInteractionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val account = "chat-ui-fixture-${java.util.UUID.randomUUID()}"
    private val timestamp = System.currentTimeMillis() / 1_000L
    private var partner by mutableStateOf("Alex")
    private var visible by mutableStateOf(true)
    private var connected by mutableStateOf(true)
    private var dark by mutableStateOf(false)
    private var fontScale by mutableFloatStateOf(1f)
    private var messages by mutableStateOf(listOf(SteamChatMessage("Alex", "Alex", timestamp, 1, "Ready to play?")))
    private val sent = mutableListOf<Pair<String, String>>()
    private var refreshes = 0
    private fun label(id: Int): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        }).getString(id)
    }
    private fun launch(thread: Boolean = false): StateRestorationTester {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val restore = StateRestorationTester(compose)
        restore.setContent {
            val base = LocalContext.current
            val activityResults = requireNotNull(LocalActivityResultRegistryOwner.current)
            val backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            val config = remember { Configuration(base.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) } }
            val context = remember { base.createConfigurationContext(config) }
            val drafts = rememberSteamChatDraftStore()
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalActivityResultRegistryOwner provides activityResults,
                LocalOnBackPressedDispatcherOwner provides backDispatcher,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalSteamChatDraftStore provides drafts) {
                MonicaTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize().testTag("chat_preview")) {
                        if (!visible) Text("conversation list")
                        else if (thread) SteamChatThread(
                            state = SteamChatUiState(accountSteamId = account, selectedPartnerSteamId = partner,
                                realtimeConnected = connected,
                                thread = SteamChatThreadSnapshot(account, partner, messages, false, 0L)),
                            friend = null, richMediaState = SteamChatRichMediaUiState(),
                            onNavigateBack = { visible = false }, onOpenInfo = {},
                            onRefresh = { refreshes++ }, onLoadOlder = {},
                            onSend = { body ->
                                sent += partner to body
                                messages = messages + SteamChatMessage(partner, account, timestamp + 1L, Int.MAX_VALUE,
                                    body, SteamChatDeliveryState.QUEUED, "client-${sent.size}")
                            },
                            onRetryMessage = {}, onReact = { _, _ -> }, onStickerReply = { _, _ -> },
                            onReport = { _, _ -> }, onAttachmentSelected = {}, onAttachmentSpoilerChanged = {},
                            onUploadAttachment = {}, onClearAttachment = {}, onClearAttachmentFailure = {}, onRefreshCatalogs = {}
                        ) else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                            SteamChatComposer(draftKey = "$account:$partner", richMediaState = SteamChatRichMediaUiState(),
                                onSend = { sent += partner to it }, onAttachmentSelected = {},
                                onAttachmentSpoilerChanged = {}, onUploadAttachment = {}, onClearAttachment = {},
                                onClearAttachmentFailure = {}, onRefreshCatalogs = {})
                        }
                    }
                }
            }
        }
        return restore
    }
    private fun input() = compose.onNode(hasSetTextAction())
    private fun assertEmptyInput() = input().assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("chat_preview").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$name.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun draftsSurviveConversationSwitchNavigationAndSavedState() {
        val restore = launch()
        input().performTextInput("Alex draft")
        compose.runOnIdle { partner = "Morgan" }
        assertEmptyInput()
        input().performTextInput("Morgan draft")
        compose.runOnIdle { visible = false }
        compose.runOnIdle { visible = true; partner = "Alex" }
        input().assertTextEquals("Alex draft")
        restore.emulateSavedInstanceStateRestore()
        input().assertTextEquals("Alex draft")
        compose.runOnIdle { partner = "Morgan" }
        input().assertTextEquals("Morgan draft")
    }

    @Test fun consecutiveSendsClearOnlyCurrentDraftAndKeepInputFocused() {
        launch()
        input().performClick().performTextInput("first")
        compose.onNodeWithContentDescription(label(R.string.steam_chat_send)).performClick()
        assertEmptyInput().assertIsFocused()
        input().performTextInput("second")
        input().performImeAction()
        assertEmptyInput().assertIsFocused()
        assertEquals(listOf("Alex" to "first", "Alex" to "second"), sent)
        compose.onNodeWithContentDescription(label(R.string.steam_chat_send)).assertIsNotEnabled()
    }

    @Test fun threadShowsIncomingMessageAndReconnectStatusWithoutReplacingDraft() {
        launch(thread = true)
        input().performTextInput("still typing")
        compose.runOnIdle {
            messages = messages + SteamChatMessage(partner, partner, timestamp + 1L, 2, "New live message")
            connected = false
        }
        try {
            compose.waitUntil(3_000L) { compose.onNodeWithText("New live message").isDisplayed() }
        } finally {
            screenshot("chat-thread-reconnecting")

        }
        input().assertTextEquals("still typing")
        compose.onNodeWithText(label(R.string.steam_chat_reconnecting)).assertIsDisplayed()
        screenshot("chat-thread-reconnecting")
        compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
        // Popups may use the host Activity locale rather than the preview context locale.
        compose.onNode(hasText(label(R.string.refresh)) or hasText("Refresh")).performClick()
        assertEquals(1, refreshes)
    }

    @Test fun largeFontAndDarkModeKeepComposerAndSendReachable() {
        fontScale = 1.5f
        dark = true
        launch(thread = true)
        input().performClick().performTextInput("A message written with larger text")
        compose.onNodeWithContentDescription(label(R.string.steam_chat_send)).assertIsDisplayed().performClick()
        input().assertIsDisplayed().assertIsFocused()
        screenshot("chat-thread-large-dark")
        assertEquals(1, sent.size)
    }

    @Test fun searchAndMessagePreviewStayInTheConversationList() {
        var query by mutableStateOf("")
        val sessions = SteamChatSessionsSnapshot(account, listOf(
            SteamChatSession("Alex", timestamp, unreadCount = 2, lastMessage = "Ready to play?"),
            SteamChatSession("Morgan", timestamp - 90L, lastMessage = "See you tomorrow")
        ), 0L)
        compose.setContent {
            MonicaTheme {
                Column(Modifier.fillMaxSize().testTag("chat_preview")) {
                    SteamChatSearchBar(query, false, true, { query = it }, {}, {})
                    SteamConversationList(SteamChatUiState(sessions = sessions), SteamGroupChatUiState(),
                        emptyList(), query, emptySet(), emptySet(), { sent += it to "open" }, { _, _ -> }, {}, {},
                        modifier = Modifier.weight(1f))
                }
            }
        }
        compose.onNodeWithText("Ready to play?").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Ready to play?", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("A short message preview must fit at large font scale", layouts.single().hasVisualOverflow)
        compose.onNodeWithTag("chat_search").performTextInput("tomorrow")
        compose.onNodeWithText("Alex").assertDoesNotExist()
        compose.onNodeWithText("Morgan").assertIsDisplayed().performClick()
        assertEquals("Morgan", sent.single().first)
        compose.onNodeWithTag("chat_search").performTextClearance()
        screenshot("chat-conversations")
    }
}
