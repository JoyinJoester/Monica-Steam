/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class TelegramPopupSizeTest {
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private void await(Runnable assertion) {
        AssertionError failure = null;
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            try { main(assertion); return; } catch (AssertionError error) { failure = error; }
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        throw failure;
    }
    private void measure(TelegramPopupLayout popup, int heightMode) {
        popup.measure(View.MeasureSpec.makeMeasureSpec(TelegramUi.dp(220), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(TelegramUi.dp(720), heightMode));
        popup.layout(0, 0, popup.getMeasuredWidth(), popup.getMeasuredHeight());
    }
    private void assertContentBounds(TelegramPopupLayout popup) {
        int rowsHeight = 0;
        for (int i = 0; i < popup.getItemsCount(); i++) rowsHeight += popup.getItemAt(i).getMeasuredHeight();
        assertEquals("Only menu rows and shadow insets determine wrap-content height",
                rowsHeight + TelegramUi.dp(16), popup.getMeasuredHeight());
        View glass = popup.getChildAt(0);
        if (glass.getVisibility() == View.VISIBLE) {
            assertEquals(TelegramUi.dp(8), glass.getTop());
            assertEquals(popup.getHeight() - TelegramUi.dp(8), glass.getBottom());
            assertEquals(popup.getWidth() - TelegramUi.dp(16), glass.getWidth());
        }
        for (int i = 0; i < popup.getItemsCount(); i++) {
            ViewGroup row = (ViewGroup) popup.getItemAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                View child = row.getChildAt(j);
                if (child instanceof TextView) {
                    TextView label = (TextView) child;
                    assertNotNull(label.getLayout());
                    assertTrue("Menu text must fit vertically", label.getLayout().getHeight() <= label.getHeight());
                }
            }
        }
    }

    @Test public void visibleGlassKeepsSameWrapHeightAsRowsAtNormalAndLargeFont() {
        main(() -> {
            Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
            for (float scale : new float[] {1f, 1.5f, 2f}) {
                Configuration config = new Configuration(base.getResources().getConfiguration());
                config.fontScale = scale;
                Context context = base.createConfigurationContext(config);
                for (boolean liquid : new boolean[] {true, false}) {
                    for (int mode : new int[] {View.MeasureSpec.AT_MOST, View.MeasureSpec.UNSPECIFIED}) {
                        TelegramPopupLayout popup = new TelegramPopupLayout(context, TelegramChatColors.DARK_BLUE);
                        for (String label : new String[] {"搜索消息", "刷新", "发起语音"})
                            popup.addAction(label, 0, TelegramChatColors.DARK_BLUE, true, () -> {});
                        measure(popup, mode);
                        int plainHeight = popup.getMeasuredHeight();
                        popup.setGlassSource(new View(context), liquid);
                        measure(popup, mode);
                        assertEquals("Backdrop must not grow the menu; font=" + scale + " liquid=" + liquid,
                                plainHeight, popup.getMeasuredHeight());
                        assertContentBounds(popup);
                        popup.setGlassSource(null, liquid);
                        measure(popup, mode);
                        assertEquals(plainHeight, popup.getMeasuredHeight());
                    }
                }
            }
        });
    }

    private void screenshot(String name) throws Exception {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "menu-size");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { bitmap.recycle(); }
    }

    @Test public void actualHeaderPopupStaysCompactAndActionsWorkAfterReopening() throws Exception {
        for (float scale : new float[] {1f, 1.5f, 2f}) {
            for (boolean liquid : new boolean[] {true, false}) {
                MenuPopupTestActivity.fontScale = scale;
                MenuPopupTestActivity.liquid = liquid;
                try (ActivityScenario<MenuPopupTestActivity> scenario = ActivityScenario.launch(MenuPopupTestActivity.class)) {
                    MenuPopupTestActivity[] host = new MenuPopupTestActivity[1];
                    scenario.onActivity(activity -> host[0] = activity);
                    await(() -> assertTrue(host[0].header.isShown()));
                    main(() -> assertTrue(host[0].header.getMenuAnchor().performClick()));
                    await(() -> {
                        assertNotNull(host[0].popup);
                        assertTrue(host[0].popup.isAttachedToWindow());
                        assertTrue(host[0].popup.getHeight() > 0);
                        assertEquals(1f, host[0].popup.getItemAt(2).getAlpha(), .01f);
                    });
                    screenshot("header-" + scale + "-" + (liquid ? "glass" : "m3e"));
                    main(() -> assertContentBounds(host[0].popup));
                    for (int repeat = 0; repeat < 2; repeat++) {
                        main(() -> assertTrue(host[0].popup.getItemAt(0).performClick()));
                        await(() -> {
                            assertEquals(1, host[0].selectedAction);
                            assertFalse(host[0].popup.isAttachedToWindow());
                        });
                        main(() -> {
                            host[0].selectedAction = 0;
                            assertTrue(host[0].header.getMenuAnchor().performClick());
                        });
                        await(() -> {
                            assertEquals(1f, host[0].popup.getItemAt(2).getAlpha(), .01f);
                            assertContentBounds(host[0].popup);
                        });
                    }
                    main(() -> host[0].header.dismissActionMenu());
                    await(() -> assertFalse(host[0].popup.isAttachedToWindow()));
                }
            }
        }
    }
}
