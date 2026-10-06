package takagi.ru.monica.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import takagi.ru.monica.R
import takagi.ru.monica.data.AppLauncherIcon

@Composable
internal fun AppLauncherIconSettingsItem(
    currentIcon: AppLauncherIcon,
    onClick: () -> Unit
) {
    SettingsItem(
        icon = Icons.Default.Apps,
        title = stringResource(R.string.icon_settings_app_icon_title),
        subtitle = stringResource(
            R.string.icon_settings_app_icon_current,
            stringResource(currentIcon.titleResource)
        ),
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppLauncherIconSelectionSheet(
    currentIcon: AppLauncherIcon,
    onIconSelected: (AppLauncherIcon) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.icon_settings_app_icon_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            items(AppLauncherIcon.entries, key = AppLauncherIcon::name) { option ->
                val selected = option == currentIcon
                Surface(
                    onClick = { if (!selected) onIconSelected(option) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
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
                        LauncherIconPreview(
                            iconRes = option.previewIconRes,
                            contentDescription = stringResource(option.titleResource)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(option.titleResource),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(option.subtitleResource),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(
                            selected = selected,
                            onClick = { if (!selected) onIconSelected(option) }
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.icon_settings_app_icon_refresh_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LauncherIconPreview(
    @DrawableRes iconRes: Int,
    contentDescription: String
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 52.dp.roundToPx() }
    val preview = remember(iconRes, sizePx) {
        ContextCompat.getDrawable(context, iconRes)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
    }
    Surface(
        modifier = Modifier.size(52.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        preview?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

private val AppLauncherIcon.titleResource: Int
    get() = when (this) {
        AppLauncherIcon.MODERN -> R.string.icon_settings_app_icon_modern_title
        AppLauncherIcon.CLASSIC -> R.string.icon_settings_app_icon_classic_title
    }

private val AppLauncherIcon.subtitleResource: Int
    get() = when (this) {
        AppLauncherIcon.MODERN -> R.string.icon_settings_app_icon_modern_subtitle
        AppLauncherIcon.CLASSIC -> R.string.icon_settings_app_icon_classic_subtitle
    }

internal val AppLauncherIcon.previewIconRes: Int
    get() = when (this) {
        AppLauncherIcon.MODERN -> R.mipmap.ic_launcher
        AppLauncherIcon.CLASSIC -> R.mipmap.ic_launcher_classic
    }
