/* Shared Dock material and motion, adapted from KernelSU (GPL-3.0) and BiliPai. */
package takagi.ru.monica.steam.navigation.liquidglass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.*
import kotlin.math.ceil

private const val VELOCITY_NORMALIZATION_DIVISOR = 10f
private const val VELOCITY_SCALE_X_MULTIPLIER = 0.75f
private const val VELOCITY_SCALE_Y_MULTIPLIER = 0.25f
private const val VELOCITY_SCALE_CLAMP = 0.2f
private const val LIQUID_GLASS_CAPTURE_ALPHA = 0.001f
private const val REDUCED_MOTION_FADE_OUT_DURATION_MILLIS = 90
internal fun steamLiquidGlassPressedScale(progress: Float): Float = lerp(1f, 88f / 56f, progress.coerceIn(0f, 1f))

/** Used directly by the Dock and every measured selector. Captured labels scale per slot. */
internal fun Modifier.steamGlassSelectionLens(
    backdrop: Backdrop?,
    fallback: Color,
    highlights: State<SteamGlassHighlights>,
    dark: Boolean,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    press: () -> Float
): Modifier {
    if (backdrop == null) return (if (layerBlock != null) graphicsLayer(layerBlock) else this).background(fallback, CircleShape)
    return drawBackdrop(backdrop = backdrop, shape = { CircleShape }, layerBlock = layerBlock, effects = {
        val progress = press()
        // Keep a fixed sampling extent through the whole animation.
        padding = ceil(14.dp.toPx())
        liquidGlassLens(
            refractionHeight = 10.dp.toPx() * progress,
            refractionAmount = 14.dp.toPx() * progress,
            depthEffect = true,
            chromaticAberration = 0.5f,
            retainZeroRefraction = true
        )
    }, highlight = { highlights.value.pill.copy(alpha = press()) }, onDrawSurface = {
        val progress = press()
        drawRect(if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f), alpha = 1f - progress)
        drawRect(Color.Black.copy(alpha = 0.03f * progress))
    }).liquidGlassInnerShadow(CircleShape) {
        LiquidGlassInnerShadow(radius = 8.dp * press(), color = Color.Black.copy(alpha = 0.15f), alpha = press())
    }
}

internal data class IndicatorTransform(val scaleX: Float, val scaleY: Float)

internal fun resolveIndicatorTransform(
    scaleProgress: Float,
    velocityItemsPerSecond: Float
): IndicatorTransform {
    val baseScale = steamLiquidGlassPressedScale(scaleProgress)
    val velocity = velocityItemsPerSecond / VELOCITY_NORMALIZATION_DIVISOR
    val velocityScaleX = (velocity * VELOCITY_SCALE_X_MULTIPLIER)
        .coerceIn(-VELOCITY_SCALE_CLAMP, VELOCITY_SCALE_CLAMP)
    val velocityScaleY = (velocity * VELOCITY_SCALE_Y_MULTIPLIER)
        .coerceIn(-VELOCITY_SCALE_CLAMP, VELOCITY_SCALE_CLAMP)
    return IndicatorTransform(
        scaleX = baseScale / (1f - velocityScaleX),
        scaleY = baseScale * (1f - velocityScaleY)
    )
}

@Composable
internal fun rememberIndicatorDragScaleProgress(
    isDragging: Boolean,
    reduceAnimations: Boolean
): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isDragging, reduceAnimations) {
        progress.animateTo(
            targetValue = if (isDragging) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (reduceAnimations) {
                    REDUCED_MOTION_FADE_OUT_DURATION_MILLIS
                } else if (isDragging) {
                    90
                } else {
                    220
                },
                easing = if (isDragging) EaseOut else FastOutSlowInEasing
            )
        )
    }
    return progress.asState()
}

// A zero-alpha RenderEffect layer may be elided or leak through on some Android GPUs.
// A near-zero offscreen layer keeps the backdrop recording alive without a visible export band.
internal fun Modifier.liquidGlassCaptureLayer(): Modifier = graphicsLayer {
    alpha = LIQUID_GLASS_CAPTURE_ALPHA
    compositingStrategy = CompositingStrategy.Offscreen
}

