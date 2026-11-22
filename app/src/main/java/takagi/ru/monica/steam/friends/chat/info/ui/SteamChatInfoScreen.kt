package takagi.ru.monica.steam.friends.chat.info.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import takagi.ru.monica.steam.friends.chat.telegram.*
import takagi.ru.monica.steam.friends.ui.label
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.LocalSteamAvatarShape
import takagi.ru.monica.steam.friends.chat.info.data.SteamProfileBackgroundStore
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.SteamGroupAvatarImage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChannelManagement
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.friends.ui.formatSteamFriendTime
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState

@Composable
internal fun SteamChatInfoScreen(
    account: SteamAccount?,
    directFriend: SteamFriend?,
    group: SteamGroupChatSummary?,
    members: List<SteamFriend>,
    preferences: SteamChatConversationPreferences,
    canEditGroup: Boolean,
    updatingGroup: Boolean,
    updatingGroupAvatar: Boolean,
    onBack: () -> Unit,
    onAddMember: () -> Unit,
    onOpenFriendDetails: (SteamFriend) -> Unit,
    sharedHistoryItems: List<SteamChatHistoryItem> = emptyList(),
    onOpenSharedMessage: (String) -> Unit = {},
    onOpenGroupAdmin: () -> Unit = {},
    onPreferencesChange: (SteamChatConversationPreferences) -> Unit,
    onUpdateGroup: (String, String) -> Unit,
    onUpdateGroupAvatar: (String) -> Unit,
    channelActionLoading: Boolean = false,
    voiceState: SteamVoiceCallState = SteamVoiceCallState(),
    onCreateChannel: (String, Boolean) -> Unit = { _, _ -> },
    onRenameChannel: (String, String) -> Unit = { _, _ -> },
    onDeleteChannel: (String) -> Unit = {},
    onReorderChannel: (String, String?) -> Unit = { _, _ -> },
    onJoinVoiceChat: (String) -> Unit = {},
    onLeaveVoiceChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var wallpaperPickerOpen by remember { mutableStateOf(false) }
    var memberQuery by remember(group?.groupId, directFriend?.steamId) { mutableStateOf("") }
    var sharedCategory by remember(group?.groupId, directFriend?.steamId) { mutableStateOf(SteamChatHistoryCategory.MEDIA) }
    val visibleMembers = remember(members, directFriend, memberQuery) {
        (listOfNotNull(directFriend) + members).distinctBy { it.steamId }.filter {
            memberQuery.isBlank() || it.displayName.contains(memberQuery.trim(), true) ||
                it.steamId.contains(memberQuery.trim())
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let(onUpdateGroupAvatar)
    }
    val context = LocalContext.current
    val backgroundStore = remember(context) { SteamProfileBackgroundStore(context) }
    var backgroundUrl by remember(directFriend?.steamId, account?.steamId) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(directFriend?.steamId, account?.steamId) {
        // Cookies are only needed for friends-only profiles; public ones resolve anonymously.
        backgroundUrl = directFriend?.let { backgroundStore.backgroundUrl(account, it.steamId) }
    }
    val heroName = group?.name ?: directFriend?.displayName.orEmpty()
    val heroSubtitle = when {
        group != null -> "${group.activeMemberCount} 位成员"
        directFriend == null -> ""
        directFriend.personaState == SteamPersonaState.OFFLINE && directFriend.lastLogoff > 0L ->
            "最近上线于 ${formatSteamFriendTime(directFriend.lastLogoff)}"
        else -> directFriend.personaState.label()
    }
    TelegramSocialTheme {
        LazyColumn(
            modifier = modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.Top
        ) {
            profileItem("profile-hero") {
                SteamChatInfoHero(
                    title = heroName,
                    subtitle = heroSubtitle,
                    backgroundUrl = backgroundUrl,
                    avatarUrl = group?.avatarUrl ?: directFriend?.avatarUrl,
                    onBack = onBack,
                    avatar = {
                        if (group != null) {
                            HeroAvatarFrame(shape = CircleShape) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (canEditGroup && !updatingGroupAvatar) {
                                                Modifier.clickable { avatarPicker.launch("image/*") }
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SteamGroupAvatarImage(
                                        url = group.avatarUrl,
                                        members = members,
                                        contentDescription = group.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else if (directFriend != null) {
                            HeroAvatarFrame {
                                CompositionLocalProvider(
                                    LocalSteamAvatarShape provides RoundedCornerShape(18)
                                ) {
                                    FriendAvatar(directFriend, 74, showStatus = false)
                                }
                            }
                        }
                    }
                ) {
                    if (group != null && canEditGroup && !updatingGroupAvatar) {
                        HeroIconButton(
                            onClick = { avatarPicker.launch("image/*") },
                            icon = Icons.Default.PhotoCamera,
                            contentDescription = "更换群组头像"
                        )
                    }
                    if (group != null && canEditGroup) {
                        HeroIconButton(
                            onClick = { editing = true },
                            icon = Icons.Default.Edit,
                            contentDescription = "编辑群组信息"
                        )
                    }
                }
            }
            profileItem("profile-actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelegramSocialAction("消息", Icons.Default.ChatBubble) { onBack() }
                    TelegramSocialAction(
                        label = if (preferences.muted) "取消静音" else "静音",
                        icon = if (preferences.muted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff
                    ) { onPreferencesChange(preferences.copy(muted = !preferences.muted)) }
                    TelegramSocialAction(
                        label = if (group != null) "邀请成员" else "添加成员",
                        icon = Icons.Default.PersonAdd,
                        onClick = onAddMember
                    )
                }
            }
            if (group != null && group.tagline.isNotBlank()) {
                profileItem("tagline") {
                    TelegramSocialSection("群组信息") {
                        // Title first, description second — the other order read as a caption
                        // belonging to a value that had no label.
                        TelegramSocialInfoRow("群组简介", value = group.tagline, multiline = true)
                    }
                }
            }
            profileItem("settings") {
                TelegramSocialSection("聊天设置") {
                    TelegramSocialTitleRow(
                        label = stringResource(R.string.steam_chat_wallpaper_title),
                        showChevron = true,
                        onClick = { wallpaperPickerOpen = true }
                    )
                    TelegramSocialDivider(21)
                    if (group != null) {
                        TelegramSocialTitleRow("群组管理", showChevron = true, onClick = onOpenGroupAdmin)
                        TelegramSocialDivider(21)
                    }
                    ToggleRow("消息免打扰", preferences.muted) {
                        onPreferencesChange(preferences.copy(muted = it))
                    }
                    TelegramSocialDivider(21)
                    ToggleRow("置顶聊天", preferences.pinned) {
                        onPreferencesChange(preferences.copy(pinned = it))
                    }
                }
            }
            if (group != null) {
                profileItem("channels") {
                    SteamGroupChannelManagement(
                        group = group,
                        canEdit = canEditGroup,
                        actionLoading = channelActionLoading,
                        voiceState = voiceState,
                        onCreate = onCreateChannel,
                        onRename = onRenameChannel,
                        onDelete = onDeleteChannel,
                        onReorder = onReorderChannel,
                        onJoinVoice = onJoinVoiceChat,
                        onLeaveVoice = onLeaveVoiceChat
                    )
                }
            }
            if (group != null) {
                profileItem("member-search") {
                    TelegramSocialSearch(
                        query = memberQuery,
                        onQueryChange = { memberQuery = it },
                        hint = "搜索成员"
                    )
                }
                items(visibleMembers, key = SteamFriend::steamId) { friend ->
                    Column(Modifier.padding(bottom = 12.dp)) {
                        TelegramNativeContactRow(friend, friend.personaState.label(), onClick = { onOpenFriendDetails(friend) }, modifier = Modifier.padding(horizontal = 14.dp))
                        TelegramSocialDivider(70)
                    }
                }
                if (visibleMembers.isEmpty()) profileItem("no-members") {
                    Text("没有匹配的成员", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            steamSharedHistory(sharedHistoryItems, sharedCategory, { sharedCategory = it }, onOpenSharedMessage)
        }
        if (editing && group != null) {
            EditGroupDialog(
                group = group,
                saving = updatingGroup,
                onDismiss = { if (!updatingGroup) editing = false },
                onSave = { name, tagline -> onUpdateGroup(name, tagline); editing = false }
            )
        }
        if (wallpaperPickerOpen) SteamChatWallpaperPicker(
            selected = preferences.wallpaper,
            onDismiss = { wallpaperPickerOpen = false },
            onSelect = { preset -> onPreferencesChange(preferences.copy(wallpaper = preset)); wallpaperPickerOpen = false }
        )
    }
}

/** Keeps profile section spacing independent from the tight media grid below it. */
private fun LazyListScope.profileItem(key: String, content: @Composable () -> Unit) {
    item(key) { Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { content() } }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 21.dp, end = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        // Material's switch is 52x32dp; Telegram's is ~37x21dp, which is what the 16dp rows expect.
        Box(Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
            Switch(checked, onCheckedChange = null, modifier = Modifier.scale(.72f))
        }
    }
}

@Composable
private fun EditGroupDialog(
    group: SteamGroupChatSummary,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(group.groupId) { mutableStateOf(group.name) }
    var tagline by remember(group.groupId) { mutableStateOf(group.tagline) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑群组信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { if (it.length <= 64) name = it }, label = { Text("群组名称") }, singleLine = true)
                OutlinedTextField(tagline, { if (it.length <= 128) tagline = it }, label = { Text("群组简介") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), tagline.trim()) }, enabled = name.isNotBlank() && !saving) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}
