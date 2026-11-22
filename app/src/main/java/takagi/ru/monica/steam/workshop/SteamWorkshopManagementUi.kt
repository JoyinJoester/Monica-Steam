package takagi.ru.monica.steam.workshop

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance

@Composable
internal fun WorkshopImportCodeDialog(onDismiss: () -> Unit, onOpen: (WorkshopShare) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<WorkshopShareProblem?>(null) }
    var loading by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workshop_import_share)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.workshop_import_hint))
            OutlinedTextField(value = text, onValueChange = { text = it.take(WorkshopShareCode.MAX_TEXT_LENGTH + 1); problem = null },
                label = { Text(stringResource(R.string.workshop_share_code)) }, modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 5, isError = problem != null, enabled = !loading,
                trailingIcon = { IconButton(onClick = {
                    text = clipboard.getText()?.text.orEmpty().take(WorkshopShareCode.MAX_TEXT_LENGTH + 1)
                    problem = null
                }, enabled = !loading) { Icon(Icons.Default.ContentPaste, stringResource(R.string.workshop_paste)) } })
            problem?.let { Text(shareProblemText(it), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = text.isNotBlank() && !loading, onClick = {
            loading = true
            scope.launch {
                try {
                    val share = withContext(Dispatchers.Default) { WorkshopShareCode.decode(text) }
                    onOpen(share)
                } catch (error: WorkshopShareCodeException) { problem = error.problem }
                finally { loading = false }
            }
        }) { Text(stringResource(R.string.workshop_open_share)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workshop_cancel)) } })
}

@Composable
internal fun WorkshopExportDialog(state: WorkshopExportState, gameName: String, onDismiss: () -> Unit, retry: () -> Unit,
    savePreset: (() -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workshop_share_title), Modifier.weight(1f))
            state.code?.let { code -> Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.workshop_share_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.workshop_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                            menu = false
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.workshop_share_message,
                                    gameName, state.count, WorkshopShareCode.link(code)))
                            }
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.workshop_share)))
                        })
                    savePreset?.let { save -> DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_save_share)) },
                        leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) }, onClick = { menu = false; save() }) }
                }
            } }
        }
    }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(gameName, style = MaterialTheme.typography.titleMedium)
            if (state.loading) WorkshopLoading(stringResource(R.string.workshop_export_progress, state.count))
            state.problem?.let { Text(shareProblemText(it), color = MaterialTheme.colorScheme.error) }
            state.failure?.let { WorkshopError(it, retry) }
            state.code?.let { code ->
                Text(stringResource(R.string.workshop_share_count, state.count))
                OutlinedTextField(value = code, onValueChange = {}, readOnly = true, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workshop_share_code)) })
                Text(stringResource(R.string.workshop_share_hint), style = MaterialTheme.typography.bodySmall)
            }
        } }, confirmButton = {
            val code = state.code
            if (code != null) Button(onClick = {
                clipboard.setText(AnnotatedString(code))
                Toast.makeText(context, R.string.workshop_copied, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.workshop_copy_code)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(if (state.loading) R.string.workshop_cancel else R.string.workshop_done)) }
        }, dismissButton = {
            if (state.code != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.workshop_done)) }
        })
}

@Composable
internal fun WorkshopBulkConfirmation(subscribe: Boolean, count: Int, accountName: String,
    onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var dependencies by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (subscribe) R.string.workshop_bulk_subscribe else R.string.workshop_bulk_unsubscribe)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.workshop_account, accountName))
            Text(stringResource(if (subscribe) R.string.workshop_bulk_subscribe_prompt else R.string.workshop_bulk_unsubscribe_prompt, count))
            if (subscribe) DependencyChoice(dependencies) { dependencies = it }
        } },
        confirmButton = { TextButton(onClick = { onConfirm(dependencies) }, enabled = count > 0) {
            Text(stringResource(if (subscribe) R.string.workshop_subscribe else R.string.workshop_unsubscribe))
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workshop_cancel)) } })
}

