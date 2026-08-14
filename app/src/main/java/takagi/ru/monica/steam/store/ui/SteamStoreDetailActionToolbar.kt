package takagi.ru.monica.steam.store.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    onOpenPurchaseOptions: () -> Unit,
    onOpenOfficialStore: () -> Unit,
    onOpenReviews: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
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
            (if (expanded) 44.dp else STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP.dp).roundToPx()
        }
        val fallbackHeight = with(density) {
            (if (expanded) 164.dp else STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP.dp).roundToPx()
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
            animationSpec = if (dragging || reduceAnimations) {
                snap()
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            },
            label = "steam_store_detail_toolbar_position"
        )
        val currentOffset by rememberUpdatedState(animatedOffset)
        val currentAttachedToLeft by rememberUpdatedState(attachedToLeft)

        Box(
            modifier = Modifier
                .offset { animatedOffset }
                .onSizeChanged { toolbarSize = it }
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
            if (reduceAnimations) {
                SteamStoreDetailToolbarBody(
                    expanded = expanded,
                    dragging = dragging,
                    edge = if (attachedToLeft) {
                        SteamStoreDetailToolbarEdge.LEFT
                    } else {
                        SteamStoreDetailToolbarEdge.RIGHT
                    },
                    onExpand = {
                        expanded = true
                        interactionGeneration++
                    },
                    onInteraction = { interactionGeneration++ },
                    onOpenPurchaseOptions = onOpenPurchaseOptions,
                    onOpenOfficialStore = onOpenOfficialStore,
                    onOpenReviews = onOpenReviews,
                    onShare = onShare
                )
            } else {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.82f))
                            .togetherWith(fadeOut() + scaleOut(targetScale = 0.82f))
                            .using(SizeTransform(clip = false))
                    },
                    label = "steam_store_detail_toolbar_collapse"
                ) { isExpanded ->
                    SteamStoreDetailToolbarBody(
                        expanded = isExpanded,
                        dragging = dragging,
                        edge = if (attachedToLeft) {
                            SteamStoreDetailToolbarEdge.LEFT
                        } else {
                            SteamStoreDetailToolbarEdge.RIGHT
                        },
                        onExpand = {
                            expanded = true
                            interactionGeneration++
                        },
                        onInteraction = { interactionGeneration++ },
                        onOpenPurchaseOptions = onOpenPurchaseOptions,
                        onOpenOfficialStore = onOpenOfficialStore,
                        onOpenReviews = onOpenReviews,
                        onShare = onShare
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamStoreDetailToolbarBody(
    expanded: Boolean,
    dragging: Boolean,
    edge: SteamStoreDetailToolbarEdge,
    onExpand: () -> Unit,
    onInteraction: () -> Unit,
    onOpenPurchaseOptions: () -> Unit,
    onOpenOfficialStore: () -> Unit,
    onOpenReviews: () -> Unit,
    onShare: () -> Unit
) {
    val cornerRadii = steamStoreDetailToolbarCornerRadii(edge, dragging)
    val toolbarShape = RoundedCornerShape(
        topStart = cornerRadii.topStartDp.dp,
        topEnd = cornerRadii.topEndDp.dp,
        bottomStart = cornerRadii.bottomStartDp.dp,
        bottomEnd = cornerRadii.bottomEndDp.dp
    )
    if (!expanded) {
        Surface(
            shape = toolbarShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp
        ) {
            Box(
                modifier = Modifier
                    .size(STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.steam_store_detail_toolbar_expand),
                        onClick = onExpand
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.steam_store_detail_toolbar_expand),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        return
    }

    Surface(
        shape = toolbarShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.ShoppingBag,
                contentDescription = stringResource(
                    R.string.steam_store_detail_purchase_options
                ),
                onClick = {
                    onInteraction()
                    onOpenPurchaseOptions()
                }
            )
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.Storefront,
                contentDescription = stringResource(R.string.steam_store_open_official),
                onClick = {
                    onInteraction()
                    onOpenOfficialStore()
                }
            )
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.RateReview,
                contentDescription = stringResource(
                    R.string.steam_store_detail_jump_to_reviews
                ),
                onClick = {
                    onInteraction()
                    onOpenReviews()
                }
            )
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.steam_store_detail_share),
                onClick = {
                    onInteraction()
                    onShare()
                }
            )
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
            .size(40.dp)
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
