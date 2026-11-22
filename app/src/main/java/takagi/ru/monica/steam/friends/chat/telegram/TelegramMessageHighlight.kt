package takagi.ru.monica.steam.friends.chat.telegram

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** How long a jumped-to message keeps its tint before the parent clears the target. */
internal const val TELEGRAM_HIGHLIGHT_HOLD_MILLIS = 1_500L

/**
 * Telegram paints the message a search result points at with an accent tint and lets it fade out
 * (ChatMessageCell#setHighlighted + highlightProgress). The row keeps its true bubble colors, the
 * tint is drawn underneath so incoming and outgoing messages read the same way.
 */
@Composable
internal fun TelegramMessageHighlight(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val color by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = HIGHLIGHT_ALPHA) else Color.Transparent,
        animationSpec = tween(durationMillis = if (active) 140 else 420),
        label = "chat-message-highlight"
    )
    Box(
        modifier.fillMaxWidth().background(color, RoundedCornerShape(14.dp))
    ) {
        content()
    }
}

private const val HIGHLIGHT_ALPHA = 0.18f
