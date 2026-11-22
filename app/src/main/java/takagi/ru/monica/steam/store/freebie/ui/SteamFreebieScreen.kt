package takagi.ru.monica.steam.store.freebie.ui

import takagi.ru.monica.steam.foundation.ui.SteamRefreshActionIcon
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.SteamAccountSwitcherSheet
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieCatalog
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieFilter
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieItem
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieLoadFailure
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieOfferKind
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieProductType
import takagi.ru.monica.steam.store.freebie.domain.filtered
import takagi.ru.monica.steam.store.freebie.presentation.SteamFreebieViewModel
import takagi.ru.monica.steam.store.freebie.ui.components.SteamFreebieCard
import takagi.ru.monica.steam.store.freebie.ui.components.SteamFreebieLoadingCard
import takagi.ru.monica.ui.components.ExpressiveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamFreebieScreen(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenOfficial: (String) -> Unit,
    onAddSteamAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SteamFreebieViewModel = viewModel(
        factory = SteamFreebieViewModel.factory(LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dockClearance = LocalSteamDockContentClearance.current
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val visibleItems = remember(state.catalog, state.filter) {
        state.catalog?.filtered(state.filter).orEmpty()
    }
    var showAccounts by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.steam_freebie_title),
                searchQuery = "",
                onSearchQueryChange = {},
                isSearchExpanded = false,
                onSearchExpandedChange = {},
                modifier = Modifier.statusBarsPadding(),
                collapsedTitleEndPadding = 120.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAccounts = true },
                        enabled = state.accounts.isNotEmpty() || state.mdbxDatabases.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.SwitchAccount,
                            contentDescription = stringResource(R.string.steam_switch_account)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.load(force = true) },
                        enabled = !state.loading
                    ) {
                        SteamRefreshActionIcon(state.loading, Icons.Default.Refresh, stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        SteamExpressivePullToRefresh(
            showIndicator = false,
            refreshing = state.loading,
            onRefresh = { viewModel.load(force = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = dockClearance + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "overview") {
                    SteamFreebieOverviewCard(
                        catalog = state.catalog,
                        account = selectedAccount
                    )
                }

                if (state.catalogFromCache) {
                    item(key = "cached") { SteamFreebieCachedNotice() }
                }

                item(key = "filters") {
                    SteamFreebieFilterRow(
                        selected = state.filter,
                        onSelect = viewModel::selectFilter
                    )
                }

                if (!state.accountSourceError.isNullOrBlank()) {
                    item(key = "account_error") {
                        SteamFreebieFailureCard(
                            message = stringResource(R.string.steam_cannot_load_mdbx_accounts),
                            onRetry = viewModel::refreshAccountSource
                        )
                    }
                }

                state.failure?.let { failure ->
                    item(key = "load_failure") {
                        SteamFreebieFailureCard(
                            message = freebieLoadFailureMessage(failure),
                            onRetry = { viewModel.load(force = true) }
                        )
                    }
                }

                when {
                    state.loading && state.catalog == null -> {
                        items(3, key = { "loading_$it" }) {
                            SteamFreebieLoadingCard()
                        }
                    }
                    visibleItems.isEmpty() && state.failure == null -> {
                        item(key = "empty") {
                            SteamFreebieEmptyCard(
                                message = stringResource(
                                    if (state.catalog?.items.isNullOrEmpty()) {
                                        R.string.steam_freebie_empty
                                    } else {
                                        R.string.steam_freebie_filter_empty
                                    }
                                )
                            )
                        }
                    }
                    else -> items(visibleItems, key = SteamFreebieItem::appId) { item ->
                        val packageId = item.packageId
                        SteamFreebieCard(
                            item = item,
                            claiming = packageId != null && packageId in state.claimingPackageIds,
                            verifying = packageId != null && packageId in state.verifyingPackageIds,
                            claimResult = packageId?.let(state.claimResults::get),
                            onOpenDetail = { onOpenDetail(item.appId) },
                            onOpenOfficial = {
                                if (selectedAccount == null) showAccounts = true
                                else onOpenOfficial(item.storeUrl)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAccounts) {
        SteamAccountSwitcherSheet(
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            storageSource = state.storageSource,
            mdbxDatabases = state.mdbxDatabases,
            loading = state.accountsLoading,
            errorMessage = state.accountSourceError,
            onSelectStorageSource = viewModel::selectStorageSource,
            onSelectAccount = {
                viewModel.selectAccount(it)
                showAccounts = false
            },
            onAddAccount = onAddSteamAccount,
            onRefresh = viewModel::refreshAccountSource,
            onDismiss = { showAccounts = false }
        )
    }
}

@Composable
private fun SteamFreebieOverviewCard(
    catalog: SteamFreebieCatalog?,
    account: SteamAccount?
) {
    val items = catalog?.items.orEmpty()
    val keepCount = items.count { it.offerKind == SteamFreebieOfferKind.KEEP_FOREVER }
    val weekendCount = items.count { it.offerKind == SteamFreebieOfferKind.FREE_WEEKEND }
    val dlcCount = items.count { it.productType == SteamFreebieProductType.DLC }
    val resolvedCountry = remember(catalog?.accountCountryCode) {
        steamCountryDisplayName(catalog?.accountCountryCode)
    }
    val country = resolvedCountry.ifBlank {
        stringResource(R.string.steam_freebie_region_auto)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Redeem,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_freebie_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (account == null) {
                            stringResource(R.string.steam_freebie_guest_account)
                        } else {
                            stringResource(
                                R.string.steam_freebie_account_region,
                                account.displayName.ifBlank { account.accountName },
                                country
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (account == null) {
                Text(
                    text = stringResource(R.string.steam_freebie_guest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                SteamFreebieMetric(
                    value = keepCount,
                    label = stringResource(R.string.steam_freebie_metric_keep),
                    modifier = Modifier.weight(1f)
                )
                SteamFreebieMetric(
                    value = weekendCount,
                    label = stringResource(R.string.steam_freebie_metric_weekend),
                    modifier = Modifier.weight(1f)
                )
                SteamFreebieMetric(
                    value = dlcCount,
                    label = stringResource(R.string.steam_freebie_metric_dlc),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(R.string.steam_freebie_official_source),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun SteamFreebieMetric(
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
            maxLines = 1
        )
    }
}

@Composable
private fun SteamFreebieFilterRow(
    selected: SteamFreebieFilter,
    onSelect: (SteamFreebieFilter) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SteamFreebieFilter.entries.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                shape = SegmentedButtonDefaults.itemShape(index, SteamFreebieFilter.entries.size),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(freebieFilterLabel(filter)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SteamFreebieCachedNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.steam_freebie_cached),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamFreebieFailureCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.steam_freebie_retry))
            }
        }
    }
}

@Composable
private fun SteamFreebieEmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun freebieLoadFailureMessage(failure: SteamFreebieLoadFailure): String =
    stringResource(
        when (failure) {
            SteamFreebieLoadFailure.SESSION_REQUIRED -> R.string.steam_freebie_load_session
            SteamFreebieLoadFailure.RATE_LIMITED -> R.string.steam_freebie_load_rate_limit
            SteamFreebieLoadFailure.NETWORK -> R.string.steam_freebie_load_network
            SteamFreebieLoadFailure.INVALID_RESPONSE -> R.string.steam_freebie_load_invalid
        }
    )

private fun freebieFilterLabel(filter: SteamFreebieFilter): Int = when (filter) {
    SteamFreebieFilter.ALL -> R.string.steam_freebie_filter_all
    SteamFreebieFilter.KEEP_FOREVER -> R.string.steam_freebie_filter_keep
    SteamFreebieFilter.FREE_WEEKEND -> R.string.steam_freebie_filter_weekend
    SteamFreebieFilter.DLC -> R.string.steam_freebie_filter_dlc
}

private fun steamCountryDisplayName(countryCode: String?): String {
    val normalized = countryCode.orEmpty().trim().uppercase(Locale.ROOT)
    if (!normalized.matches(Regex("[A-Z]{2}"))) return ""
    return Locale("", normalized).getDisplayCountry(Locale.getDefault()).ifBlank { normalized }
}
