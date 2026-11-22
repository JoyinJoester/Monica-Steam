package takagi.ru.monica.steam.workshop

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance

@Composable
internal fun WorkshopPresetsPanel(
    library: WorkshopPresetLibrary, canReadSubscriptions: Boolean,
    create: () -> Unit, importCode: () -> Unit, preview: (WorkshopPreset) -> Unit,
    share: (WorkshopPreset) -> Unit, rename: (WorkshopPreset) -> Unit,
    replace: (WorkshopPreset) -> Unit, delete: (WorkshopPreset) -> Unit,
    retry: () -> Unit, modifier: Modifier = Modifier
) {
    var deleting by remember { mutableStateOf<WorkshopPreset?>(null) }
    var creating by remember { mutableStateOf(false) }
    val clearance = LocalSteamDockContentClearance.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = clearance + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.workshop_preset_count, library.items.size), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Box {
                        Button(onClick = { creating = true }, enabled = !library.loading) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.workshop_preset_create))
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = creating, onDismissRequest = { creating = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_from_subscriptions)) },
                                leadingIcon = { Icon(Icons.Default.Checklist, null) }, enabled = canReadSubscriptions && !library.loading,
                                onClick = { creating = false; create() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_from_code)) },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) }, enabled = !library.loading,
                                onClick = { creating = false; importCode() })
                        }
                    }
                }
                Text(stringResource(R.string.workshop_preset_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (library.loading) item { WorkshopLoading(stringResource(R.string.workshop_preset_loading)) }
        library.problem?.let { problem -> item {
            Column {
                Text(presetProblemText(problem), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = retry) { Text(stringResource(R.string.workshop_retry)) }
            }
        } }
        if (!library.loading && library.problem == null && library.items.isEmpty()) item {
            Text(stringResource(R.string.workshop_preset_empty), Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(library.items, key = { it.id }) { preset ->
            var menu by remember(preset.id) { mutableStateOf(false) }
            val switchLabel = stringResource(R.string.workshop_preset_switch_to, preset.name)
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.workshop_preset_manage_named, preset.name))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.workshop_share)) },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { menu = false; share(preset) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_rename)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    enabled = !library.loading, onClick = { menu = false; rename(preset) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_update)) },
                                    leadingIcon = { Icon(Icons.Default.Sync, null) },
                                    enabled = canReadSubscriptions && !library.loading, onClick = { menu = false; replace(preset) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(stringResource(R.string.workshop_preset_delete)) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error,
                                        leadingIconColor = MaterialTheme.colorScheme.error),
                                    enabled = !library.loading, onClick = { menu = false; deleting = preset })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.workshop_share_count, preset.share.itemIds.size), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = { preview(preset) }, enabled = canReadSubscriptions && !library.loading,
                            modifier = Modifier.semantics { contentDescription = switchLabel }) {
                            Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.workshop_preset_switch_short))
                        }
                    }
                }
            }
        }
    }
    deleting?.let { preset ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.workshop_preset_delete)) },
            text = { Text(stringResource(R.string.workshop_preset_delete_prompt, preset.name)) },
            confirmButton = { TextButton(onClick = { deleting = null; delete(preset) }) { Text(stringResource(R.string.workshop_preset_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.workshop_cancel)) } })
    }
}

@Composable
internal fun WorkshopPresetDraftDialog(draft: WorkshopPresetDraft, name: (String) -> Unit, save: () -> Unit,
    retry: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(stringResource(when {
            draft.replacingId == null -> R.string.workshop_preset_create
            draft.fromSubscriptions -> R.string.workshop_preset_update
            else -> R.string.workshop_preset_rename
        })) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = draft.name, onValueChange = name, label = { Text(stringResource(R.string.workshop_preset_name)) },
                placeholder = { Text(stringResource(R.string.workshop_preset_name_example)) }, singleLine = true,
                enabled = !draft.saving, modifier = Modifier.fillMaxWidth(),
                isError = draft.problem in listOf(WorkshopPresetProblem.NAME, WorkshopPresetProblem.DUPLICATE_NAME))
            if (draft.loading) WorkshopLoading(stringResource(if (draft.fromSubscriptions)
                R.string.workshop_export_progress else R.string.workshop_preset_loading, draft.count))
            else if (draft.share != null) Text(stringResource(R.string.workshop_share_count, draft.count))
            if (draft.replacingId != null && draft.fromSubscriptions) Text(stringResource(R.string.workshop_preset_update_prompt))
            draft.problem?.let { Text(presetProblemText(it), color = MaterialTheme.colorScheme.error) }
            draft.shareProblem?.let { Text(stringResource(when (it) {
                WorkshopShareProblem.EMPTY -> R.string.workshop_preset_no_items
                WorkshopShareProblem.INVALID -> R.string.workshop_share_invalid
                WorkshopShareProblem.TOO_LARGE -> R.string.workshop_share_too_large
            }), color = MaterialTheme.colorScheme.error) }
            draft.failure?.let { WorkshopError(it, retry) }
            if (draft.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = save, enabled = !draft.loading && !draft.saving && draft.share != null &&
            draft.name.isNotBlank() && draft.failure == null && draft.shareProblem == null) { Text(stringResource(R.string.workshop_preset_save)) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !draft.saving) { Text(stringResource(R.string.workshop_cancel)) } })
}

