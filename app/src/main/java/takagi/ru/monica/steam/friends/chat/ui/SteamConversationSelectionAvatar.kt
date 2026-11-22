package takagi.ru.monica.steam.friends.chat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * The selected state of a conversation avatar, drawn as a bitmap.
 *
 * The inbox row is a native Telegram cell whose avatar slot is 56dp at (10dp, 10dp). A Compose
 * overlay cannot occupy that slot reliably — padding and size both apply to the same modifier
 * chain, so the overlay ends up smaller than the disc and leaves the original picture visible
 * around it. Handing the cell a picture of the selected state instead makes the avatar *become*
 * the selection: same slot, same size, no second layer.
 */
@Composable
internal fun rememberSteamSelectionAvatarBitmap(size: Dp): Bitmap {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val fill = MaterialTheme.colorScheme.primary.toArgb()
    val check = MaterialTheme.colorScheme.onPrimary.toArgb()
    return remember(sizePx, fill, check) {
        renderSteamSelectionAvatarBitmap(sizePx, fill, check)
    }
}

/** Filled disc plus a round-capped tick, both sized from [sizePx] so any density stays sharp. */
internal fun renderSteamSelectionAvatarBitmap(sizePx: Int, fillArgb: Int, checkArgb: Int): Bitmap {
    val size = sizePx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center, disc)
    val stroke = size * 0.085f
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = checkArgb
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // Inset by half the stroke so the round caps stay inside the disc.
    val path = Path().apply {
        moveTo(size * 0.29f, size * 0.52f)
        lineTo(size * 0.44f, size * 0.67f)
        lineTo(size * 0.72f, size * 0.36f)
    }
    canvas.drawPath(path, tick)
    return bitmap
}
