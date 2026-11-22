package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.design.telegram.SteamTelegramMenuAction
import takagi.ru.monica.steam.design.telegram.SteamTelegramMenu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.telegram.TelegramNativeConversationRow
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.rememberSteamGroupAvatarBitmap

/**
 * One inbox row. Tapping opens the chat, long pressing opens the per-chat menu (pin / archive /
 * delete) and swiping right starts a multi-selection, which is why the row keeps its native
 * Telegram cell: the menu only has to add a long-click listener next to the existing one.
 */
@Composable
internal fun SteamConversationRow(
    entry: SteamConversationListEntry,
    selectionMode: Boolean,
    selected: Boolean,
    actions: SteamConversationActions,
    onToggleSelected: () -> Unit,
    onSwipeSelect: () -> Unit,
    onRequestDelete: (List<SteamConversationListEntry>) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(selectionMode) { if (selectionMode) menuOpen = false }
    // The session bridge carries no message text, so fall back to the profile name and only claim
    // "no messages" when the chat really has no timestamp to show.
    val noMessages = stringResource(R.string.steam_chat_no_messages)
    val subtitle = entry.rowSubtitle()
        .ifBlank { entry.friend?.realName.orEmpty() }
        .ifBlank { if (entry.timestamp == 0L) noMessages else "" }
    // Groups without a picture get a composite disc, baked into one bitmap so the native cell
    // draws it in its own avatar slot — no overlay, so it can never sit off the frame.
    val groupAvatar = if (entry.type == SteamConversationType.GROUP && entry.avatarUrl.isBlank()) {
        rememberSteamGroupAvatarBitmap(
            members = entry.groupMembers,
            emptyLabel = entry.title,
            sizeDp = 56.dp
        )
    } else {
        null
    }
    // Selection replaces the picture the cell draws instead of covering it: a Compose overlay is
    // subject to the row's own padding, so it can end up smaller than the 56dp slot and leave the
    // original avatar visible around its edge.
    val selectionAvatar = if (selectionMode && selected) {
        rememberSteamSelectionAvatarBitmap(56.dp)
    } else {
        null
    }
    SteamConversationSwipeActions(
        onSwipeLeft = { onRequestDelete(listOf(entry)) },
        onSwipeRight = onSwipeSelect,
        allowSwipeLeft = !selectionMode,
        modifier = modifier
    ) {
        Box {
            if (selected) {
                Box(
                    Modifier.matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                )
            }
            // The avatar becomes the checkmark inside the same moving foreground as the cell.
            TelegramNativeConversationRow(
                title = entry.title,
                subtitle = subtitle,
                timestamp = if (entry.timestamp > 0) conversationTimestamp(entry.timestamp) else "",
                unreadCount = entry.unreadCount,
                pinned = entry.pinned,
                avatarUrl = entry.friend?.avatarUrl ?: entry.avatarUrl,
                onClick = { if (selectionMode) onToggleSelected() else onClick() },
                onLongClick = { position ->
                    if (selectionMode) onToggleSelected()
                    else { menuPosition = position; menuOpen = true }
                },
                avatarOverride = selectionAvatar ?: groupAvatar
            )
            ConversationMenu(
                entry = entry,
                expanded = menuOpen && !selectionMode,
                touchPosition = menuPosition,
                onDismiss = { menuOpen = false },
                onTogglePin = {
                    menuOpen = false
                    actions.onTogglePin(entry)
                },
                onToggleArchive = {
                    menuOpen = false
                    actions.onToggleArchive(entry)
                },
                onDelete = {
                    menuOpen = false
                    onRequestDelete(listOf(entry))
                }
            )
        }
    }
}

@Composable
private fun ConversationMenu(
    entry: SteamConversationListEntry,
    expanded: Boolean,
    touchPosition: Offset?,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    if (!expanded) return
    val density = LocalDensity.current
    val edge = with(density) { 12.dp.roundToPx() }
    val gap = with(density) { 8.dp.roundToPx() }
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
    val provider = remember(touchPosition, edge, gap, topInset, bottomInset) {
        SteamConversationMenuPositionProvider(
            touchPosition?.let { IntOffset(it.x.toInt(), it.y.toInt()) }, edge, gap, topInset, bottomInset
        )
    }
    SteamTelegramMenu(
        actions = listOf(
            SteamTelegramMenuAction(
                stringResource(if (entry.pinned) R.string.steam_chat_unpin else R.string.steam_chat_pin),
                com.monica.telegramchat.R.drawable.telegram_msg_pin,
                onClick = onTogglePin
            ),
            SteamTelegramMenuAction(
                stringResource(if (entry.archived) R.string.steam_chat_unarchive else R.string.steam_chat_archive),
                onClick = onToggleArchive
            ),
            SteamTelegramMenuAction(stringResource(R.string.steam_chat_delete),
                com.monica.telegramchat.R.drawable.telegram_msg_delete,
                destructive = true, onClick = onDelete)
        ),
        onDismiss = onDismiss, positionProvider = provider
    )
}

private fun SteamConversationListEntry.rowSubtitle(): String = if (voiceActive) {
    "语音通话中" + (if (voiceMemberCount > 0) " · ${voiceMemberCount} 人" else "") + "\n" + subtitle
} else {
    subtitle
}

private fun conversationTimestamp(timestamp: Long): String {
    val message = Calendar.getInstance().apply { timeInMillis = timestamp * 1_000L }
    val now = Calendar.getInstance()
    val today = message.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        message.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return (if (today) DateFormat.getTimeInstance(DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.SHORT))
        .format(message.time)
}
