package takagi.ru.monica.steam.friends.chat.richmedia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Matches Monica's version-and-links dialog: destination pills and connected utility rows. */
@Composable
internal fun SteamChatGameActions(
    primary: SteamChatCardAction?,
    actions: List<SteamChatCardAction>,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = listOfNotNull(primary) + actions.filter { it.prominent }
    val utilities = actions.filterNot { it.prominent }
    fun run(action: SteamChatCardAction) { onAction(); action.run() }
    Column(modifier.testTag("steam-game-card-actions"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (destinations.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Use the same responsive boundary as VersionInfoDialog.
            if (destinations.size == 2 && maxWidth >= 276.dp && LocalDensity.current.fontScale < 1.3f) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    destinations.forEach { action ->
                        GameDestination(action, { run(action) }, Modifier.weight(1f))
                    }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                destinations.forEach { action ->
                    GameDestination(action, { run(action) }, Modifier.fillMaxWidth())
                }
            }
        }
        if (utilities.isNotEmpty()) Column(Modifier.testTag("steam-game-card-utilities"),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            utilities.forEachIndexed { index, action ->
                val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Surface(onClick = { run(action) },
                    shape = RoundedCornerShape(
                        topStart = if (index == 0) 16.dp else 4.dp,
                        topEnd = if (index == 0) 16.dp else 4.dp,
                        bottomStart = if (index == utilities.lastIndex) 16.dp else 4.dp,
                        bottomEnd = if (index == utilities.lastIndex) 16.dp else 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = color) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(action.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        action.icon?.let { Icon(it, null, Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDestination(action: SteamChatCardAction, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick, modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        action.icon?.let {
            Icon(it, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(action.label)
    }
}
