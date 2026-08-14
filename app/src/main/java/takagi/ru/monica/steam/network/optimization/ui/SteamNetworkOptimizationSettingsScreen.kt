package takagi.ru.monica.steam.network.optimization.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.optimization.SteamNetworkOptimizationRuntime
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamHostsDiagnosticsRunner
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRule
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser
import takagi.ru.monica.steam.network.optimization.ui.components.SteamHostsAdvancedEditor
import takagi.ru.monica.steam.network.optimization.ui.components.SteamHostsActionsMenu
import takagi.ru.monica.steam.network.optimization.ui.components.SteamHostsRulesSection
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkOverviewCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNetworkOptimizationSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val diagnosticsRunner = remember { SteamHostsDiagnosticsRunner() }
    val dockClearance = LocalSteamDockContentClearance.current

    LaunchedEffect(context) {
        SteamNetworkOptimizationRuntime.initialize(context)
    }
    val settings by SteamNetworkOptimizationRuntime.settings.collectAsState()
    val sessionStats by SteamNetworkOptimizationRuntime.sessionStats.collectAsState()

    var hostsDraft by rememberSaveable { mutableStateOf(settings.hostsText) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    var probeResults by remember { mutableStateOf<Map<String, SteamHostProbeResult>>(emptyMap()) }
    var probingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isTestingAll by remember { mutableStateOf(false) }

    LaunchedEffect(settings.hostsText) {
        hostsDraft = settings.hostsText
    }

    val parsedDraft = remember(hostsDraft) { SteamHostsRuleParser.parse(hostsDraft) }
    val savedRules = remember(settings.hostsText) {
        SteamHostsRuleParser.parse(settings.hostsText).rules
    }
    val savedTargetKeys = remember(savedRules) {
        savedRules.flatMap { rule ->
            rule.addresses.map { address -> SteamHostProbeTarget(rule.hostname, address).key }
        }.toSet()
    }
    val visibleProbeResults = remember(probeResults, savedTargetKeys) {
        probeResults.filterKeys(savedTargetKeys::contains)
    }
    val availableTargetCount = visibleProbeResults.values.count(SteamHostProbeResult::isAvailable)

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val dataExchange = rememberSteamHostsDataExchange(
        currentText = hostsDraft,
        onDraftImported = { importedText ->
            hostsDraft = importedText
            advancedExpanded = true
        },
        onMessage = ::showMessage
    )

    fun testTarget(target: SteamHostProbeTarget) {
        if (isTestingAll || target.key in probingKeys) return
        scope.launch {
            probingKeys = probingKeys + target.key
            probeResults = probeResults - target.key
            try {
                diagnosticsRunner.run(
                    rules = listOf(
                        SteamHostsRule(
                            hostname = target.hostname,
                            addresses = listOf(target.address)
                        )
                    )
                ) { result ->
                    probeResults = probeResults + (result.target.key to result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showMessage(context.getString(R.string.steam_network_optimization_probe_failed))
            } finally {
                probingKeys = probingKeys - target.key
            }
        }
    }

    fun testAll() {
        if (savedRules.isEmpty() || isTestingAll || probingKeys.isNotEmpty()) return
        scope.launch {
            isTestingAll = true
            probingKeys = probingKeys + savedTargetKeys
            probeResults = probeResults - savedTargetKeys
            try {
                val results = diagnosticsRunner.run(savedRules) { result ->
                    probeResults = probeResults + (result.target.key to result)
                }
                val available = results.count(SteamHostProbeResult::isAvailable)
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.steam_network_optimization_probe_complete,
                        available,
                        results.size
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.steam_network_optimization_probe_failed)
                )
            } finally {
                probingKeys = probingKeys - savedTargetKeys
                isTestingAll = false
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = {
                Text(context.getString(R.string.steam_network_optimization_clear_draft_title))
            },
            text = {
                Text(context.getString(R.string.steam_network_optimization_clear_draft_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        hostsDraft = ""
                        advancedExpanded = true
                        showMessage(
                            context.getString(
                                R.string.steam_network_optimization_draft_cleared
                            )
                        )
                    }
                ) {
                    Text(
                        text = context.getString(R.string.clear),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.steam_network_static_hosts_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            context.getString(R.string.back)
                        )
                    }
                },
                actions = {
                    SteamHostsActionsMenu(
                        hasDraftContent = hostsDraft.isNotEmpty(),
                        onUseBuiltInPreset = {
                            val result = SteamNetworkOptimizationRuntime.applyBuiltInHostsPreset(context)
                            if (result.isValid) {
                                hostsDraft = SteamNetworkOptimizationRuntime.settings.value.hostsText
                                probeResults = emptyMap()
                                advancedExpanded = false
                                showMessage(
                                    context.getString(
                                        R.string.steam_network_static_hosts_builtin_applied
                                    )
                                )
                            }
                        },
                        onImport = dataExchange.importFromFile,
                        onExport = dataExchange.exportToFile,
                        onCopy = dataExchange.copyToClipboard,
                        onPaste = dataExchange.pasteFromClipboard,
                        onClear = { showClearConfirmation = true }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            item(key = "overview") {
                SteamNetworkOverviewCard(
                    enabled = settings.enabled,
                    canEnable = settings.hostCount > 0,
                    hostCount = settings.hostCount,
                    targetCount = savedRules.sumOf(SteamHostsRule::targetCount),
                    sessionHitCount = sessionStats.totalHitCount,
                    testedTargetCount = visibleProbeResults.size,
                    availableTargetCount = availableTargetCount,
                    fallbackToSystemDns = settings.fallbackToSystemDns,
                    isTestingAll = isTestingAll,
                    canTestAll = savedRules.isNotEmpty() && probingKeys.isEmpty(),
                    onEnabledChange = { enabled ->
                        SteamNetworkOptimizationRuntime.setEnabled(context, enabled)
                    },
                    onFallbackChange = { enabled ->
                        SteamNetworkOptimizationRuntime.setFallbackToSystemDns(context, enabled)
                    },
                    onTestAll = ::testAll
                )
            }

            item(key = "rules") {
                SteamHostsRulesSection(
                    rules = savedRules,
                    sessionStats = sessionStats,
                    probeResults = visibleProbeResults,
                    probingKeys = probingKeys,
                    probesLocked = isTestingAll,
                    onProbeTarget = ::testTarget,
                    onOpenEditor = { advancedExpanded = true }
                )
            }

            item(key = "advanced_editor") {
                SteamHostsAdvancedEditor(
                    expanded = advancedExpanded,
                    onExpandedChange = { advancedExpanded = it },
                    value = hostsDraft,
                    onValueChange = { hostsDraft = it },
                    hostCount = parsedDraft.hostCount,
                    error = parsedDraft.errors.firstOrNull(),
                    hasChanges = hostsDraft != settings.hostsText,
                    onSave = {
                        val result = SteamNetworkOptimizationRuntime.saveHosts(context, hostsDraft)
                        if (result.isValid) {
                            probeResults = emptyMap()
                            advancedExpanded = false
                            showMessage(
                                context.getString(
                                    R.string.steam_network_optimization_hosts_saved
                                )
                            )
                        }
                    }
                )
            }

            item(key = "scope") {
                SteamNetworkScopeNote()
            }
        }
    }
}

@Composable
private fun SteamNetworkScopeNote() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = LocalContext.current.getString(
                        R.string.steam_network_optimization_scope_title
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = LocalContext.current.getString(
                        R.string.steam_network_optimization_scope_description
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
