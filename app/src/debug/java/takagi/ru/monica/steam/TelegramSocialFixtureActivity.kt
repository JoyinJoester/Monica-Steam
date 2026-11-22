package takagi.ru.monica.steam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryClassifier
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.friends.chat.info.ui.SteamChatInfoScreen
import takagi.ru.monica.steam.friends.chat.info.ui.SteamChatFriendDetailScreen
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.telegram.*
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationActions
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationFlags
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationList
import takagi.ru.monica.steam.friends.chat.ui.SteamChatRootContent
import takagi.ru.monica.steam.data.SteamAccountSourceState
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.domain.*
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.SteamGroupAvatarImage
import takagi.ru.monica.steam.friends.groupchat.domain.*
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.presentation.SteamFriendsUiState
import takagi.ru.monica.steam.friends.ui.SteamFriendsListContent
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.steamLiquidGlassBackdropSource
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamLiquidGlassDock
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamLiquidGlassDockVisibility
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.SteamDockContentClearance
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.steam.design.telegram.SteamTelegramTheme

/** Offline social-page navigation. No account service, Steam sends or uploads. */
@Keep
class TelegramSocialFixtureActivity : ComponentActivity() {
    var fixtureColorScheme by mutableStateOf(takagi.ru.monica.data.ColorScheme.DEFAULT)
    var fixtureDark by mutableStateOf(true)
    var fixtureOled by mutableStateOf(false)
    var fixtureHideDock by mutableStateOf(false)
    var fixtureDesignStyle by mutableStateOf(SteamDockStyle.FIXED)
    var currentPalette: androidx.compose.material3.ColorScheme? = null
    override fun attachBaseContext(base: android.content.Context) {
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }
    var route by mutableStateOf("conversations")
    var preferences by mutableStateOf(SteamChatConversationPreferences())
    var relationshipChanges = 0
    var invitesAccepted = 0
    var lastFriend = ""
    var selectedEmoji = ""
    var selectedAttachment = ""
    var selectedFolder = ""
    var rootShowFriends by mutableStateOf(false)
    var rootSearchExpanded by mutableStateOf(false)
    var rootQuery by mutableStateOf("")
    var rootMany by mutableStateOf(false)
    var accountsShown = 0
    var groupsCreated = 0
    var spacesOpened = 0
    var conversationsRefreshed = 0
    var conversationPins = 0
    var conversationArchives = 0
    var conversationDeletes = 0
    var lastConversationTarget = ""
    private val friend = SteamFriend("76561198000000001", SteamFriendRelationship.FRIEND,
        personaName = "Alex", personaState = SteamPersonaState.ONLINE, gameName = "Portal 2")
    private val members = listOf(friend, SteamFriend("76561198000000002", SteamFriendRelationship.FRIEND,
        personaName = "Sam", personaState = SteamPersonaState.OFFLINE)) + listOf("小林", "明月", "夏日", "游戏搭子", "星河", "Monica 玩家").mapIndexed { i, name ->
        // Half of them carry a real name, which is what the inbox shows when nobody is in game.
        SteamFriend("765611980000000${10 + i}", SteamFriendRelationship.FRIEND, personaName = name,
            realName = if (i % 2 == 0) "$name 的昵称" else "",
            personaState = if (i < 2) SteamPersonaState.ONLINE else SteamPersonaState.OFFLINE)
    }
    /** Extra chats so the inbox can actually overflow and be scrolled in tests. */
    private val longListMembers = listOf(
        "青柠", "阿澈", "老K", "Momo", "远山", "拾一", "小满", "七月", "阿岚", "白露"
    ).mapIndexed { i, name ->
        SteamFriend("7656119800000${100 + i}", SteamFriendRelationship.FRIEND, personaName = name,
            personaState = SteamPersonaState.OFFLINE)
    }
    private val group = SteamGroupChatSummary("fixture-group", "Monica 游戏小组",
        tagline = "一起发现好游戏", activeMemberCount = 2, defaultChatId = "general",
        topMemberSteamIds = members.take(4).map(SteamFriend::steamId),
        rooms = listOf(SteamGroupChatRoom("general", "General")))
    /** Un-archived group so the default inbox list also shows a composite avatar (three tiles). */
    private val liveGroup = SteamGroupChatSummary("fixture-group-live", "周末开黑",
        tagline = "周末一起上分", activeMemberCount = 3, defaultChatId = "general",
        topMemberSteamIds = members.drop(2).take(3).map(SteamFriend::steamId),
        rooms = listOf(SteamGroupChatRoom("general", "General")))
    /** Public Steam profile with an equipped background, used to preview the chat info hero. */
    private val heroFriend = SteamFriend(
        steamId = "76561198028121353",
        relationship = SteamFriendRelationship.FRIEND,
        personaName = "Sonix",
        avatarUrl = "https://avatars.fastly.steamstatic.com/54b97d0998d152f01d876d03dad1fdd2fb642dd2_full.jpg",
        personaState = SteamPersonaState.OFFLINE,
        lastLogoff = 1_761_338_880L
    )

