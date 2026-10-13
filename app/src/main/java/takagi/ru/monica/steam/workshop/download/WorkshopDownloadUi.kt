package takagi.ru.monica.steam.workshop.download

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.workshop.WorkshopItem
import takagi.ru.monica.steam.workshop.WorkshopImage
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding

@Composable
internal fun WallpaperTile(item: WorkshopItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = modifier.testTag("wallpaper_${item.id}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.82f)) {
            WorkshopImage(item.preview, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.86f))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = Color.White,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(Formatter.formatShortFileSize(LocalContext.current, item.size),
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
            }
        }
    }
}

@Composable
internal fun WorkshopDownloadDialog(item: WorkshopItem, handle: SteamAccountSessionHandle?,
    previous: WorkshopDownloadRecord?, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("workshop_download_preferences", 0) }
    val downloads = remember { WorkshopDownloads.get(context) }
    val scope = rememberCoroutineScope()
    var tree by rememberSaveable { mutableStateOf(prefs.getString("tree", null)) }
    var authenticated by rememberSaveable { mutableStateOf(previous?.handleKey != null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                tree = uri.toString()
                prefs.edit().putString("tree", tree).apply()
                failure = false
            } catch (_: SecurityException) { failure = true }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Default.Download, null) },
        title = { Text(stringResource(R.string.wallpaper_download_original)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.wallpaper_original_hint), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !authenticated, onClick = { authenticated = false }, enabled = !busy)
                Text(stringResource(R.string.wallpaper_anonymous))
            }
            if (handle != null) Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = authenticated, onClick = { authenticated = true }, enabled = !busy)
                Text(stringResource(R.string.wallpaper_account_download,
                    handle.account.displayName.ifBlank { handle.account.accountName }), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { folder.launch(tree?.let(Uri::parse)) }, enabled = !busy) {
                Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(if (tree == null) R.string.wallpaper_choose_folder else R.string.wallpaper_change_folder))
            }
            if (tree != null) Text(Uri.decode(tree!!.substringAfterLast('/')), maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (failure) Text(stringResource(R.string.wallpaper_error_storage), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(enabled = tree != null && !busy && (!authenticated || handle != null), onClick = {
            busy = true; failure = false
            scope.launch {
                try {
                    val selected = if (authenticated) handle else null
                    if (previous == null) downloads.enqueue(item, Uri.parse(requireNotNull(tree)), selected)
                    else downloads.retry(previous, Uri.parse(requireNotNull(tree)), selected)
                    onQueued()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failure = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.wallpaper_download_start)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.workshop_cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkshopDownloadsScreen(rows: List<WorkshopDownloadRow>, onBack: () -> Unit,
    cancel: (WorkshopDownloadRecord) -> Unit, retry: (WorkshopDownloadRecord) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var openFailed by remember { mutableStateOf(false) }
    val clearance = LocalSteamDockContentClearance.current
    Scaffold(modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(modifier = Modifier.steamWindowTopPadding(), windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.wallpaper_downloads)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.workshop_back)) } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, clearance + 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                Text(stringResource(R.string.wallpaper_downloads_hint), Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (openFailed) item { Text(stringResource(R.string.wallpaper_open_folder_failed), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            if (rows.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Download, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.wallpaper_download_empty))
                }
            }
            itemsIndexed(rows, key = { _, row -> row.record.id }) { index, row ->
                val r = row.record
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(
                    topStart = if (index == 0) 24.dp else 4.dp, topEnd = if (index == 0) 24.dp else 4.dp,
                    bottomStart = if (index == rows.lastIndex) 24.dp else 4.dp, bottomEnd = if (index == rows.lastIndex) 24.dp else 4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WorkshopImage(r.preview, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(downloadStatus(row)), style = MaterialTheme.typography.bodySmall,
                                    color = if (r.failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when {
                                row.completed -> IconButton(onClick = {
                                    try {
                                        val uri = Uri.parse(requireNotNull(r.savedRoot))
                                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                        openFailed = false
                                    } catch (_: Exception) { openFailed = true }
                                }) { Icon(Icons.Default.FolderOpen, stringResource(R.string.wallpaper_open_folder)) }
                                row.active -> IconButton(onClick = { cancel(r) }) { Icon(Icons.Default.Close, stringResource(R.string.workshop_cancel)) }
                                else -> IconButton(onClick = { retry(r) }) { Icon(Icons.Default.Refresh, stringResource(R.string.workshop_retry)) }
                            }
                        }
                        if (row.active) {
                            val done = row.work?.progress?.getLong("done", 0) ?: 0
                            val total = row.work?.progress?.getLong("total", 0) ?: 0
                            if (total > 0) {
                                LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${Formatter.formatShortFileSize(context, done)} / ${Formatter.formatShortFileSize(context, total)}",
                                    style = MaterialTheme.typography.labelSmall)
                            } else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

internal fun downloadStatus(row: WorkshopDownloadRow): Int = when {
    row.completed -> R.string.wallpaper_saved
    row.work?.state == WorkInfo.State.RUNNING -> when (row.work.progress.getString("phase")) {
        "saving" -> R.string.wallpaper_saving
        "downloading" -> R.string.wallpaper_downloading
        else -> R.string.wallpaper_resolving
    }
    row.active -> R.string.wallpaper_waiting
    row.work?.state == WorkInfo.State.CANCELLED -> R.string.wallpaper_cancelled
    else -> when (row.record.failure) {
        "access" -> R.string.wallpaper_error_access
        "unavailable" -> R.string.wallpaper_error_unavailable
        "login" -> R.string.wallpaper_error_login
        "storage" -> R.string.wallpaper_error_storage
        "invalid" -> R.string.wallpaper_error_invalid
        "network" -> R.string.wallpaper_error_network
        else -> R.string.wallpaper_interrupted
    }
}
