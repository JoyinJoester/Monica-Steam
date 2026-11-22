/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.graphics.Canvas;
import android.graphics.RenderNode;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;
import java.util.IdentityHashMap;
import java.util.Map;

/** One display list per visible source, shared by its glass pills and edge backdrops. */
@RequiresApi(31)
final class TelegramGlassSource {
    private static final Map<View, TelegramGlassSource> SOURCES = new IdentityHashMap<>();
    private final View source;
    private final RenderNode node = new RenderNode("Steam shared glass source");
    private final Map<View, int[]> targets = new IdentityHashMap<>();
    private final int[] location = new int[2], targetLocation = new int[2];
    private boolean dirty = true;
    private int width, height;
    private final ViewTreeObserver observer;
    private final ViewTreeObserver.OnPreDrawListener preDraw = this::onPreDraw;
    private boolean onPreDraw() {
        boolean changed = source.isDirty() || source.isLayoutRequested() || source.hasTransientState();
        if (changed) dirty = true;
        source.getLocationOnScreen(location);
        for (Map.Entry<View, int[]> entry : targets.entrySet()) {
            View target = entry.getKey();
            target.getLocationOnScreen(targetLocation);
            int x = location[0] - targetLocation[0], y = location[1] - targetLocation[1];
            int[] previous = entry.getValue();
            if (changed || previous[0] != x || previous[1] != y) {
                previous[0] = x; previous[1] = y;
                // Invalidate in this traversal, avoiding a perpetually delayed extra frame.
                if (target.isShown()) target.invalidate();
            }
        }
        return true;
    }
    private final ViewTreeObserver.OnScrollChangedListener scroll = () -> invalidate();
    private final RecyclerView.OnScrollListener listScroll = new RecyclerView.OnScrollListener() {
        @Override public void onScrolled(RecyclerView view, int dx, int dy) {
            // RecyclerView moves its children without changing View.getScrollY().
            // Its callback reports actual movement and viewport layout changes.
            invalidate();
        }
    };

    private TelegramGlassSource(View source) {
        this.source = source;
        observer = source.getViewTreeObserver();
        observer.addOnPreDrawListener(preDraw);
        if (source instanceof RecyclerView) ((RecyclerView) source).addOnScrollListener(listScroll);
        else observer.addOnScrollChangedListener(scroll);
    }

    static TelegramGlassSource acquire(View source, View target) {
        TelegramGlassSource shared = SOURCES.get(source);
        if (shared == null) { shared = new TelegramGlassSource(source); SOURCES.put(source, shared); }
        shared.targets.put(target, new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE});
        return shared;
    }

    void release(View target) {
        targets.remove(target);
        if (!targets.isEmpty()) return;
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(preDraw);
            if (!(source instanceof RecyclerView)) observer.removeOnScrollChangedListener(scroll);
        }
        if (source instanceof RecyclerView) ((RecyclerView) source).removeOnScrollListener(listScroll);
        node.discardDisplayList();
        SOURCES.remove(source);
    }

    void invalidate() {
        dirty = true;
        for (View target : targets.keySet()) if (target.isShown()) target.invalidate();
    }

    void draw(Canvas canvas) {
        int w = source.getWidth(), h = source.getHeight();
        if (w <= 0 || h <= 0 || !source.isAttachedToWindow()) return;
        if (dirty || width != w || height != h || !node.hasDisplayList()) {
            width = w; height = h; dirty = false;
            node.setPosition(0, 0, w, h);
            Canvas recording = node.beginRecording(w, h);
            try { source.draw(recording); } finally { node.endRecording(); }
        }
        canvas.drawRenderNode(node);
    }
}
