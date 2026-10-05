/*
 * Visual structure adapted from KernelSU FloatingBottomBar commit
 * 778fb38bbf0c43f168b8bbd7d9e369d6fb46754b and verified against BiliPai's
 * KernelSU-aligned Dock. KernelSU is GPL-3.0; Miuix and AndroidLiquidGlass are
 * Apache-2.0.
 */
package takagi.ru.monica.steam.navigation.liquidglass.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.liquidglass.motion.LiquidGlassDockMotionState
import takagi.ru.monica.steam.navigation.liquidglass.motion.liquidGlassDockHorizontalDrag
import takagi.ru.monica.steam.navigation.liquidglass.motion.rememberLiquidGlassDockMotionState
import takagi.ru.monica.steam.navigation.liquidglass.render.LiquidGlassInnerShadow
import takagi.ru.monica.steam.navigation.liquidglass.render.SteamLiquidGlassBackdrop
import takagi.ru.monica.steam.navigation.liquidglass.render.isSteamLiquidGlassRuntimeSupported
import takagi.ru.monica.steam.navigation.liquidglass.render.liquidGlassInnerShadow
import takagi.ru.monica.steam.navigation.liquidglass.render.liquidGlassLens
import takagi.ru.monica.steam.navigation.liquidglass.render.rememberSteamCombinedBackdrop
import takagi.ru.monica.steam.navigation.ui.steamWindowBottomPadding
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private val SteamLiquidGlassIndicatorHighlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f
        ),
        dualPeak = true
    )
)

private const val LIGHT_REFERENCE_X = 0.5f
private const val LIGHT_REFERENCE_Y = 0.7f
private const val GRAVITY_DIRECTION_THRESHOLD_SQUARED = 0.01f
private const val INDICATOR_DRAG_SCALE_TARGET = 88f / 56f
private const val VELOCITY_NORMALIZATION_DIVISOR = 10f
private const val VELOCITY_SCALE_X_MULTIPLIER = 0.75f
private const val VELOCITY_SCALE_Y_MULTIPLIER = 0.25f
private const val VELOCITY_SCALE_CLAMP = 0.2f
private const val LIQUID_GLASS_CAPTURE_ALPHA = 0.001f
private const val REDUCED_MOTION_DURATION_MILLIS = 120
private const val REDUCED_MOTION_FADE_OUT_DURATION_MILLIS = 90

@Composable
internal fun SteamLiquidGlassDockVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduceAnimations = LocalReduceAnimations.current
    val enterEasing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val exitEasing = remember { CubicBezierEasing(0.32f, 0f, 0.67f, 0f) }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceAnimations) fadeIn(
            animationSpec = tween(durationMillis = REDUCED_MOTION_DURATION_MILLIS)
        ) else {
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 255, easing = enterEasing)) +
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
        },
        exit = if (reduceAnimations) fadeOut(
            animationSpec = tween(durationMillis = REDUCED_MOTION_FADE_OUT_DURATION_MILLIS)
        ) else {
            slideOutVertically(
                animationSpec = tween(durationMillis = 160, easing = exitEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 160, easing = exitEasing)) +
                scaleOut(
                    animationSpec = tween(durationMillis = 160, easing = exitEasing),
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
        }
    ) {
        content()
    }
}

