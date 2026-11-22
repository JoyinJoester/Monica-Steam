package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/** Empty-result presentation is independent of the list's selection and navigation state. */
@Composable
internal fun SteamConversationEmptyState(filter: SteamConversationFilter) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(
                when {
                    filter == SteamConversationFilter.UNREAD -> R.string.steam_chat_no_unread
                    filter == SteamConversationFilter.ARCHIVED -> R.string.steam_chat_no_archived
                    filter == SteamConversationFilter.PINNED -> R.string.steam_chat_no_pinned
                    else -> R.string.steam_chat_no_conversations
                }
            ),
            style = MaterialTheme.typography.titleMedium
        )
    }
}
