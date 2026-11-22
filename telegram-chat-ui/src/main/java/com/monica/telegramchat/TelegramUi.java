/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.content.res.Resources;
import androidx.core.graphics.ColorUtils;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** The complete platform boundary of the extracted Telegram UI; retains no Activity. */
public final class TelegramUi {
    private static Resources resources = Resources.getSystem();
    public static float density = resources.getDisplayMetrics().density;

    private TelegramUi() {}

    public static void initialize(Context context) {
        resources = context.getApplicationContext().getResources();
        density = context.getResources().getDisplayMetrics().density;
    }

    public static Resources resources() { return resources; }
    public static boolean isRTL() { return resources.getConfiguration().getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL; }
    public static int dp(float value) { return (int) Math.ceil(density * value); }
    public static int dp2(float value) { return (int) Math.floor(density * value); }
    public static float dpf2(float value) { return density * value; }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static int lerp(int a, int b, float t) { return a + (int) ((b - a) * t); }
    public static int getOffsetColor(int a, int b, float t, float alpha) {
        int color = ColorUtils.blendARGB(a, b, Math.max(0, Math.min(1, t)));
        return ColorUtils.setAlphaComponent(color, Math.round(android.graphics.Color.alpha(color) * alpha));
    }
    public static String readRes(int id) {
        try (InputStream stream = resources.openRawResource(id);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = stream.read(buffer)) != -1) bytes.write(buffer, 0, length);
            return bytes.toString(StandardCharsets.UTF_8.name());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read Telegram shader", exception);
        }
    }
}
