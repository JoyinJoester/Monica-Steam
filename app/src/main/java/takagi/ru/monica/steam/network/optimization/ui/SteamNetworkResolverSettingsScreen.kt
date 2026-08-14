package takagi.ru.monica.steam.network.optimization.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.optimization.SteamNetworkResolverSettingsRuntime
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkResolverSettings
import takagi.ru.monica.steam.network.optimization.domain.SteamResolverInputValidator
import takagi.ru.monica.steam.network.optimization.ui.components.SteamDynamicDnsSettingsCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamResolverServerBenchmarkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNetworkResolverSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val settings by SteamNetworkResolverSettingsRuntime.settings.collectAsState()
    val dockClearance = LocalSteamDockContentClearance.current
    val scope = rememberCoroutineScope()
    var cacheCount by rememberSaveable { mutableStateOf(SteamHttpClientProvider.dynamicDnsCacheSize()) }
    var refreshing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(context) {
        SteamNetworkResolverSettingsRuntime.initialize(context)
        cacheCount = SteamHttpClientProvider.dynamicDnsCacheSize()
    }
    LaunchedEffect(settings) {
        cacheCount = SteamHttpClientProvider.dynamicDnsCacheSize()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_network_dynamic_dns_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = dockClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "dynamic_dns") {
                SteamDynamicDnsSettingsCard(
                    enabled = settings.dynamicDnsEnabled,
                    activeProviderCount = settings.activeProviders.size,
                    cacheCount = cacheCount,
                    refreshing = refreshing,
                    canEnable = settings.hasResolver,
                    onEnabledChange = {
                        SteamNetworkResolverSettingsRuntime.setDynamicDnsEnabled(
                            applicationContext,
                            it
                        )
                    },
                    onClearCache = {
                        SteamHttpClientProvider.clearDynamicDnsCache()
                        cacheCount = SteamHttpClientProvider.dynamicDnsCacheSize()
                    },
                    onForceRefresh = {
                        if (!refreshing) {
                            refreshing = true
                            scope.launch {
                                try {
                                    cacheCount = SteamHttpClientProvider.refreshDynamicDnsCache()
                                } finally {
                                    refreshing = false
                                }
                            }
                        }
                    }
                )
            }

            item(key = "resolver_servers") {
                SteamResolverServerBenchmarkCard(
                    onRemoveCustomDns = {
                        SteamNetworkResolverSettingsRuntime.removeCustomDns(
                            applicationContext,
                            it
                        )
                    },
                    onRemoveCustomDoh = {
                        SteamNetworkResolverSettingsRuntime.removeCustomDoh(
                            applicationContext,
                            it
                        )
                    }
                )
            }

            item(key = "custom_resolver") {
                CustomResolverAddCard(
                    dnsValues = settings.customDnsServers,
                    dohValues = settings.customDohEndpoints,
                    dohBootstrapValues = settings.customDohBootstrapAddresses,
                    dnsLimit = SteamNetworkResolverSettings.MAX_CUSTOM_DNS,
                    dohLimit = SteamNetworkResolverSettings.MAX_CUSTOM_DOH,
                    onAddDns = {
                        SteamNetworkResolverSettingsRuntime.addCustomDns(
                            applicationContext,
                            it
                        )
                    },
                    onAddDoh = { endpoint, bootstrapAddresses ->
                        SteamNetworkResolverSettingsRuntime.addCustomDoh(
                            applicationContext,
                            endpoint,
                            bootstrapAddresses
                        )
                    }
                )
            }

            item(key = "resolver_strategy") {
                ResolverStrategyCard(
                    preferIpv6 = settings.preferIpv6,
                    onPreferIpv6 = {
                        SteamNetworkResolverSettingsRuntime.setPreferIpv6(
                            applicationContext,
                            it
                        )
                    }
                )
            }

            item(key = "resolver_privacy") {
                ResolverPrivacyCard()
            }
        }
    }
}

