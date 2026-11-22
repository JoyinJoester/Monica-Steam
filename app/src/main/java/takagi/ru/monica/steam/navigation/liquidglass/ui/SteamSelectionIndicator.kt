package takagi.ru.monica.steam.navigation.liquidglass.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import takagi.ru.monica.ui.LocalReduceAnimations

/** Read the animated bounds during drawing; tab motion must not recompose the whole list. */
@Composable
internal fun Modifier.steamSelectionIndicator(target: Rect?, color: Color): Modifier {
    if (target == null) return this
    val bounds = animateRectAsState(target,
        if (LocalReduceAnimations.current) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "Steam tab indicator")
    return drawBehind {
        val rect = bounds.value
        drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(rect.height / 2f))
    }
}
