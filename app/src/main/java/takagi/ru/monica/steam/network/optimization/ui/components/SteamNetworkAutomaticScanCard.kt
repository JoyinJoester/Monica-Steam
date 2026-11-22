package takagi.ru.monica.steam.network.optimization.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.network.optimization.domain.SteamAutoHostsSummary
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage
import takagi.ru.monica.steam.network.optimization.ui.SteamAutoOptimizationUiState
import takagi.ru.monica.steam.network.optimization.ui.isBusy

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamNetworkAutomaticScanCard(
    state: SteamAutoOptimizationUiState,
    summary: SteamAutoHostsSummary?,
    enabled: Boolean,
    canScan: Boolean,
    onScan: () -> Unit,
    onApply: () -> Unit,
    onDisable: () -> Unit
) {
    val colors = automaticCardColors(enabled = enabled, state = state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.container)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.content.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = colors.content.copy(alpha = 0.82f),
                                shape = MaterialTheme.shapes.extraLarge
                            )
                    )
                    Text(
                        text = scanStatusText(state = state, enabled = enabled),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.content
                    )
                }
            }

            Text(
                text = stringResource(R.string.steam_network_static_hosts_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.content
            )
            Text(
                text = stringResource(R.string.steam_network_static_hosts_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.content.copy(alpha = 0.82f)
            )

            when (state) {
                is SteamAutoOptimizationUiState.Running -> ScanProgressContent(
                    progress = state.progress,
                    contentColor = colors.content
                )
                SteamAutoOptimizationUiState.Applying -> ScanProgressContent(
                    progress = null,
                    contentColor = colors.content
                )
                is SteamAutoOptimizationUiState.Error -> ScanErrorContent(state)
                is SteamAutoOptimizationUiState.Success -> ResultSummaryContent(
                    selectedHostCount = state.result.availableHostCount,
                    totalHostCount = state.result.totalHostCount,
                    averageLatencyMillis = state.result.averageLatencyMillis,
                    contentColor = colors.content
                )
                SteamAutoOptimizationUiState.Idle -> summary?.let {
                    ResultSummaryContent(
                        selectedHostCount = it.selectedHostCount,
                        totalHostCount = it.totalHostCount,
                        averageLatencyMillis = it.averageLatencyMillis,
                        contentColor = colors.content
                    )
                }
            }

            if (!canScan && !state.isReadyToApply) {
                Text(
                    text = stringResource(R.string.steam_network_resolver_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = if (state.isReadyToApply) onApply else onScan,
                enabled = !state.isBusy && (state.isReadyToApply || canScan),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
            ) {
                if (state.isBusy) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = if (state.isReadyToApply) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.NetworkCheck
                        },
                        contentDescription = null
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        when {
                            state.isReadyToApply -> R.string.steam_network_static_hosts_apply
                            state === SteamAutoOptimizationUiState.Idle &&
                                summary == null && !enabled -> R.string.steam_network_static_hosts_scan
                            else -> R.string.steam_network_static_hosts_rescan
                        }
                    )
                )
            }

            if (state.isReadyToApply) {
                TextButton(
                    onClick = onScan,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.steam_network_static_hosts_rescan))
                }
            } else if (enabled) {
                TextButton(
                    onClick = onDisable,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.steam_network_static_hosts_disable))
                }
            }
        }
    }
}

@Composable
private fun ScanProgressContent(
    progress: SteamDnsScanProgress?,
    contentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress?.fraction ?: 1f },
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            trackColor = contentColor.copy(alpha = 0.14f)
        )
        Text(
            text = if (progress == null) {
                stringResource(R.string.steam_network_auto_applying)
            } else {
                stringResource(
                    when (progress.stage) {
                        SteamDnsScanStage.RESOLVING ->
                            R.string.steam_network_auto_resolving_progress
                        SteamDnsScanStage.VERIFYING ->
                            R.string.steam_network_auto_verifying_progress
                        SteamDnsScanStage.RECOVERING ->
                            R.string.steam_network_auto_recovering_progress
                    },
                    progress.completed,
                    progress.total
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun ScanErrorContent(state: SteamAutoOptimizationUiState.Error) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(
                    if (state.applyFailed) {
                        R.string.steam_network_auto_apply_failed
                    } else {
                        R.string.steam_network_auto_scan_incomplete
                    },
                    state.availableHostCount,
                    state.totalHostCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ResultSummaryContent(
    selectedHostCount: Int,
    totalHostCount: Int,
    averageLatencyMillis: Long?,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = contentColor.copy(alpha = 0.09f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = contentColor
            )
            Text(
                text = if (averageLatencyMillis != null) {
                    stringResource(
                        R.string.steam_network_auto_result_summary,
                        selectedHostCount,
                        totalHostCount,
                        averageLatencyMillis
                    )
                } else {
                    stringResource(
                        R.string.steam_network_auto_result_summary_no_latency,
                        selectedHostCount,
                        totalHostCount
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun scanStatusText(
    state: SteamAutoOptimizationUiState,
    enabled: Boolean
): String = when (state) {
    is SteamAutoOptimizationUiState.Running -> stringResource(
        when (state.progress.stage) {
            SteamDnsScanStage.RESOLVING -> R.string.steam_network_auto_resolving
            SteamDnsScanStage.VERIFYING -> R.string.steam_network_auto_verifying
            SteamDnsScanStage.RECOVERING -> R.string.steam_network_auto_recovering
        }
    )
    SteamAutoOptimizationUiState.Applying ->
        stringResource(R.string.steam_network_auto_applying)
    is SteamAutoOptimizationUiState.Error ->
        stringResource(R.string.steam_network_auto_status_failed)
    is SteamAutoOptimizationUiState.Success -> stringResource(
        if (state.applied) {
            R.string.steam_network_auto_status_active
        } else {
            R.string.steam_network_auto_status_ready
        }
    )
    SteamAutoOptimizationUiState.Idle -> stringResource(
        if (enabled) {
            R.string.steam_network_auto_status_active
        } else {
            R.string.steam_network_auto_status_idle
        }
    )
}

@Composable
private fun automaticCardColors(
    enabled: Boolean,
    state: SteamAutoOptimizationUiState
): AutomaticCardColors = when {
    state is SteamAutoOptimizationUiState.Error -> AutomaticCardColors(
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer
    )
    state.isReadyToApply -> AutomaticCardColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer
    )
    enabled ||
        (state is SteamAutoOptimizationUiState.Success && state.applied) -> AutomaticCardColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer
    )
    else -> AutomaticCardColors(
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface
    )
}

private data class AutomaticCardColors(
    val container: Color,
    val content: Color
)

private val SteamAutoOptimizationUiState.isReadyToApply: Boolean
    get() = this is SteamAutoOptimizationUiState.Success && !applied
