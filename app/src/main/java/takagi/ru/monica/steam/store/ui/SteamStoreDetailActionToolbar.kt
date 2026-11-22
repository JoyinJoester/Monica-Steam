package takagi.ru.monica.steam.store.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassRole
import takagi.ru.monica.steam.navigation.liquidglass.ui.steamGlassSurface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.ui.LocalReduceAnimations
import kotlin.math.roundToInt

private const val AUTO_COLLAPSE_MILLIS = 3_500L
internal const val STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP = 48

internal enum class SteamStoreDetailToolbarEdge {
    LEFT,
    RIGHT
}

internal data class SteamStoreDetailToolbarCornerRadii(
    val topStartDp: Int,
    val topEndDp: Int,
    val bottomStartDp: Int,
    val bottomEndDp: Int
)

@Composable
internal fun SteamStoreDetailActionToolbar(
    onOpenPurchaseOptions: (() -> Unit)?,
    onOpenOfficialStore: () -> Unit,
    onOpenReviews: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    SteamDraggableDetailToolbar(modifier) { interact ->
        if (onOpenPurchaseOptions != null) SteamStoreDetailToolbarAction(Icons.Default.ShoppingBag,
            stringResource(R.string.steam_store_detail_purchase_options)) { interact(); onOpenPurchaseOptions() }
        SteamStoreDetailToolbarAction(Icons.Default.Storefront,
            stringResource(R.string.steam_store_open_official)) { interact(); onOpenOfficialStore() }
        if (onOpenReviews != null) SteamStoreDetailToolbarAction(Icons.Default.RateReview,
            stringResource(R.string.steam_store_detail_jump_to_reviews)) { interact(); onOpenReviews() }
        if (onShare != null) SteamStoreDetailToolbarAction(Icons.Default.Share,
            stringResource(R.string.steam_store_detail_share)) { interact(); onShare() }
    }
}

