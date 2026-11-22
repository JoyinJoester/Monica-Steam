package takagi.ru.monica.steam.friends.chat.info.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URI
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle

@Composable
internal fun ChatContentRow(item: SteamChatHistoryItem, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val icon = when (item.category) {
        SteamChatHistoryCategory.FILE -> Icons.Default.Description
        SteamChatHistoryCategory.INVITE -> Icons.Default.SportsEsports
        else -> Icons.Default.Link
    }
    // The older classifier adds a Chinese type prefix. The row already has a typed icon;
    // display the actual filename and localize only its empty/default label.
    val title = when {
        item.category == SteamChatHistoryCategory.FILE -> item.body.removePrefix("文件 · ")
            .takeUnless { it == "文件" } ?: stringResource(R.string.steam_history_files)
        item.category == SteamChatHistoryCategory.INVITE && item.body == "游戏邀请" ->
            stringResource(R.string.steam_history_invites)
        else -> item.body
    }
    val host = item.contentUrl?.let { runCatching { URI(it).host }.getOrNull() }
    Surface(color = if (liquid) androidx.compose.ui.graphics.Color.Transparent else colors.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("chat-content-row-${item.id}")
            .padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = colors.onSecondaryContainer) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                host?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text("${item.senderName} · ${chatContentTimestamp(item.timestamp)}", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Format media metadata in the active locale, including the original send time. */
internal fun chatContentTimestamp(timestamp: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
        java.util.Locale.getDefault()).format(java.util.Date(timestamp * 1000L))
