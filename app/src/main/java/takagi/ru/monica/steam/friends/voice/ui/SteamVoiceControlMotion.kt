package takagi.ru.monica.steam.friends.voice.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import takagi.ru.monica.ui.LocalReduceAnimations

/** Chat-only adaptation of Telegram VoIPToggleButton's short press/color/icon transitions.
 * State comes from the existing call runtime, never from a decorative speaking timer. */
@Composable
internal fun SteamVoiceControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    destructive: Boolean = false
) {
    val reduced = LocalReduceAnimations.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) .9f else 1f,
        if (reduced) snap() else tween(150), label = "Voice control press")
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (destructive || emphasized) colors.errorContainer else colors.secondaryContainer,
        if (reduced) snap() else tween(150), label = "Voice control state")
    val foreground by animateColorAsState(
        if (destructive || emphasized) colors.onErrorContainer else colors.onSecondaryContainer,
        if (reduced) snap() else tween(150), label = "Voice control icon")
    FilledTonalIconButton(onClick = onClick, interactionSource = interactions,
        modifier = Modifier.size(40.dp).semantics { contentDescription = description }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = background, contentColor = foreground)) {
        // A single immediate accessible label; outgoing icons are decorative only.
        Crossfade(icon, animationSpec = if (reduced) snap() else tween(150), label = "Voice icon") {
            Icon(it, contentDescription = null)
        }
    }
}
