package takagi.ru.monica.steam.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally

/** One incoming page, with no scaled glass or second outgoing full-screen render tree. */
internal fun steamPageTransition(reduceAnimations: Boolean, forward: Boolean = true): ContentTransform {
    val enter = if (reduceAnimations) {
        fadeIn(tween(100))
    } else {
        fadeIn(tween(160)) + slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) {
            (if (forward) 1 else -1) * (it / 20)
        }
    }
    return ContentTransform(enter, ExitTransition.None, sizeTransform = null)
}
