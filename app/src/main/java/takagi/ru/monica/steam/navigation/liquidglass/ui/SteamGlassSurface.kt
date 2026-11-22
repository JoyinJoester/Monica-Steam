/*
 * Glass material for floating controls, sharing the Dock's Miuix backdrop
 * pipeline (Apache-2.0) and AndroidLiquidGlass lens (Apache-2.0).
 */
package takagi.ru.monica.steam.navigation.liquidglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.render.SteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.isSteamLiquidGlassRuntimeSupported
import takagi.ru.monica.steam.navigation.liquidglass.render.liquidGlassLens
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.steamLiquidGlassBackdropSource
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.drawBackdrop

/**
 * App-wide design style chosen in Settings › Appearance › Design style.
 * [SteamDockStyle.FIXED] is presented as M3E; the stored values are unchanged.
 */
val LocalSteamDesignStyle = staticCompositionLocalOf { SteamDockStyle.FIXED }

/** Content recorded behind the floating controls of the current surface, if any. */
internal val LocalSteamGlassBackdrop = staticCompositionLocalOf<SteamLiquidGlassBackdrop?> { null }

/** Independent overlay sources only; never publish a source into its own recorded content. */
internal val LocalSteamPopupBackdrop = staticCompositionLocalOf<SteamLiquidGlassBackdrop?> { null }
internal val LocalSteamGlassEffectsEnabled = staticCompositionLocalOf { true }
internal enum class SteamGlassRole { CONTROL, TOOLBAR, PANEL, FILTER }

@Composable
internal fun steamGlassSupported(): Boolean =
    android.os.Build.VERSION.SDK_INT >= 31 && LocalSteamGlassEffectsEnabled.current

@Composable
internal fun steamGlassActive(): Boolean =
    steamGlassSupported() && LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS

@Composable
internal fun SteamGlassHost(
    modifier: Modifier = Modifier,
    backdropColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    val supported = steamGlassSupported()
    val backdrop = rememberSteamLiquidGlassBackdrop()
    Box(modifier) {
        Box(Modifier.matchParentSize()
            .steamLiquidGlassBackdropSource(backdrop, supported && backdrop.consumers > 0)
            .background(backdropColor)) {
            CompositionLocalProvider(LocalSteamGlassBackdrop provides null,
                LocalSteamPopupBackdrop provides backdrop.takeIf { supported }) { content() }
        }
        CompositionLocalProvider(LocalSteamGlassBackdrop provides backdrop.takeIf { supported },
            LocalSteamPopupBackdrop provides backdrop.takeIf { supported }) { overlay() }
    }
}

@Composable
internal fun steamGlassAvailable(): Boolean =
    steamGlassSupported() && LocalSteamGlassBackdrop.current != null

/** Glass tint derived from the current theme, so dynamic and custom palettes carry into glass. */
internal fun ColorScheme.steamGlassTint(): Color =
    if (background.luminance() < 0.5f) {
        surfaceContainer.copy(alpha = 0.4f)
    } else {
        surfaceContainerLowest.copy(alpha = 0.4f)
    }

/** A Kyant lens for Liquid Glass; a quieter, more opaque frost for M3E. */
@Composable
internal fun Modifier.steamGlassSurface(
    shape: Shape,
    fallback: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    role: SteamGlassRole = SteamGlassRole.CONTROL,
    source: SteamLiquidGlassBackdrop? = LocalSteamGlassBackdrop.current
): Modifier {
    val supported = steamGlassSupported()
    val backdrop = source?.takeIf { supported } ?: return background(fallback, shape)
    // Registration follows the actual layout node (including a menu's exit animation),
    // rather than the caller that can remain composed while the popup is closed.
    val usage = Modifier.composed {
        androidx.compose.runtime.DisposableEffect(backdrop) {
            backdrop.consumers++
            onDispose { backdrop.consumers-- }
        }
        Modifier
    }
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = fallback.copy(alpha = when {
        !liquid && role == SteamGlassRole.FILTER -> if (dark) .48f else .60f
        !liquid -> .88f
        role == SteamGlassRole.PANEL -> .78f
        role == SteamGlassRole.TOOLBAR -> .72f
        dark -> .52f
        else -> .58f
    })
    val radius = if (liquid) 6.dp else if (role == SteamGlassRole.FILTER) 18.dp else 10.dp
    val shadow = if (role == SteamGlassRole.CONTROL || role == SteamGlassRole.FILTER) 6.dp else 0.dp
    // A zero-radius dropShadow still draws an opaque mask. Avoid retaining an
    // earlier contour outside a moving toolbar's newly rounded glass surface.
    val base = this.then(usage).then(if (shadow > 0.dp) Modifier.dropShadow(shape,
        Shadow(radius = shadow, color = Color.Black, alpha = if (dark) .18f else .09f)) else Modifier)
    // Backdrop 2.0.1 refreshes effect drawing on a shape update, but its placement
    // layer can retain the old outline at a fixed size. The toolbar's changing
    // contour therefore owns an explicit outer Compose clip; the blur layer stays
    // rectangular so it cannot cut away newly flattened corners or a meniscus.
    val clippedBase = if (role == SteamGlassRole.TOOLBAR) base.clip(shape) else base
    return clippedBase.drawBackdrop(
        backdrop = backdrop.screenAligned,
        shape = { if (role == SteamGlassRole.TOOLBAR) RectangleShape else shape },
        effects = {
            blur(radius.toPx())
            if (liquid && role != SteamGlassRole.TOOLBAR) {
                val rim = minOf(12.dp.toPx(), size.minDimension / 4f)
                lens(refractionHeight = rim, refractionAmount = rim * 1.25f)
            }
        },
        highlight = { if (liquid && role != SteamGlassRole.TOOLBAR) com.kyant.backdrop.highlight.Highlight.Default else null },
        shadow = null,
        // onDrawBehind runs outside the backdrop mask. Its base must use exactly
        // the same outline, otherwise asymmetric corners expose a solid rectangle.
        onDrawBehind = { drawOutline(shape.createOutline(size, layoutDirection, this), fallback) },
        onDrawSurface = {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, tint)
            if (liquid && role == SteamGlassRole.TOOLBAR) {
                drawOutline(outline, Color.White.copy(alpha = if (dark) .22f else .38f),
                    style = Stroke(1.dp.toPx()))
            }
        }
    )
}

