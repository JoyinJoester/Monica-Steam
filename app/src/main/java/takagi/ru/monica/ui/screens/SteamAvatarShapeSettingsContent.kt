package takagi.ru.monica.ui.screens

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamAvatarShapeOption
import takagi.ru.monica.steam.foundation.ui.steamAvatarShape

@Composable
internal fun SteamAvatarShapeSettingsItem(
    currentShape: SteamAvatarShapeOption,
    onClick: () -> Unit
) {
    AvatarShapeSettingsItem(
        icon = Icons.Default.AccountBox,
        titleResource = R.string.steam_avatar_shape_title,
        currentShape = currentShape,
        onClick = onClick
    )
}

@Composable
internal fun SteamAvatarFrameShapeSettingsItem(
    currentShape: SteamAvatarShapeOption,
    onClick: () -> Unit
) {
    AvatarShapeSettingsItem(
        icon = Icons.Default.Badge,
        titleResource = R.string.steam_avatar_frame_shape_title,
        currentShape = currentShape,
        onClick = onClick
    )
}

@Composable
private fun AvatarShapeSettingsItem(
    icon: ImageVector,
    titleResource: Int,
    currentShape: SteamAvatarShapeOption,
    onClick: () -> Unit
) {
    SettingsItem(
        icon = icon,
        title = stringResource(titleResource),
        subtitle = stringResource(
            R.string.steam_avatar_shape_current,
            stringResource(currentShape.titleResource)
        ),
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamAvatarShapeSelectionSheet(
    currentShape: SteamAvatarShapeOption,
    onShapeSelected: (SteamAvatarShapeOption) -> Unit,
    onDismiss: () -> Unit
) {
    AvatarShapeSelectionSheet(
        titleResource = R.string.steam_avatar_shape_title,
        descriptionResource = R.string.steam_avatar_shape_description,
        currentShape = currentShape,
        onShapeSelected = onShapeSelected,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamAvatarFrameShapeSelectionSheet(
    currentShape: SteamAvatarShapeOption,
    onShapeSelected: (SteamAvatarShapeOption) -> Unit,
    onDismiss: () -> Unit
) {
    AvatarShapeSelectionSheet(
        titleResource = R.string.steam_avatar_frame_shape_title,
        descriptionResource = R.string.steam_avatar_frame_shape_description,
        currentShape = currentShape,
        onShapeSelected = onShapeSelected,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarShapeSelectionSheet(
    titleResource: Int,
    descriptionResource: Int,
    currentShape: SteamAvatarShapeOption,
    onShapeSelected: (SteamAvatarShapeOption) -> Unit,
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(titleResource),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(descriptionResource),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(SteamAvatarShapeOption.entries, key = SteamAvatarShapeOption::storedValue) { shape ->
                val selected = shape == currentShape
                Surface(
                    onClick = { onShapeSelected(shape) },
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
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = shape.steamAvatarShape(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(shape.titleResource),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        RadioButton(
                            selected = selected,
                            onClick = { onShapeSelected(shape) }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onShapeSelected(SteamAvatarShapeOption.SQUARE) },
                        enabled = currentShape != SteamAvatarShapeOption.SQUARE,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Text(
                            text = stringResource(R.string.steam_avatar_shape_reset),
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

private val SteamAvatarShapeOption.titleResource: Int
    get() = when (this) {
        SteamAvatarShapeOption.SQUARE -> R.string.steam_avatar_shape_square
        SteamAvatarShapeOption.ROUNDED -> R.string.steam_avatar_shape_rounded
        SteamAvatarShapeOption.CIRCLE -> R.string.steam_avatar_shape_circle
    }