/** Reuses the original drag, edge attachment, saved position and delayed collapse behavior. */
@Composable
internal fun SteamDraggableDetailToolbar(
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit) -> Unit
) {
    val reduceAnimations = LocalReduceAnimations.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    var attachedToLeft by rememberSaveable { mutableStateOf(false) }
    var verticalFraction by rememberSaveable { mutableFloatStateOf(0.48f) }
    var interactionGeneration by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(expanded, dragging, interactionGeneration) {
        if (!expanded || dragging) return@LaunchedEffect
        delay(AUTO_COLLAPSE_MILLIS)
        expanded = false
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidth = constraints.maxWidth
        val containerHeight = constraints.maxHeight
        val fallbackWidth = with(density) {
            (if (expanded) 52.dp else STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP.dp).roundToPx()
        }
        val fallbackHeight = with(density) {
            (if (expanded) 196.dp else STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP.dp).roundToPx()
        }
        val toolbarWidth = toolbarSize.width.takeIf { it > 0 } ?: fallbackWidth
        val toolbarHeight = toolbarSize.height.takeIf { it > 0 } ?: fallbackHeight
        val maxX = (containerWidth - toolbarWidth).coerceAtLeast(0)
        val maxY = (containerHeight - toolbarHeight).coerceAtLeast(0)
        val snappedOffset = IntOffset(
            x = if (attachedToLeft) 0 else maxX,
            y = (verticalFraction * maxY).roundToInt().coerceIn(0, maxY)
        )
        val targetOffset = if (dragging) {
            IntOffset(
                x = dragPosition.x.roundToInt().coerceIn(0, maxX),
                y = dragPosition.y.roundToInt().coerceIn(0, maxY)
            )
        } else {
            snappedOffset
        }
        val animatedOffset by animateIntOffsetAsState(
            targetValue = targetOffset,
            animationSpec = if (reduceAnimations) {
                snap()
            } else {
                spring(
                    dampingRatio = if (dragging) .82f else .72f,
                    stiffness = if (dragging) 1100f else 420f
                )
            },
            label = "steam_store_detail_toolbar_position"
        )
        val renderedOffset = IntOffset(animatedOffset.x.coerceIn(0, maxX), animatedOffset.y.coerceIn(0, maxY))
        val breakDistance = with(density) { STEAM_TOOLBAR_BREAK_DISTANCE_DP.dp.toPx() }
        val contactEdge = when {
            toolbarSize.width == 0 -> null
            renderedOffset.x <= 0 -> SteamStoreDetailToolbarEdge.LEFT
            renderedOffset.x >= maxX -> SteamStoreDetailToolbarEdge.RIGHT
            else -> null
        }
        var tetherEdge by remember { mutableStateOf<SteamStoreDetailToolbarEdge?>(null) }
        val contactRadius = remember(density.density) { Animatable(with(density) { 24.dp.toPx() }) }
        LaunchedEffect(contactEdge, reduceAnimations) {
            if (contactEdge != null) {
                tetherEdge = contactEdge
                if (reduceAnimations) contactRadius.snapTo(with(density) { 5.dp.toPx() })
                else {
                    // Contact happens first. Flatten the seam, then let surface tension return.
                    contactRadius.animateTo(0f, tween(85))
                    contactRadius.animateTo(with(density) { 5.dp.toPx() }, spring(.68f, 380f))
                }
            } else if (reduceAnimations) {
                tetherEdge = null
                contactRadius.snapTo(with(density) { 24.dp.toPx() })
            } else contactRadius.animateTo(with(density) { 24.dp.toPx() }, spring(.58f, 480f))
        }
        val gap = when (tetherEdge) {
            SteamStoreDetailToolbarEdge.LEFT -> renderedOffset.x.toFloat()
            SteamStoreDetailToolbarEdge.RIGHT -> (maxX - renderedOffset.x).toFloat()
            null -> 0f
        }.coerceAtLeast(0f)
        SideEffect { if (gap >= breakDistance) tetherEdge = null }
        val bridgeGap = if (reduceAnimations || gap >= breakDistance || tetherEdge == null) 0 else gap.roundToInt()
        val contourEdge = if (contactEdge != null) contactEdge else tetherEdge.takeIf { bridgeGap > 0 }
        val toolbarShape = SteamToolbarTensionShape(contourEdge, bridgeGap.toFloat(),
            contactRadius.value, breakDistance)
        val currentOffset by rememberUpdatedState(renderedOffset)
        val currentAttachedToLeft by rememberUpdatedState(attachedToLeft)

        Box(
            modifier = Modifier
                .offset { IntOffset(renderedOffset.x - if (tetherEdge == SteamStoreDetailToolbarEdge.LEFT) bridgeGap else 0, renderedOffset.y) }
                .testTag("game_detail_tools")
                .pointerInput(containerWidth, containerHeight, toolbarSize) {
                    detectDragGestures(
                        onDragStart = {
                            interactionGeneration++
                            dragging = true
                            dragPosition = Offset(
                                currentOffset.x.toFloat(),
                                currentOffset.y.toFloat()
                            )
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragPosition = Offset(
                                x = (dragPosition.x + amount.x).coerceIn(0f, maxX.toFloat()),
                                y = (dragPosition.y + amount.y).coerceIn(0f, maxY.toFloat())
                            )
                        },
                        onDragEnd = {
                            val edge = resolveSteamStoreDetailToolbarEdge(
                                leftPx = dragPosition.x,
                                toolbarWidthPx = toolbarWidth,
                                containerWidthPx = containerWidth
                            )
                            val edgeChanged = currentAttachedToLeft !=
                                (edge == SteamStoreDetailToolbarEdge.LEFT)
                            attachedToLeft = edge == SteamStoreDetailToolbarEdge.LEFT
                            verticalFraction = steamStoreDetailToolbarVerticalFraction(
                                topPx = dragPosition.y,
                                toolbarHeightPx = toolbarHeight,
                                containerHeightPx = containerHeight
                            )
                            dragging = false
                            interactionGeneration++
                            if (edgeChanged) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            interactionGeneration++
                        }
                    )
                }
        ) {
            SteamStoreDetailToolbarBody(
                expanded = expanded,
                toolbarShape = toolbarShape,
                bridgeGap = bridgeGap,
                edge = tetherEdge,
                reduceAnimations = reduceAnimations,
                onBodySize = { toolbarSize = it },
                onExpand = { expanded = true; interactionGeneration++ },
                onInteraction = { interactionGeneration++ },
                actions = actions
            )
        }
    }
}

