package takagi.ru.monica.steam.network.optimization.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget
import takagi.ru.monica.steam.network.optimization.domain.SteamHostSessionStats
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRule

@Composable
internal fun SteamHostsRulesSection(
    rules: List<SteamHostsRule>,
    sessionStats: SteamHostSessionStats,
    probeResults: Map<String, SteamHostProbeResult>,
    probingKeys: Set<String>,
    probesLocked: Boolean,
    onProbeTarget: (SteamHostProbeTarget) -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetCount = rules.sumOf(SteamHostsRule::targetCount)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.steam_network_optimization_rules_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.steam_network_optimization_rules_summary,
                    rules.size,
                    targetCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (rules.isEmpty()) {
            EmptyRulesCard(onOpenEditor = onOpenEditor)
        } else {
            rules.forEach { rule ->
                val hitStats = sessionStats.hosts[rule.hostname]
                SteamHostsRuleCard(
                    rule = rule,
                    hitCount = hitStats?.hitCount ?: 0L,
                    lastHitAtEpochMillis = hitStats?.lastHitAtEpochMillis ?: 0L,
                    probeResults = probeResults,
                    probingKeys = probingKeys,
                    probesLocked = probesLocked,
                    onProbeTarget = onProbeTarget
                )
            }
        }
    }
}

@Composable
private fun EmptyRulesCard(onOpenEditor: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.steam_network_optimization_rules_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.steam_network_optimization_rules_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenEditor) {
                Text(stringResource(R.string.steam_network_optimization_open_editor))
            }
        }
    }
}

@Composable
private fun SteamHostsRuleCard(
    rule: SteamHostsRule,
    hitCount: Long,
    lastHitAtEpochMillis: Long,
    probeResults: Map<String, SteamHostProbeResult>,
    probingKeys: Set<String>,
    probesLocked: Boolean,
    onProbeTarget: (SteamHostProbeTarget) -> Unit
) {
    val relativeLastHit = relativeLastHitText(lastHitAtEpochMillis)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.hostname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = relativeLastHit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(
                            R.string.steam_network_optimization_rule_hits,
                            hitCount
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            rule.addresses.forEach { address ->
                val target = SteamHostProbeTarget(rule.hostname, address)
                HorizontalDivider(
                    modifier = Modifier.padding(top = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SteamHostTargetRow(
                    address = address,
                    result = probeResults[target.key],
                    isTesting = target.key in probingKeys,
                    enabled = !probesLocked,
                    onTest = { onProbeTarget(target) }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SteamHostTargetRow(
    address: String,
    result: SteamHostProbeResult?,
    isTesting: Boolean,
    enabled: Boolean,
    onTest: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ProbeStatusLine(result = result)
        }
        FilledTonalIconButton(
            onClick = onTest,
            enabled = enabled && !isTesting,
            modifier = Modifier.size(48.dp)
        ) {
            if (isTesting) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = context.getString(
                        R.string.steam_network_optimization_test_address,
                        address
                    )
                )
            }
        }
    }
}

@Composable
private fun ProbeStatusLine(result: SteamHostProbeResult?) {
    val context = LocalContext.current
    val presentation = probePresentation(result)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = presentation.color
        )
        Text(
            text = when {
                result == null -> context.getString(R.string.steam_network_optimization_probe_not_tested)
                result.status == SteamHostProbeStatus.AVAILABLE -> buildList {
                    add(context.getString(R.string.steam_network_optimization_probe_available))
                    result.latencyMillis?.let {
                        add(context.getString(R.string.steam_network_optimization_probe_latency, it))
                    }
                    result.httpStatusCode?.let { add("HTTP $it") }
                }.joinToString(" · ")
                result.status == SteamHostProbeStatus.TIMEOUT ->
                    context.getString(R.string.steam_network_optimization_probe_timeout)
                result.status == SteamHostProbeStatus.TLS_ERROR ->
                    context.getString(R.string.steam_network_optimization_probe_tls_error)
                else -> context.getString(R.string.steam_network_optimization_probe_connection_error)
            },
            style = MaterialTheme.typography.bodySmall,
            color = presentation.color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun probePresentation(result: SteamHostProbeResult?): ProbePresentation = when (result?.status) {
    SteamHostProbeStatus.AVAILABLE -> ProbePresentation(
        icon = Icons.Default.CheckCircle,
        color = MaterialTheme.colorScheme.tertiary
    )
    SteamHostProbeStatus.TIMEOUT -> ProbePresentation(
        icon = Icons.Default.Schedule,
        color = MaterialTheme.colorScheme.secondary
    )
    SteamHostProbeStatus.TLS_ERROR,
    SteamHostProbeStatus.CONNECTION_ERROR -> ProbePresentation(
        icon = Icons.Default.ErrorOutline,
        color = MaterialTheme.colorScheme.error
    )
    null -> ProbePresentation(
        icon = Icons.Default.Schedule,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun relativeLastHitText(timestamp: Long): String {
    val context = LocalContext.current
    if (timestamp <= 0L) {
        return stringResource(R.string.steam_network_optimization_rule_never_used)
    }
    val relative = remember(timestamp) {
        DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
    return context.getString(R.string.steam_network_optimization_rule_last_used, relative)
}

private data class ProbePresentation(
    val icon: ImageVector,
    val color: Color
)
