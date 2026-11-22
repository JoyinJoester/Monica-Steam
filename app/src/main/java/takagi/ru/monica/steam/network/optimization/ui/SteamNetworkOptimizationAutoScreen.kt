package takagi.ru.monica.steam.network.optimization.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassTopAppBar as TopAppBar
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.optimization.SteamNetworkOptimizationRuntime
import takagi.ru.monica.steam.network.optimization.SteamNetworkResolverSettingsRuntime
import takagi.ru.monica.steam.network.optimization.domain.SteamAutoHostsFormatter
import takagi.ru.monica.steam.network.optimization.ui.components.SteamDynamicResolverEntryCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkAdvancedSettingsCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkAutomaticScanCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkCurrentSelectionCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkScopeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNetworkOptimizationAutoScreen(
    onNavigateBack: () -> Unit,
    onOpenResolvers: () -> Unit,
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val optimizationViewModel: SteamNetworkOptimizationViewModel = viewModel {
        SteamNetworkOptimizationViewModel()
    }
    val dockClearance = LocalSteamDockContentClearance.current
    val settings by SteamNetworkOptimizationRuntime.settings.collectAsState()
    val resolverSettings by SteamNetworkResolverSettingsRuntime.settings.collectAsState()
    val scanState by optimizationViewModel.scanState.collectAsState()
    val summary = remember(settings.hostsText, settings.enabled) {
        if (settings.enabled) SteamAutoHostsFormatter.summary(settings.hostsText) else null
    }
    val existingRoutes = remember(settings.hostsText, settings.enabled) {
        if (settings.enabled) SteamAutoHostsFormatter.routes(settings.hostsText) else emptyList()
    }

    LaunchedEffect(context) {
        SteamNetworkOptimizationRuntime.initialize(context)
        SteamNetworkResolverSettingsRuntime.initialize(context)
    }
    val activeProviders = resolverSettings.activeProviders

    val selectedRoutes = (scanState as? SteamAutoOptimizationUiState.Success)
        ?.result
        ?.selectedRoutes
        .orEmpty()
    val showingScanResult = (scanState as? SteamAutoOptimizationUiState.Success)
        ?.applied == false
    val missingHostnames = (scanState as? SteamAutoOptimizationUiState.Success)
        ?.result
        ?.missingHostnames
        ?: summary?.missingHostnames.orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_network_auto_card_title)) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = dockClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "dynamic_dns") {
                SteamDynamicResolverEntryCard(
                    enabled = resolverSettings.dynamicDnsEnabled,
                    activeProviders = activeProviders,
                    onClick = onOpenResolvers
                )
            }
            item(key = "static_hosts_scan") {
                SteamNetworkAutomaticScanCard(
                    state = scanState,
                    summary = summary,
                    enabled = settings.enabled,
                    canScan = activeProviders.isNotEmpty(),
                    onScan = {
                        optimizationViewModel.startScan(existingRoutes, activeProviders)
                    },
                    onApply = {
                        optimizationViewModel.applyScannedOptimization { result ->
                            SteamNetworkOptimizationRuntime.applyAutoOptimization(
                                applicationContext,
                                result
                            )
                        }
                    },
                    onDisable = {
                        optimizationViewModel.cancelScan()
                        SteamNetworkOptimizationRuntime.setEnabled(applicationContext, false)
                    }
                )
            }
            item(key = "static_hosts_selection") {
                SteamNetworkCurrentSelectionCard(
                    summary = summary.takeUnless { showingScanResult },
                    routes = selectedRoutes,
                    showingScanResult = showingScanResult,
                    missingHostnames = missingHostnames
                )
            }
            item(key = "advanced_hosts") {
                SteamNetworkAdvancedSettingsCard(onClick = onOpenAdvanced)
            }
            item(key = "scope") {
                SteamNetworkScopeCard()
            }
        }
    }
}
