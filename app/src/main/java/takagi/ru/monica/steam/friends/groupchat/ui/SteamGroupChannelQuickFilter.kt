package takagi.ru.monica.steam.friends.groupchat.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoomType
import takagi.ru.monica.steam.foundation.ui.SteamListChoiceChip as MonicaExpressiveFilterChip

/** Monica Android's horizontal quick-filter pattern for Steam channels. */
@Composable
internal fun SteamGroupChannelQuickFilter(
    rooms: List<SteamGroupChatRoom>,
    selectedChatId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rooms.size <= 1) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rooms
            .sortedWith(compareBy<SteamGroupChatRoom> { it.sortOrder }.thenBy { it.chatId })
            .forEach { room ->
                MonicaExpressiveFilterChip(
                    selected = room.chatId == selectedChatId,
                    onClick = { onSelect(room.chatId) },
                    label = room.name,
                    leadingIcon = room.type.icon
                )
            }
    }
}

private val SteamGroupChatRoomType.icon: ImageVector
    get() = when (this) {
        SteamGroupChatRoomType.TEXT -> Icons.Default.ChatBubbleOutline
        SteamGroupChatRoomType.VOICE -> Icons.Default.Phone
    }