@Composable
internal fun SteamLiquidGlassDock(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    backdrop: SteamLiquidGlassBackdrop,
    onSelected: (SteamDockTab) -> Unit,
    runtimeEffectsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val reduceAnimations = LocalReduceAnimations.current
    val tabs = remember(order) { SteamDockTab.completeLiquidGlassOrder(order) }
    if (tabs.isEmpty()) return

    val selectedIndex = tabs.indexOf(selected)
    val haptic = rememberHapticFeedback()
    val motionState = rememberLiquidGlassDockMotionState(
        initialIndex = selectedIndex.coerceAtLeast(0),
        itemCount = tabs.size,
        reduceMotion = reduceAnimations,
        onIndexChanged = { index ->
            tabs.getOrNull(index)?.let { tab ->
                haptic.performLightClick()
                onSelected(tab)
            }
        }
    )
    LaunchedEffect(selectedIndex, motionState) {
        if (selectedIndex >= 0) {
            motionState.updateIndex(selectedIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .steamWindowBottomPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val dockWidth = resolveLiquidGlassDockWidth(
            containerWidth = maxWidth,
            itemCount = tabs.size
        )
        val itemWidth = ((dockWidth - 8.dp) / tabs.size).coerceAtLeast(0.dp)
        val density = LocalDensity.current
        val itemWidthPx = with(density) { itemWidth.toPx() }.coerceAtLeast(1f)
        val dockWidthPx = with(density) { dockWidth.toPx() }.coerceAtLeast(1f)
        val panelOffsetPx by remember(motionState, dockWidthPx, density) {
            derivedStateOf {
                val fraction = (motionState.dragOffset / dockWidthPx).coerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val indicatorPosition by remember(motionState, tabs.size) {
            derivedStateOf {
                motionState.value.coerceIn(0f, (tabs.size - 1).toFloat())
            }
        }
        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val runtimeSupported = remember(runtimeEffectsEnabled) {
            runtimeEffectsEnabled && isSteamLiquidGlassRuntimeSupported()
        }
        val shellShape = CircleShape
        val shellContainerColor = if (runtimeSupported) {
            (if (isDarkTheme) Color(0xFF242424) else Color.White).copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
        val selectedColor = MaterialTheme.colorScheme.primary
        val unselectedColor = MaterialTheme.colorScheme.onSurface
        val shellBackdrop = rememberLayerBackdrop()
        val tabsBackdrop = rememberLayerBackdrop()
        val combinedBackdrop = rememberSteamCombinedBackdrop(
            shellBackdrop,
            tabsBackdrop
        )
        val (shellHighlight, pillHighlight) = rememberGravityRotatedHighlights(runtimeSupported)
        val dragScaleProgress = rememberIndicatorDragScaleProgress(
            isDragging = motionState.isDragging,
            reduceAnimations = reduceAnimations
        )
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .width(dockWidth)
                .height(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = panelOffsetPx }
                    .dropShadow(
                        shape = shellShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isDarkTheme) 0.2f else 0.1f
                        )
                    )
                    .then(if (runtimeSupported) Modifier.layerBackdrop(shellBackdrop) else Modifier)
                    .then(
                        if (runtimeSupported) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop.delegate,
                                shape = { shellShape },
                                effects = {
                                    liquidGlassVibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    liquidGlassLens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx()
                                    )
                                },
                                highlight = { shellHighlight().copy(alpha = 0.75f) },
                                layerBlock = {
                                    val width = size.width.coerceAtLeast(1f)
                                    val scale = lerp(
                                        1f,
                                        1f + 16.dp.toPx() / width,
                                        motionState.pressProgress
                                    )
                                    scaleX = scale
                                    scaleY = scale
                                },
                                onDrawSurface = { drawRect(shellContainerColor) }
                            )
                        } else {
                            Modifier.background(shellContainerColor, shellShape)
                        }
                    )
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(4.dp)
                    .graphicsLayer { translationX = panelOffsetPx }
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val coverage = {
                        (1f - abs(index.toFloat() - indicatorPosition)).coerceIn(0f, 1f)
                    }
                    SteamLiquidGlassDockItemVisual(
                        tab = tab,
                        itemWidth = itemWidth,
                        selectedAlpha = coverage,
                        contentColor = {
                            if (runtimeSupported) {
                                unselectedColor
                            } else {
                                lerpColor(unselectedColor, selectedColor, coverage())
                            }
                        },
                        scale = { 1f }
                    )
                }
            }

            if (runtimeSupported) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .width(dockWidth)
                        .align(Alignment.CenterStart)
                        .clearAndSetSemantics {}
                        .liquidGlassCaptureLayer()
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffsetPx }

                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp)
                            .graphicsLayer(colorFilter = ColorFilter.tint(selectedColor)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tab ->
                            SteamLiquidGlassDockItemVisual(
                                tab = tab,
                                itemWidth = itemWidth,
                                selectedAlpha = { 1f },
                                contentColor = { Color.White },
                                scale = { lerp(1f, 1.2f, motionState.pressProgress) }
                            )
                        }
                    }
                }
            }

            if (selectedIndex >= 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            translationX = itemWidthPx * indicatorPosition + panelOffsetPx
                            val indicatorTransform = resolveIndicatorTransform(
                                scaleProgress = maxOf(dragScaleProgress.value, motionState.pressProgress),
                                velocityItemsPerSecond = motionState.deformationVelocityItemsPerSecond
                            )
                            scaleX = indicatorTransform.scaleX
                            scaleY = indicatorTransform.scaleY
                        }
                        .then(
                            if (runtimeSupported) {
                                Modifier
                                    .drawBackdrop(
                                        backdrop = combinedBackdrop,
                                        shape = { shellShape },
                                        effects = {
                                            val progress = motionState.pressProgress
                                            liquidGlassLens(
                                                refractionHeight = 10.dp.toPx() * progress,
                                                refractionAmount = 14.dp.toPx() * progress,
                                                depthEffect = true,
                                                chromaticAberration = 0.5f
                                            )
                                        },
                                        highlight = {
                                            pillHighlight().copy(alpha = motionState.pressProgress)
                                        },
                                        onDrawSurface = {
                                            val progress = motionState.pressProgress
                                            drawRect(
                                                color = if (isDarkTheme) {
                                                    Color.White.copy(alpha = 0.1f)
                                                } else {
                                                    Color.Black.copy(alpha = 0.1f)
                                                },
                                                alpha = 1f - progress
                                            )
                                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                        }
                                    )
                                    .liquidGlassInnerShadow(shape = shellShape) {
                                        LiquidGlassInnerShadow(
                                            radius = 8.dp * motionState.pressProgress,
                                            color = Color.Black.copy(alpha = 0.15f),
                                            alpha = motionState.pressProgress
                                        )
                                    }
                            } else {
                                Modifier.background(
                                    selectedColor.copy(alpha = 0.15f),
                                    shellShape
                                )
                            }
                        )
                        .height(56.dp)
                        .width(itemWidth)
                        .align(Alignment.CenterStart)
                        .zIndex(2f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .alpha(0f)
                    .graphicsLayer { translationX = panelOffsetPx }
                    .liquidGlassDockHorizontalDrag(
                        motionState = motionState,
                        itemWidthPx = itemWidthPx
                    )
                    .zIndex(3f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    SteamLiquidGlassDockInputTarget(
                        itemWidth = itemWidth,
                        label = tab.liquidGlassLabel(),
                        selected = tab == selected,
                        onPressChanged = motionState::setPressed,
                        onClick = {
                            motionState.updateIndex(index)
                            haptic.performLightClick()
                            onSelected(tab)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.SteamLiquidGlassDockItemVisual(
    tab: SteamDockTab,
    itemWidth: Dp,
    selectedAlpha: () -> Float,
    contentColor: () -> Color,
    scale: () -> Float
) {
    val label = tab.liquidGlassLabel()
    Column(
        modifier = Modifier
            .width(itemWidth)
            .fillMaxHeight()
            .graphicsLayer {
                // Tint the cached glyph layer; animation must not recompose text.
                colorFilter = ColorFilter.tint(contentColor())
                val itemScale = scale()
                scaleX = itemScale
                scaleY = itemScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = tab.liquidGlassIcon(selected = false),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { alpha = 1f - selectedAlpha().coerceIn(0f, 1f) }
            )
            Icon(
                imageVector = tab.liquidGlassIcon(selected = true),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { alpha = selectedAlpha().coerceIn(0f, 1f) }
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun RowScope.SteamLiquidGlassDockInputTarget(
    itemWidth: Dp,
    label: String,
    selected: Boolean,
    onPressChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnPressChanged by rememberUpdatedState(onPressChanged)

    LaunchedEffect(isPressed) {
        currentOnPressChanged(isPressed)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnPressChanged(false) }
    }

    Box(
        modifier = Modifier
            .width(itemWidth)
            .fillMaxHeight()
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
    )
}

private data class IndicatorTransform(val scaleX: Float, val scaleY: Float)

private fun resolveIndicatorTransform(
    scaleProgress: Float,
    velocityItemsPerSecond: Float
): IndicatorTransform {
    val baseScale = lerp(
        1f,
        INDICATOR_DRAG_SCALE_TARGET,
        scaleProgress.coerceIn(0f, 1f)
    )
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
private fun rememberIndicatorDragScaleProgress(
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

@Composable
private fun rememberGravityRotatedHighlights(enabled: Boolean): Pair<() -> Highlight, () -> Highlight> {
    // One subscription, and only while the GPU effects are actually in use.
    val tilt = if (enabled) rememberDeviceTilt() else null
    return remember(tilt) {
        val shell = derivedStateOf {
            val value = tilt?.value
            rotatedDockHighlight(value?.gravityX ?: 0f, value?.gravityY ?: -1f, extraDegrees = -45f)
        }
        val pill = derivedStateOf {
            val value = tilt?.value
            rotatedDockHighlight(value?.gravityX ?: 0f, value?.gravityY ?: -1f, extraDegrees = 90f)
        }
        // Read tilt in the draw callback, never in the Dock's composition.
        Pair({ shell.value }, { pill.value })
    }
}

private fun rotatedDockHighlight(gravityX: Float, gravityY: Float, extraDegrees: Float): Highlight {
    val base = SteamLiquidGlassIndicatorHighlight
    val style = base.style as BloomStroke
    val magnitudeSquared = gravityX * gravityX + gravityY * gravityY
    val inverseMagnitude = if (magnitudeSquared > GRAVITY_DIRECTION_THRESHOLD_SQUARED) 1f / sqrt(magnitudeSquared) else 0f
    val lightX = gravityX * inverseMagnitude
    val lightY = if (inverseMagnitude == 0f) -1f else gravityY * inverseMagnitude
    val radians = extraDegrees * PI / 180.0
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val position = LightPosition(
        x = LIGHT_REFERENCE_X + cosine * lightX - sine * lightY,
        y = LIGHT_REFERENCE_Y + sine * lightX + cosine * lightY,
        z = style.primaryLight.position.z
    )
    return base.copy(style = style.copy(primaryLight = style.primaryLight.copy(position = position)))
}

private fun BackdropEffectScope.liquidGlassVibrancy() {
    colorControls(brightness = 0f, contrast = 1f, saturation = 1.5f)
}

// A zero-alpha RenderEffect layer may be elided or leak through on some Android GPUs.
// A near-zero offscreen layer keeps the backdrop recording alive without a visible export band.
private fun Modifier.liquidGlassCaptureLayer(): Modifier = graphicsLayer {
    alpha = LIQUID_GLASS_CAPTURE_ALPHA
    compositingStrategy = CompositingStrategy.Offscreen
}

private fun resolveLiquidGlassDockWidth(containerWidth: Dp, itemCount: Int): Dp {
    val safeItemCount = itemCount.coerceAtLeast(1)
    val preferredWidth = 76.dp * safeItemCount + 8.dp
    val minimumWidth = 52.dp * safeItemCount + 8.dp
    val widthCap = (containerWidth - 40.dp).coerceAtLeast(minimumWidth)
    return minOf(preferredWidth, widthCap).coerceAtMost(containerWidth)
}

@Composable
private fun SteamDockTab.liquidGlassLabel(): String = when (this) {
    SteamDockTab.STORE -> stringResource(R.string.steam_store_title)
    SteamDockTab.LIBRARY -> stringResource(R.string.steam_dock_library_label)
    SteamDockTab.CHAT -> stringResource(R.string.steam_dock_chat_label)
    SteamDockTab.TOKEN -> stringResource(R.string.steam_dock_token)
    SteamDockTab.SETTINGS -> stringResource(R.string.settings_title)
}

private fun SteamDockTab.liquidGlassIcon(selected: Boolean): ImageVector = when (this) {
    SteamDockTab.STORE -> if (selected) Icons.Filled.Storefront else Icons.Outlined.Storefront
    SteamDockTab.LIBRARY -> if (selected) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports
    SteamDockTab.CHAT -> if (selected) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline
    SteamDockTab.TOKEN -> if (selected) Icons.Filled.Security else Icons.Outlined.Security
    SteamDockTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
