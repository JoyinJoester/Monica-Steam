package takagi.ru.monica.steam.foundation.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import takagi.ru.monica.R
import takagi.ru.monica.ui.password.MonicaTopActionsDropdownMenu

private const val PULL_REFRESH_HIDDEN_EPSILON = 0.001f
private const val PULL_REFRESH_OVERSHOOT_RESISTANCE = 0.18f
private const val PULL_REFRESH_MAX_VISUAL_FRACTION = 1.15f

data class SteamPageOverflowAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun SteamPageOverflowMenu(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    additionalActions: List<SteamPageOverflowAction> = emptyList()
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        MonicaTopActionsDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_notifications_title)) },
                leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenNotifications()
                }
            )
            additionalActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.refresh)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                enabled = !refreshing,
                onClick = {
                    expanded = false
                    onRefresh()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SteamExpressivePullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
        return
    }

    val state = rememberPullToRefreshState()
    val visualIndicatorState = remember(state) {
        VisualPullToRefreshState(state)
    }
    val positionalThresholdPx = with(LocalDensity.current) {
        PullToRefreshDefaults.PositionalThreshold.toPx()
    }
    var keepContentAtRestUntilHidden by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing, state.distanceFraction) {
        when {
            refreshing -> keepContentAtRestUntilHidden = true
            keepContentAtRestUntilHidden &&
                state.distanceFraction <= PULL_REFRESH_HIDDEN_EPSILON -> {
                keepContentAtRestUntilHidden = false
            }
        }
    }

    val trackPull = !refreshing &&
        !keepContentAtRestUntilHidden &&
        !state.isAnimating
    val contentOffsetTargetPx = calculatePullRefreshContentOffsetPx(
        distanceFraction = state.distanceFraction,
        positionalThresholdPx = positionalThresholdPx,
        trackPull = trackPull
    )
    val contentOffsetPx by animateFloatAsState(
        targetValue = contentOffsetTargetPx,
        animationSpec = if (trackPull) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "steam_page_pull_content_offset"
    )

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = visualIndicatorState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, contentOffsetPx.roundToInt()) }
        ) {
            content()
        }
    }
}

internal fun calculatePullRefreshContentOffsetPx(
    distanceFraction: Float,
    positionalThresholdPx: Float,
    trackPull: Boolean
): Float {
    if (
        !trackPull ||
        !distanceFraction.isFinite() ||
        !positionalThresholdPx.isFinite() ||
        positionalThresholdPx <= 0f
    ) {
        return 0f
    }
    return calculatePullRefreshVisualFraction(distanceFraction) * positionalThresholdPx
}

internal fun calculatePullRefreshVisualFraction(distanceFraction: Float): Float {
    if (!distanceFraction.isFinite()) return 0f
    val normalizedFraction = distanceFraction.coerceAtLeast(0f)
    val resistedFraction = if (normalizedFraction <= 1f) {
        normalizedFraction
    } else {
        1f + (normalizedFraction - 1f) * PULL_REFRESH_OVERSHOOT_RESISTANCE
    }
    return resistedFraction.coerceAtMost(PULL_REFRESH_MAX_VISUAL_FRACTION)
}

private class VisualPullToRefreshState(
    private val delegate: PullToRefreshState
) : PullToRefreshState {
    override val distanceFraction: Float
        get() = calculatePullRefreshVisualFraction(delegate.distanceFraction)

    override val isAnimating: Boolean
        get() = delegate.isAnimating

    override suspend fun animateToThreshold() {
        delegate.animateToThreshold()
    }

    override suspend fun animateToHidden() {
        delegate.animateToHidden()
    }

    override suspend fun snapTo(targetValue: Float) {
        delegate.snapTo(targetValue)
    }
}
