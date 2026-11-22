package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.LocalReduceAnimations

/** Same expressive polygons as Nearby Sync, inside the existing action hit target. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamRefreshActionIcon(refreshing: Boolean, icon: ImageVector, label: String) {
    val loading = stringResource(R.string.loading)
    Box(Modifier.size(36.dp).semantics {
        contentDescription = label
        if (refreshing) stateDescription = loading
    }, contentAlignment = Alignment.Center) {
        if (refreshing) {
            val modifier = Modifier.size(32.dp).testTag("steam_refresh_shape").clearAndSetSemantics {}
            if (LocalReduceAnimations.current) LoadingIndicator(progress = { 0f }, modifier = modifier,
                polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons)
            else LoadingIndicator(modifier = modifier)
        } else Icon(icon, contentDescription = null)
    }
}

@Composable
internal fun SteamRefreshIconButton(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onRefresh, enabled = !refreshing, modifier = modifier) {
        SteamRefreshActionIcon(refreshing, androidx.compose.material.icons.Icons.Default.Refresh,
            stringResource(R.string.refresh))
    }
}
