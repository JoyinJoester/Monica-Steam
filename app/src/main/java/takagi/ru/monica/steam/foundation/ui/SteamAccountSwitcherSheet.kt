package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.session.SteamTemporaryAccounts
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamAccountSwitcherSheet(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    storageSource: SteamStorageSource,
    mdbxDatabases: List<LocalMdbxDatabase>,
    loading: Boolean,
    errorMessage: String?,
    onSelectStorageSource: (SteamStorageSource) -> Unit,
    onSelectAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var endingAccount by remember { mutableStateOf<SteamAccount?>(null) }
    endingAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { endingAccount = null },
            title = { Text(stringResource(R.string.steam_temporary_logout)) },
            text = { Text(stringResource(R.string.steam_temporary_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    SteamTemporaryAccounts.shared.remove(account.id)
                    endingAccount = null
                    onDismiss()
                }) { Text(stringResource(R.string.steam_temporary_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { endingAccount = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Text(
            text = stringResource(R.string.steam_switch_account),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        if (mdbxDatabases.isNotEmpty()) {
            Text(
                text = stringResource(R.string.category_selection_menu_databases),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "local") {
                    MonicaExpressiveFilterChip(
                        selected = storageSource is SteamStorageSource.Local,
                        onClick = { onSelectStorageSource(SteamStorageSource.Local) },
                        label = stringResource(R.string.category_selection_menu_local_database),
                        leadingIcon = Icons.Default.Smartphone
                    )
                }
                items(mdbxDatabases, key = LocalMdbxDatabase::id) { database ->
                    MonicaExpressiveFilterChip(
                        selected = storageSource is SteamStorageSource.Mdbx &&
                            storageSource.databaseId == database.id,
                        onClick = {
                            onSelectStorageSource(SteamStorageSource.Mdbx(database.id))
                        },
                        label = database.name.ifBlank { "MDBX" },
                        leadingIcon = Icons.Default.Storage,
                        statusDotColor = Color(0xFF22C55E)
                    )
                }
            }
        }

        when {
            loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 28.dp))
                }
            }
            !errorMessage.isNullOrBlank() -> {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.steam_cannot_load_mdbx_accounts))
                    },
                    supportingContent = {
                        Text(
                            text = errorMessage,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            accounts.isEmpty() -> {
                Text(
                    text = stringResource(R.string.steam_store_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = accounts,
                        key = SteamAccount::id
                    ) { account ->
                        SteamSwitcherAccountCard(
                            account = account,
                            selected = account.id == selectedAccountId,
                            onClick = { onSelectAccount(account.id) },
                            onEndTemporary = { endingAccount = account }
                        )
                    }
                }
            }
        }

        SteamSwitcherAddAccountCard(
            onClick = {
                onDismiss()
                onAddAccount()
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SteamSwitcherAccountCard(
    account: SteamAccount,
    selected: Boolean,
    onClick: () -> Unit,
    onEndTemporary: () -> Unit
) {
    SteamSwitcherCard(
        headline = account.displayName.ifBlank {
            account.accountName.ifBlank { account.visibleSteamId }
        },
        supporting = listOf(
            if (account.isTemporary) stringResource(R.string.steam_temporary_login) else "",
            account.accountName, account.visibleSteamId)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · "),
        leadingContent = { SteamAvatarImage(account = account, size = 48.dp) },
        trailingContent = {
            if (account.isTemporary) {
                IconButton(onClick = onEndTemporary) {
                    Icon(Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.steam_temporary_logout))
                }
            } else if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.steam_selected_account_marker),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = onClick
    )
}

@Composable
private fun SteamSwitcherAddAccountCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SteamSwitcherCard(
        headline = stringResource(R.string.steam_add_account_title),
        supporting = stringResource(R.string.steam_add_account_switcher_summary),
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                }
            }
        },
        trailingContent = {},
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun SteamSwitcherCard(
    headline: String,
    supporting: String,
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supporting.isNotBlank()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailingContent()
        }
    }
}