@Composable
internal fun WorkshopBulkDialog(state: WorkshopBulkState, stop: () -> Unit, retry: () -> Unit, dismiss: () -> Unit,
    targetLabel: String = "") {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(stringResource(when {
            state.preset != null -> R.string.workshop_preset_switch
            state.subscribe -> R.string.workshop_bulk_subscribe
            else -> R.string.workshop_bulk_unsubscribe
        })) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (targetLabel.isNotBlank()) Text(targetLabel, style = MaterialTheme.typography.labelLarge)
            state.preset?.let { preset ->
                Text(if (state.presetVerified) stringResource(R.string.workshop_preset_applied, preset.name) else preset.name,
                    style = MaterialTheme.typography.titleMedium)
                if (!state.running && !state.presetVerified) Text(stringResource(R.string.workshop_preset_incomplete),
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.workshop_bulk_progress, state.results.size, state.ids.size))
            if (state.running) {
                if (state.queued || state.phase != WorkshopBulkPhase.ITEMS) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { state.results.size.toFloat() / state.ids.size }, modifier = Modifier.fillMaxWidth())
                Text(when {
                    state.stopRequested -> stringResource(R.string.workshop_stopping)
                    state.queued -> stringResource(R.string.workshop_background_queued)
                    state.phase == WorkshopBulkPhase.CHECKING -> stringResource(R.string.workshop_preset_checking)
                    state.phase == WorkshopBulkPhase.VERIFYING -> stringResource(R.string.workshop_preset_verifying)
                    else -> state.currentTitle
                },
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.workshop_background_hint), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.workshop_bulk_summary, state.updated, state.unchanged, state.failed, state.remaining))
            state.failure?.let { Text(stringResource(workshopFailureResource(it)), color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results, key = { it.id }) { result ->
                    Column {
                        Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(when (result.outcome) {
                            WorkshopBulkOutcome.UPDATED -> if (state.preset == null) R.string.workshop_synced
                                else if (state.subscribes(result.id)) R.string.workshop_preset_added else R.string.workshop_preset_removed
                            WorkshopBulkOutcome.UNCHANGED -> R.string.workshop_bulk_unchanged
                            WorkshopBulkOutcome.UNAVAILABLE -> R.string.workshop_unavailable_item
                            WorkshopBulkOutcome.FAILED -> result.failure?.let(::workshopFailureResource) ?: R.string.workshop_unconfirmed
                        }), style = MaterialTheme.typography.bodySmall,
                            color = if (result.outcome in listOf(WorkshopBulkOutcome.FAILED, WorkshopBulkOutcome.UNAVAILABLE))
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } },
        confirmButton = {
            TextButton(onClick = dismiss) { Text(stringResource(
                if (state.running) R.string.workshop_background_continue else R.string.workshop_done)) }
        }, dismissButton = {
            if (state.running) TextButton(onClick = stop, enabled = !state.stopRequested) { Text(stringResource(R.string.workshop_stop_queue)) }
            else if (state.canRetry) TextButton(onClick = retry) { Text(stringResource(R.string.workshop_retry_remaining)) }
        })
}

