/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import com.monica.telegramchat.upstream.BackDrawable;
import com.monica.telegramchat.upstream.MessageDrawable;

/** Small host API; every drawable delegates to compiled Telegram source or original assets. */
public final class TelegramDrawables {
    private TelegramDrawables() {}

    public static MessageDrawable message(boolean outgoing, int color, boolean groupTop, boolean groupBottom) {
        MessageDrawable drawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, outgoing, false) {
            @Override public void draw(Canvas canvas) {
                setTop(0, getBounds().width(), getBounds().height(), groupTop, groupBottom);
                super.draw(canvas);
            }
        };
        drawable.overrideColor = color;
        drawable.setDrawFullBubble(true);
        return drawable;
    }

    public static BackDrawable back(int color) {
        BackDrawable drawable = new BackDrawable(false);
        drawable.setColor(color);
        return drawable;
    }

    public static Drawable send(Context context, int color) {
        return tinted(context, R.drawable.telegram_msg_send, color);
    }

    public static Drawable attach(Context context, int color) {
        return tinted(context, R.drawable.telegram_input_attach, color);
    }

    public static Drawable gift(Context context, int color) {
        return tinted(context, R.drawable.telegram_msg_input_gift, color);
    }

    public static Drawable tinted(Context context, int resource, int color) {
        initialize(context);
        Drawable drawable = context.getDrawable(resource).mutate();
        drawable.setTint(color);
        return drawable;
    }

    public static void initialize(Context context) { TelegramUi.initialize(context); }
}
