// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.core.math.MathUtils;
public final class GlassStroke {
    public static void drawStroke(
        Canvas canvas,
        float left, float top, float right, float bottom,
        float[] radii, float strokeWidth, boolean isTop,
        Paint paint
    ) {

        final boolean radiiAreSame = isTop ?
            radii[0] == radii[1] && radii[1] == radii[2] && radii[2] == radii[3]:
            radii[4] == radii[5] && radii[5] == radii[6] && radii[6] == radii[7];

        final float strokeHalf = strokeWidth / 2f;

        if (isTop) {
            // float topLeft, float topRight, float bottomRight, float bottomLeft

            if (radiiAreSame) {
                canvas.save();
                if (canvas.clipRect(left, top, right, MathUtils.clamp(top + radii[0] * 2, top, bottom))) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top + strokeHalf,
                            right + strokeHalf,
                            bottom + strokeHalf,
                            radii[0], radii[0],
                            paint
                    );
                }
                canvas.restore();
            } else {
                final float cx = (left + right) / 2f;
                canvas.save();
                if (canvas.clipRect(left, top, cx, MathUtils.clamp(top + radii[0] * 2, top, bottom))) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top + strokeHalf,
                            right + strokeHalf,
                            bottom + strokeHalf,
                            radii[0], radii[1],
                            paint
                    );
                }
                canvas.restore();
                canvas.save();
                if (canvas.clipRect(cx, top, right, MathUtils.clamp(top + radii[0] * 2, top, bottom))) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top + strokeHalf,
                            right + strokeHalf,
                            bottom + strokeHalf,
                            radii[2], radii[3],
                            paint
                    );
                }
                canvas.restore();
            }
        } else {
            if (radiiAreSame) {
                canvas.save();
                if (canvas.clipRect(left, MathUtils.clamp(bottom - radii[4] * 2, top, bottom), right, bottom)) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top - strokeHalf,
                            right + strokeHalf,
                            bottom - strokeHalf,
                            radii[4], radii[4],
                            paint
                    );
                }
                canvas.restore();
            } else {
                final float cx = (left + right) / 2f;
                canvas.save();
                if (canvas.clipRect(left, MathUtils.clamp(bottom - radii[4] * 2, top, bottom), cx, bottom)) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top - strokeHalf,
                            right + strokeHalf,
                            bottom - strokeHalf,
                            radii[6], radii[7],
                            paint
                    );
                }
                canvas.restore();
                canvas.save();
                if (canvas.clipRect(cx, MathUtils.clamp(bottom - radii[4] * 2, top, bottom), right, bottom)) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top - strokeHalf,
                            right + strokeHalf,
                            bottom - strokeHalf,
                            radii[4], radii[5],
                            paint
                    );
                }
                canvas.restore();
            }
        }
    }
}
