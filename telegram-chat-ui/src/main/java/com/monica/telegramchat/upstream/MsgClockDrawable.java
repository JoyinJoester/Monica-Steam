// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.monica.telegramchat.TelegramUi;

public class MsgClockDrawable extends Drawable {

    private ConstantState constantState;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;
    private int colorAlpha = 255;
    private long startTime;
    private int color;

    public MsgClockDrawable() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(TelegramUi.dp(1f));
        startTime = System.currentTimeMillis();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int r = Math.min(bounds.width(), bounds.height());
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), (r >> 1) - TelegramUi.dp(0.5f), paint);

        long currentTime = System.currentTimeMillis();
        float rotateTime = 1500;
        float rotateHourTime = rotateTime * 3;

        canvas.save();
        canvas.rotate(360 * ((currentTime - startTime) % rotateTime) / rotateTime, bounds.centerX(), bounds.centerY());
        canvas.drawLine(bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY() - TelegramUi.dp(3), paint);
        canvas.restore();

        canvas.save();
        canvas.rotate(360 * ((currentTime - startTime) % rotateHourTime) / rotateHourTime, bounds.centerX(), bounds.centerY());
        canvas.drawLine(bounds.centerX(), bounds.centerY(), bounds.centerX() + TelegramUi.dp(2.3f), bounds.centerY(), paint);
        canvas.restore();
    }

    public void setColor(int color) {
        if (color != this.color) {
            colorAlpha = Color.alpha(color);
            paint.setColor(ColorUtils.setAlphaComponent(color, (int) (alpha * (colorAlpha / 255f))));
        }
        this.color = color;
    }

    @Override
    public int getIntrinsicHeight() {
        return TelegramUi.dp(12);
    }

    @Override
    public int getIntrinsicWidth() {
        return TelegramUi.dp(12);
    }

    @Override
    public void setAlpha(int i) {
        if (alpha != i) {
            alpha = i;
            paint.setAlpha((int) (alpha * (colorAlpha / 255f)));
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Nullable
    @Override
    public ConstantState getConstantState() {
        if (constantState == null) {
            constantState = new ConstantState() {
                @NonNull
                @Override
                public Drawable newDrawable() {
                    return new MsgClockDrawable();
                }
                @Override
                public int getChangingConfigurations() {
                    return 0;
                }
            };
        }
        return constantState;
    }
}