@Composable
private fun CustomResolverAddCard(
    dnsValues: List<String>,
    dohValues: List<String>,
    dohBootstrapValues: Map<String, List<String>>,
    dnsLimit: Int,
    dohLimit: Int,
    onAddDns: (String) -> Boolean,
    onAddDoh: (String, String) -> Boolean
) {
    var resolverInput by rememberSaveable { mutableStateOf("") }
    var bootstrapInput by rememberSaveable { mutableStateOf("") }

    val trimmedInput = resolverInput.trim()
    val looksLikeDoh = trimmedInput.startsWith("https://", ignoreCase = true)
    val normalizedDoh = SteamResolverInputValidator.normalizeDohEndpoint(resolverInput)
    val normalizedDns = if (normalizedDoh == null && !looksLikeDoh) {
        SteamResolverInputValidator.normalizeDnsServer(resolverInput)
    } else {
        null
    }
    val normalizedBootstrap = SteamResolverInputValidator.normalizeBootstrapAddresses(bootstrapInput)
    val resolverInvalid = resolverInput.isNotBlank() && normalizedDoh == null && normalizedDns == null
    val bootstrapInvalid = bootstrapInput.isNotBlank() && normalizedBootstrap == null
    val isUpdatingDoh = normalizedDoh != null && normalizedDoh in dohValues
    val duplicate = when {
        normalizedDoh != null -> isUpdatingDoh &&
            normalizedBootstrap != null &&
            dohBootstrapValues[normalizedDoh].orEmpty() == normalizedBootstrap
        normalizedDns != null -> normalizedDns in dnsValues
        else -> false
    }
    val limitReached = when {
        normalizedDoh != null -> !isUpdatingDoh && dohValues.size >= dohLimit
        normalizedDns != null -> dnsValues.size >= dnsLimit
        else -> false
    }
    val canAdd = !resolverInvalid &&
        !bootstrapInvalid &&
        !duplicate &&
        !limitReached &&
        (normalizedDoh != null || normalizedDns != null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResolverAddCardHeader(
                dnsCount = dnsValues.size,
                dnsLimit = dnsLimit,
                dohCount = dohValues.size,
                dohLimit = dohLimit
            )

            OutlinedTextField(
                value = resolverInput,
                onValueChange = { value ->
                    resolverInput = value.take(512)
                    if (!value.trimStart().startsWith("https://", ignoreCase = true)) {
                        bootstrapInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = resolverInvalid,
                label = { Text(stringResource(R.string.steam_network_custom_dns_address_label)) },
                placeholder = {
                    Text(stringResource(R.string.steam_network_custom_dns_unified_placeholder))
                },
                supportingText = if (resolverInvalid) {
                    { Text(stringResource(R.string.steam_network_resolver_invalid)) }
                } else {
                    { Text(stringResource(R.string.steam_network_custom_dns_address_hint)) }
                }
            )

            if (looksLikeDoh) {
                OutlinedTextField(
                    value = bootstrapInput,
                    onValueChange = { bootstrapInput = it.take(512) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = bootstrapInvalid,
                    label = { Text(stringResource(R.string.steam_network_custom_dns_bootstrap_label)) },
                    placeholder = {
                        Text(stringResource(R.string.steam_network_custom_doh_bootstrap_placeholder))
                    },
                    supportingText = {
                        Text(
                            if (bootstrapInvalid) {
                                stringResource(R.string.steam_network_custom_doh_bootstrap_invalid)
                            } else {
                                stringResource(R.string.steam_network_custom_dns_bootstrap_hint)
                            }
                        )
                    }
                )
            }

            FilledTonalButton(
                onClick = {
                    val added = when {
                        normalizedDoh != null -> onAddDoh(resolverInput, bootstrapInput)
                        normalizedDns != null -> onAddDns(resolverInput)
                        else -> false
                    }
                    if (added) {
                        resolverInput = ""
                        bootstrapInput = ""
                    }
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isUpdatingDoh) Icons.Default.Dns else Icons.Default.Add,
                    contentDescription = null
                )
                Text(
                    text = stringResource(
                        if (isUpdatingDoh) {
                            R.string.steam_network_resolver_update
                        } else {
                            R.string.steam_network_resolver_add
                        }
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ResolverStrategyCard(
    preferIpv6: Boolean,
    onPreferIpv6: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ResolverToggleRow(
            title = stringResource(R.string.steam_network_ipv6_prefer),
            description = stringResource(R.string.steam_network_ipv6_prefer_description),
            checked = preferIpv6,
            onCheckedChange = onPreferIpv6
        )
    }
}

@Composable
private fun ResolverToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ResolverAddCardHeader(
    dnsCount: Int,
    dnsLimit: Int,
    dohCount: Int,
    dohLimit: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.steam_network_custom_resolver_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.steam_network_custom_dns_unified_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(
                R.string.steam_network_custom_resolver_capacity,
                dnsCount,
                dnsLimit,
                dohCount,
                dohLimit
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResolverPrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.steam_network_dynamic_dns_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
