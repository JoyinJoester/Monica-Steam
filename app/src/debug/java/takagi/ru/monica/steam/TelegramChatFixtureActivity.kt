package takagi.ru.monica.steam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import androidx.compose.runtime.*
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.ui.SteamChatThread
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.*
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChatThread
import takagi.ru.monica.ui.theme.MonicaTheme

/** Non-exported debug fixture. All IDs, messages and callbacks are offline test data. */
@Keep
class TelegramChatFixtureActivity : ComponentActivity() {
    var fixtureWallpaper by mutableStateOf(takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset.TELEGRAM_PATTERN)
    var fixtureColorScheme by mutableStateOf(takagi.ru.monica.data.ColorScheme.DEFAULT)
    var fixtureDark by mutableStateOf(true)
    var fixtureOled by mutableStateOf(false)
    var currentPalette: androidx.compose.material3.ColorScheme? = null
    val sent = mutableListOf<String>()
    var fixtureInfoOpen by mutableStateOf(false)
    var fixtureTargetId by mutableStateOf<String?>(null)
    var fixtureTargetNonce by mutableIntStateOf(0)
    var sharedOpened: String? = null
    var infoOpened = false
    var retries = 0
    var selectedRoom = "general"
    var requestedHistory = 0
    var refreshes = 0
    private val self = "telegram-fixture-self"
    private val peer = "telegram-fixture-peer"
    private val epoch = 1792900800L
    private val initialMessages = listOf(
        "今晚一起玩 Portal 2 吗？", "我已经建好房间了。", "好的，我先更新一下。",
        "九点开始，怎么样？", "九点没问题。"
    )
    private fun fixtureBody(index: Int): String {
        if (!intent.getBooleanExtra("rich", false)) return initialMessages[index % initialMessages.size]
        return when (index % 5) {
            0 -> "[img]https://avatars.fastly.steamstatic.com/synthetic-chrome.jpg[/img]"
            1 -> "Portal guide https://steamcommunity.com/sharedfiles/filedetails/?id=123456"
            else -> initialMessages[index % initialMessages.size]
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fixtureColorScheme = intent.getStringExtra("theme")?.let { takagi.ru.monica.data.ColorScheme.valueOf(it) }
            ?: takagi.ru.monica.data.ColorScheme.DEFAULT
        fixtureDark = intent.getBooleanExtra("dark", true)
        fixtureOled = intent.getBooleanExtra("oled", false)
        val count = if (intent.getBooleanExtra("long", false)) 80 else initialMessages.size
        val fixtureKey = intent.getStringExtra("fixtureKey") ?: "fixture"
        setContent {
            MonicaTheme(darkTheme = fixtureDark, colorScheme = fixtureColorScheme, oledPureBlackEnabled = fixtureOled) {
                val palette = androidx.compose.material3.MaterialTheme.colorScheme
                SideEffect { currentPalette = palette }
                val partner = peer + fixtureKey
                val friend = SteamFriend(partner, personaName = "小林")
                if (intent.getBooleanExtra("group", false)) {
                    var room by remember { mutableStateOf("general") }
                    var messages by remember { mutableStateOf(List(count) { i ->
                        SteamGroupChatMessage("fixture-$fixtureKey", room, if (i == 2 || i == count - 1) self else partner,
                            epoch + i * 60, i, fixtureBody(i), clientMessageId = "fixture-$i")
                    }) }
                    val group = SteamGroupChatSummary("fixture-$fixtureKey", "Monica Steam 测试群", defaultChatId = "general",
                        activeMemberCount = 5, rooms = listOf(SteamGroupChatRoom("general", "General"), SteamGroupChatRoom("team", "联机组队")))
                    if (fixtureInfoOpen) takagi.ru.monica.steam.friends.chat.info.ui.SteamChatInfoScreen(
                        account = null, directFriend = null, group = group,
                        members = listOf(friend),
                        preferences = takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences(),
                        canEditGroup = false, updatingGroup = false, updatingGroupAvatar = false,
                        onBack = { fixtureInfoOpen = false }, onAddMember = {}, onOpenFriendDetails = {},
                        sharedHistoryItems = takagi.ru.monica.steam.friends.chat.ui.buildSteamChatHistory(null, null, listOf(friend).associateBy { it.steamId }, emptyList(), "", messages, self),
                        onOpenSharedMessage = { sharedOpened = it; fixtureTargetId = it; fixtureTargetNonce++; fixtureInfoOpen = false },
                        onPreferencesChange = {}, onUpdateGroup = { _, _ -> }, onUpdateGroupAvatar = {}
                    ) else
                    SteamGroupChatThread(
                        targetMessageId = fixtureTargetId, targetMessageNonce = fixtureTargetNonce,
                        wallpaperPreset = fixtureWallpaper,
                        state = SteamGroupChatUiState(self, groups = listOf(group), selectedGroupId = group.groupId,
                            selectedChatId = room, thread = SteamGroupChatThreadSnapshot(self, group.groupId, room, messages, false, 0)),
                        richMediaState = SteamChatRichMediaUiState(), group = group, friends = listOf(friend),
                        onBack = { finish() }, onOpenInfo = { infoOpened = true; if (intent.getBooleanExtra("chrome", false)) fixtureInfoOpen = true },
                        onOpenRoom = { _, next -> room = next; selectedRoom = next },
                        onLoadOlder = { requestedHistory++ },
                        onSend = { body -> sent += body; messages = messages + SteamGroupChatMessage(group.groupId, room, self, epoch + 10000 + messages.size, messages.size, body, clientMessageId = "sent-${messages.size}") },
                        onRetryMessage = { retries++ }, onInvite = {}, onAttachmentSelected = {},
                        onAttachmentSpoilerChanged = {}, onUploadAttachment = {}, onClearAttachment = {},
                        onClearAttachmentFailure = {}, onRefreshCatalogs = {}, onUpdateReaction = { _, _, _, _ -> },
                        onReportMessage = { _, _ -> }, onDeleteMessage = {}
                    )
                } else {
                    var messages by remember { mutableStateOf(List(count) { i ->
                        SteamChatMessage(partner, if (i == 2 || i == count - 1) self else partner, epoch + i * 60, i,
                            fixtureBody(i), clientMessageId = "fixture-$i",
                            deliveryState = if (i == count - 1) SteamChatDeliveryState.FAILED_RETRYABLE else SteamChatDeliveryState.SENT)
                    }) }
                    if (fixtureInfoOpen) takagi.ru.monica.steam.friends.chat.info.ui.SteamChatInfoScreen(
                        account = null, directFriend = friend, group = null,
                        members = emptyList(),
                        preferences = takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences(),
                        canEditGroup = false, updatingGroup = false, updatingGroupAvatar = false,
                        onBack = { fixtureInfoOpen = false }, onAddMember = {}, onOpenFriendDetails = {},
                        sharedHistoryItems = takagi.ru.monica.steam.friends.chat.ui.buildSteamChatHistory(partner, friend, emptyMap(), messages, self, emptyList(), ""),
                        onOpenSharedMessage = { sharedOpened = it; fixtureTargetId = it; fixtureTargetNonce++; fixtureInfoOpen = false },
                        onPreferencesChange = {}, onUpdateGroup = { _, _ -> }, onUpdateGroupAvatar = {}
                    ) else
                    SteamChatThread(
                        targetMessageId = fixtureTargetId, targetMessageNonce = fixtureTargetNonce,
                        wallpaperPreset = fixtureWallpaper,
                        state = SteamChatUiState(accountSteamId = self, selectedPartnerSteamId = partner,
                            thread = SteamChatThreadSnapshot(self, partner, messages, false, 0)),
                        richMediaState = SteamChatRichMediaUiState(), friend = friend,
                        onNavigateBack = { finish() }, onOpenInfo = { infoOpened = true; if (intent.getBooleanExtra("chrome", false)) fixtureInfoOpen = true }, onRefresh = { refreshes++ },
                        onLoadOlder = { requestedHistory++ },
                        onSend = { body -> sent += body; messages = messages + SteamChatMessage(partner, self, epoch + 10000 + messages.size, messages.size, body, clientMessageId = "sent-${messages.size}") },
                        onRetryMessage = { id -> retries++; messages = messages.map { if (it.clientMessageId == id) it.copy(deliveryState = SteamChatDeliveryState.SENT) else it } },
                        onReact = { _, _ -> }, onStickerReply = { _, _ -> }, onReport = { _, _ -> },
                        onAttachmentSelected = {}, onAttachmentSpoilerChanged = {}, onUploadAttachment = {},
                        onClearAttachment = {}, onClearAttachmentFailure = {}, onRefreshCatalogs = {}
                    )
                }
            }
        }
    }
}
