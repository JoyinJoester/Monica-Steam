package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassSelectionRow
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** One capsule in both styles: refracting glass or Gaussian frost, with scrollable labels. */
@Composable
internal fun <T : Any> SteamListFilterRow(
    options: List<T>, selected: T, onSelect: (T) -> Unit,
    label: @Composable (T) -> String, modifier: Modifier = Modifier,
    count: (T) -> Int = { 0 },
    floatingSelection: Boolean = true,
    fillWidth: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val shellBackdrop = rememberLayerBackdrop()
    val shell = Modifier.steamGlassSurface(
        if (liquid) RoundedCornerShape(28.dp) else CircleShape,
        colors.surfaceContainer, role = SteamGlassRole.FILTER)
    Box(modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .then(if (liquid && floatingSelection) Modifier else shell)) {
        // Every glass selector needs a sibling shell: nesting it inside the
        // backdrop clips its lifted lens, including search and sheet filters.
        if (liquid && floatingSelection) Box(Modifier.matchParentSize().layerBackdrop(shellBackdrop).then(shell))
        SteamGlassSelectionRow(options, selected, onSelect,
            if (liquid) colors.primaryContainer else colors.primaryContainer.copy(alpha = .72f),
            modifier = Modifier.padding(4.dp), scrollable = true, dragFromAnyOption = true,
            surfaceBackdrop = shellBackdrop.takeIf { floatingSelection }) { option, active, item, click ->
            val interaction = remember(option) { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Surface(color = Color.Transparent,
                contentColor = when {
                    active && liquid -> colors.primary
                    active -> colors.onPrimaryContainer
                    else -> colors.onSurface
                },
                shape = CircleShape, modifier = item.heightIn(min = 48.dp).clip(CircleShape)
                    .selectable(active, interactionSource = interaction, indication = null, role = Role.Tab, onClick = click)) {
                FilterLabel(label(option), count(option), active,
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp).graphicsLayer {
                        // The moving lens magnifies its captured labels. Scaling
                        // the pressed target separately creates a second pulse.
                        alpha = if (!liquid && pressed) .7f else 1f
                    })
            }
        }
    }
}

@Composable
private fun FilterLabel(label: String, count: Int, active: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (count > 0) Badge(
            containerColor = if (active) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (active) colors.onPrimary else colors.onSurface
        ) { Text(count.coerceAtMost(99).toString()) }
    }
}
