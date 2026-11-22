/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.graphics.drawable.ColorDrawable;
import android.widget.PopupWindow;

/** PopupWindow lifecycle adapter around the extracted native Telegram popup layout. */
final class TelegramMenuPopupWindow extends PopupWindow {
    private final TelegramPopupLayout layout;
    TelegramMenuPopupWindow(TelegramPopupLayout layout, int width) {
        super(layout, width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        this.layout = layout;
        setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        setOutsideTouchable(true);
        setInputMethodMode(INPUT_METHOD_NOT_NEEDED);
        setAnimationStyle(0);
    }
    @Override public void dismiss() { layout.dismiss(this::dismissImmediately); }
    void dismissImmediately() { super.dismiss(); }
}
