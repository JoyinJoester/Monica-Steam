/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

final class TelegramControlBackground {
    private TelegramControlBackground() {}
    static Drawable create() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(TelegramUi.dp(23));
        return new RippleDrawable(ColorStateList.valueOf(0x20ffffff), null, mask);
    }
}
