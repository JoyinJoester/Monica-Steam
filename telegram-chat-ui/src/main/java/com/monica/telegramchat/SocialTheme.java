/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
/** Per-screen color boundary; never changes Telegram or application global themes. */
public final class SocialTheme {
    public static final int key_windowBackgroundWhiteBlackText = 0, key_windowBackgroundWhiteGrayText2 = 1,
        key_windowBackgroundWhiteGrayText = 1, key_windowBackgroundWhiteGrayIcon = 1;
    public final int text, secondary, accent, accentText, surface;
    public final Paint dividerPaint = new Paint(3), badgePaint = new Paint(3);
    public SocialTheme(int text, int secondary, int accent, int accentText, int surface, int divider) {
        this.text=text; this.secondary=secondary; this.accent=accent; this.accentText=accentText; this.surface=surface;
        dividerPaint.setColor(divider); badgePaint.setColor(accent);
    }
    public int getColor(int key) { return key == 0 ? text : secondary; }
    public Drawable selector() { return new RippleDrawable(ColorStateList.valueOf((accent & 0xffffff) | 0x22000000), new ColorDrawable(0), new ColorDrawable(-1)); }
}