@Composable
private fun SteamStoreDetailToolbarBody(
    expanded: Boolean,
    toolbarShape: Shape,
    bridgeGap: Int,
    edge: SteamStoreDetailToolbarEdge?,
    reduceAnimations: Boolean,
    onBodySize: (IntSize) -> Unit,
    onExpand: () -> Unit,
    onInteraction: () -> Unit,
    actions: @Composable (() -> Unit) -> Unit
) {
    val gapDp = with(LocalDensity.current) { bridgeGap.toDp() }
    Box(Modifier
        // A single live surface morphs in size; outgoing glass surfaces cannot overlap.
        .steamGlassSurface(toolbarShape, MaterialTheme.colorScheme.surfaceContainerHighest, SteamGlassRole.TOOLBAR)
        .clip(toolbarShape)
        .absolutePadding(left = if (edge == SteamStoreDetailToolbarEdge.LEFT) gapDp else 0.dp,
            right = if (edge == SteamStoreDetailToolbarEdge.RIGHT) gapDp else 0.dp)) {
        Box(Modifier.onSizeChanged(onBodySize)
            .then(if (reduceAnimations) Modifier else Modifier.animateContentSize(spring(.8f, 500f)))) {
        if (!expanded) {
            Box(Modifier.size(STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP.dp)
                .clickable(role = Role.Button,
                    onClickLabel = stringResource(R.string.steam_store_detail_toolbar_expand), onClick = onExpand),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.steam_store_detail_toolbar_expand),
                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
        } else Column(Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            actions(onInteraction)
        }
        }
    }
}

@Composable
private fun SteamStoreDetailToolbarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

internal fun resolveSteamStoreDetailToolbarEdge(
    leftPx: Float,
    toolbarWidthPx: Int,
    containerWidthPx: Int
): SteamStoreDetailToolbarEdge {
    val center = leftPx + toolbarWidthPx.coerceAtLeast(0) / 2f
    return if (center < containerWidthPx.coerceAtLeast(0) / 2f) {
        SteamStoreDetailToolbarEdge.LEFT
    } else {
        SteamStoreDetailToolbarEdge.RIGHT
    }
}

internal fun steamStoreDetailToolbarCornerRadii(
    edge: SteamStoreDetailToolbarEdge,
    dragging: Boolean
): SteamStoreDetailToolbarCornerRadii {
    if (dragging) {
        return SteamStoreDetailToolbarCornerRadii(
            topStartDp = 24,
            topEndDp = 24,
            bottomStartDp = 24,
            bottomEndDp = 24
        )
    }
    return if (edge == SteamStoreDetailToolbarEdge.LEFT) {
        SteamStoreDetailToolbarCornerRadii(
            topStartDp = 5,
            topEndDp = 24,
            bottomStartDp = 5,
            bottomEndDp = 24
        )
    } else {
        SteamStoreDetailToolbarCornerRadii(
            topStartDp = 24,
            topEndDp = 5,
            bottomStartDp = 24,
            bottomEndDp = 5
        )
    }
}

internal fun steamStoreDetailToolbarVerticalFraction(
    topPx: Float,
    toolbarHeightPx: Int,
    containerHeightPx: Int
): Float {
    val maxTop = (containerHeightPx - toolbarHeightPx).coerceAtLeast(0)
    if (maxTop == 0) return 0f
    return (topPx / maxTop.toFloat()).coerceIn(0f, 1f)
}
