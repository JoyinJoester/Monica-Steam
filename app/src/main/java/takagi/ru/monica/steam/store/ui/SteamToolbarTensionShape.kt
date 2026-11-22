package takagi.ru.monica.steam.store.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal const val STEAM_TOOLBAR_BREAK_DISTANCE_DP = 28f

/** Roots retreat towards the middle; the neck disappears only at the end. */
internal data class SteamToolbarTension(val rootFraction: Float, val neckFraction: Float)

internal fun steamToolbarTension(gapFraction: Float): SteamToolbarTension {
    val p = gapFraction.coerceIn(0f, 1f)
    val smooth = p * p * (3f - 2f * p)
    val root = 1f - smooth
    return SteamToolbarTension(root, root * (1f - p) * (1f - p))
}

/** One physical contour for the capsule and its tether, including both screen edges. */
internal class SteamToolbarTensionShape(
    private val edge: SteamStoreDetailToolbarEdge?,
    private val gapPx: Float,
    private val attachedRadiusPx: Float,
    private val breakDistancePx: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val gap = if (edge == null) 0f else gapPx.coerceIn(0f, size.width)
        val left = if (edge == SteamStoreDetailToolbarEdge.LEFT) gap else 0f
        val right = if (edge == SteamStoreDetailToolbarEdge.RIGHT) size.width - gap else size.width
        val radius = minOf(with(density) { 24.dp.toPx() }, (right - left) / 2f, size.height / 2f)
        val inner = attachedRadiusPx.coerceIn(0f, radius)
        val start = if (edge == SteamStoreDetailToolbarEdge.LEFT) inner else radius
        val end = if (edge == SteamStoreDetailToolbarEdge.RIGHT) inner else radius
        val body = Path().apply {
            addRoundRect(RoundRect(Rect(left, 0f, right, size.height),
                CornerRadius(start), CornerRadius(end), CornerRadius(end), CornerRadius(start)))
        }
        if (edge == null || gap <= 0f || gap >= breakDistancePx) return Outline.Generic(body)
        val tension = steamToolbarTension(gap / breakDistancePx)
        val root = (size.height / 2f - inner).coerceAtLeast(0f) * tension.rootFraction
        val neck = root * tension.neckFraction
        val middleY = size.height / 2f
        val anchor = if (edge == SteamStoreDetailToolbarEdge.LEFT) 0f else size.width
        // Overlap the body so the union has no internal rim or translucent seam.
        val join = if (edge == SteamStoreDetailToolbarEdge.LEFT) left + radius else right - radius
        val middleX = if (edge == SteamStoreDetailToolbarEdge.LEFT) gap / 2f else size.width - gap / 2f
        val bridge = Path().apply {
            moveTo(anchor, middleY - root)
            cubicTo(anchor, middleY - neck, middleX, middleY - neck, middleX, middleY - neck)
            cubicTo(middleX, middleY - neck, join, middleY - root, join, middleY - root)
            lineTo(join, middleY + root)
            cubicTo(join, middleY + root, middleX, middleY + neck, middleX, middleY + neck)
            cubicTo(middleX, middleY + neck, anchor, middleY + neck, anchor, middleY + root)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, bridge))
    }
}
