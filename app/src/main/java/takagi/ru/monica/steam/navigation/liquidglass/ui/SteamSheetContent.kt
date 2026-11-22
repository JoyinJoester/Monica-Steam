package takagi.ru.monica.steam.navigation.liquidglass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/** Content layers share the modal's material. No per-row blur, lens or backdrop registration. */
@Composable
internal fun steamSheetGroupColor() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
    alpha = if (LocalSteamPopupBackdrop.current != null && steamGlassSupported()) .94f else 1f
)

internal fun steamSheetRowShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) 24.dp else 6.dp,
    topEnd = if (index == 0) 24.dp else 6.dp,
    bottomStart = if (index == count - 1) 24.dp else 6.dp,
    bottomEnd = if (index == count - 1) 24.dp else 6.dp
)

@Composable
internal fun SteamSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f).padding(top = 6.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            actions()
            FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = steamSheetGroupColor())) {
                Icon(Icons.Default.Close, stringResource(R.string.close))
            }
        }
        if (!subtitle.isNullOrBlank()) Text(subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun SteamSheetSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
        content()
    }
}

@Composable
internal fun SteamSheetChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    role: Role = Role.RadioButton
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clip(shape).then(
            if (role == Role.Checkbox) Modifier.toggleable(selected, role = role, onValueChange = { onClick() })
                .semantics { this.selected = selected }
            else Modifier.selectable(selected, onClick = onClick, role = role)),
        shape = shape,
        color = if (selected) colors.secondaryContainer else steamSheetGroupColor(),
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface
    ) {
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center) {
            // Equal gutters keep the label centered in both states, including wrapped lines.
            Text(label, Modifier.padding(horizontal = 22.dp), style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            if (selected) Icon(Icons.Default.Check, contentDescription = null,
                modifier = Modifier.size(18.dp).align(Alignment.CenterStart))
        }
    }
}

@Composable
internal fun SteamSheetSearchField(
    query: String, onQueryChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier
) {
    takagi.ru.monica.steam.foundation.ui.SteamListSearchField(query, onQueryChange, hint, modifier)
}

@Composable
internal fun SteamSheetFooter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