    /** Odd rows are the search hit the "thread-jump" route scrolls to and highlights. */
    private val jumpTargetOrdinal = 9
    private val fixtureThreadMessages: List<SteamChatMessage> by lazy {
        val now = System.currentTimeMillis() / 1000
        (0 until 26).map { index ->
            SteamChatMessage(
                partnerSteamId = heroFriend.steamId,
                senderSteamId = if (index % 3 == 0) "fixture-self" else heroFriend.steamId,
                timestamp = now - (26 - index) * 300L,
                ordinal = index,
                body = if (index == jumpTargetOrdinal) "就是这条：今晚八点开黑，记得开麦"
                else "示例消息 #$index，用来把目标消息挤出首屏"
            )
        }
    }
    private val jumpTargetId: String get() = fixtureThreadMessages[jumpTargetOrdinal].stableId

    /** Remote images for the composite group avatar; the last entry has no picture on purpose. */
    private val fixtureAvatarMembers: List<SteamFriend> by lazy {
        listOf(620, 730, 570, 440, 105600).mapIndexed { index, app ->
            SteamFriend(
                steamId = "7656119800000010$index",
                relationship = SteamFriendRelationship.FRIEND,
                personaName = "成员 $index",
                avatarUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/$app/header.jpg"
            )
        } + SteamFriend(
            steamId = "76561198000000199",
            relationship = SteamFriendRelationship.FRIEND,
            personaName = "小林"
        )
    }

    /** Square Steam portraits: the shape real group members arrive with. */
    private val fixtureSquareAvatarMembers: List<SteamFriend> by lazy {
        List(4) { index ->
            SteamFriend(
                steamId = "7656119800000020$index",
                relationship = SteamFriendRelationship.FRIEND,
                personaName = "方图 $index",
                avatarUrl = heroFriend.avatarUrl
            )
        }
    }

