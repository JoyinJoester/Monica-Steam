package takagi.ru.monica.steam.backup.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

internal data class MaFileConsentAccount(val key: String, val label: String, val detail: String)
internal data class MaFileConsentUi(
    val busy: Boolean = true,
    val canUnlock: Boolean = false,
    val authenticating: Boolean = false,
    val choosing: Boolean = false,
    val accounts: List<MaFileConsentAccount> = emptyList(),
    val selectedKey: String? = null,
    val error: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamMaFileConsentScreen(
    state: MaFileConsentUi, unlock: () -> Unit, select: (String) -> Unit,
    confirm: () -> Unit, cancel: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.steam_handoff_title)) }, navigationIcon = {
            IconButton(onClick = cancel) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
        })
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 12.dp)) {
            LazyColumn(Modifier.weight(1f).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.steam_handoff_recipient)) },
                        supportingContent = { Text(SteamMaFileShareContract.STEAM_PACKAGE, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.OpenInNew, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clip(groupShape(true, false))
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.steam_handoff_scope)) },
                        supportingContent = { Text(stringResource(R.string.steam_handoff_scope_detail)) },
                        leadingContent = { Icon(Icons.Default.Key, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clip(groupShape(false, true))
                    )
                }
                item { Text(stringResource(R.string.steam_handoff_warning), modifier = Modifier.padding(8.dp, 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.choosing) {
                    item { Text(stringResource(R.string.steam_handoff_choose), modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.titleMedium) }
                    itemsIndexed(state.accounts, key = { _, account -> account.key }) { index, account ->
                        val selected = state.selectedKey == account.key
                        ListItem(
                            headlineContent = { Text(account.label) },
                            supportingContent = { Text(account.detail) },
                            leadingContent = { Icon(Icons.Default.AccountCircle, null) },
                            trailingContent = { RadioButton(selected = selected, onClick = null, enabled = !state.busy) },
                            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clip(groupShape(index == 0, index == state.accounts.lastIndex))
                                .selectable(selected, enabled = !state.busy, role = Role.RadioButton) { select(account.key) }
                        )
                    }
                    item { Text(stringResource(R.string.steam_handoff_preview_hint), modifier = Modifier.padding(8.dp, 16.dp),
                        style = MaterialTheme.typography.bodyMedium) }
                }
                state.error?.let { message ->
                    item { Text(stringResource(message), modifier = Modifier.padding(8.dp, 16.dp), color = MaterialTheme.colorScheme.error) }
                }
            }
            Surface {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick = if (state.choosing) confirm else unlock, modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy && !state.authenticating &&
                            if (state.choosing) state.selectedKey != null else state.canUnlock) {
                        Text(stringResource(if (state.choosing) R.string.steam_handoff_confirm else R.string.steam_handoff_unlock),
                            modifier = Modifier.padding(vertical = 6.dp))
                    }
                    TextButton(onClick = cancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

private fun groupShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 28.dp else 4.dp, topEnd = if (first) 28.dp else 4.dp,
    bottomStart = if (last) 28.dp else 4.dp, bottomEnd = if (last) 28.dp else 4.dp
)
