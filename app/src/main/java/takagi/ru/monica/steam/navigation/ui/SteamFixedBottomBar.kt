package takagi.ru.monica.steam.navigation.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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

/** Monica Pass-style fixed M3 navigation bar for Steam's top-level pages. */
@Composable
internal fun SteamFixedBottomBar(
    modifier: Modifier = Modifier,
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    onSelected: (SteamDockTab) -> Unit
) {
    val windowInsets = rememberSteamWindowBottomInsets()
    val glass = steamGlassAvailable()
    NavigationBar(
        modifier = modifier.fillMaxWidth().then(if (glass) Modifier.steamGlassSurface(
            RectangleShape, MaterialTheme.colorScheme.surface, SteamGlassRole.TOOLBAR) else Modifier),
        tonalElevation = 0.dp,
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        windowInsets = windowInsets
    ) {
        SteamDockTab.completeFixedOrder(order).forEach { tab ->
            val label = tab.label()
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon(),
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}
