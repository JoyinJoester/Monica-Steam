package takagi.ru.monica.ui.screens

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassTopAppBar as TopAppBar
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.render.isSteamLiquidGlassRuntimeSupported
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance

/** Chooses the app-wide design style while retaining the user's colors and display settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamDesignStyleScreen(
    style: SteamDockStyle,
    onStyleChange: (SteamDockStyle) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember(style) { mutableStateOf(style) }
    val dockContentClearance = LocalSteamDockContentClearance.current
    val glassSupported = remember { isSteamLiquidGlassRuntimeSupported() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_design_style_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = dockContentClearance + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "preview") {
                CompositionLocalProvider(LocalSteamDesignStyle provides selected) {
                    SteamDesignStylePreview(selected)
                }
            }
            item(key = "options") {
                SettingsGroup(modifier = Modifier.selectableGroup()) {
                    SteamDockStyle.entries.forEach { candidate ->
                        SteamDesignStyleOption(
                            title = stringResource(
                                when (candidate) {
                                    SteamDockStyle.FIXED -> R.string.steam_dock_style_fixed
                                    SteamDockStyle.LIQUID_GLASS -> R.string.steam_dock_style_liquid_glass
                                }
                            ),
                            description = stringResource(
                                when (candidate) {
                                    SteamDockStyle.FIXED -> R.string.steam_design_style_m3e_description
                                    SteamDockStyle.LIQUID_GLASS ->
                                        R.string.steam_design_style_liquid_glass_description
                                }
                            ),
                            selected = selected == candidate,
                            modifier = Modifier.testTag("steam-design-style-${candidate.name}"),
                            onSelect = {
                                if (selected != candidate) {
                                    selected = candidate
                                    onStyleChange(candidate)
                                }
                            }
                        )
                    }
                }
            }
            item(key = "notes") {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selected == SteamDockStyle.LIQUID_GLASS && !glassSupported) {
                        Text(
                            text = stringResource(R.string.steam_design_style_glass_unsupported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = stringResource(R.string.steam_design_style_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.steam_design_style_order_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamDesignStyleOption(
    title: String,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A miniature chat list under the navigation of the selected style; glass refracts it live. */
@Composable
private fun SteamDesignStylePreview(style: SteamDockStyle) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.steam_design_style_preview)
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        SteamGlassHost(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerLow)
                .clearAndSetSemantics {},
            backdropColor = scheme.surfaceContainerLow,
            content = {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PreviewRow(width = 0.62f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(scheme.primaryContainer, scheme.tertiaryContainer)
                                )
                            )
                    )
                    PreviewRow(width = 0.48f)
                    PreviewRow(width = 0.7f)
                }
            },
            overlay = {
                val icons = listOf(
                    Icons.Default.Storefront,
                    Icons.Default.SportsEsports,
                    Icons.Default.ChatBubble,
                    Icons.Default.Security,
                    Icons.Default.Settings
                )
                when (style) {
                    SteamDockStyle.LIQUID_GLASS -> PreviewDock(
                        icons = icons,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .steamGlassSurface(CircleShape, fallback = scheme.surfaceContainerHighest)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    SteamDockStyle.FIXED -> PreviewDock(
                        icons = icons,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(scheme.surfaceContainer)
                            .padding(vertical = 10.dp),
                        spread = true
                    )
                }
            }
        )
    }
}

@Composable
private fun PreviewRow(width: Float) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(scheme.primaryContainer))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth(width).height(10.dp)
                    .clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.24f))
            )
            Box(
                Modifier.fillMaxWidth(width * 0.7f).height(8.dp)
                    .clip(CircleShape).background(scheme.onSurfaceVariant.copy(alpha = 0.18f))
            )
        }
    }
}

@Composable
private fun PreviewDock(icons: List<ImageVector>, modifier: Modifier, spread: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = if (spread) Arrangement.SpaceEvenly else Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEachIndexed { index, icon ->
            val selected = index == 2
            Box(
                modifier = Modifier
                    .size(width = if (spread) 56.dp else 44.dp, height = 32.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.secondaryContainer else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
