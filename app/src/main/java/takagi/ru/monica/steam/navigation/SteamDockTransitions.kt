package takagi.ru.monica.steam.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Physical incoming edge: +1 is right, -1 is left, 0 needs no translation. */
internal fun dockPageSlideSign(
    order: List<SteamDockTab>,
    from: SteamDockTab,
    to: SteamDockTab,
    isRtl: Boolean,
    reduceAnimations: Boolean
): Int {
    val fromIndex = order.indexOf(from)
    val toIndex = order.indexOf(to)
    if (reduceAnimations || fromIndex < 0 || toIndex < 0) return 0
    val logicalSign = toIndex.compareTo(fromIndex)
    return if (isRtl) -logicalSign else logicalSign
}

internal fun <T> AnimatedContentTransitionScope<T>.steamDockPageTransition(
    order: List<SteamDockTab>,
    from: SteamDockTab,
    to: SteamDockTab,
    isRtl: Boolean,
    reduceAnimations: Boolean
): ContentTransform {
    val sign = dockPageSlideSign(order, from, to, isRtl, reduceAnimations)
    val transform = if (sign == 0) {
        fadeIn(tween(120)) togetherWith fadeOut(tween(90))
    } else {
        // Both pages travel together; only the content moves, never the Dock.
        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it * sign } togetherWith
            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it * sign }
    }
    return transform.using(null)
}
