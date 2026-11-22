package takagi.ru.monica.steam.design.telegram

import takagi.ru.monica.steam.navigation.liquidglass.ui.*
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.steamLiquidGlassBackdropSource

import takagi.ru.monica.steam.navigation.SteamDockStyle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.monica.telegramchat.TelegramGlassView

internal val LocalSteamTelegramSourceView = staticCompositionLocalOf<View?> { null }

/**
 * One independently composed content View, with sibling overlays. The View boundary stays
 * mounted when changing styles so drafts, focus and navigation state are not recreated.
 * The native Telegram renderer captures only the overlay's clipped rectangle of this source.
 */
@Composable
internal fun SteamTelegramGlassHost(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    val backdrop = rememberSteamLiquidGlassBackdrop()
    val material = steamGlassSupported() && enabled
    val parent = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    val parentDensity by rememberUpdatedState(LocalDensity.current)
    val sourceId = rememberSaveable { View.generateViewId() }
    var source by remember { mutableStateOf<ComposeView?>(null) }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize().steamLiquidGlassBackdropSource(backdrop, material && backdrop.consumers > 0),
            factory = { context ->
                ComposeView(context).apply {
                    id = sourceId
                    setParentCompositionContext(parent)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        // Do not inherit an ancestor's source into content: a nested surface
                        // must supply its own sibling source before it can render glass.
                        // ComposeView installs fresh Android locals. Carry the caller's density
                        // explicitly so app text scaling survives this separate view boundary.
                        CompositionLocalProvider(
                            LocalSteamTelegramSourceView provides null,
                            LocalSteamGlassBackdrop provides null,
                            LocalDensity provides parentDensity
                        ) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { latestContent() }
                        }
                    }
                    source = this
                }
            },
            onRelease = { it.disposeComposition(); if (source === it) source = null }
        )
        CompositionLocalProvider(LocalSteamTelegramSourceView provides source.takeIf { enabled },
            LocalSteamGlassBackdrop provides backdrop.takeIf { material }) {
            overlay()
        }
    }
}

/** Uses the compiled Telegram AGSL/blur/stroke implementation, including its older-API fallback. */
@Composable
internal fun SteamTelegramGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val source = LocalSteamTelegramSourceView.current
    val colors = MaterialTheme.colorScheme
    val radius = with(LocalDensity.current) { cornerRadius.toPx() }
    val tint = colors.surfaceContainer.copy(alpha = if (source == null) .96f else .78f).toArgb()
    val dark = colors.background.luminance() < .5f
    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { TelegramGlassView(it) },
            update = {
                it.setSourceView(source)
                it.setLiquidStyle(liquid)
                it.setCornerRadius(radius)
                it.setTintColor(tint)
                it.setDark(dark)
            },
            onRelease = { it.setSourceView(null) }
        )
        content()
    }
}
