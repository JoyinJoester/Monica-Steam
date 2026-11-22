package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.navigation.steamPageTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The inbox and the conversation share this transition, including offline UI verification. */
@Composable
internal fun SteamChatNavigation(
    targetState: Triple<String?, String?, SteamChatSubpage?>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.(Triple<String?, String?, SteamChatSubpage?>) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val opening = targetState.third != null ||
                (targetState.first != null || targetState.second != null) &&
                initialState.third == null
            steamPageTransition(reduceAnimations, forward = opening)
        },
        label = "SteamChatNavigation",
        content = content
    )
}
