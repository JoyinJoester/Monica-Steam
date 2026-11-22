package takagi.ru.monica.steam.space.ui

import takagi.ru.monica.steam.foundation.ui.SteamRefreshActionIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding

/** Only the feed is recorded; the floating header cannot sample its own glass. */
@Composable
internal fun SteamSpaceChrome(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    overlay: @Composable BoxScope.(Dp) -> Unit = {},
    content: @Composable BoxScope.(Dp) -> Unit
) {
    val density = LocalDensity.current
    var clearance by remember(density) { mutableStateOf(80.dp) }
    SteamGlassHost(modifier.fillMaxSize(), content = { content(clearance) }, overlay = {
        Box(Modifier.fillMaxWidth().onSizeChanged {
            clearance = with(density) { it.height.toDp() }
        }) { header() }
        overlay(clearance)
    })
}

@Composable
internal fun SteamSpaceTopBar(
    filtered: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onFilters: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshLabel = stringResource(R.string.steam_space_refresh)
    Row(modifier.fillMaxWidth().steamWindowTopPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp).testTag("steam-space-top-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)
            .steamGlassSurface(CircleShape, MaterialTheme.colorScheme.surfaceContainer, SteamGlassRole.FILTER)
            .testTag("steam-space-back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.steam_space_back))
        }
        Surface(color = Color.Transparent, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f).steamGlassSurface(RoundedCornerShape(28.dp),
                MaterialTheme.colorScheme.surfaceContainer, SteamGlassRole.FILTER)
                .testTag("steam-space-title-capsule")) {
            Row(Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.steam_space_title), Modifier.weight(1f).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onFilters, modifier = Modifier.size(48.dp)
                    .semantics { selected = filtered }.testTag("steam-space-filters")) {
                    Box(Modifier.size(40.dp).then(if (filtered) Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Tune, stringResource(R.string.steam_space_content_settings),
                            tint = if (filtered) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current)
                    }
                }
                IconButton(onClick = onRefresh, enabled = !busy,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = refreshLabel }.testTag("steam-space-refresh")) {
                    SteamRefreshActionIcon(busy, Icons.Rounded.Refresh, refreshLabel)
                }
            }
        }
    }
}
