/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.Canvas;
import androidx.recyclerview.widget.RecyclerView;
import com.monica.telegramchat.upstream.RecyclerItemsEnterAnimator;

/** Connects the original Telegram item entry animator to AndroidX RecyclerView. */
public class TelegramRecyclerView extends RecyclerView {
    private final RecyclerItemsEnterAnimator enterAnimator;
    private Runnable beforeViewportResize;

    public TelegramRecyclerView(Context context) {
        super(context);
        TelegramUi.initialize(context);
        enterAnimator = new RecyclerItemsEnterAnimator(this, false);
    }

    public RecyclerItemsEnterAnimator getItemsEnterAnimator() { return enterAnimator; }
    public void setBeforeViewportResize(Runnable listener) { beforeViewportResize = listener; }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // LinearLayoutManager(stackFromEnd=true) normally preserves the bottom edge.
        // Let the host pin an older reading anchor before children are laid out again.
        if (oldHeight > 0 && height != oldHeight && beforeViewportResize != null) beforeViewportResize.run();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        enterAnimator.dispatchDraw();
        super.dispatchDraw(canvas);
    }

    @Override protected void onDetachedFromWindow() {
        enterAnimator.onDetached();
        super.onDetachedFromWindow();
    }
}
