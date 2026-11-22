package takagi.ru.monica.steam.friends.chat.info.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

private val HERO_HEIGHT = 268.dp
private val HERO_AVATAR_SIZE = 78.dp
/** Keeps the name block above the fade into the page colour, as Telegram does. */
private val HERO_IDENTITY_BOTTOM_PADDING = 46.dp

/**
 * Telegram profile header: the Steam background fills the top of the page, a scrim keeps
 * the name readable, and the avatar sits on the information block instead of floating in
 * the middle of the screen. Anything unavailable (private profile, no network, disallowed
 * host) degrades to a blurred avatar behind the same scrim, so the layout never collapses.
 */
@Composable
internal fun SteamChatInfoHero(
    title: String,
    subtitle: String,
    backgroundUrl: String?,
    avatarUrl: String?,
    onBack: () -> Unit,
    avatar: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    // The cover photo sits under the status bar, so the light-icon treatment is temporary.
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        if (controller != null) controller.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && previous != null) {
                controller.isAppearanceLightStatusBars = previous
            }
        }
    }
    val background by produceState<ImageBitmap?>(initialValue = null, backgroundUrl) {
        value = backgroundUrl?.let { runCatching { loadSteamRemoteImage(context, it) }.getOrNull() }
    }
    val avatarBitmap by produceState<ImageBitmap?>(initialValue = null, avatarUrl) {
        value = avatarUrl?.takeIf(String::isNotBlank)?.let {
            runCatching { SteamRemoteImageCache.get(context).load(it)?.asImageBitmap() }.getOrNull()
        }
    }
    val surface = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        val image = background
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            surface
                        )
                    )
                )
            )
            avatarBitmap?.let { fallback ->
                Image(
                    bitmap = fallback,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(36.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                // The page colour only wins in the last few dp, below the name block, so
                // white labels stay readable in the light theme too.
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .20f),
                    .45f to Color.Black.copy(alpha = .46f),
                    .70f to Color.Black.copy(alpha = .68f),
                    .86f to Color.Black.copy(alpha = .78f),
                    .96f to surface,
                    1f to surface
                )
            )
        )
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            Spacer(Modifier.weight(1f))
            actions()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = HERO_IDENTITY_BOTTOM_PADDING
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            avatar()
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = .82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Telegram's translucent circular affordance used over cover photos. */
@Composable
internal fun HeroIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = .34f), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
    }
}

/** Rounded-square avatar frame matching the Steam profile page treatment. */
@Composable
internal fun HeroAvatarFrame(
    shape: Shape = RoundedCornerShape(20),
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(HERO_AVATAR_SIZE)
            .background(Color.Black.copy(alpha = .28f), shape)
            .padding(2.dp)
    ) {
        content()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
