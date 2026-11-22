/*
 * Adapted from Kyant0/AndroidLiquidGlass and the compose-miuix-ui example
 * used by KernelSU. Both upstream implementations are Apache-2.0.
 */
package takagi.ru.monica.steam.navigation.liquidglass.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Immutable
internal data class LiquidGlassInnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
)

internal fun Modifier.liquidGlassInnerShadow(
    shape: Shape,
    shadow: () -> LiquidGlassInnerShadow?
): Modifier = this then LiquidGlassInnerShadowElement(shape, shadow)

private class LiquidGlassInnerShadowElement(
    val shape: Shape,
    val shadow: () -> LiquidGlassInnerShadow?
) : ModifierNodeElement<LiquidGlassInnerShadowNode>() {
    override fun create(): LiquidGlassInnerShadowNode =
        LiquidGlassInnerShadowNode(shape, shadow)

    override fun update(node: LiquidGlassInnerShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlassInnerShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidGlassInnerShadowElement) return false
        return shape == other.shape && shadow == other.shadow
    }

    override fun hashCode(): Int = 31 * shape.hashCode() + shadow.hashCode()
}

private class LiquidGlassInnerShadowNode(
    var shape: Shape,
    var shadow: () -> LiquidGlassInnerShadow?
) : Modifier.Node(), DrawModifierNode {
    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private val clipPath = Path()
    private var previousRadius = Float.NaN
    private var previousOutline: Outline? = null
    private var previousSize = Size.Unspecified
    private var previousColor = Color.Unspecified
    private var previousOffsetX = Float.NaN
    private var previousOffsetY = Float.NaN
    private var shadowRecorded = false

    override fun ContentDrawScope.draw() {
        drawContent()
        val resolvedShadow = shadow() ?: return
        if (resolvedShadow.alpha <= 0f) return
        val layer = shadowLayer ?: requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
            shadowLayer = this
        }
        val radius = resolvedShadow.radius.toPx()
        val offsetX = resolvedShadow.offset.x.toPx()
        val offsetY = resolvedShadow.offset.y.toPx()

        val outline = shape.createOutline(size, layoutDirection, this)
        // Generic outlines may wrap a mutable Path; do not reuse its old mask.
        val outlineChanged = previousOutline != outline || previousSize != size || outline is Outline.Generic
        if (outlineChanged) {
            clipPath.reset()
            when (outline) {
                is Outline.Rectangle -> clipPath.addRect(outline.rect)
                is Outline.Rounded -> clipPath.addRoundRect(outline.roundRect)
                is Outline.Generic -> clipPath.addPath(outline.path)
            }
        }

        paint.color = resolvedShadow.color
        layer.alpha = resolvedShadow.alpha
        layer.blendMode = resolvedShadow.blendMode
        if (previousRadius != radius) {
            layer.renderEffect = if (radius > 0f) {
                BlurEffect(radius, radius, TileMode.Decal)
            } else {
                null
            }
            previousRadius = radius
        }

        // Moving the Dock, changing highlight direction or fading this shadow
        // can reuse the mask. Only geometry, offset and color require recording.
        if (!shadowRecorded || outlineChanged || previousColor != resolvedShadow.color ||
            previousOffsetX != offsetX || previousOffsetY != offsetY
        ) {
            layer.record {
                drawContext.canvas.let { canvas ->
                    canvas.save()
                    canvas.clipPath(clipPath)
                    canvas.drawOutline(outline, paint)
                    canvas.translate(offsetX, offsetY)
                    canvas.drawOutline(outline, ShadowMaskPaint)
                    canvas.translate(-offsetX, -offsetY)
                    canvas.restore()
                }
            }
            previousOutline = outline
            previousSize = size
            previousColor = resolvedShadow.color
            previousOffsetX = offsetX
            previousOffsetY = offsetY
            shadowRecorded = true
        }

        drawContext.canvas.let { canvas ->
            canvas.save()
            canvas.clipPath(clipPath)
            drawLayer(layer)
            canvas.restore()
        }
    }

    override fun onDetach() {
        shadowLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        previousRadius = Float.NaN
        previousOutline = null
        shadowRecorded = false
    }
}

private val ShadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}
