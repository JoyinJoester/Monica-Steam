package takagi.ru.monica.steam.foundation.media

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Only the last touched mounted thumbnail is referenced, weakly and per window. */
private val touchedImages = WeakHashMap<View, WeakReference<SteamImageAnchor>>()
internal class SteamImageAnchor(val url: String) {
    var hidden by mutableStateOf(false)
    var coordinates: LayoutCoordinates? = null
    var image: ImageBitmap? = null
    var scale: ContentScale = ContentScale.Fit
    var radius: Float = 0f
    fun origin(): SteamImageOrigin? {
        val node = coordinates?.takeIf { it.isAttached } ?: return null
        val bitmap = image ?: return null
        if (node.size.width <= 0 || node.size.height <= 0) return null
        val bounds = Rect(node.positionOnScreen(), Size(node.size.width.toFloat(), node.size.height.toFloat()))
        return SteamImageOrigin(this, bounds, bitmap, scale, radius)
    }
}

internal data class SteamImageOrigin(
    val anchor: SteamImageAnchor,
    val bounds: Rect,
    val image: ImageBitmap,
    val scale: ContentScale,
    val radius: Float
)

@Composable
internal fun Modifier.steamImageThumbnail(
    url: String, image: ImageBitmap?, scale: ContentScale = ContentScale.Fit, radius: Float = 0f
): Modifier {
    val root = LocalView.current.rootView
    val anchor = remember(url) { SteamImageAnchor(url) }
    SideEffect { anchor.image = image; anchor.scale = scale; anchor.radius = radius }
    DisposableEffect(root, anchor) {
        onDispose {
            anchor.coordinates = null
            anchor.image = null
            if (touchedImages[root]?.get() === anchor) touchedImages.remove(root)
        }
    }
    return onGloballyPositioned { anchor.coordinates = it }.graphicsLayer { alpha = if (anchor.hidden) 0f else 1f }.pointerInput(root, anchor) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touchedImages[root] = WeakReference(anchor)
            // Observe the gesture without taking it away from the thumbnail's click handler.
            while (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) { }
        }
    }
}

internal fun touchedSteamImageOrigin(root: View, url: String): SteamImageOrigin? =
    touchedImages.remove(root.rootView)?.get()?.takeIf { it.url == url }?.origin()

/** Crop/fit and the actual bitmap travel together; no bitmap readback on an animation frame. */
@Composable
internal fun SteamImageFlight(origin: SteamImageOrigin, fraction: () -> Float, screenOffset: () -> Offset) {
    Canvas(Modifier.fillMaxSize().testTag("steam-image-flight")) {
        val t = fraction().coerceIn(0f, 1f)
        val source = origin.bounds.translate(-screenOffset())
        val imageSize = Size(origin.image.width.toFloat(), origin.image.height.toFloat())
        val available = Size(size.width - 16.dp.toPx(), (size.height - 168.dp.toPx()).coerceAtLeast(1f))
        val fit = ContentScale.Fit.computeScaleFactor(imageSize, available)
        val targetSize = Size(imageSize.width * fit.scaleX, imageSize.height * fit.scaleY)
        val target = Rect((size.width - targetSize.width) / 2, (size.height - targetSize.height) / 2,
            (size.width + targetSize.width) / 2, (size.height + targetSize.height) / 2)
        fun mix(a: Float, b: Float) = a + (b - a) * t
        val startScale = origin.scale.computeScaleFactor(imageSize, source.size)
        val startPaint = Rect(source.center - Offset(imageSize.width * startScale.scaleX / 2,
            imageSize.height * startScale.scaleY / 2), Size(imageSize.width * startScale.scaleX, imageSize.height * startScale.scaleY))
        val clip = Rect(mix(source.left, target.left), mix(source.top, target.top), mix(source.right, target.right), mix(source.bottom, target.bottom))
        val paint = Rect(mix(startPaint.left, target.left), mix(startPaint.top, target.top), mix(startPaint.right, target.right), mix(startPaint.bottom, target.bottom))
        val path = Path().apply { addRoundRect(RoundRect(clip, CornerRadius(origin.radius * (1 - t)))) }
        clipPath(path) {
            drawImage(origin.image, srcOffset = IntOffset.Zero, srcSize = IntSize(origin.image.width, origin.image.height),
                dstOffset = IntOffset(paint.left.roundToInt(), paint.top.roundToInt()),
                dstSize = IntSize(paint.width.roundToInt().coerceAtLeast(1), paint.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium)
        }
    }
}
