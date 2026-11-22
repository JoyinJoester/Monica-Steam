/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Telegram's original pattern, rasterized once off the UI thread and tinted per drawable. */
public final class TelegramWallpaperDrawable extends Drawable {
    // A single immutable alpha mask at the SVG's native resolution: 4,262,400 bytes.
    // It contains only bundled decoration, never messages, account data or a captured View.
    private static final int PATTERN_WIDTH = 1440, PATTERN_HEIGHT = 2960;
    private static final Object TILE_LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "telegram-wallpaper");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final List<WeakReference<TelegramWallpaperDrawable>> WAITING = new ArrayList<>();
    private static Bitmap sharedTile;
    private static boolean loading;

    private final Resources resources;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint background = new Paint();
    private final RectF destination = new RectF();
    private Bitmap tile;
    private boolean waiting;
    private TelegramChatColors colors = TelegramChatColors.DARK_BLUE;
    private int alpha = 255;
    private float patternStrength = 1f;

    public TelegramWallpaperDrawable(Context context) {
        resources = context.getApplicationContext().getResources();
        updatePaint();
    }

    public void setColors(TelegramChatColors colors) {
        boolean unchanged = this.colors.background == colors.background &&
                this.colors.secondaryText == colors.secondaryText && this.colors.dark == colors.dark;
        this.colors = colors;
        if (!unchanged) updatePaint();
    }

    private void updatePaint() {
        background.setColor(colors.background);
        paint.setColorFilter(new PorterDuffColorFilter(colors.secondaryText, PorterDuff.Mode.SRC_IN));
        paint.setAlpha(Math.round(alpha * (colors.dark ? .13f : .18f) * patternStrength));
        invalidateSelf();
    }

    public void setPatternStrength(float strength) {
        float next = Math.max(0f, Math.min(1f, strength));
        if (patternStrength == next) return;
        patternStrength = next;
        updatePaint();
        requestTile();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        requestTile();
    }

    private void requestTile() {
        if (patternStrength <= 0f || getBounds().isEmpty() || tile != null) return;
        synchronized (TILE_LOCK) {
            if (sharedTile != null) { tile = sharedTile; return; }
            if (!waiting) {
                waiting = true;
                WAITING.add(new WeakReference<>(this));
            }
            if (loading) return;
            loading = true;
        }
        // Only application resources cross this boundary. Neither the worker nor the waiting
        // list retains a screen, and plain wallpaper never schedules parsing or allocation.
        Resources appResources = resources;
        WORKER.execute(() -> {
            Bitmap result = null;
            try {
                SVG pattern = SVG.getFromResource(appResources, R.raw.telegram_default_pattern);
                result = Bitmap.createBitmap(PATTERN_WIDTH, PATTERN_HEIGHT, Bitmap.Config.ALPHA_8);
                pattern.setDocumentWidth(PATTERN_WIDTH);
                pattern.setDocumentHeight(PATTERN_HEIGHT);
                pattern.renderToCanvas(new Canvas(result));
            } catch (SVGParseException | RuntimeException error) {
                Log.w("TelegramWallpaper", "Bundled pattern unavailable; keeping theme background", error);
                result = null;
            }
            Bitmap ready = result;
            MAIN.post(() -> {
                List<WeakReference<TelegramWallpaperDrawable>> listeners;
                synchronized (TILE_LOCK) {
                    sharedTile = ready;
                    loading = false;
                    listeners = new ArrayList<>(WAITING);
                    WAITING.clear();
                }
                for (WeakReference<TelegramWallpaperDrawable> reference : listeners) {
                    TelegramWallpaperDrawable drawable = reference.get();
                    if (drawable != null) {
                        drawable.waiting = false;
                        drawable.tile = ready;
                        drawable.invalidateSelf();
                    }
                }
            });
        });
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        background.setAlpha(alpha);
        canvas.drawRect(bounds, background);
        if (tile != null && patternStrength > 0f && !bounds.isEmpty()) {
            int height = Math.max(1, Math.round(bounds.width() * PATTERN_HEIGHT / (float) PATTERN_WIDTH));
            int save = canvas.save();
            canvas.clipRect(bounds);
            for (int y = bounds.top; y < bounds.bottom; y += height) {
                destination.set(bounds.left, y, bounds.right, y + height);
                canvas.drawBitmap(tile, null, destination, paint);
            }
            canvas.restoreToCount(save);
        }
    }

    @Override public void setAlpha(int alpha) {
        if (this.alpha != alpha) { this.alpha = alpha; updatePaint(); }
    }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return alpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }
}
