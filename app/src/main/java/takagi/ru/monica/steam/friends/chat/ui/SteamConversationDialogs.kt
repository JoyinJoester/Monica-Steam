package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/**
 * Deleting a chat asks what happens to the messages first: hide the row only, or also drop the
 * locally cached messages. "Cancel" is always one of the two explicit buttons, never a back-press.
 */
@Composable
internal fun SteamConversationDeleteDialog(
    deleting: List<SteamConversationListEntry>,
    onDismiss: () -> Unit,
    onConfirm: (deleteContent: Boolean) -> Unit
) {
    var deleteContent by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(
        if (deleting.size == 1) R.string.steam_chat_delete_title else R.string.steam_chat_delete_title_many,
        deleting.size
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (deleting.size == 1) {
                    Text(
                        deleting.first().title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                DeleteOption(
                    selected = !deleteContent,
                    title = stringResource(R.string.steam_chat_delete_keep_messages),
                    hint = stringResource(R.string.steam_chat_delete_keep_messages_hint),
                    onClick = { deleteContent = false }
                )
                DeleteOption(
                    selected = deleteContent,
                    title = stringResource(R.string.steam_chat_delete_with_messages),
                    hint = stringResource(R.string.steam_chat_delete_with_messages_hint),
                    onClick = { deleteContent = true }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteContent) }) {
                Text(
                    stringResource(R.string.steam_chat_delete),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.steam_chat_delete_cancel))
            }
        }
    )
}

@Composable
private fun DeleteOption(
    selected: Boolean,
    title: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
