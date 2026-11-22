package takagi.ru.monica.ui.common.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassAvailable
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface

data class SelectionActionBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun SelectionActionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMoveToCategory: (() -> Unit)? = null,
    onStack: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showSelectionControls: Boolean = true,
    actions: List<SelectionActionBarAction> = emptyList(),
    selectAllContentDescription: String? = null,
    exitIcon: ImageVector = Icons.Default.Close,
    exitContentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val glass = steamGlassAvailable()
    Surface(
        modifier = modifier.then(if (glass) Modifier.steamGlassSurface(RoundedCornerShape(28.dp), containerColor) else Modifier),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = if (glass) 0.dp else 3.dp,
        shadowElevation = if (glass) 0.dp else 8.dp,
        color = if (glass) Color.Transparent else containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showSelectionControls) {
                Surface(
                    // Reserve the action buttons first so a large count cannot squeeze out Exit.
                    modifier = Modifier.weight(1f, fill = false),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = selectedCount.toString(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                ActionIcon(
                    icon = Icons.Outlined.CheckCircle,
                    contentDescription = selectAllContentDescription
                        ?: stringResource(id = R.string.select_all),
                    onClick = onSelectAll
                )
            }

            actions.forEach { action ->
                ActionIcon(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    enabled = action.enabled,
                    onClick = action.onClick
                )
            }

            onFavorite?.let {
                ActionIcon(
                    icon = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(id = R.string.favorite),
                    onClick = it
                )
            }

            onMoveToCategory?.let {
                ActionIcon(
                    icon = Icons.Default.Folder,
                    contentDescription = stringResource(id = R.string.move_to_category),
                    onClick = it
                )
            }

            onStack?.let {
                ActionIcon(
                    icon = Icons.Default.Layers,
                    contentDescription = stringResource(id = R.string.batch_stack),
                    onClick = it
                )
            }

            onDelete?.let {
                ActionIcon(
                    icon = Icons.Outlined.Delete,
                    contentDescription = stringResource(id = R.string.delete),
                    onClick = it
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            ActionIcon(
                icon = exitIcon,
                contentDescription = exitContentDescription
                    ?: stringResource(id = R.string.close),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}
