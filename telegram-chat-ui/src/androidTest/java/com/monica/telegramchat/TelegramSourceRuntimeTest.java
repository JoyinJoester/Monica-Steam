/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RenderNode;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import com.monica.telegramchat.upstream.LiquidGlassEffect;
import com.monica.telegramchat.upstream.MessageDrawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class TelegramSourceRuntimeTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    private Bitmap bubble(boolean outgoing, boolean topNear, boolean bottomNear, int alpha) {
        TelegramDrawables.initialize(context());
        MessageDrawable drawable = TelegramDrawables.message(outgoing, Color.RED, topNear, bottomNear);
        drawable.shadow = false;
        int width = TelegramUi.dp(180), height = TelegramUi.dp(80);
        drawable.setBounds(0, 0, width, height);
        drawable.setAlpha(alpha);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }

    @Test public void originalBubbleRendererChangesTailAndGroupingAndKeepsAlpha() {
        Bitmap incoming = bubble(false, false, false, 255);
        Bitmap outgoing = bubble(true, false, false, 255);
        Bitmap grouped = bubble(true, true, true, 255);
        Bitmap translucent = bubble(true, false, false, 128);
        assertEquals(Color.RED, outgoing.getPixel(outgoing.getWidth() / 2, outgoing.getHeight() / 2));
        assertEquals(0, Color.alpha(outgoing.getPixel(0, 0)));
        assertFalse("Incoming and outgoing tails must differ", Arrays.equals(pixels(incoming), pixels(outgoing)));
        assertFalse("Adjacent grouping must change original path", Arrays.equals(pixels(outgoing), pixels(grouped)));
        int alpha = Color.alpha(translucent.getPixel(translucent.getWidth() / 2, translucent.getHeight() / 2));
        assertTrue("Cached nine-patch must preserve drawable opacity: " + alpha, alpha >= 126 && alpha <= 130);
        incoming.recycle(); outgoing.recycle(); grouped.recycle(); translucent.recycle();
    }

    @Test public void nativeInputPreservesUnicodeMultilineAndSuppressesProgrammaticEcho() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TelegramEditText input = new TelegramEditText(context());
            input.setCursorColor(Color.BLUE);
            input.setCursorSize(TelegramUi.dp(22));
            AtomicInteger changed = new AtomicInteger();
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.incrementAndGet(); }
                @Override public void afterTextChanged(Editable text) {}
            });
            input.setTextWatchersSuppressed(true, false);
            input.setText("Steam 中文\nemoji 😀");
            assertEquals(0, changed.get());
            input.setTextWatchersSuppressed(false, false);
            input.append(" reply");
            assertEquals(1, changed.get());
            assertEquals("Steam 中文\nemoji 😀 reply", input.getText().toString());
            input.setSelection(input.length());
            assertEquals(input.length(), input.getSelectionStart());
            input.measure(android.view.View.MeasureSpec.makeMeasureSpec(500, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.AT_MOST));
            input.layout(0, 0, input.getMeasuredWidth(), input.getMeasuredHeight());
            Bitmap bitmap = Bitmap.createBitmap(500, 200, Bitmap.Config.ARGB_8888);
            input.draw(new Canvas(bitmap));
            assertNotNull(input.getLayout());
            bitmap.recycle();
        });
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    public void originalAgslCompilesAndAcceptsRealRenderNodeDimensions() {
        TelegramUi.initialize(context());
        RenderNode node = new RenderNode("Original Telegram AGSL test");
        node.setPosition(0, 0, 480, 96);
        LiquidGlassEffect effect = new LiquidGlassEffect(node);
        effect.update(0, 0, 480, 96, 24, 24, 24, 24, 7, 0.8f, 1.15f, 0xaaffffff);
        assertEquals(480, node.getWidth());
        node.discardDisplayList();
    }

    @Test public void composerEchoKeepsComposingSpanSelectionAndCaptionlessSend() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TelegramChatComposerView composer = new TelegramChatComposerView(context());
            AtomicInteger echoes = new AtomicInteger(), sends = new AtomicInteger();
            composer.setCallbacks(text -> echoes.incrementAndGet(), sends::incrementAndGet, null, null, null);
            composer.setTextIfChanged("中文 composing");
            android.text.Editable editable = composer.getEditText().getText();
            Object composing = new Object();
            editable.setSpan(composing, 0, 2, android.text.Spanned.SPAN_COMPOSING | android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            composer.getEditText().setSelection(2);
            composer.setTextIfChanged("中文 composing");
            assertSame(editable, composer.getEditText().getText());
            assertEquals(0, editable.getSpanStart(composing));
            assertEquals(2, composer.getEditText().getSelectionStart());
            assertEquals(0, echoes.get());
            composer.setTextIfChanged("");
            composer.setSendContent(true);
            composer.setSendEnabled(true);
            composer.getEditText().onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
            assertEquals(1, sends.get());
            composer.setSendEnabled(false);
            composer.getEditText().onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
            assertEquals(1, sends.get());
        });
    }

    @Test public void textCellMeasuresUnicodeRtlAndTimestampWithinNarrowBounds() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TelegramMessageTextView view = new TelegramMessageTextView(context());
            for (String text : new String[] {"中文 😀\nsecond line", "مرحبا بالعالم", "W", "a long text ".repeat(80)}) {
                view.setMessage(text, "12:35", true, TelegramMessageTextView.DELIVERY_SENT);
                int width = TelegramUi.dp(210);
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.AT_MOST),
                        android.view.View.MeasureSpec.makeMeasureSpec(4096, android.view.View.MeasureSpec.AT_MOST));
                assertTrue(view.getMeasuredWidth() <= width);
                assertTrue(view.getMeasuredHeight() > 0);
                view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(bitmap));
                assertFalse(Arrays.stream(pixels(bitmap)).allMatch(pixel -> pixel == 0));
                bitmap.recycle();
            }
        });
    }

    @Test public void sourceAttachmentAnimationsDecodeAndNativeTargetsDispatch() {
        for (TelegramChatColors colors : new TelegramChatColors[] {TelegramChatColors.DAY,
                TelegramChatColors.NIGHT, TelegramChatColors.DARK_BLUE}) {
            assertTrue("Attachment labels/icons must contrast with the themed glass background",
                    androidx.core.graphics.ColorUtils.calculateContrast(colors.tabDefault,
                            colors.composerTint | 0xff000000) >= 4.5);
        }
        for (int id : new int[] {R.raw.telegram_tab_gallery, R.raw.telegram_tab_gallery_reverse,
                R.raw.telegram_tab_files, R.raw.telegram_tab_files_reverse}) {
            com.airbnb.lottie.LottieResult<com.airbnb.lottie.LottieComposition> result =
                    com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(context(), id);
            assertNull(result.getException());
            assertNotNull(result.getValue());
            assertTrue(result.getValue().getDurationFrames() > 1);
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TelegramAttachmentTabsView tabs = new TelegramAttachmentTabsView(context());
            AtomicInteger gallery = new AtomicInteger(), file = new AtomicInteger();
            tabs.setLabels("Gallery", "Images and videos", "File", "Supported documents");
            tabs.setColors(TelegramChatColors.DARK_BLUE);
            tabs.setCallbacks(gallery::incrementAndGet, file::incrementAndGet);
            int width = TelegramUi.dp(320);
            tabs.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(TelegramUi.dp(300), android.view.View.MeasureSpec.AT_MOST));
            tabs.layout(0, 0, width, tabs.getMeasuredHeight());
            for (int index = 1; index < tabs.getChildCount(); index++) {
                android.view.View target = tabs.getChildAt(index);
                assertTrue(target.getHeight() >= TelegramUi.dp(48));
                assertTrue(target.getWidth() >= TelegramUi.dp(48));
                assertTrue(target.performClick());
            }
            assertEquals(1, gallery.get()); assertEquals(1, file.get());
        });
    }
}
