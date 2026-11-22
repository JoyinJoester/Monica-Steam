/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.ImageView;
import com.monica.telegramchat.upstream.ButtonBounce;

/** Native control drawing driven by Telegram's original press/release interpolators. */
final class TelegramBounceImageView extends ImageView {
    private final ButtonBounce bounce = new ButtonBounce(this);

    TelegramBounceImageView(Context context) { super(context); }

    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (bounce != null) bounce.setPressed(pressed);
    }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        float scale = bounce.getScale(.1f);
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        super.draw(canvas);
        canvas.restoreToCount(save);
    }
}
