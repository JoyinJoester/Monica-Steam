package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.ui.SteamUiScaleOption

@Composable
internal fun SteamUiScaleSettingsItem(
    currentScale: SteamUiScaleOption,
    onClick: () -> Unit
) {
    SettingsItem(
        icon = Icons.Default.AspectRatio,
        title = stringResource(R.string.steam_ui_scale_title),
        subtitle = stringResource(R.string.steam_ui_scale_current, currentScale.percent),
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamUiScaleSelectionSheet(
    currentScale: SteamUiScaleOption,
    onScaleSelected: (SteamUiScaleOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.steam_ui_scale_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.steam_ui_scale_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(SteamUiScaleOption.entries, key = SteamUiScaleOption::percent) { scale ->
                val selected = scale == currentScale
                Surface(
                    onClick = { onScaleSelected(scale) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    tonalElevation = if (selected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onScaleSelected(scale) }
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(scale.titleResource),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(
                                    R.string.steam_ui_scale_option_percent,
                                    scale.percent
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onScaleSelected(SteamUiScaleOption.DEFAULT) },
                        enabled = currentScale != SteamUiScaleOption.DEFAULT,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Text(
                            text = stringResource(R.string.steam_ui_scale_reset),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

private val SteamUiScaleOption.titleResource: Int
    get() = when (this) {
        SteamUiScaleOption.COMPACT -> R.string.steam_ui_scale_compact
        SteamUiScaleOption.SMALL -> R.string.steam_ui_scale_small
        SteamUiScaleOption.DEFAULT -> R.string.steam_ui_scale_default
        SteamUiScaleOption.LARGE -> R.string.steam_ui_scale_large
    }
