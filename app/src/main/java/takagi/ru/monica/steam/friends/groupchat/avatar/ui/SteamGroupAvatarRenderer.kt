package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

/**
 * Composite group avatar drawn into a single bitmap with antialiased circular sectors.
 *
 * The inbox row is a native Telegram cell, so the avatar has to be a bitmap the cell can draw in
 * its own 56dp slot. Photos are keyed by member identity, transparent pixels fall back to the
 * group tone, and the seams come from the same geometry the Compose grid uses.
 */
internal fun renderSteamGroupAvatarBitmap(
    members: List<SteamGroupAvatarMember>,
    photos: Map<String, Bitmap>,
    sizePx: Int,
    tileColorArgb: Int,
    labelColorArgb: Int,
    dividerColorArgb: Int,
    emptyLabel: String = ""
): Bitmap {
    val size = sizePx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val side = size.toFloat()
    val disc = RectF(0f, 0f, side, side)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tileColorArgb
        style = Paint.Style.FILL
    }

    // Paint the whole disc first: a member picture with an alpha channel then fades into the
    // group tone, never into the chat list behind the row.
    canvas.drawOval(disc, fill)

    val slots = steamGroupAvatarSlots(Rect(0f, 0f, side, side), members.size)
    if (slots.isEmpty()) {
        drawInitial(canvas, emptyLabel, disc, 0.40f * side, labelColorArgb)
    } else {
        // One font size for every tile. Sizing each wedge on its own made the wide top slice of a
        // three-member group visibly bigger than the two bottom quarters.
        val sharedTextSize = slots.mapIndexed { index, slot ->
            val edge = min(slot.bounds.width, slot.bounds.height)
            fittedInitialTextSize(
                displayName = members.getOrNull(index)?.displayName.orEmpty(),
                textSizePx = edge * slot.initialScale,
                bounds = slot.bounds.toRectF(),
                fillRatio = slotFillRatio(slot)
            )
        }.minOrNull() ?: 0f
        slots.forEachIndexed { index, slot ->
            val photo = photos[members[index].steamId]
            if (photo != null && !photo.isRecycled && photo.width > 0 && photo.height > 0) {
                drawCentreCropped(canvas, photo, slot, disc)
            } else {
                // The centroid of a circular sector is closer to the circle centre than the
                // centre of its bounding rectangle. Centring ink there keeps CJK corners clear
                // of the rim without making every initial unnecessarily small.
                val sweep = Math.toRadians(slot.sweepAngle.toDouble())
                val angle = Math.toRadians((slot.startAngle + slot.sweepAngle / 2f).toDouble())
                val distance = if (slot.isWholeDisc) 0.0 else 4.0 * (side / 2.0) * sin(sweep / 2.0) / (3.0 * sweep)
                drawInitial(
                    canvas,
                    members.getOrNull(index)?.displayName.orEmpty(),
                    slot.bounds.toRectF(),
                    sharedTextSize,
                    labelColorArgb,
                    centerX = disc.centerX() + (distance * cos(angle)).toFloat(),
                    centerY = disc.centerY() + (distance * sin(angle)).toFloat(),
                    fillRatio = 1f
                )
            }
        }
        drawDividers(canvas, slots.size, dividerColorArgb, disc)
    }
    return bitmap
}

/** Same wedge as [toPath] but in the android.graphics.Path the bitmap canvas needs. */
internal fun GroupAvatarSlot.toAndroidPath(disc: RectF): Path = Path().apply {
    if (isWholeDisc) {
        addOval(disc, Path.Direction.CW)
    } else {
        moveTo(disc.centerX(), disc.centerY())
        arcTo(disc, startAngle, sweepAngle, false)
        close()
    }
}

private fun Rect.toRectF(): RectF = RectF(left, top, right, bottom)

