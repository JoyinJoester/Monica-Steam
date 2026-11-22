package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassFloatingActionButton as FloatingActionButton
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassSelectionRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionActionBarAction
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle

/** Every inbox filter, including unread, belongs to one mutually exclusive selection. */
@Composable
internal fun SteamConversationFilters(
    selected: SteamConversationFilter,
    onSelect: (SteamConversationFilter) -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0
) {
    takagi.ru.monica.steam.foundation.ui.SteamListFilterRow(
        SteamConversationFilter.entries, selected, onSelect,
        label = { stringResource(it.labelRes()) }, modifier = modifier,
        count = { if (it == SteamConversationFilter.UNREAD) unreadCount else 0 },
        floatingSelection = true)
}

internal fun SteamConversationFilter.labelRes(): Int = when (this) {
    SteamConversationFilter.ALL -> R.string.steam_chat_filter_all
    SteamConversationFilter.UNREAD -> R.string.steam_chat_unread
    SteamConversationFilter.DIRECT -> R.string.steam_chat_filter_direct
    SteamConversationFilter.GROUP -> R.string.steam_chat_filter_group
    SteamConversationFilter.PINNED -> R.string.steam_chat_filter_pinned
    SteamConversationFilter.ARCHIVED -> R.string.steam_chat_filter_archived
}

/** The compose action floats above the dock; unread stays with the grouped filters. */
@Composable
internal fun SteamInboxActionsBar(
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        FloatingActionButton(
            onClick = onNewChat,
            shape = if (LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS) CircleShape
                else RoundedCornerShape(16.dp),
            modifier = Modifier.size(56.dp)
        ) { Icon(Icons.Default.Edit, stringResource(R.string.steam_chat_new_chat)) }
    }
}

/** Uses the token list's floating selection controls without moving the filters or rows. */
@Composable
internal fun SteamConversationSelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = count > 0
    val selectionDescription = stringResource(R.string.steam_chat_selection_count, count)
    SelectionActionBar(
        modifier = modifier.semantics { contentDescription = selectionDescription },
        selectedCount = count,
        onExit = onCancel,
        onSelectAll = onSelectAll,
        exitContentDescription = stringResource(R.string.steam_chat_selection_cancel),
        actions = listOf(
            SelectionActionBarAction(
                icon = Icons.Default.PushPin,
                contentDescription = stringResource(R.string.steam_chat_pin),
                enabled = enabled,
                onClick = onTogglePin
            ),
            SelectionActionBarAction(
                icon = Icons.Default.Archive,
                contentDescription = stringResource(R.string.steam_chat_archive),
                enabled = enabled,
                onClick = onToggleArchive
            ),
            SelectionActionBarAction(
                icon = Icons.Default.DeleteOutline,
                contentDescription = stringResource(R.string.steam_chat_delete),
                enabled = enabled,
                onClick = onDelete
            )
        )
    )
}
