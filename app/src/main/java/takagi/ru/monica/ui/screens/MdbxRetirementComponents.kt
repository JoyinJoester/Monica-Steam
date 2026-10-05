package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun MdbxSourceActions(onOpen: () -> Unit, onCreate: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("mdbx_open")) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mdbx_manage_open))
        }
        Button(onClick = onCreate, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("mdbx_create")) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mdbx_manage_create))
        }
    }
}

@Composable
internal fun MdbxRetiredVaultPage(onMigrate: (() -> Unit)?, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).testTag("mdbx_legacy_unavailable"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.mdbx_legacy_unavailable_title)) },
            supportingContent = { Text(stringResource(R.string.mdbx_legacy_unavailable_description)) },
            leadingContent = { Icon(Icons.Default.Upgrade, null) }
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Surface(onClick = { onMigrate?.invoke() }, enabled = onMigrate != null, color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Upgrade, null)
                    Text(stringResource(R.string.mdbx_legacy_upgrade_action))
                }
            }
            Surface(onClick = onDelete, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.RemoveCircleOutline, null)
                    Text(stringResource(R.string.mdbx_delete))
                }
            }
        }
        Text(stringResource(R.string.mdbx_legacy_remote_copy_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