@Composable
internal fun WorkshopBulkBanner(state: WorkshopBulkState, open: () -> Unit) {
    val actionLabel = stringResource(R.string.workshop_background_view)
    val needsAttention = state.failure != null || state.failed > 0 || state.remaining > 0 ||
        (state.preset != null && !state.presetVerified)
    Card(onClick = open, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .semantics { contentDescription = actionLabel }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(if (needsAttention) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                null, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(when {
                    state.preset != null && state.running -> R.string.workshop_preset_switch
                    state.running -> R.string.workshop_background_channel
                    needsAttention -> R.string.workshop_background_attention
                    else -> R.string.workshop_background_complete
                }),
                    style = MaterialTheme.typography.labelLarge)
                Text(if (state.queued) stringResource(R.string.workshop_background_queued)
                    else stringResource(R.string.workshop_bulk_progress, state.results.size, state.ids.size),
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
internal fun WorkshopImportPreview(state: WorkshopImportState, accountName: String,
    toggle: (String) -> Unit, selectAll: (Boolean) -> Unit, subscribe: (Boolean) -> Unit,
    retry: () -> Unit, open: (WorkshopItem) -> Unit, dependencies: Boolean, changeDependencies: (Boolean) -> Unit,
    listState: LazyListState, modifier: Modifier = Modifier, busy: Boolean = false,
    savePreset: (() -> Unit)? = null) {
    var menu by remember(state.share) { mutableStateOf(false) }
    val eligible = state.selectableIds
    val selected = state.selectedIds.intersect(eligible)
    val clearance = LocalSteamDockContentClearance.current
    Column(modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.workshop_import_game, state.share.appId, state.share.itemIds.size),
                        style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.workshop_account, accountName), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.workshop_import_preview_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading) item { WorkshopLoading(stringResource(R.string.workshop_import_progress,
                state.entries.count { it.loaded }, state.entries.size)) }
            state.failure?.let { failure -> item { WorkshopError(failure, retry) } }
            items(state.entries, key = { it.id }) { entry ->
                ElevatedCard(onClick = { toggle(entry.id) }, enabled = entry.selectable && !state.loading && !busy,
                    colors = CardDefaults.elevatedCardColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.fillMaxWidth().semantics {
                        role = Role.Checkbox
                        toggleableState = ToggleableState(entry.id in selected)
                    }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Checkbox(checked = entry.id in selected, onCheckedChange = null,
                            enabled = entry.selectable && !state.loading && !busy)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.item?.title ?: "#${entry.id}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall)
                            Text("#${entry.id}", style = MaterialTheme.typography.labelSmall)
                            if (entry.loaded && !entry.selectable) Text(stringResource(
                                if (entry.item?.subscribed == true) R.string.workshop_subscribed else R.string.workshop_unavailable_item),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        entry.item?.let { item -> IconButton(onClick = { open(item) }) {
                            Icon(Icons.Default.Info, stringResource(R.string.workshop_view_details))
                        } }
                    }
                }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = clearance + 12.dp),
            tonalElevation = 3.dp, shadowElevation = 4.dp, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.workshop_selected_count, selected.size), style = MaterialTheme.typography.titleSmall)
                        if (dependencies) Text(stringResource(R.string.workshop_dependencies_included),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { menu = true }) {
                            Text(stringResource(R.string.workshop_import_actions))
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.workshop_select_all)) },
                                leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                enabled = !state.loading && !busy && selected.size < eligible.size,
                                onClick = { menu = false; selectAll(true) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.workshop_clear_selection)) },
                                leadingIcon = { Icon(Icons.Default.Deselect, null) },
                                enabled = !state.loading && !busy && selected.isNotEmpty(),
                                onClick = { menu = false; selectAll(false) })
                            savePreset?.let { save -> DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_save_share)) },
                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) }, onClick = { menu = false; save() }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.workshop_with_dependencies)) }, enabled = !busy,
                                trailingIcon = { Checkbox(checked = dependencies, onCheckedChange = null, enabled = !busy) },
                                onClick = { changeDependencies(!dependencies); menu = false })
                        }
                    }
                }
                Button(onClick = { subscribe(dependencies) }, enabled = !busy && !state.loading && state.failure == null && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (selected == eligible) R.string.workshop_subscribe_all_count else R.string.workshop_subscribe_selected_count,
                        selected.size), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DependencyChoice(checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(stringResource(R.string.workshop_with_dependencies), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun shareProblemText(problem: WorkshopShareProblem): String = stringResource(when (problem) {
    WorkshopShareProblem.INVALID -> R.string.workshop_share_invalid
    WorkshopShareProblem.EMPTY -> R.string.workshop_share_empty
    WorkshopShareProblem.TOO_LARGE -> R.string.workshop_share_too_large
})
