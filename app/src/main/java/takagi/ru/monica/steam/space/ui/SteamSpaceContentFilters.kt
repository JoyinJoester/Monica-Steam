package takagi.ru.monica.steam.space.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind

private val contentKinds = listOf(
    SteamSpaceEventKind.ACHIEVEMENT,
    SteamSpaceEventKind.GAME_ADDED,
    SteamSpaceEventKind.STATUS,
    SteamSpaceEventKind.SCREENSHOT,
    SteamSpaceEventKind.VIDEO,
    SteamSpaceEventKind.REVIEW,
    SteamSpaceEventKind.WISHLISTED,
    SteamSpaceEventKind.FIRST_PLAY,
    SteamSpaceEventKind.ANNOUNCEMENT,
    SteamSpaceEventKind.UNKNOWN
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamSpaceContentFilters(
    hiddenKinds: Set<SteamSpaceEventKind>,
    saving: Boolean,
    failed: Boolean,
    onSetKindVisible: (SteamSpaceEventKind, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.steam_space_content_settings), Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onShowAll, enabled = !saving && (hiddenKinds.isNotEmpty() || failed)) {
                    Text(stringResource(R.string.steam_space_content_show_all))
                }
            }
            if (failed) Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.steam_space_content_settings_failed),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry, enabled = !saving) { Text(stringResource(R.string.steam_space_retry)) }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("steam-space-content-kinds"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                itemsIndexed(contentKinds, key = { _, kind -> kind.name }) { index, kind ->
                    val visible = kind !in hiddenKinds
                    Surface(shape = RoundedCornerShape(
                        topStart = if (index == 0) 28.dp else 6.dp,
                        topEnd = if (index == 0) 28.dp else 6.dp,
                        bottomStart = if (index == contentKinds.lastIndex) 28.dp else 6.dp,
                        bottomEnd = if (index == contentKinds.lastIndex) 28.dp else 6.dp
                    ), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().testTag("steam-space-kind-${kind.name}")
                            .toggleable(value = visible, enabled = !saving, role = Role.Switch,
                                onValueChange = { onSetKindVisible(kind, it) })
                            .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(kind.contentLabelResource()), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = visible, onCheckedChange = null, enabled = !saving)
                        }
                    }
                }
            }
        }
    }
}

private fun SteamSpaceEventKind.contentLabelResource() = when (this) {
    SteamSpaceEventKind.ACHIEVEMENT -> R.string.steam_space_content_achievements
    SteamSpaceEventKind.GAME_ADDED -> R.string.steam_space_content_games
    SteamSpaceEventKind.STATUS -> R.string.steam_space_content_statuses
    SteamSpaceEventKind.SCREENSHOT -> R.string.steam_space_content_screenshots
    SteamSpaceEventKind.VIDEO -> R.string.steam_space_content_videos
    SteamSpaceEventKind.REVIEW -> R.string.steam_space_content_reviews
    SteamSpaceEventKind.WISHLISTED -> R.string.steam_space_content_wishlist
    SteamSpaceEventKind.FIRST_PLAY -> R.string.steam_space_content_first_play
    SteamSpaceEventKind.ANNOUNCEMENT -> R.string.steam_space_content_announcements
    SteamSpaceEventKind.UNKNOWN -> R.string.steam_space_content_other
}
