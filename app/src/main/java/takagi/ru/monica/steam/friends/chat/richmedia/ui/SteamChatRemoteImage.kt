package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.github.penfeizhou.animation.apng.APNGDrawable
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteBytes
import takagi.ru.monica.steam.foundation.ui.normalizeSteamImageUrl
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile
import takagi.ru.monica.steam.foundation.ui.decodeSteamRemoteBitmap
import takagi.ru.monica.steam.foundation.ui.isSafeSteamRemoteImagePayload
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

/** Rendering policy for the small, fixed-size assets served by Steam. */
internal enum class SteamChatRemoteImageMode {
    CONTENT,
    ARTWORK,
    EMOTICON,
    STICKER
}

/**
 * Steam emoticons are deliberately pixel-art assets (54×54).  Filtering them
 * while enlarging a picker cell blends their hard edges into the dark
 * background, so nearest-neighbour sampling is the only readable policy.
 */
internal fun staticSteamImageFilterQuality(mode: SteamChatRemoteImageMode): FilterQuality =
    if (mode == SteamChatRemoteImageMode.EMOTICON || mode == SteamChatRemoteImageMode.STICKER) {
        FilterQuality.None
    } else {
        FilterQuality.High
    }

@Composable
internal fun SteamChatRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    playAnimation: Boolean = true,
    mode: SteamChatRemoteImageMode = SteamChatRemoteImageMode.CONTENT,
    fallbackIcon: ImageVector = Icons.Default.EmojiEmotions
) {
    val context = LocalContext.current
    val normalizedUrl = remember(url) { normalizeSteamImageUrl(url) }
    var drawable by remember(normalizedUrl) { mutableStateOf<Drawable?>(null) }
    var bitmap by remember(normalizedUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(normalizedUrl) {
        drawable = null
        bitmap = null
        if (!SteamRemoteImageCache.isAllowedSteamImageUrl(normalizedUrl)) {
            return@LaunchedEffect
        }
        val payload = loadSteamRemoteBytes(context, normalizedUrl) ?: return@LaunchedEffect
        if (!isSafeSteamRemoteImagePayload(payload)) {
            SteamDiagLogger.append("chat_media image_rejected reason=unsafe_dimensions")
            return@LaunchedEffect
        }
        if (isAnimatedPng(payload)) {
            val animationResult = runCatching {
                withContext(Dispatchers.Default) {
                    val cacheFile = steamRemoteImageCacheFile(context, normalizedUrl)
                    if (!cacheFile.isFile) return@withContext null
                    val source = APNGDrawable.fromFile(cacheFile.absolutePath).apply {
                        // The host view controls visibility, but APNG4Android
                        // must still be allowed to start when that view attaches.
                        setAutoPlay(true)
                        setLoopLimit(0)
                    }
                    val width = source.intrinsicWidth.takeIf { it > 0 } ?: 150
                    val height = source.intrinsicHeight.takeIf { it > 0 } ?: 150
                    SteamPixelAnimatedDrawable(source, width, height)
                }
            }
            drawable = animationResult.getOrNull()
            if (drawable == null) {
                val reason = animationResult.exceptionOrNull()?.javaClass?.simpleName ?: "cache_unavailable"
                SteamDiagLogger.append("chat_media apng_decode_failed reason=$reason")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAnimatedSteamImage(payload)) {
            drawable = runCatching {
                withContext(Dispatchers.Default) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(payload)))
                }
            }.getOrNull()
        } else {
            bitmap = decodeStaticSteamBitmap(payload)
        }
        // A malformed animation should never leave a permanent placeholder.
        if (drawable == null && bitmap == null) bitmap = decodeStaticSteamBitmap(payload)
    }
    val currentDrawable = drawable
    val animated = currentDrawable as? Animatable
    DisposableEffect(currentDrawable) {
        onDispose {
            when (currentDrawable) {
                is SteamPixelAnimatedDrawable -> currentDrawable.release()
                else -> stopSteamAnimation(currentDrawable)
            }
        }
    }
    when {
        animated != null -> SteamAnimatedRemoteImage(
            drawable = currentDrawable,
            contentDescription = contentDescription,
            modifier = modifier,
            playAnimation = playAnimation,
            mode = mode
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable != null -> SteamAnimatedRemoteImage(
            drawable = drawable,
            contentDescription = contentDescription,
            modifier = modifier,
            playAnimation = playAnimation,
            mode = mode
        )
        bitmap != null && mode == SteamChatRemoteImageMode.EMOTICON ->
            SteamPixelEmoticonImage(
                bitmap = requireNotNull(bitmap),
                contentDescription = contentDescription,
                modifier = modifier
            )
        bitmap != null -> Image(
            painter = BitmapPainter(
                image = requireNotNull(bitmap).asImageBitmap(),
                filterQuality = staticSteamImageFilterQuality(mode)
            ),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = when (mode) {
                SteamChatRemoteImageMode.ARTWORK -> ContentScale.Crop
                SteamChatRemoteImageMode.STICKER -> {
                    // A Steam sticker is only 150px wide. Never invent pixels by
                    // scaling it to a multi-density dp box.
                    ContentScale.Inside
                }
                else -> ContentScale.Fit
            }
        )
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(fallbackIcon, contentDescription = contentDescription)
        }
    }
}

