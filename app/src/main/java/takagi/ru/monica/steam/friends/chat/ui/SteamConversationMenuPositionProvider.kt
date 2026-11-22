package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.max

/** Place a context menu next to the actual native-cell press, constrained to the visible window. */
internal class SteamConversationMenuPositionProvider(
    private val touchPosition: IntOffset?,
    private val edge: Int,
    private val gap: Int,
    private val topInset: Int,
    private val bottomInset: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // Keyboard/accessibility long clicks have no finger; use the center of the same row.
        val touch = touchPosition ?: IntOffset(anchorBounds.width / 2, anchorBounds.height / 2)
        val x = anchorBounds.left + touch.x
        val y = anchorBounds.top + touch.y
        val top = max(edge, topInset)
        val bottom = windowSize.height - max(edge, bottomInset)
        val below = y + gap
        val desiredY = if (below + popupContentSize.height <= bottom) below else y - gap - popupContentSize.height
        return IntOffset(
            x.coerceIn(edge, max(edge, windowSize.width - edge - popupContentSize.width)),
            desiredY.coerceIn(top, max(top, bottom - popupContentSize.height))
        )
    }
}
