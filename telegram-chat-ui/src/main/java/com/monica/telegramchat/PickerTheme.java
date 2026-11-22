/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

/** Explicit per-picker palette for the extracted source cells. No account theme globals. */
public final class PickerTheme {
    public static final int key_checkboxCheck = 1, key_chat_serviceBackground = 2,
        key_dialogBackground = 3, key_chat_attachPhotoBackground = 4, key_checkbox = 5,
        key_checkboxDisabled = 6, key_switchTrack = 7, key_switch2Track = 8,
        key_text = 9, key_secondaryText = 10;
    public static final Paint PAINT_CLEAR = new Paint(Paint.ANTI_ALIAS_FLAG);
    static { PAINT_CLEAR.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); }
    public interface ResourcesProvider { int getColor(int key); boolean isDark(); }
    public static int getColor(int key, ResourcesProvider provider) { return provider.getColor(key); }
    public static ResourcesProvider colors(TelegramChatColors colors) {
        return new ResourcesProvider() {
            public boolean isDark() { return colors.dark; }
            public int getColor(int key) {
                switch (key) {
                    case key_checkboxCheck: return colors.accentText;
                    case key_dialogBackground: return colors.menuBackground;
                    case key_text: return colors.text;
                    case key_secondaryText: return colors.secondaryText;
                    case key_checkbox: case key_chat_attachPhotoBackground: return colors.accent;
                    default: return colors.secondaryText;
                }
            }
        };
    }
}
