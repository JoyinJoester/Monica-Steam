package takagi.ru.monica.steam.friends.chat.telegram

import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.navigation.SteamDockStyle

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.monica.telegramchat.TelegramChatColors
import com.monica.telegramchat.TelegramDrawables
import com.monica.telegramchat.TelegramGlassView
import com.monica.telegramchat.TelegramEdgeBlurView
import com.monica.telegramchat.TelegramWallpaperDrawable
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.steam.navigation.ui.steamWindowBottomPadding
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset

/** Application color roles supplied to the original Telegram drawing components. */
internal data class TelegramColors(val native: TelegramChatColors) {
    val background = Color(native.background)
    val incoming = Color(native.incoming)
    val outgoing = Color(native.outgoing)
    val text = Color(native.text)
    val outgoingText = Color(native.outgoingText)
    val accentText = Color(native.accentText)
    val secondary = Color(native.secondaryText)
    val accent = Color(native.accent)
    val glass = Color(native.glassTint)
}

@Composable
internal fun telegramChatColors(): TelegramColors {
    val c = MaterialTheme.colorScheme
    return remember(c) {
        TelegramColors(TelegramChatColors(
            c.background.luminance() < 0.5f,
            c.background.toArgb(), c.surfaceContainerHigh.toArgb(), c.primaryContainer.toArgb(),
            c.onSurface.toArgb(), c.onSurfaceVariant.toArgb(), c.primary.toArgb(),
            c.surfaceContainer.copy(alpha = .85f).toArgb(), c.surfaceContainerHigh.copy(alpha = .85f).toArgb(),
            c.onSurfaceVariant.toArgb(), c.onSurfaceVariant.toArgb(), c.onPrimaryContainer.toArgb(),
            c.onSurfaceVariant.toArgb(), c.surfaceContainerHigh.toArgb(), c.onSurface.toArgb(),
            c.onSurfaceVariant.toArgb(), c.onSurfaceVariant.toArgb(), c.primary.toArgb(), c.onPrimary.toArgb(),
            c.onPrimaryContainer.toArgb(), c.onPrimary.toArgb()
        ))
    }
}

internal val LocalTelegramSourceView = staticCompositionLocalOf<View?> { null }

/**
 * Full-width blurred backdrop behind the floating chat controls.
 *
 * The header pills and the composer already run their own glass, but they only cover their own
 * shapes — messages scrolling underneath stayed razor-sharp in the gaps between them. This layer
 * blurs the whole bar and fades it out towards the message list, so nothing shows through sharply.
 */
@Composable
internal fun TelegramControlBackdrop(topEdge: Boolean, modifier: Modifier = Modifier) {
    val colors = telegramChatColors()
    val source = LocalTelegramSourceView.current
    val tint = remember(colors) { opacity(colors.native.glassTint, BACKDROP_TINT_ALPHA) }
    AndroidView(
        modifier = modifier,
        factory = { TelegramEdgeBlurView(it) },
        update = {
            it.setSourceView(source)
            it.setTopEdge(topEdge)
            it.setTintColor(tint)
        },
        onRelease = { it.setSourceView(null) }
    )
}

/** Replaces the alpha channel of an ARGB tint without touching its colour. */
private fun opacity(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)

/** Slightly clearer than the pills' 0.85 so the backdrop reads as blur, not as a solid bar. */
private const val BACKDROP_TINT_ALPHA = 0x96

/** Insets are owned once here. Messages extend behind the glass controls. */
@Composable
internal fun TelegramChatScaffold(
    state: TelegramChatListState,
    modifier: Modifier = Modifier,
    wallpaperPreset: SteamChatWallpaperPreset = SteamChatWallpaperPreset.TELEGRAM_PATTERN,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = telegramChatColors()
    val density = LocalDensity.current
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val source = state.sourceView
    DisposableEffect(source, colors, wallpaperPreset) {
        val wallpaper = source?.let { TelegramWallpaperDrawable(it.context).apply {
            setColors(colors.native); setPatternStrength(wallpaperPreset.patternStrength)
        } }
        source?.background = wallpaper
        onDispose { if (source?.background === wallpaper) source?.background = null }
    }
    CompositionLocalProvider(LocalTelegramSourceView provides source, LocalContentColor provides colors.text) {
        Box(modifier.fillMaxSize().background(colors.background).imePadding()) {
            TelegramChatWallpaper(Modifier.matchParentSize(), wallpaperPreset)
            content(PaddingValues(
                top = with(density) { topHeight.toDp() },
                bottom = with(density) { bottomHeight.toDp() },
                start = 6.dp, end = 6.dp
            ))
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topHeight = it.height }) {
                TelegramControlBackdrop(topEdge = true, Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) { top() }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomHeight = it.height }) {
                TelegramControlBackdrop(topEdge = false, Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(top = 24.dp)
                    .steamWindowBottomPadding(suppressWhenImeVisible = true)) { bottom() }
            }
        }
    }
}

@Composable
internal fun TelegramChatWallpaper(modifier: Modifier = Modifier, preset: SteamChatWallpaperPreset = SteamChatWallpaperPreset.TELEGRAM_PATTERN) {
    val colors = telegramChatColors()
    AndroidView(
        modifier = modifier,
        factory = { context -> View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = TelegramWallpaperDrawable(context).apply { setColors(colors.native); setPatternStrength(preset.patternStrength) }
        } },
        update = { (it.background as TelegramWallpaperDrawable).apply { setColors(colors.native); setPatternStrength(preset.patternStrength) } }
    )
}

/** Actual compiled Telegram MessageDrawable; no Compose imitation of its bubble path. */
@Composable
internal fun TelegramMessageSurface(
    outgoing: Boolean,
    groupedWithPrevious: Boolean = false,
    groupedWithNext: Boolean = false,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = telegramChatColors()
    val bubble = remember(context, outgoing, groupedWithPrevious, groupedWithNext, color) {
        TelegramDrawables.initialize(context)
        TelegramDrawables.message(outgoing, color.toArgb(), groupedWithPrevious, groupedWithNext)
    }
    CompositionLocalProvider(LocalContentColor provides if (outgoing && color.alpha > 0f) colors.outgoingText else colors.text) {
        Box(modifier.drawBehind {
            bubble.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawIntoCanvas { bubble.draw(it.nativeCanvas) }
        }) { content() }
    }
}

@Composable
internal fun TelegramGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    sourceView: View? = LocalTelegramSourceView.current,
    content: @Composable () -> Unit
) {
    val liquid = LocalSteamDesignStyle.current == SteamDockStyle.LIQUID_GLASS
    val colors = telegramChatColors()
    val radius = with(LocalDensity.current) { cornerRadius.toPx() }
    Box(modifier) {
        AndroidView(modifier = Modifier.matchParentSize(), factory = { TelegramGlassView(it) }, update = {
            it.setSourceView(sourceView)
            it.setLiquidStyle(liquid)
            it.setCornerRadius(radius)
            it.setTintColor(colors.native.glassTint)
            it.setDark(colors.native.dark)
        }, onRelease = { it.setSourceView(null) })
        CompositionLocalProvider(LocalContentColor provides colors.text) { content() }
    }
}

@Composable
internal fun TelegramDatePill(timestampSeconds: Long, modifier: Modifier = Modifier) {
    val colors = telegramChatColors()
    val label = remember(timestampSeconds) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestampSeconds * 1000)) }
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Surface(color = colors.glass, shape = RoundedCornerShape(16.dp)) {
            Text(label, Modifier.padding(horizontal = 9.dp, vertical = 3.dp), color = colors.text,
                fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
    }
}
