package takagi.ru.monica.steam.friends.groupchat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamPopupBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSupported
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.ui.FriendAvatar

@Composable
internal fun SteamGroupChatDialogsHost(
    state: SteamGroupChatUiState,
    friends: List<SteamFriend>,
    showCreateGroup: Boolean,
    showInviteFriend: Boolean,
    initialInviteeSteamIds: Set<String> = emptySet(),
    onCreate: (String, List<String>) -> Unit,
    onInvite: (String) -> Unit,
    onDismissCreate: () -> Unit,
    onDismissInvite: () -> Unit
) {
    if (showCreateGroup) {
        SteamCreateGroupDialog(
            friends = friends,
            creating = state.creatingGroup,
            initialInviteeSteamIds = initialInviteeSteamIds,
            onCreate = onCreate,
            onDismiss = onDismissCreate
        )
    }
    if (showInviteFriend) {
        SteamInviteFriendDialog(
            friends = friends,
            onInvite = onInvite,
            onDismiss = onDismissInvite
        )
    }
}

@Composable
internal fun SteamCreateGroupDialog(
    friends: List<SteamFriend>,
    creating: Boolean,
    initialInviteeSteamIds: Set<String> = emptySet(),
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selected by remember(initialInviteeSteamIds) { mutableStateOf(initialInviteeSteamIds) }
    GroupDialogFrame(
        title = stringResource(R.string.steam_group_chat_create),
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text(stringResource(R.string.cancel)) }
            Button(
                onClick = { onCreate(name.trim(), selected.toList()) },
                enabled = name.isNotBlank() && !creating
            ) { Text(stringResource(R.string.steam_group_chat_create_action)) }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 64) name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.steam_group_chat_name)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        Text(stringResource(R.string.steam_group_chat_invitees), style = MaterialTheme.typography.titleSmall)
        FriendSearch(query, { query = it })
        FriendSelectionList(
            friends = filteredFriends(friends, query),
            selected = selected,
            modifier = Modifier.weight(1f),
            onToggle = { id -> selected = if (id in selected) selected - id else selected + id }
        )
    }
}

@Composable
internal fun SteamInviteFriendDialog(
    friends: List<SteamFriend>,
    onInvite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    GroupDialogFrame(
        title = stringResource(R.string.steam_group_chat_invite),
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        FriendSearch(query, { query = it })
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filteredFriends(friends, query), key = SteamFriend::steamId) { friend ->
                FriendRow(friend, selected = false) { onInvite(friend.steamId) }
            }
        }
    }
}

@Composable
private fun GroupDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogHeight = minOf(maxHeight, 680.dp)
            val glassSource = LocalSteamPopupBackdrop.current
            val glassActive = glassSource != null && steamGlassSupported()
            val panelShape = RoundedCornerShape(30.dp)
            val panelColor = MaterialTheme.colorScheme.surfaceContainerHigh
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .height(dialogHeight)
                    .then(if (glassActive) Modifier.steamGlassSurface(panelShape, panelColor,
                        SteamGlassRole.PANEL, glassSource) else Modifier),
                shape = panelShape,
                color = if (glassActive) androidx.compose.ui.graphics.Color.Transparent else panelColor,
                tonalElevation = if (glassActive) 0.dp else 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        content = content
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendSearch(query: String, onQueryChange: (String) -> Unit) {
    takagi.ru.monica.steam.foundation.ui.SteamListSearchField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.steam_chat_search_hint)
    )
}

@Composable
private fun FriendSelectionList(
    friends: List<SteamFriend>,
    selected: Set<String>,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(friends, key = SteamFriend::steamId) { friend ->
            FriendRow(friend, friend.steamId in selected) { onToggle(friend.steamId) }
        }
    }
}

@Composable
private fun FriendRow(friend: SteamFriend, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FriendAvatar(friend, 42)
        Text(friend.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Checkbox(selected, onCheckedChange = { onClick() })
    }
}

private fun filteredFriends(friends: List<SteamFriend>, query: String): List<SteamFriend> =
    friends.filter { query.isBlank() || it.displayName.contains(query, true) || it.steamId.contains(query) }
