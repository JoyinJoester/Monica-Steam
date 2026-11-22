package takagi.ru.monica.steam.network.optimization.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import androidx.compose.ui.res.stringResource

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamNetworkOverviewCard(
    enabled: Boolean,
    canEnable: Boolean,
    hostCount: Int,
    targetCount: Int,
    sessionHitCount: Long,
    testedTargetCount: Int,
    availableTargetCount: Int,
    fallbackToSystemDns: Boolean,
    isTestingAll: Boolean,
    canTestAll: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
    onTestAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp),
                        tint = contentColor
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (enabled) {
                                R.string.steam_network_optimization_overview_enabled
                            } else {
                                R.string.steam_network_optimization_overview_disabled
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = stringResource(
                            if (enabled) {
                                R.string.steam_network_optimization_overview_enabled_description
                            } else {
                                R.string.steam_network_optimization_overview_disabled_description
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f)
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = canEnable,
                    onCheckedChange = onEnabledChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverviewMetric(
                    value = hostCount.toString(),
                    label = stringResource(R.string.steam_network_optimization_metric_rules),
                    modifier = Modifier.weight(1f)
                )
                OverviewMetric(
                    value = sessionHitCount.toString(),
                    label = stringResource(R.string.steam_network_optimization_metric_hits),
                    modifier = Modifier.weight(1f)
                )
                OverviewMetric(
                    value = if (testedTargetCount > 0) {
                        "$availableTargetCount/$testedTargetCount"
                    } else {
                        "—"
                    },
                    label = stringResource(R.string.steam_network_optimization_metric_test),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(
                    R.string.steam_network_optimization_rules_summary,
                    hostCount,
                    targetCount
                ),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.76f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .toggleable(
                        value = fallbackToSystemDns,
                        role = Role.Switch,
                        onValueChange = onFallbackChange
                    )
                    .defaultMinSize(minHeight = 64.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = contentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_network_optimization_fallback_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    Text(
                        text = stringResource(R.string.steam_network_optimization_fallback_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                }
                Switch(
                    checked = fallbackToSystemDns,
                    onCheckedChange = null
                )
            }

            Button(
                onClick = onTestAll,
                enabled = canTestAll && !isTestingAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                if (isTestingAll) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.steam_network_optimization_testing_all))
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.steam_network_optimization_test_all))
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
