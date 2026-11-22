/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

/** Synthetic host for the real native header and its separate PopupWindow. */
public final class MenuPopupTestActivity extends Activity {
    static float fontScale = 1f;
    static boolean liquid = true;
    TelegramChatHeaderView header;
    TelegramPopupLayout popup;
    int selectedAction;

    @Override protected void attachBaseContext(Context base) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TelegramUi.initialize(this);
        FrameLayout root = new FrameLayout(this);
        View background = new View(this) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(0xff161827);
                for (int i = 0; i < 5; i++) {
                    paint.setColor(i % 2 == 0 ? 0xff343d60 : 0xff272b41);
                    float top = TelegramUi.dp(92 + i * 124);
                    canvas.drawRoundRect(TelegramUi.dp(12), top, getWidth() - TelegramUi.dp(48),
                            top + TelegramUi.dp(96), TelegramUi.dp(20), TelegramUi.dp(20), paint);
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(TelegramUi.dp(16));
                    canvas.drawText("Synthetic message " + (i + 1), TelegramUi.dp(32), top + TelegramUi.dp(46), paint);
                }
            }
        };
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        header = new TelegramChatHeaderView(this);
        header.setTitle("菜单测试");
        header.setSubtitle("离线");
        header.setSourceView(background);
        header.setLiquidStyle(liquid);
        header.setCallbacks(null, null, () -> {
            popup = new TelegramPopupLayout(this, TelegramChatColors.DARK_BLUE);
            popup.setGlassSource(background, liquid);
            String[] labels = {"搜索消息", "刷新", "发起语音"};
            for (int i = 0; i < labels.length; i++) {
                final int action = i + 1;
                popup.addAction(labels[i], 0, TelegramChatColors.DARK_BLUE, true, () -> {
                    selectedAction = action;
                    header.dismissActionMenu();
                });
            }
            header.showActionMenu(popup);
        });
        root.addView(header, new FrameLayout.LayoutParams(-1, TelegramUi.dp(56)));
        setContentView(root);
    }
}
