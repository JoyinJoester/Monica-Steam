package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding

@Composable
internal fun SteamChatSearchBar(
    query: String,
    showingFriends: Boolean,
    accountEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleFriends: () -> Unit,
    onShowAccounts: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().steamWindowTopPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFriends) {
                Icon(
                    if (showingFriends) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Groups,
                    stringResource(if (showingFriends) R.string.back else R.string.steam_friends_title)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).testTag("chat_search"),
                decorationBox = { input ->
                    Box(Modifier.padding(vertical = 14.dp)) {
                        if (query.isEmpty()) Text(
                            stringResource(R.string.steam_chat_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        input()
                    }
                }
            )
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Close, stringResource(R.string.steam_chat_clear_search))
            }
            IconButton(onClick = onShowAccounts, enabled = accountEnabled) {
                Icon(Icons.Default.SwitchAccount, stringResource(R.string.steam_switch_account))
            }
        }
    }
}
