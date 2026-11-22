package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamSheetGroupColor

/** One library-family choice treatment for both single and multiple filter selections. */
@Composable
internal fun SteamListFilterChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = null, border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val palette = MaterialTheme.colorScheme
    MaterialFilterChip(selected, onClick, label, modifier.heightIn(min = 48.dp), enabled,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = steamSheetGroupColor(), labelColor = palette.onSurface,
            selectedContainerColor = palette.secondaryContainer, selectedLabelColor = palette.onSecondaryContainer),
        elevation = null, border = BorderStroke(0.dp, Color.Transparent), interactionSource = interactionSource)
}

/** String-label adapter preserves source icons and status dots without another visual style. */
@Composable
internal fun SteamListChoiceChip(
    selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null, leadingIcon: ImageVector? = null,
    selectedLeadingIcon: ImageVector? = leadingIcon, statusDotColor: Color? = null, animated: Boolean = true
) {
    val icon = if (selected) selectedLeadingIcon else leadingIcon
    SteamListFilterChip(selected, onClick, label = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
            statusDotColor?.let { Box(Modifier.size(6.dp).background(it, CircleShape)) }
        }
    }, modifier = modifier, interactionSource = interactionSource, leadingIcon =
        if (icon != null) { { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) } }
        else if (selected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } }
        else null)
}