@Composable
internal fun WorkshopPresetSwitchDialog(preview: WorkshopPresetPreview, accountName: String,
    apply: () -> Unit, retry: () -> Unit, dismiss: () -> Unit) {
    val plan = preview.plan
    val matching = preview.canApply && plan?.addIds?.isEmpty() == true && plan.removeIds.isEmpty()
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.workshop_preset_switch)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(preview.preset.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.workshop_account, accountName), style = MaterialTheme.typography.labelLarge)
            if (preview.loading) WorkshopLoading(stringResource(R.string.workshop_preset_preview_loading, preview.collected))
            if (plan != null) {
                val diff = stringResource(R.string.workshop_preset_diff, plan.addIds.size, plan.removeIds.size, plan.retained)
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = diff },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WorkshopPresetStat("+${plan.addIds.size}", stringResource(R.string.workshop_preset_add),
                        MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    WorkshopPresetStat("−${plan.removeIds.size}", stringResource(R.string.workshop_preset_remove),
                        MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    WorkshopPresetStat(plan.retained.toString(), stringResource(R.string.workshop_preset_keep),
                        MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
                HorizontalDivider()
                Text(stringResource(if (matching) R.string.workshop_preset_matching else R.string.workshop_preset_switch_prompt),
                    style = MaterialTheme.typography.bodySmall)
                if (!matching) Text(stringResource(R.string.workshop_preset_dependencies), style = MaterialTheme.typography.bodySmall)
            }
            preview.failure?.let { Text(stringResource(workshopFailureResource(it)), color = MaterialTheme.colorScheme.error) }
            preview.problem?.let { Text(shareProblemText(it), color = MaterialTheme.colorScheme.error) }
            if (preview.unavailableIds.isNotEmpty()) Text(stringResource(R.string.workshop_preset_unavailable, preview.unavailableIds.size),
                color = MaterialTheme.colorScheme.error)
            if (preview.changes.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(preview.changes, key = { it.id }) { change ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(change.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(if (change.subscribe) R.string.workshop_preset_add else R.string.workshop_preset_remove) +
                            " · #${change.id}" + if (change.id in preview.unavailableIds) " · " + stringResource(R.string.workshop_unavailable_item) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (change.id in preview.unavailableIds) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = if (matching) dismiss else apply, enabled = preview.canApply) {
            Text(stringResource(if (matching) R.string.workshop_done else R.string.workshop_preset_confirm))
        } },
        dismissButton = { Row {
            if (!preview.loading && !preview.canApply) TextButton(onClick = retry) { Text(stringResource(R.string.workshop_retry)) }
            TextButton(onClick = dismiss) { Text(stringResource(R.string.workshop_cancel)) }
        } })
}

@Composable
private fun WorkshopPresetStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun presetProblemText(problem: WorkshopPresetProblem): String = stringResource(when (problem) {
    WorkshopPresetProblem.NAME -> R.string.workshop_preset_bad_name
    WorkshopPresetProblem.DUPLICATE_NAME -> R.string.workshop_preset_duplicate_name
    WorkshopPresetProblem.LIMIT -> R.string.workshop_preset_limit
    WorkshopPresetProblem.STORAGE -> R.string.workshop_preset_storage_error
    WorkshopPresetProblem.MISSING -> R.string.workshop_preset_missing
})
