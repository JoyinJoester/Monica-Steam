package takagi.ru.monica.steam.foundation.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.*

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassAlertDialog as AlertDialog

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import takagi.ru.monica.steam.foundation.ui.SteamListChoiceChip as MonicaExpressiveFilterChip

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
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .86f)) {
            SteamSheetHeader(stringResource(R.string.steam_switch_account), onDismiss)

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
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = accounts,
                            key = { _, account -> account.id }
                        ) { index, account ->
                            SteamSwitcherAccountCard(
                                account = account,
                                shape = steamSheetRowShape(index, accounts.size),
                                selected = account.id == selectedAccountId,
                                onClick = { onSelectAccount(account.id) },
                                onEndTemporary = { endingAccount = account }
                            )
                        }
                    }
                }
            }

            SteamSheetFooter {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { onDismiss(); onAddAccount() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Text(stringResource(R.string.steam_add_account_title), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SteamSwitcherAccountCard(
    account: SteamAccount,
    shape: androidx.compose.ui.graphics.Shape,
    selected: Boolean,
    onClick: () -> Unit,
    onEndTemporary: () -> Unit
) {
    SteamSwitcherCard(
        selected = selected, shape = shape,
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
            }
            if (selected) {
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
private fun SteamSwitcherCard(
    headline: String,
    supporting: String,
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 80.dp).semantics { this.selected = selected },
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else steamSheetGroupColor(),
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (supporting.isNotBlank()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailingContent()
        }
    }
}