    /** Mixed-category history used by the "chat-search" route. */    private val fixtureHistory: List<SteamChatHistoryItem> by lazy {
        val now = System.currentTimeMillis() / 1000
        val day = 24L * 60L * 60L
        fun image(id: String, sender: String, url: String, timestamp: Long) =
            SteamChatHistoryClassifier.item(id, sender, "[img]$url[/img]", timestamp)
        val cover: (Int) -> String = { app -> "https://cdn.cloudflare.steamstatic.com/steam/apps/$app/header.jpg" }
        listOf(
            SteamChatHistoryClassifier.item("h1", "Alex", "今晚八点开黑吗？", now - 600),
            image(
                "h2", "Sam",
                "https://avatars.fastly.steamstatic.com/54b97d0998d152f01d876d03dad1fdd2fb642dd2_full.jpg",
                now - 2400
            ),
            SteamChatHistoryClassifier.item(
                "h3", "Alex",
                "这个攻略写得挺细的 https://steamcommunity.com/sharedfiles/filedetails/?id=123456",
                now - 5400
            ),
            SteamChatHistoryClassifier.item(
                "h4", "我",
                "[url=https://cdn.example.com/mods.zip]mods.zip[/url]",
                now - 7200
            ),
            SteamChatHistoryClassifier.item(
                "h5", "小林",
                "steam://joinlobby/730/109775241234567890/76561198000000001",
                now - 9000
            ),
            SteamChatHistoryClassifier.item("h6", "明月", "/sticker 1234567890", now - 12000),
            image("h9", "Alex", cover(620), now - 15000),
            image("h10", "Sam", cover(730), now - 18000),
            image("h11", "小林", cover(570), now - 21000),
            SteamChatHistoryClassifier.item("h7", "Alex", "昨天那把翻盘太爽了", now - day - 1800),
            image("h12", "我", cover(440), now - day - 2600),
            image("h13", "明月", cover(105600), now - day - 3200),
            SteamChatHistoryClassifier.item(
                "h8", "Sam",
                "https://store.steampowered.com/app/620/Portal_2/",
                now - day - 4000
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fixtureColorScheme = intent.getStringExtra("theme")?.let { takagi.ru.monica.data.ColorScheme.valueOf(it) }
            ?: takagi.ru.monica.data.ColorScheme.DEFAULT
        fixtureDark = intent.getBooleanExtra("dark", true)
        fixtureOled = intent.getBooleanExtra("oled", false)
        fixtureHideDock = intent.getBooleanExtra("hideDock", false)
        fixtureDesignStyle = SteamDockStyle.entries.firstOrNull { it.name == intent.getStringExtra("designStyle") }
            ?: SteamDockStyle.FIXED
        route = intent.getStringExtra("route") ?: "conversations"
        rootShowFriends = intent.getBooleanExtra("rootFriends", false)
        rootSearchExpanded = intent.getBooleanExtra("rootSearch", false)
        rootQuery = intent.getStringExtra("rootQuery").orEmpty()
        rootMany = intent.getBooleanExtra("many", false)
        setContent {
            CompositionLocalProvider(LocalSteamDesignStyle provides fixtureDesignStyle) {
            MonicaTheme(darkTheme = fixtureDark, colorScheme = fixtureColorScheme, oledPureBlackEnabled = fixtureOled) {
            SteamTelegramTheme {
                val palette = MaterialTheme.colorScheme
                SideEffect { currentPalette = palette }
                when (route) {
                    "emoji" -> takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMediaPickerPanel(
                        state = takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState(),
                        onDismiss = {}, onEmojiSelected = { selectedEmoji = it }, onEmoticonSelected = {},
                        onStickerSelected = {}, onEffectSelected = {}, onRefresh = {}
                    )
                    "attachments" -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatAttachmentPickerPanel(
                            onGalleryClick = {}, onFileClick = {}, onSelected = { selectedAttachment = it }
                        )
                    }
                    "files" -> androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxWidth().height(420.dp).statusBarsPadding(),
                        factory = { context -> com.monica.telegramchat.TelegramAttachmentPickerView(context).apply {
                            setItems(listOf(
                                com.monica.telegramchat.TelegramAttachmentPickerView.Entry("content://fixture/folder", "测试文件夹", "", "", 0, 2, 0, true),
                                com.monica.telegramchat.TelegramAttachmentPickerView.Entry("content://fixture/archive", "测试附件.zip", "application/zip", "", 4096, 1, 0, false)
                            ), true, false, null)
                            setCallbacks({ selectedAttachment = it.uri }, { selectedFolder = it.uri }, {}, {}, {})
                        } }
                    )
                    "friend" -> SteamChatFriendDetailScreen(friend, false,
                        onBack = { route = "conversations" }, onStartChat = { route = "conversations" },
                        onChangeRelationship = { relationshipChanges++ })
                    "chat-info" -> SteamChatInfoScreen(
                        account = null, directFriend = heroFriend, group = null, members = emptyList(),
                        preferences = preferences, canEditGroup = false, updatingGroup = false,
                        updatingGroupAvatar = false, onBack = { route = "conversations" },
                        onAddMember = {}, onOpenFriendDetails = { lastFriend = it.steamId; route = "friend" },
                        sharedHistoryItems = fixtureHistory, onPreferencesChange = { preferences = it },
                        onUpdateGroup = { _, _ -> }, onUpdateGroupAvatar = {}
                    )
                    "group-avatar" -> TelegramSocialTheme {
                        // Composite group avatars at realistic sizes: 1/2/3/4 members plus an
                        // initial-only tile, so the disc sectors can be eyeballed.
                        Column(
                            Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf(1, 2, 3, 4, 6).forEach { count ->
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text("$count", style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.width(18.dp))
                                    listOf(40, 56, 88).forEach { size ->
                                        SteamGroupAvatarImage(
                                            url = "",
                                            members = fixtureAvatarMembers.take(count),
                                            contentDescription = null,
                                            modifier = Modifier.size(size.dp)
                                        )
                                    }
                                }
                            }
                            // A member without a picture renders its initial inside the sector.
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("A", style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(18.dp))
                                listOf(40, 56, 88).forEach { size ->
                                    SteamGroupAvatarImage(
                                        url = "",
                                        members = listOf(fixtureAvatarMembers.last()),
                                        contentDescription = null,
                                        modifier = Modifier.size(size.dp)
                                    )
                                }
                            }
                            // Square Steam portraits, the shape the app actually receives.
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("S", style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(18.dp))
                                listOf(56, 88).forEach { size ->
                                    SteamGroupAvatarImage(
                                        url = "",
                                        members = fixtureSquareAvatarMembers.take(2),
                                        contentDescription = null,
                                        modifier = Modifier.size(size.dp)
                                    )
                                    SteamGroupAvatarImage(
                                        url = "",
                                        members = fixtureSquareAvatarMembers,
                                        contentDescription = null,
                                        modifier = Modifier.size(size.dp)
                                    )
                                }
                            }
                        }
                    }
                    "chat-search" -> takagi.ru.monica.steam.friends.chat.info.ui.SteamChatHistorySearchScreen(                        items = fixtureHistory,
                        onBack = { route = "group" },
                        onOpenMessage = {},
                        initialCategory = intent.getStringExtra("historyCategory")
                            ?.let { runCatching { SteamChatHistoryCategory.valueOf(it) }.getOrNull() }
                            ?: SteamChatHistoryCategory.ALL,
                        initialGridMode = intent.getBooleanExtra("historyGrid", false),
                        initialQuery = intent.getStringExtra("historyQuery").orEmpty(),
                        initialSearchExpanded = intent.getBooleanExtra("historySearchExpanded", false)
                    )
                    "thread-jump" -> takagi.ru.monica.steam.friends.chat.ui.SteamChatThread(
                        state = SteamChatUiState(
                            accountSteamId = "fixture-self",
                            selectedPartnerSteamId = heroFriend.steamId,
                            thread = SteamChatThreadSnapshot(
                                accountSteamId = "fixture-self",
                                partnerSteamId = heroFriend.steamId,
                                messages = fixtureThreadMessages,
                                moreAvailable = true,
                                fetchedAt = 0L
                            )
                        ),
                        richMediaState = takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState(),
                        friend = heroFriend,
                        targetMessageId = jumpTargetId,
                        targetMessageNonce = 1,
                        // The fixture keeps the tint so a screenshot can prove where it landed.
                        highlightMessageId = jumpTargetId,
                        onHighlightConsumed = {},
                        onNavigateBack = {}, onOpenInfo = {}, onRefresh = {}, onLoadOlder = {},
                        onSend = {}, onRetryMessage = {}, onReact = { _, _ -> },
                        onStickerReply = { _, _ -> }, onReport = { _, _ -> },
                        onAttachmentSelected = {}, onAttachmentSpoilerChanged = {},
                        onUploadAttachment = {}, onClearAttachment = {},
                        onClearAttachmentFailure = {}, onRefreshCatalogs = {}
                    )
                    "group" -> SteamChatInfoScreen(
                        account = null, directFriend = null, group = group, members = members,
                        preferences = preferences, canEditGroup = false, updatingGroup = false,
                        updatingGroupAvatar = false, onBack = { route = "conversations" },
                        onAddMember = {}, onOpenFriendDetails = { lastFriend = it.steamId; route = "friend" },
                        sharedHistoryItems = fixtureHistory,
                        onPreferencesChange = { preferences = it },
                        onUpdateGroup = { _, _ -> }, onUpdateGroupAvatar = {}
                    )
                    // The real app floats the liquid-glass dock over the chat tab; host the route
                    // the same way so previews match what ships (clearance + backdrop + dock).
                    "conversations" -> Box(Modifier.fillMaxSize()) {
                        val dockBackdrop = rememberSteamLiquidGlassBackdrop()
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalSteamDockContentClearance provides SteamDockContentClearance
                        ) {
                            Box(
                                Modifier.fillMaxSize().steamLiquidGlassBackdropSource(
                                    backdrop = dockBackdrop,
                                    enabled = true
                                )
                            ) {
                                SteamChatRootContent(
                        standalone = !intent.getBooleanExtra("embedded", false), showFriends = rootShowFriends, addFriendOpen = false,
                        standaloneSearchQuery = rootQuery, searchExpanded = rootSearchExpanded,
                        accountSourceState = SteamAccountSourceState(accounts = listOf(SteamAccount(
                            id = 1, steamId = "fixture-self", accountName = "alex", displayName = "Alex",
                            deviceId = "", sharedSecret = "", identitySecret = null, revocationCode = null,
                            tokenGid = null, accessToken = null, refreshToken = null, steamLoginSecure = null,
                            rawSteamGuardJson = "", selected = true, sortOrder = 0, createdAt = 0, updatedAt = 0
                        )), selectedAccountId = 1),
                        friendsState = SteamFriendsUiState(snapshot = SteamFriendsSnapshot(
                            if (rootMany) members + longListMembers else members
                        )),
                        chatState = SteamChatUiState(sessions = SteamChatSessionsSnapshot("fixture-self", listOf(
                            SteamChatSession(friend.steamId, System.currentTimeMillis() / 1000, unreadCount = 2),
                            SteamChatSession(members[1].steamId, System.currentTimeMillis() / 1000 - 600, unreadCount = 0),
                            SteamChatSession(members[2].steamId, System.currentTimeMillis() / 1000 - 7_200, unreadCount = 5),
                            SteamChatSession(members[3].steamId, System.currentTimeMillis() / 1000 - 86_400, unreadCount = 0),
                            SteamChatSession(members[4].steamId, System.currentTimeMillis() / 1000 - 172_800, unreadCount = 0),
                            SteamChatSession(members[5].steamId, System.currentTimeMillis() / 1000 - 259_200, unreadCount = 1)
                        ) + if (rootMany) {
                            longListMembers.mapIndexed { i, member ->
                                SteamChatSession(
                                    member.steamId,
                                    System.currentTimeMillis() / 1000 - 400_000 - i * 3_600L,
                                    unreadCount = if (i % 3 == 0) i + 1 else 0
                                )
                            }
                        } else emptyList(), 0)),
                        groupChatState = SteamGroupChatUiState(groups = listOf(liveGroup, group)),
                        voiceState = takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState(),
                        effectiveSearchQuery = rootQuery,
                        conversationFlags = SteamConversationFlags(
                            pinnedDirectIds = setOf(friend.steamId),
                            archivedGroupIds = setOf(group.groupId),
                            archivedDirectIds = setOf(members[1].steamId)
                        ),
                        conversationActions = SteamConversationActions(
                            onTogglePin = { conversationPins++; lastConversationTarget = it.id },
                            onToggleArchive = { conversationArchives++; lastConversationTarget = it.id },
                            onDelete = { entries, _ -> conversationDeletes++; lastConversationTarget = entries.joinToString("|") { it.id } }
                        ),
                        onStandaloneSearchQueryChange = { rootQuery = it },
                        onSearchExpandedChange = { rootSearchExpanded = it; if (!it) rootQuery = "" },
                        onShowAccounts = { accountsShown++ }, onToggleFriends = { rootShowFriends = !rootShowFriends },
                        onAddFriendOpenChange = {}, onOpenOfficialAddFriend = {}, onFindFriendCandidates = {},
                        onAddFriend = {}, onRespondToInvite = { _, _ -> },
                        onOpenDirect = { lastFriend = it; route = "friend" }, onOpenGroup = { _, _ -> route = "group" },
                        onRefreshFriends = {}, onRefreshConversations = { conversationsRefreshed++ }, onCreateGroup = { groupsCreated++ },
                        onLeaveVoice = {}, onToggleVoiceMicrophone = {}, onToggleVoiceOutput = {}, onSelectVoiceAudioRoute = {},
                        onOpenSpace = { spacesOpened++ }
                                )
                            }
                        }
                        SteamLiquidGlassDockVisibility(
                            // Some cases focus on chat controls; other cases retain the host dock.
                            visible = !fixtureHideDock,
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        ) {
                            SteamLiquidGlassDock(
                                order = SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
                                selected = SteamDockTab.CHAT,
                                backdrop = dockBackdrop,
                                onSelected = { }
                            )
                        }
                    }
                    else -> TelegramSocialTheme {
                        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                            TelegramSocialTopBar(if (route == "friends") "联系人" else "Monica Steam",
                                actions = { TextButton(onClick = { route = if (route == "friends") "conversations" else "friends" }) {
                                    Text(if (route == "friends") "聊天列表" else "好友列表")
                                } })
                            var query by remember { mutableStateOf("") }
                            TelegramSocialSearch(query, { query = it }, if (route == "friends") "搜索联系人" else "搜索聊天")
                            if (route == "friends") {
                                var filter by remember { mutableStateOf(SteamFriendsFilter.ALL) }
                                SteamFriendsListContent(
                                    SteamFriendsUiState(snapshot = SteamFriendsSnapshot(members + SteamFriend(
                                        "76561198000000003", SteamFriendRelationship.REQUEST_INCOMING, personaName = "New friend"))),
                                    query = query, filter = filter, onFilterChange = { filter = it },
                                    onOpenFriend = { lastFriend = it.steamId; route = "friend" },
                                    onRespondToInvite = { _, accepted -> if (accepted) invitesAccepted++ }, onRetry = {},
                                    onAddFriend = {},
                                    modifier = Modifier.weight(1f)
                                )
                            } else SteamConversationList(
                                chatState = SteamChatUiState(sessions = SteamChatSessionsSnapshot("fixture-self",
                                    listOf(SteamChatSession(friend.steamId, System.currentTimeMillis() / 1000, unreadCount = 2)), 0)),
                                groupState = SteamGroupChatUiState(groups = listOf(group)), friends = members,
                                query = query,
                                flags = SteamConversationFlags(pinnedDirectIds = setOf(friend.steamId)),
                                onOpenDirect = { lastFriend = it; route = "friend" }, onOpenGroup = { _, _ -> route = "group" },
                                onRefresh = {}, onCreateGroup = {}, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            }
            }
        }
    }
}
