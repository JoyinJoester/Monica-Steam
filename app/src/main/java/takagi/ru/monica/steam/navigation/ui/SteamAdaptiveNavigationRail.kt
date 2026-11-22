package takagi.ru.monica.steam.navigation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.icon
import takagi.ru.monica.steam.navigation.label

/** Compact top-level navigation for landscape phones and expanded windows. */
@Composable
internal fun SteamAdaptiveNavigationRail(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    onSelected: (SteamDockTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = steamGlassAvailable()
    NavigationRail(
        modifier = modifier.fillMaxHeight().then(if (glass) Modifier.steamGlassSurface(
            RectangleShape, MaterialTheme.colorScheme.surface, SteamGlassRole.TOOLBAR) else Modifier),
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            order.forEach { tab ->
                val label = tab.label()
                NavigationRailItem(
                    selected = selected == tab,
                    onClick = { onSelected(tab) },
                    icon = { Icon(tab.icon(), contentDescription = label) },
                    label = {
                        Text(
                            text = label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    alwaysShowLabel = true
                )
            }
        }
    }
}
