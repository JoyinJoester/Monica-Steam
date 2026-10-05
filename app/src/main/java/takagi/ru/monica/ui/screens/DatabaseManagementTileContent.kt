package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

@Immutable
internal data class DatabaseManagementTileMetrics(
    val nameHeight: Dp,
    val sourceHeight: Dp,
    val statusHeight: Dp,
    val actionHeight: Dp,
)

/** Resolve shared font metrics once per grid, including the supported scripts' fallback fonts. */
@Composable
internal fun rememberDatabaseManagementTileMetrics(): DatabaseManagementTileMetrics {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val bodyStyle = MaterialTheme.typography.bodySmall
    val actionStyle = MaterialTheme.typography.labelLarge
    return remember(measurer, density, titleStyle, bodyStyle, actionStyle) {
        // Metrics sample only; this text is never displayed. Latin-only metrics can
        // otherwise make Chinese and mixed-script database names a few pixels taller.
        val sample = "Ag国あ한Яấ😀"
        fun height(style: TextStyle, lines: Int): Dp = with(density) {
            measurer.measure(List(lines) { sample }.joinToString("\n"), style = style).size.height.toDp()
        }
        DatabaseManagementTileMetrics(
            nameHeight = height(titleStyle, 2),
            sourceHeight = height(bodyStyle, 1),
            statusHeight = height(bodyStyle, 2),
            actionHeight = maxOf(48.dp, height(actionStyle, 2) + 16.dp),
        )
    }
}

/** Identical text slots keep lazy-grid tiles equal without measuring off-screen databases. */
@Composable
internal fun DatabaseManagementTileContent(
    name: String,
    status: String,
    warning: Boolean,
    metrics: DatabaseManagementTileMetrics,
    sourceLabel: String = "",
    titleColor: Color = LocalContentColor.current,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    reserveActionSlot: Boolean = false,
    actionLabel: String = "",
    onAction: (() -> Unit)? = null,
    header: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(28.dp)) { header() }
        Text(
            name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.height(metrics.nameHeight),
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, color = titleColor,
        )
        Text(
            sourceLabel, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.height(metrics.sourceHeight),
            minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            if (warning) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
            Text(
                status, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.height(metrics.statusHeight),
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (warning) MaterialTheme.colorScheme.error else statusColor,
            )
        }
        if (reserveActionSlot) {
            Box(Modifier.fillMaxWidth().height(metrics.actionHeight)) {
                if (onAction != null) {
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
