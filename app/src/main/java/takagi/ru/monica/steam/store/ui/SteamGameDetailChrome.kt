package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

internal typealias SteamGameSectionHeader = @Composable (() -> Unit) -> Unit

internal enum class SteamGameDetailSection { STORE, STATS, WORKSHOP }

/** Chrome is a sibling of its recorded page, so neither the header nor toolbar samples itself. */
@Composable
internal fun SteamGameDetailSectionHost(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    toolbar: @Composable BoxScope.() -> Unit = {},
    content: @Composable (Dp) -> Unit
) {
    val density = LocalDensity.current
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeight by remember(density, inset) { mutableStateOf(inset + 64.dp) }
    SteamGlassHost(modifier.fillMaxSize(), content = { content(headerHeight) }, overlay = {
        Box(Modifier.fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) { header() }
        Box(Modifier.fillMaxSize().padding(top = headerHeight,
            bottom = LocalSteamDockContentClearance.current + 12.dp)) { toolbar() }
    })
}

@Composable
internal fun SteamGameDetailNavigation(
    section: SteamGameDetailSection,
    workshopAvailable: Boolean,
    onSectionChanged: (SteamGameDetailSection) -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val liquid = LocalSteamDesignStyle.current == takagi.ru.monica.steam.navigation.SteamDockStyle.LIQUID_GLASS
    val shellBackdrop = rememberLayerBackdrop()
    val options = remember(workshopAvailable) { buildList {
        add(SteamGameDetailSection.STORE)
        add(SteamGameDetailSection.STATS)
        if (workshopAvailable) add(SteamGameDetailSection.WORKSHOP)
    } }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag("game_detail_navigation"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)
            .steamGlassSurface(CircleShape, colors.surfaceContainer, role = SteamGlassRole.FILTER).testTag("game_detail_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
        Box(Modifier.weight(1f, fill = false).testTag("game_detail_tabs")) {
            Box(Modifier.matchParentSize().layerBackdrop(shellBackdrop)
                .steamGlassSurface(CircleShape, colors.surfaceContainer, role = SteamGlassRole.FILTER))
            SteamGlassSelectionRow(options, section, onSectionChanged,
                if (liquid) colors.primaryContainer else colors.primaryContainer.copy(alpha = .72f),
                scrollable = true, indicatorInset = 4.dp,
                dragFromAnyOption = true, surfaceBackdrop = shellBackdrop) { option, active, item, click ->
                val interaction = remember(option) { MutableInteractionSource() }
                Box(item.heightIn(min = 48.dp)
                    .selectable(active, interactionSource = interaction, indication = null, role = Role.Tab, onClick = click)
                    .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(when (option) {
                        SteamGameDetailSection.STORE -> R.string.steam_game_tab_store
                        SteamGameDetailSection.STATS -> R.string.steam_game_tab_stats
                        SteamGameDetailSection.WORKSHOP -> R.string.workshop_title
                    }), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        color = when {
                            active && liquid -> colors.primary
                            active -> colors.onPrimaryContainer
                            else -> colors.onSurface
                        })
                }
            }
        }
    }
}
