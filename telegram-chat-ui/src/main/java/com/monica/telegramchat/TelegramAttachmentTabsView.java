/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.monica.telegramchat.upstream.GlassAttachTab;
import static com.monica.telegramchat.TelegramUi.dp;

/** Steam's supported attachment actions hosted by original Telegram attach tabs. */
public final class TelegramAttachmentTabsView extends ViewGroup {
    private final TelegramGlassView glass;
    private final GlassAttachTab gallery, file;
    private Runnable onGallery, onFile;

    public TelegramAttachmentTabsView(Context context) {
        super(context);
        TelegramUi.initialize(context);
        glass = new TelegramGlassView(context);
        glass.setChatStyle(true); glass.setCornerRadius(dp(28)); addView(glass);
        gallery = new GlassAttachTab(context); file = new GlassAttachTab(context);
        gallery.setAnimations(R.raw.telegram_tab_gallery, R.raw.telegram_tab_gallery_reverse);
        file.setAnimations(R.raw.telegram_tab_files, R.raw.telegram_tab_files_reverse);
        addView(gallery); addView(file);
        gallery.setTabSelected(true, false);
        gallery.setOnClickListener(v -> {
            gallery.setTabSelected(true, true); file.setTabSelected(false, true);
            if (onGallery != null) onGallery.run();
        });
        file.setOnClickListener(v -> {
            gallery.setTabSelected(false, true); file.setTabSelected(true, true);
            if (onFile != null) onFile.run();
        });
    }
    public void setCallbacks(Runnable onGallery, Runnable onFile) {
        this.onGallery = onGallery; this.onFile = onFile;
    }
    public void setLabels(String galleryLabel, String gallerySummary, String fileLabel, String fileSummary) {
        gallery.setText(galleryLabel); gallery.setContentDescription(galleryLabel + ", " + gallerySummary);
        file.setText(fileLabel); file.setContentDescription(fileLabel + ", " + fileSummary);
        requestLayout();
    }
    public void setColors(TelegramChatColors colors) {
        glass.setTintColor(colors.composerTint); glass.setDark(colors.dark);
        gallery.setColors(colors); file.setColors(colors);
    }
    public void setSourceView(View source) { glass.setSourceView(source); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = resolveSize(dp(72), heightSpec);
        int tabHeight = Math.max(0, height - dp(16));
        int tabWidth = Math.max(dp(48), (width - dp(32)) / 2);
        int h = MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY);
        gallery.measure(MeasureSpec.makeMeasureSpec(Math.max(dp(48), Math.min(tabWidth,
                (int) gallery.measureAttachTabWidth())), MeasureSpec.EXACTLY), h);
        file.measure(MeasureSpec.makeMeasureSpec(Math.max(dp(48), Math.min(tabWidth,
                (int) file.measureAttachTabWidth())), MeasureSpec.EXACTLY), h);
        glass.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(14)), MeasureSpec.EXACTLY), h);
        setMeasuredDimension(width, height);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = dp(4), bottom = top + gallery.getMeasuredHeight();
        glass.layout(dp(7), top, getWidth() - dp(7), bottom);
        int left = getWidth() / 4 - gallery.getMeasuredWidth() / 2;
        gallery.layout(left, top, left + gallery.getMeasuredWidth(), bottom);
        left = getWidth() * 3 / 4 - file.getMeasuredWidth() / 2;
        file.layout(left, top, left + file.getMeasuredWidth(), bottom);
    }
}
