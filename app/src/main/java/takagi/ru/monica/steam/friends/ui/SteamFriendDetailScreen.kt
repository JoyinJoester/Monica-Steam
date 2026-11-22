package takagi.ru.monica.steam.friends.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import takagi.ru.monica.steam.friends.chat.telegram.*
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.token.identity.ui.SteamIdentityInfoCard
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun SteamFriendDetailScreen(
    friend: SteamFriend,
    actionInProgress: Boolean,
    onStartChat: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
    onChangeRelationship: (SteamFriendRelationshipAction) -> Unit
) {
    val context = LocalContext.current
    val dockContentClearance = LocalSteamDockContentClearance.current
    var pendingAction by remember(friend.steamId) {
        mutableStateOf<SteamFriendRelationshipAction?>(null)
    }
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.steam_friend_confirm_action)) },
            text = {
                Text(stringResource(R.string.steam_friend_confirm_action_summary, friend.displayName))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    onChangeRelationship(action)
                }) { Text(action.label(friend.relationship)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    TelegramSocialTheme {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = 8.dp,
            bottom = dockContentClearance + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "friend-detail-hero") { FriendDetailHero(friend) }
        item(key = "friend-detail-chat") {
            val clipboard = LocalClipboardManager.current
            TelegramNativeProfileActions(
                message = stringResource(R.string.steam_chat_send_message), profile = "Steam 主页", copy = "复制 ID",
                onMessage = onStartChat,
                onProfile = { onOpenProfile?.invoke() ?: openSteamProfile(context, friend) },
                onCopy = { clipboard.setText(AnnotatedString(friend.steamId)) }
            )
        }
        item(key = "friend-detail-information") {
            TelegramSocialSection(stringResource(R.string.steam_friend_profile_information)) {
                if (friend.isPlaying) {
                    TelegramSocialInfoRow(
                        friend.gameName.ifBlank { friend.gameId },
                        stringResource(R.string.steam_friend_current_game), Icons.Default.SportsEsports
                    )
                    TelegramSocialDivider(20)
                }
                val clipboard = LocalClipboardManager.current
                TelegramSocialInfoRow(friend.steamId, "Steam ID", Icons.Default.ContentCopy,
                    onClick = { clipboard.setText(AnnotatedString(friend.steamId)) })
                SteamIdentityInfoCard(steamId64 = friend.steamId, modifier = Modifier.padding(horizontal = 20.dp), embedded = true)
                if (friend.friendSince > 0L) {
                    TelegramSocialDivider(20)
                    TelegramSocialInfoRow(formatSteamFriendTime(friend.friendSince),
                        stringResource(R.string.steam_friend_friends_since), Icons.Default.Groups)
                }
                TelegramSocialDivider(20)
                TelegramSocialInfoRow(
                    if (friend.lastLogoff > 0L) formatSteamFriendTime(friend.lastLogoff)
                    else stringResource(R.string.steam_friend_unknown_time),
                    stringResource(R.string.steam_friend_last_online), Icons.Default.Schedule
                )
                if (friend.countryCode.isNotBlank()) {
                    TelegramSocialDivider(20)
                    TelegramSocialInfoRow(friend.countryCode,
                        stringResource(R.string.steam_friend_location), Icons.Default.Person)
                }
            }
        }
        item(key = "friend-detail-links") {
            TelegramSocialSection(stringResource(R.string.steam_friend_open_profile)) {
                if (onOpenProfile != null) {
                    TelegramSocialInfoRow(stringResource(R.string.steam_profile_open_game_profile),
                        icon = Icons.Default.Person, onClick = onOpenProfile)
                    TelegramSocialDivider(20)
                }
                TelegramSocialInfoRow(stringResource(R.string.steam_friend_open_profile),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openSteamProfile(context, friend) })
            }
        }
        relationshipActions(friend.relationship).forEach { action ->
            item(key = "friend-detail-action-${action.name}") {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(16.dp)) {
                    TelegramSocialInfoRow(
                        action.label(friend.relationship), enabled = !actionInProgress,
                        onClick = { pendingAction = action }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun SteamFriendRelationshipAction.label(
    relationship: SteamFriendRelationship
): String = stringResource(
    when (this) {
        SteamFriendRelationshipAction.ADD -> R.string.steam_friend_accept
        SteamFriendRelationshipAction.REMOVE -> if (
            relationship == SteamFriendRelationship.REQUEST_OUTGOING
        ) R.string.steam_friend_cancel_request else R.string.steam_friend_remove
        SteamFriendRelationshipAction.BLOCK -> R.string.steam_friend_block
        SteamFriendRelationshipAction.UNBLOCK -> R.string.steam_friend_unblock
    }
)

private fun relationshipActions(
    relationship: SteamFriendRelationship
): List<SteamFriendRelationshipAction> = when (relationship) {
    SteamFriendRelationship.FRIEND -> listOf(
        SteamFriendRelationshipAction.REMOVE,
        SteamFriendRelationshipAction.BLOCK
    )
    SteamFriendRelationship.REQUEST_OUTGOING -> listOf(SteamFriendRelationshipAction.REMOVE)
    SteamFriendRelationship.BLOCKED -> listOf(SteamFriendRelationshipAction.UNBLOCK)
    SteamFriendRelationship.UNKNOWN -> listOf(SteamFriendRelationshipAction.ADD)
    SteamFriendRelationship.REQUEST_INCOMING -> emptyList()
}

@Composable
private fun FriendDetailHero(friend: SteamFriend) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelegramFriendAvatar(friend = friend, size = 110)
            Spacer(Modifier.size(6.dp))
            Text(
                text = friend.displayName,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            friend.realName.takeIf(String::isNotBlank)?.let { realName ->
                Text(
                    text = realName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(friend.personaState.label(), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