/** Draws the 54px Steam source directly with a deterministic nearest-neighbour pass. */
@Composable
private fun SteamPixelEmoticonImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Box(
        modifier = modifier.semantics {
            contentDescription?.let { this.contentDescription = it }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (image.width <= 0 || image.height <= 0) return@Canvas
            val scale = minOf(
                size.width / image.width.toFloat(),
                size.height / image.height.toFloat()
            )
            val dstWidth = (image.width * scale).toInt().coerceAtLeast(1)
            val dstHeight = (image.height * scale).toInt().coerceAtLeast(1)
            val dstOffset = IntOffset(
                x = ((size.width - dstWidth) / 2f).toInt(),
                y = ((size.height - dstHeight) / 2f).toInt()
            )
            drawImage(
                image = image,
                dstOffset = dstOffset,
                dstSize = IntSize(dstWidth, dstHeight),
                filterQuality = FilterQuality.None
            )
        }
    }
}

@Composable
private fun SteamAnimatedRemoteImage(
    drawable: Drawable?,
    contentDescription: String?,
    modifier: Modifier,
    playAnimation: Boolean,
    mode: SteamChatRemoteImageMode
) {
    AndroidView(
        factory = { context ->
            SteamAnimatedImageView(context).apply {
                scaleType = imageScaleType(mode)
            }
        },
        modifier = modifier,
        update = { view ->
            view.bind(
                drawable = drawable,
                contentDescription = contentDescription,
                playAnimation = playAnimation,
                scaleType = imageScaleType(mode)
            )
        }
    )
}

private class SteamAnimatedImageView(context: Context) : ImageView(context) {
    private var boundDrawable: Drawable? = null
    private var shouldPlay = false

    fun bind(
        drawable: Drawable?,
        contentDescription: String?,
        playAnimation: Boolean,
        scaleType: ScaleType
    ) {
        this.contentDescription = contentDescription
        this.scaleType = scaleType
        shouldPlay = playAnimation
        if (boundDrawable !== drawable) {
            stopSteamAnimation(boundDrawable)
            boundDrawable = drawable
            setImageDrawable(drawable)
        }
        if (shouldPlay) scheduleStart() else stopSteamAnimation(boundDrawable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (shouldPlay) scheduleStart()
    }

    override fun onDetachedFromWindow() {
        stopSteamAnimation(boundDrawable)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && shouldPlay) scheduleStart()
        if (visibility != VISIBLE) stopSteamAnimation(boundDrawable)
    }

    private fun scheduleStart() {
        post {
            if (!shouldPlay || !isAttachedToWindow || visibility != VISIBLE) return@post
            startSteamAnimation(boundDrawable)
        }
    }
}

/** APNG4Android implements Animatable2Compat rather than android.graphics.Animatable. */
private fun startSteamAnimation(drawable: Drawable?) {
    drawable ?: return
    // AndroidView update may run for unrelated chat state changes. Do not use
    // restart=true here or every recomposition pins an APNG near frame one.
    drawable.setVisible(true, false)
    if (!isSteamAnimationRunning(drawable)) (drawable as? Animatable)?.start()
}

private fun isSteamAnimationRunning(drawable: Drawable?): Boolean =
    (drawable as? Animatable)?.isRunning == true

private fun stopSteamAnimation(drawable: Drawable?) {
    drawable ?: return
    (drawable as? Animatable)?.let { if (it.isRunning) it.stop() }
    drawable.setVisible(false, false)
}

private suspend fun decodeStaticSteamBitmap(payload: ByteArray): Bitmap? =
    withContext(Dispatchers.Default) { decodeSteamRemoteBitmap(payload) }

private fun imageScaleType(mode: SteamChatRemoteImageMode): ImageView.ScaleType = when (mode) {
    SteamChatRemoteImageMode.ARTWORK -> ImageView.ScaleType.CENTER_CROP
    SteamChatRemoteImageMode.STICKER -> ImageView.ScaleType.CENTER_INSIDE
    else -> ImageView.ScaleType.FIT_CENTER
}
