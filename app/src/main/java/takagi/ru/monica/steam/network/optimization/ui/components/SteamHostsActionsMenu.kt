package takagi.ru.monica.steam.network.optimization.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.password.MonicaTopActionsDropdownMenu

@Composable
internal fun SteamHostsActionsMenu(
    hasDraftContent: Boolean,
    onUseBuiltInPreset: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(
                    R.string.steam_network_optimization_more_actions
                )
            )
        }
        MonicaTopActionsDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_network_static_hosts_builtin_apply)) },
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                onClick = {
                    expanded = false
                    onUseBuiltInPreset()
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_network_optimization_import_file)) },
                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                onClick = {
                    expanded = false
                    onImport()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_network_optimization_export_file)) },
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                enabled = hasDraftContent,
                onClick = {
                    expanded = false
                    onExport()
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_network_optimization_copy)) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                enabled = hasDraftContent,
                onClick = {
                    expanded = false
                    onCopy()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_network_optimization_paste)) },
                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                onClick = {
                    expanded = false
                    onPaste()
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.steam_network_optimization_clear_draft),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                enabled = hasDraftContent,
                onClick = {
                    expanded = false
                    onClear()
                }
            )
        }
    }
}
