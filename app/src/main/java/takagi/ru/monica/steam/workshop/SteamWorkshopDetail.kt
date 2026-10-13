package takagi.ru.monica.steam.workshop

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance

@Composable
internal fun SteamWorkshopDetail(
    state: WorkshopUiState, subscribe: (Boolean, Boolean) -> Unit, retry: () -> Unit,
    accountName: String, onDependency: (WorkshopItem) -> Unit, modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null
) {
    val item = state.selected ?: return
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val clearance = LocalSteamDockContentClearance.current
    var confirmUnsubscribe by rememberSaveable(item.id) { mutableStateOf(false) }
    var confirmDependencies by rememberSaveable(item.id) { mutableStateOf(false) }
    val busy = item.id in state.pending || state.loadingDetail || state.bulk?.running == true
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = clearance + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { WorkshopImage(item.preview, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                if (item.author.isNotBlank()) Text(item.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.workshop_metadata, NumberFormat.getIntegerInstance().format(item.subscriptions),
                    if (item.updated > 0) DateFormat.getDateInstance().format(Date(item.updated * 1000)) else "—",
                    Formatter.formatShortFileSize(context, item.size)), style = MaterialTheme.typography.bodySmall)
                if (item.tags.isNotEmpty()) Text(item.tags.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onDownload != null && item.fileType == 0 && !item.banned) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wallpaper_download_original))
                }
                Text(stringResource(R.string.wallpaper_original_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (accountName.isBlank()) stringResource(R.string.wallpaper_anonymous)
                    else stringResource(R.string.workshop_account, accountName), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.workshop_sync_hint), style = MaterialTheme.typography.bodySmall)
                if (item.incompatible) Text(stringResource(R.string.workshop_incompatible), color = MaterialTheme.colorScheme.error)
                if (state.loadingDetail) WorkshopLoading(stringResource(R.string.workshop_loading))
                state.detailFailure?.let { WorkshopError(it, retry) }
                state.actionFailure?.let { WorkshopError(it, retry) }
                if (state.actionSucceeded) Text(stringResource(R.string.workshop_synced), color = MaterialTheme.colorScheme.primary)
                val available = item.subscribed == true || (item.canSubscribe && item.fileType == 0 && !item.banned)
                if (available) {
                    Button(onClick = {
                        if (item.subscribed == true) confirmUnsubscribe = true
                        else if (item.childCount > 0 || item.children.isNotEmpty()) confirmDependencies = true
                        else subscribe(true, false)
                    }, enabled = !busy && item.subscribed != null && state.detailFailure == null,
                        modifier = Modifier.fillMaxWidth()) {
                        if (item.id in state.pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (item.subscribed == true) R.string.workshop_unsubscribe else R.string.workshop_subscribe))
                    }
                } else if (!state.loadingDetail && state.detailFailure == null) Text(stringResource(R.string.workshop_unavailable_item))
            }
            }
        }
        if (item.previews.isNotEmpty()) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.previews.take(12).forEach { WorkshopImage(it, Modifier.width(240.dp).height(150.dp).clip(MaterialTheme.shapes.medium)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.workshop_description), style = MaterialTheme.typography.titleMedium)
                Text(item.description.ifBlank { stringResource(R.string.workshop_no_description) }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (item.childCount > 0 || item.children.isNotEmpty()) item {
            Column {
                Text(stringResource(R.string.workshop_dependencies, maxOf(item.childCount, item.children.size)), style = MaterialTheme.typography.titleMedium)
                item.children.forEach { id ->
                    val dependency = item.dependencies.firstOrNull { it.id == id }
                    ListItem(headlineContent = { Text(dependency?.title ?: "#$id") },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable {
                        if (dependency != null) onDependency(dependency)
                        else uri.openUri("https://steamcommunity.com/sharedfiles/filedetails/?id=$id")
                    })
                }
            }
        }
    }
    if (confirmUnsubscribe) AlertDialog(
        onDismissRequest = { confirmUnsubscribe = false }, title = { Text(stringResource(R.string.workshop_unsubscribe)) },
        text = { Text(stringResource(R.string.workshop_unsubscribe_prompt, item.title)) },
        confirmButton = { TextButton(onClick = { confirmUnsubscribe = false; subscribe(false, false) }) { Text(stringResource(R.string.workshop_unsubscribe)) } },
        dismissButton = { TextButton(onClick = { confirmUnsubscribe = false }) { Text(stringResource(R.string.workshop_cancel)) } }
    )
    if (confirmDependencies) AlertDialog(
        onDismissRequest = { confirmDependencies = false }, title = { Text(stringResource(R.string.workshop_dependencies, maxOf(item.childCount, item.children.size))) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workshop_dependencies_prompt))
            TextButton(onClick = { confirmDependencies = false; subscribe(true, false) }) { Text(stringResource(R.string.workshop_only_item)) }
        } },
        confirmButton = { TextButton(onClick = { confirmDependencies = false; subscribe(true, true) }) { Text(stringResource(R.string.workshop_with_dependencies)) } },
        dismissButton = { TextButton(onClick = { confirmDependencies = false }) { Text(stringResource(R.string.workshop_cancel)) } }
    )
}