private fun drawCentreCropped(canvas: Canvas, photo: Bitmap, slot: GroupAvatarSlot, disc: RectF) {
    val bounds = slot.bounds
    // Cover the slot, keep the subject centred: scale to fill, then crop the overflow.
    val scale = max(bounds.width / photo.width, bounds.height / photo.height)
    val drawWidth = photo.width * scale
    val drawHeight = photo.height * scale
    val left = bounds.left + (bounds.width - drawWidth) / 2f
    val top = bounds.top + (bounds.height - drawHeight) / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(scale, scale); postTranslate(left, top) })
        }
    }
    // Drawing the sector, rather than clipping a software canvas, keeps its curved edge smooth.
    canvas.drawPath(slot.toAndroidPath(disc), paint)
}

private fun drawInitial(
    canvas: Canvas,
    displayName: String,
    bounds: RectF,
    textSizePx: Float,
    colorArgb: Int,
    centerX: Float = bounds.centerX(),
    centerY: Float = bounds.centerY(),
    fillRatio: Float = INITIAL_FILL_RATIO
) {
    val name = displayName.trim()
    val initial = if (name.isEmpty()) "?" else name.substring(0, name.offsetByCodePoints(0, 1)).uppercase(Locale.ROOT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        textSize = textSizePx.coerceAtLeast(1f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    // A CJK glyph is nearly a full em wide and taller than a latin capital, so a size derived
    // from the tile edge alone spills over the divider and gets cut by the disc. Cap both axes
    // to a share of the tile before drawing.
    fitTextToTile(paint, initial, bounds, fillRatio)
    val glyph = android.graphics.Rect()
    paint.getTextBounds(initial, 0, initial.length, glyph)
    // Centre on the glyph's own box: font metrics centre latin capitals too high and CJK is
    // carried by a different baseline, which is what pushed quadrant names off the divider.
    val baseline = centerY - (glyph.top + glyph.bottom) / 2f
    canvas.drawText(initial, centerX - (glyph.left + glyph.right) / 2f, baseline, paint)
}

/** A quarter disc has much less room than the whole disc, so it needs a smaller ink share. */
private fun slotFillRatio(slot: GroupAvatarSlot): Float =
    if (slot.sweepAngle >= 180f || slot.isWholeDisc) INITIAL_FILL_RATIO else QUADRANT_FILL_RATIO

private fun fittedInitialTextSize(
    displayName: String,
    textSizePx: Float,
    bounds: RectF,
    fillRatio: Float
): Float {
    val name = displayName.trim()
    val initial = if (name.isEmpty()) "?" else name.substring(0, name.offsetByCodePoints(0, 1)).uppercase(Locale.ROOT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx.coerceAtLeast(1f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    fitTextToTile(paint, initial, bounds, fillRatio)
    return paint.textSize
}

/** Shrinks [paint] until its glyph fits inside [bounds] with a margin on both axes. */
private fun fitTextToTile(paint: Paint, text: String, bounds: RectF, fillRatio: Float) {
    // Fit the ink box rather than the advance width: a CJK glyph fills its em square, while a
    // latin capital is much narrower, and only the ink box says how close it gets to the edge.
    val glyph = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, glyph)
    if (glyph.width() <= 0 || glyph.height() <= 0) return
    val fit = min(
        bounds.width() * fillRatio / glyph.width(),
        bounds.height() * fillRatio / glyph.height()
    )
    if (fit < 1f) paint.textSize *= fit
}

private const val INITIAL_FILL_RATIO = 0.54f
private const val QUADRANT_FILL_RATIO = 0.38f

private fun drawDividers(canvas: Canvas, tileCount: Int, colorArgb: Int, disc: RectF) {
    if (tileCount < 2) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    val halfWidth = (disc.width() * 0.022f).coerceIn(1f, 2.5f) / 2f
    val seams = Path().apply {
        addRect(disc.left, disc.centerY() - halfWidth, disc.right, disc.centerY() + halfWidth, Path.Direction.CW)
        if (tileCount >= 3) {
            val startY = if (tileCount == 3) disc.centerY() else disc.top
            addRect(disc.centerX() - halfWidth, startY, disc.centerX() + halfWidth, disc.bottom, Path.Direction.CW)
        }
        op(Path().apply { addOval(disc, Path.Direction.CW) }, Path.Op.INTERSECT)
    }
    canvas.drawPath(seams, paint)
}
