// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package com.monica.telegramchat.upstream;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.animation.DecelerateInterpolator;

import com.monica.telegramchat.TelegramUi;

public class TypingDotsDrawable extends StatusDrawable {

    private boolean isChat = false;
    private float[] scales = new float[3];
    private float[] startTimes = new float[] {0, 150, 300};
    private float[] elapsedTimes = new float[] {0, 0, 0};
    private long lastUpdateTime = 0;
    private boolean started = false;
    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private boolean ignoreAnimationLocks;

    private Paint currentPaint;

    public TypingDotsDrawable(boolean createPaint) {
        currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setIgnoreAnimationLocks() {
        this.ignoreAnimationLocks = true;
    }

    @Override
    public void setColor(int color) {
        if (currentPaint != null) {
            currentPaint.setColor(color);
        }
    }

    public void setIsChat(boolean value) {
        isChat = value;
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 50) {
            dt = 50;
        }

        for (int a = 0; a < 3; a++) {
            elapsedTimes[a] += dt;
            float timeSinceStart = elapsedTimes[a] - startTimes[a];
            if (timeSinceStart > 0) {
                if (timeSinceStart <= 320) {
                    float diff = decelerateInterpolator.getInterpolation(timeSinceStart / 320.0f);
                    scales[a] = 1.33f + diff;
                } else if (timeSinceStart <= 640) {
                    float diff = decelerateInterpolator.getInterpolation((timeSinceStart - 320.0f) / 320.0f);
                    scales[a] = 1.33f + (1 - diff);
                } else if (timeSinceStart >= 800) {
                    elapsedTimes[a] = 0;
                    startTimes[a] = 0;
                    scales[a] = 1.33f;
                } else {
                    scales[a] = 1.33f;
                }
            } else {
                scales[a] = 1.33f;
            }
        }

        invalidateLimited();
    }

    public void start() {
        lastUpdateTime = System.currentTimeMillis();
        started = true;
        invalidateSelf();
    }

    public void stop() {
        for (int a = 0; a < 3; a++) {
            elapsedTimes[a] = 0;
            scales[a] = 1.33f;
        }
        startTimes[0] = 0;
        startTimes[1] = 150;
        startTimes[2] = 300;
        started = false;
    }

    @Override
    public void draw(Canvas canvas) {
        int x = getBounds().left;
        int y;
        if (isChat) {
            y = TelegramUi.dp(8.5f) + getBounds().top;
        } else {
            y = TelegramUi.dp(9.3f) + getBounds().top;
        }
        Paint paint;
        if (currentPaint == null) {
            paint = currentPaint;
            paint.setAlpha(255);
        } else {
            paint = currentPaint;
        }

        canvas.drawCircle(x + TelegramUi.dp(3), y, scales[0] * TelegramUi.density, paint);
        canvas.drawCircle(x + TelegramUi.dp(9), y, scales[1] * TelegramUi.density, paint);
        canvas.drawCircle(x + TelegramUi.dp(15), y, scales[2] * TelegramUi.density, paint);
        checkUpdate();
    }

    private void checkUpdate() {
        if (started) update();
    }

    public boolean isStarted() {
        return started;
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        if (currentPaint != null) {
            currentPaint.setColorFilter(cf);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return TelegramUi.dp(18);
    }

    @Override
    public int getIntrinsicHeight() {
        return TelegramUi.dp(18);
    }
}
