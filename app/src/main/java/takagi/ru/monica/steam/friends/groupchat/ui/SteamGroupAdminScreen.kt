package takagi.ru.monica.steam.friends.groupchat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatAdminSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatInviteLink
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMember
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRole
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoleActions
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.ui.FriendAvatar

@Composable
internal fun SteamGroupAdminScreen(
    group: SteamGroupChatSummary,
    snapshot: SteamGroupChatAdminSnapshot?,
    friends: List<SteamFriend>,
    loading: Boolean,
    actionLoading: Boolean,
    canEdit: Boolean,
    createdInviteLink: SteamGroupChatInviteLink?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateInviteLink: (Long, String?) -> Unit,
    onDeleteInviteLink: (String) -> Unit,
    onRevokeInvite: (String) -> Unit,
    onSetBanState: (String, Boolean) -> Unit,
    onKick: (String, Int) -> Unit,
    onMute: (String, Int) -> Unit,
    onCreateRole: (String) -> Unit,
    onRenameRole: (String, String) -> Unit,
    onDeleteRole: (String) -> Unit,
    onReplaceRoleActions: (SteamGroupChatRoleActions) -> Unit,
    onAddRoleToUser: (String, String) -> Unit,
    onRemoveRoleFromUser: (String, String) -> Unit,
    onClearCreatedInviteLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val friendsById = remember(friends) { friends.associateBy(SteamFriend::steamId) }
    var showCreateLink by remember { mutableStateOf(false) }
    var showCreateRole by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<SteamGroupChatRole?>(null) }
    var editingPermissions by remember { mutableStateOf<SteamGroupChatRole?>(null) }
    var memberRoles by remember { mutableStateOf<SteamGroupChatMember?>(null) }
    var pendingMemberAction by remember { mutableStateOf<MemberAction?>(null) }

    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("群组管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(group.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh, enabled = !loading && !actionLoading) {
                Icon(Icons.Default.Refresh, "刷新")
            }
        }
        when {
            snapshot == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Security, null, Modifier.size(48.dp))
                    Text("Steam 未返回群组管理数据", Modifier.padding(top = 12.dp))
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item("invite-links") {
                    AdminSection(
                        title = "分享链接",
                        subtitle = "由 Steam 生成，可随时撤销",
                        action = if (canEdit) {
                            {
                                OutlinedButton(onClick = { showCreateLink = true }, enabled = !actionLoading) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Text("生成", Modifier.padding(start = 6.dp))
                                }
                            }
                        } else null
                    ) {
                        if (snapshot.inviteLinks.isEmpty()) EmptyAdminLine("暂无有效链接")
                        snapshot.inviteLinks.forEach { link ->
                            InviteLinkRow(
                                link = link,
                                canEdit = canEdit,
                                actionLoading = actionLoading,
                                onDelete = { onDeleteInviteLink(link.inviteCode) }
                            )
                        }
                    }
                }
                item("invites") {
                    AdminSection("已邀请账户", "尚未加入群组的 Steam 账户") {
                        if (snapshot.invites.isEmpty()) EmptyAdminLine("暂无待处理邀请")
                        snapshot.invites.forEach { invite ->
                            AccountAdminRow(
                                steamId = invite.steamId,
                                friend = friendsById[invite.steamId],
                                trailing = if (canEdit) {
                                    {
                                        TextButton(
                                            onClick = { onRevokeInvite(invite.steamId) },
                                            enabled = !actionLoading
                                        ) { Text("撤销") }
                                    }
                                } else null
                            )
                        }
                    }
                }
                item("bans") {
                    AdminSection("封禁账户", "与 Steam 群组封禁列表同步") {
                        if (snapshot.bans.isEmpty()) EmptyAdminLine("暂无封禁账户")
                        snapshot.bans.forEach { ban ->
                            AccountAdminRow(
                                steamId = ban.steamId,
                                friend = friendsById[ban.steamId],
                                subtitle = ban.reason.ifBlank { formatTimestamp(ban.bannedAt) },
                                trailing = if (canEdit) {
                                    {
                                        TextButton(
                                            onClick = { onSetBanState(ban.steamId, false) },
                                            enabled = !actionLoading
                                        ) { Text("解除") }
                                    }
                                } else null
                            )
                        }
                    }
                }
                item("roles") {
                    AdminSection(
                        title = "角色与权限",
                        subtitle = "权限字段与 Steam 官方角色模型一致",
                        action = if (canEdit) {
                            {
                                OutlinedButton(onClick = { showCreateRole = true }, enabled = !actionLoading) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Text("角色", Modifier.padding(start = 6.dp))
                                }
                            }
                        } else null
                    ) {
                        if (snapshot.roles.isEmpty()) EmptyAdminLine("暂无自定义角色")
                        snapshot.roles.forEach { role ->
                            RoleAdminRow(
                                role = role,
                                canEdit = canEdit,
                                actionLoading = actionLoading,
                                onEditPermissions = { editingPermissions = role },
                                onRename = { editingRole = role },
                                onDelete = { onDeleteRole(role.roleId) }
                            )
                        }
                    }
                }
                item("members") {
                    AdminSection("群成员", "管理角色、禁言、移出与封禁") {
                        if (snapshot.members.isEmpty()) EmptyAdminLine("Steam 未返回成员列表")
                        snapshot.members.forEach { member ->
                            MemberAdminRow(
                                member = member,
                                friend = friendsById[member.steamId],
                                canEdit = canEdit,
                                actionLoading = actionLoading,
                                onRoles = { memberRoles = member },
                                onMute = { pendingMemberAction = MemberAction(member, MemberActionType.MUTE) },
                                onKick = { pendingMemberAction = MemberAction(member, MemberActionType.KICK) },
                                onBan = { pendingMemberAction = MemberAction(member, MemberActionType.BAN) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateLink) {
        InviteDurationDialog(
            rooms = group.rooms,
            saving = actionLoading,
            onDismiss = { if (!actionLoading) showCreateLink = false },
            onCreate = { seconds, chatId ->
                onCreateInviteLink(seconds, chatId)
                showCreateLink = false
            }
        )
    }
    if (showCreateRole) {
        RoleNameDialog(
            title = "创建角色",
            initialName = "",
            saving = actionLoading,
            onDismiss = { if (!actionLoading) showCreateRole = false },
            onSave = { onCreateRole(it); showCreateRole = false }
        )
    }
    editingRole?.let { role ->
        RoleNameDialog(
            title = "重命名角色",
            initialName = role.name,
            saving = actionLoading,
            onDismiss = { if (!actionLoading) editingRole = null },
            onSave = { onRenameRole(role.roleId, it); editingRole = null }
        )
    }
    editingPermissions?.let { role ->
        RolePermissionsDialog(
            role = role,
            saving = actionLoading,
            onDismiss = { if (!actionLoading) editingPermissions = null },
            onSave = { onReplaceRoleActions(it); editingPermissions = null }
        )
    }
    memberRoles?.let { member ->
        MemberRolesDialog(
            member = member,
            roles = snapshot?.roles.orEmpty(),
            actionLoading = actionLoading,
            onDismiss = { memberRoles = null },
            onAdd = { roleId -> onAddRoleToUser(roleId, member.steamId) },
            onRemove = { roleId -> onRemoveRoleFromUser(roleId, member.steamId) }
        )
    }
    pendingMemberAction?.let { action ->
        MemberActionDialog(
            action = action,
            friend = friendsById[action.member.steamId],
            actionLoading = actionLoading,
            onDismiss = { if (!actionLoading) pendingMemberAction = null },
            onConfirm = {
                when (action.type) {
                    MemberActionType.MUTE -> onMute(action.member.steamId, ONE_HOUR_SECONDS)
                    MemberActionType.KICK -> onKick(action.member.steamId, ONE_HOUR_SECONDS)
                    MemberActionType.BAN -> onSetBanState(action.member.steamId, true)
                }
                pendingMemberAction = null
            }
        )
    }
    createdInviteLink?.let { link ->
        CreatedInviteLinkDialog(link, onClearCreatedInviteLink)
    }
}

@Composable
private fun AdminSection(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun InviteLinkRow(
    link: SteamGroupChatInviteLink,
    canEdit: Boolean,
    actionLoading: Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(link.shareUrl, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (link.expiresAt > 0L) Text(
                "有效至 ${DateFormat.getDateTimeInstance().format(Date(link.expiresAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { shareText(context, link.shareUrl) }) {
            Icon(Icons.Default.Share, "分享")
        }
        if (canEdit) IconButton(onClick = onDelete, enabled = !actionLoading) {
            Icon(Icons.Default.DeleteOutline, "删除链接")
        }
    }
}

@Composable
private fun AccountAdminRow(
    steamId: String,
    friend: SteamFriend?,
    subtitle: String = "",
    trailing: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (friend != null) FriendAvatar(friend, 42) else Surface(Modifier.size(42.dp), CircleShape) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(friend?.displayName ?: steamId, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun RoleAdminRow(
    role: SteamGroupChatRole,
    canEdit: Boolean,
    actionLoading: Boolean,
    onEditPermissions: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(role.name)
            Text("顺序 ${role.ordinal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canEdit) {
            TextButton(onClick = onEditPermissions, enabled = !actionLoading) { Text("权限") }
            IconButton(onClick = onRename, enabled = !actionLoading) { Icon(Icons.Default.Edit, "重命名") }
            IconButton(onClick = onDelete, enabled = !actionLoading) { Icon(Icons.Default.DeleteOutline, "删除") }
        }
    }
}

@Composable
private fun MemberAdminRow(
    member: SteamGroupChatMember,
    friend: SteamFriend?,
    canEdit: Boolean,
    actionLoading: Boolean,
    onRoles: () -> Unit,
    onMute: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    Column {
        AccountAdminRow(
            steamId = member.steamId,
            friend = friend,
            subtitle = "等级 ${member.rank} · ${member.roleIds.size} 个角色"
        )
        if (canEdit) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRoles, enabled = !actionLoading) { Text("角色") }
            TextButton(onClick = onMute, enabled = !actionLoading) { Text("禁言") }
            TextButton(onClick = onKick, enabled = !actionLoading) { Text("移出") }
            TextButton(onClick = onBan, enabled = !actionLoading) { Text("封禁") }
        }
    }
}

@Composable
private fun InviteDurationDialog(
    rooms: List<takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (Long, String?) -> Unit
) {
    val options = listOf(86_400L to "1 天", 604_800L to "7 天", 2_592_000L to "30 天", 0L to "永久")
    var duration by remember { mutableStateOf(604_800L) }
    var chatId by remember(rooms) { mutableStateOf(rooms.firstOrNull()?.chatId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成邀请链接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { duration = option.first }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = duration == option.first, onClick = { duration = option.first })
                        Text(option.second)
                    }
                }
                if (rooms.size > 1) {
                    Text("进入频道", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    rooms.forEach { room ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chatId = room.chatId }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = chatId == room.chatId, onClick = { chatId = room.chatId })
                            Text(room.name)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(duration, chatId) }, enabled = !saving) { Text("生成") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun RoleNameDialog(
    title: String,
    initialName: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { if (it.length <= 64) name = it }, label = { Text("角色名称") }) },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && !saving) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun RolePermissionsDialog(
    role: SteamGroupChatRole,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (SteamGroupChatRoleActions) -> Unit
) {
    var actions by remember(role.roleId, role.actions) {
        mutableStateOf(role.actions ?: SteamGroupChatRoleActions(role.roleId))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${role.name} · 权限") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { PermissionToggle("管理频道", actions.canCreateRenameDeleteChannel) { actions = actions.copy(canCreateRenameDeleteChannel = it) } }
                item { PermissionToggle("移出成员", actions.canKick) { actions = actions.copy(canKick = it) } }
                item { PermissionToggle("封禁成员", actions.canBan) { actions = actions.copy(canBan = it) } }
                item { PermissionToggle("邀请成员", actions.canInvite) { actions = actions.copy(canInvite = it) } }
                item { PermissionToggle("修改群组信息", actions.canChangeGroupMetadata) { actions = actions.copy(canChangeGroupMetadata = it) } }
                item { PermissionToggle("发送消息", actions.canChat) { actions = actions.copy(canChat = it) } }
                item { PermissionToggle("查看历史", actions.canViewHistory) { actions = actions.copy(canViewHistory = it) } }
                item { PermissionToggle("管理角色", actions.canChangeGroupRoles) { actions = actions.copy(canChangeGroupRoles = it) } }
                item { PermissionToggle("分配角色", actions.canChangeUserRoles) { actions = actions.copy(canChangeUserRoles = it) } }
                item { PermissionToggle("@全体成员", actions.canMentionAll) { actions = actions.copy(canMentionAll = it) } }
            }
        },
        confirmButton = { Button(onClick = { onSave(actions) }, enabled = !saving) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun PermissionToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MemberRolesDialog(
    member: SteamGroupChatMember,
    roles: List<SteamGroupChatRole>,
    actionLoading: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var selected by remember(member.steamId, member.roleIds) { mutableStateOf(member.roleIds.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("成员角色") },
        text = {
            Column {
                roles.forEach { role ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !actionLoading) {
                            if (role.roleId in selected) {
                                selected -= role.roleId
                                onRemove(role.roleId)
                            } else {
                                selected += role.roleId
                                onAdd(role.roleId)
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = role.roleId in selected, onCheckedChange = null, enabled = !actionLoading)
                        Text(role.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun MemberActionDialog(
    action: MemberAction,
    friend: SteamFriend?,
    actionLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val label = when (action.type) {
        MemberActionType.MUTE -> "禁言 1 小时"
        MemberActionType.KICK -> "移出 1 小时"
        MemberActionType.BAN -> "永久封禁"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = { Text("账户：${friend?.displayName ?: action.member.steamId}") },
        confirmButton = { Button(onClick = onConfirm, enabled = !actionLoading) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !actionLoading) { Text("取消") } }
    )
}

@Composable
private fun CreatedInviteLinkDialog(link: SteamGroupChatInviteLink, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("邀请链接已生成") },
        text = { Text(link.shareUrl) },
        confirmButton = {
            Button(onClick = { shareText(context, link.shareUrl); onDismiss() }) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Text("分享", Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun EmptyAdminLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatTimestamp(timestamp: Long): String = if (timestamp > 0L) {
    DateFormat.getDateTimeInstance().format(Date(timestamp * 1_000L))
} else ""

private fun shareText(context: android.content.Context, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            null
        )
    )
}

private data class MemberAction(val member: SteamGroupChatMember, val type: MemberActionType)
private enum class MemberActionType { MUTE, KICK, BAN }
private const val ONE_HOUR_SECONDS = 3_600
