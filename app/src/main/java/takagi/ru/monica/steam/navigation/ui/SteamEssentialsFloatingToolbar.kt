package takagi.ru.monica.steam.navigation.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.navigation.SteamDockTab
import takagi.ru.monica.steam.navigation.dockSwipeTarget

/**
 * Space reserved by every top-level Steam page for the floating toolbar and
 * the system navigation area. Child screens inherit this from the activity.
 */
internal val SteamDockContentClearance = 104.dp

internal data class SteamToolbarItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val hasBadge: Boolean = false
)

/**
 * Handles page switching only while the pointer is inside the floating Dock.
 * Content lists never receive this modifier, so their normal vertical and
 * horizontal gestures remain independent from top-level navigation.
 */
internal fun Modifier.steamDockSwipe(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    thresholdPx: Float,
    onSelected: (SteamDockTab) -> Unit
): Modifier = pointerInput(order, selected, thresholdPx) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragEnd = {
            dockSwipeTarget(
                order = order,
                selected = selected,
                totalDragPx = totalDrag,
                thresholdPx = thresholdPx
            )?.let(onSelected)
            totalDrag = 0f
        },
        onDragCancel = { totalDrag = 0f },
        onHorizontalDrag = { change, dragAmount ->
            totalDrag += dragAmount
            change.consume()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamEssentialsFloatingToolbar(
    modifier: Modifier = Modifier,
    items: List<SteamToolbarItem>,
    selectedIndex: Int = -1,
    floatingActionButton: (@Composable () -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    expanded: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val screenWidth = configuration.screenWidthDp
    val isLargeFont = fontScale > 1.25f
    val isCompactScreen = screenWidth < 400
    val shouldHideLabel = isLargeFont || (isCompactScreen && items.size > 3)

    HorizontalFloatingToolbar(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 0.dp),
        expanded = expanded,
        floatingActionButton = floatingActionButton ?: {},
        scrollBehavior = scrollBehavior,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
            toolbarContainerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            val itemWidth by animateDpAsState(
                targetValue = if (expanded || isSelected) 48.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "steam_toolbar_item_width_$index"
            )
            val labelWidth by animateDpAsState(
                targetValue = if (isSelected && !shouldHideLabel) 80.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "steam_toolbar_label_width_$index"
            )
            val spacerWidth by animateDpAsState(
                targetValue = if (index < items.lastIndex) 8.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "steam_toolbar_spacer_width_$index"
            )

            if (itemWidth > 0.dp || isSelected) {
                IconButton(
                    onClick = item.onClick,
                    modifier = Modifier
                        .width(itemWidth + labelWidth)
                        .height(48.dp),
                    colors = if (isSelected) {
                        IconButtonDefaults.filledIconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    } else {
                        IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.background,
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        if (isSelected && !shouldHideLabel) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }

                if (index < items.lastIndex) {
                    Spacer(modifier = Modifier.width(spacerWidth))
                }
            }
        }
    }
}
