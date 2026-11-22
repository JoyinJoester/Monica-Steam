/*
 * Backdrop composition follows the compose-miuix-ui and KernelSU liquid-glass
 * examples. Miuix is Apache-2.0; KernelSU is GPL-3.0.
 */
package takagi.ru.monica.steam.navigation.liquidglass.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Stable
internal class SteamLiquidGlassBackdrop internal constructor(
    internal val delegate: LayerBackdrop
) {
    internal var consumers by mutableIntStateOf(0)
    internal var sourceCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    private var drawGeneration by mutableIntStateOf(0)

    internal fun sourceDrawn() {
        // Source drawing must not subscribe to its own notification.
        Snapshot.withoutReadObservation { drawGeneration++ }
    }

    // Popup and dialog coordinates belong to another window. Window-relative offsets
    // would sample the wrong area, so these consumers share screen-relative coordinates.
    internal val screenAligned: com.kyant.backdrop.Backdrop = object : com.kyant.backdrop.Backdrop {
        override val isCoordinatesDependent = true
        override fun DrawScope.drawBackdrop(
            density: Density, coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?
        ) {
            val source = sourceCoordinates?.takeIf { it.isAttached } ?: return
            val target = coordinates?.takeIf { it.isAttached } ?: return
            val offset = if (source.findRootCoordinates() === target.findRootCoordinates()) {
                // Keep content coordinates during a shared page scale/translation transition.
                target.localPositionOf(source, Offset.Zero)
            } else {
                // A GraphicsLayer change invalidates consumers in its ViewRoot, but a
                // popup/dialog has a separate RenderThread display list. Subscribe only
                // that window to source redraws; same-window consumers need no extra frame.
                @Suppress("UNUSED_VARIABLE") val generation = drawGeneration
                source.positionOnScreen() - target.positionOnScreen()
            }
            if (!offset.x.isFinite() || !offset.y.isFinite()) return
            translate(offset.x, offset.y) { drawLayer(delegate.graphicsLayer) }
        }
    }
}

@Composable
internal fun rememberSteamLiquidGlassBackdrop(): SteamLiquidGlassBackdrop {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { SteamLiquidGlassBackdrop(backdrop) }
}

internal fun Modifier.steamLiquidGlassBackdropSource(
    backdrop: SteamLiquidGlassBackdrop,
    enabled: Boolean
): Modifier = if (enabled) {
    onGloballyPositioned { backdrop.sourceCoordinates = it }
        .drawWithContent { drawContent(); backdrop.sourceDrawn() }
        .layerBackdrop(backdrop.delegate)
} else this

internal fun isSteamLiquidGlassRuntimeSupported(): Boolean = isRuntimeShaderSupported()

@Stable
private class CombinedBackdrop(
    private val first: Backdrop,
    private val second: Backdrop
) : Backdrop {
    override val isCoordinatesDependent: Boolean =
        first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
internal fun rememberSteamCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }
