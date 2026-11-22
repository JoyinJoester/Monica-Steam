package takagi.ru.monica.steam.friends.voice.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.ui.LocalReduceAnimations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val LocalSteamVoiceAudioLevels = staticCompositionLocalOf<StateFlow<Map<String, Float>>?> { null }
internal val LocalSteamVoiceFriends = staticCompositionLocalOf<Map<String, SteamFriend>> { emptyMap() }

@Composable
private fun voiceMotionReduced(): Boolean {
    val context = LocalContext.current
    var systemEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemEnabled = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return LocalReduceAnimations.current || !systemEnabled
}

/** Telegram's short upward status replacement, with only the current label exposed to accessibility. */
@Composable
internal fun SteamVoiceStatusText(text: String, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium) {
    val reduced = voiceMotionReduced()
    val enter = with(LocalDensity.current) { 8.dp.roundToPx() }
    val exit = with(LocalDensity.current) { 6.dp.roundToPx() }
    Box(modifier.clearAndSetSemantics { contentDescription = text }) {
        if (reduced) Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else AnimatedContent(text, transitionSpec = {
            ((fadeIn(tween(250)) + slideInVertically(tween(250)) { enter }) togetherWith
                (fadeOut(tween(180)) + slideOutVertically(tween(250)) { -exit }))
                .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(250) }))
        }, label = "Voice status") { label ->
            Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Two soft contours driven by RTC amplitude. No looping motion when silent, hidden or reduced. */
@Composable
internal fun SteamVoiceAvatar(steamId: String, friend: SteamFriend?, enabled: Boolean,
    avatarSize: Int = 42, modifier: Modifier = Modifier) {
    val flow = LocalSteamVoiceAudioLevels.current
    val levels = flow?.collectAsStateWithLifecycle()?.value.orEmpty()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val target = if (enabled && visible) (levels[steamId] ?: 0f).coerceIn(0f, 1f) else 0f
    val reduced = voiceMotionReduced()
    val amplitude = animateFloatAsState(target, if (reduced) snap() else tween(if (target > 0) 100 else 220), label = "Voice amplitude")
    val moving = target > 0 && !reduced
    val phase = if (moving) {
        val transition = rememberInfiniteTransition(label = "Voice contour")
        transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2300, easing = LinearEasing)), label = "Voice contour phase")
    } else remember { mutableFloatStateOf(0f) }
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.size((avatarSize + 16).dp).drawBehind {
        val amount = if (reduced) { if (target > 0) .45f else 0f } else amplitude.value
        if (amount > .001f) {
            val radius = avatarSize.dp.toPx() / 2f
            if (reduced) {
                drawCircle(color.copy(alpha = .65f), radius + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
            } else repeat(2) { layer ->
                val path = Path()
                for (index in 0..64) {
                    val angle = index * 2 * PI / 64
                    val wobble = sin(angle * (if (layer == 0) 6 else 8) + phase.value * (if (layer == 0) 1 else -1))
                    val r = radius + (2 + amount * (3 + layer * 2) + wobble * amount * 1.2).toFloat().dp.toPx()
                    val x = center.x + cos(angle).toFloat() * r
                    val y = center.y + sin(angle).toFloat() * r
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color.copy(alpha = amount * (if (layer == 0) .3f else .15f)))
            }
        }
    }, contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer {
            val scale = if (reduced || !visible || !enabled) 1f else 1f + amplitude.value * .035f
            scaleX = scale; scaleY = scale
        }) {
            if (friend != null) FriendAvatar(friend, avatarSize)
            else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(avatarSize.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size((avatarSize * .55f).dp))
                }
            }
        }
    }
}
