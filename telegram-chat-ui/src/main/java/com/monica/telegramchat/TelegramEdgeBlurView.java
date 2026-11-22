/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import androidx.annotation.RequiresApi;

/** Full-width chrome: blur radius and tint both taper towards the conversation. */
public final class TelegramEdgeBlurView extends View {
    private View source;
    private boolean top;
    private int tint;
    private Renderer31 renderer;
    private final Paint paint = new Paint();
    private TelegramGlassSource sharedSource;
    private int gradientHeight = -1, gradientTint;
    private boolean gradientTop;
    private LinearGradient tintGradient;

    public TelegramEdgeBlurView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setSourceView(View next) {
        if (source == next) return;
        if (next == this) throw new IllegalArgumentException("Blur source must be separate");
        for (android.view.ViewParent parent = getParent(); parent != null; parent = parent.getParent()) {
            if (parent == next) throw new IllegalArgumentException("Blur source cannot contain its overlay");
        }
        removeListeners();
        source = next;
        if (isAttachedToWindow()) addListeners();
        invalidate();
    }

    public void setTopEdge(boolean value) { if (top != value) { top = value; invalidate(); } }
    public void setTintColor(int value) { if (tint != value) { tint = value; invalidate(); } }
    public void refresh() {
        if (sharedSource != null) sharedSource.invalidate();
        else postInvalidateOnAnimation();
    }
    private void addListeners() {
        if (source != null && sharedSource == null && Build.VERSION.SDK_INT >= 31) {
            sharedSource = TelegramGlassSource.acquire(source, this);
        }
    }
    private void removeListeners() {
        if (sharedSource != null) { sharedSource.release(this); sharedSource = null; }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); addListeners(); }
    @Override protected void onDetachedFromWindow() {
        removeListeners();
        if (renderer != null) renderer.release();
        renderer = null;
        super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated() && source != null && source.getWidth() > 0) {
            if (renderer == null) renderer = new Renderer31(getResources().getDisplayMetrics().density);
            renderer.draw(canvas, this, source, sharedSource, top);
        }
        // Software/older hosts retain a continuous tint fade without pretending to blur.
        if (tintGradient == null || gradientHeight != getHeight() || gradientTop != top || gradientTint != tint) {
            int transparentTint = tint & 0x00ffffff;
            tintGradient = new LinearGradient(0, 0, 0, getHeight(),
                    top ? tint : transparentTint, top ? transparentTint : tint, Shader.TileMode.CLAMP);
            gradientHeight = getHeight(); gradientTop = top; gradientTint = tint;
        }
        paint.setShader(tintGradient);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
    }

    @RequiresApi(31)
    private static final class Renderer31 {
        // Interpolate Gaussian radii, rather than just fading a single fixed blur.
        private static final int LEVELS = 3;
        private final RenderNode captured = new RenderNode("Steam edge source");
        private final RenderNode[] levels = new RenderNode[LEVELS];
        private final Paint mask = new Paint(), merge = new Paint();
        private final int[] sourceLocation = new int[2], targetLocation = new int[2];
        private final int bleed;
        private final LinearGradient[] gradients = new LinearGradient[LEVELS];
        private LinearGradient fade;
        private int gradientHeight = -1;
        private boolean gradientTop;

        Renderer31(float density) {
            bleed = (int) Math.ceil(42f * density);
            mask.setBlendMode(BlendMode.DST_IN);
            merge.setBlendMode(BlendMode.PLUS);
            for (int i = 0; i < LEVELS; i++) {
                levels[i] = new RenderNode("Steam edge radius " + i);
                if (i > 0) {
                    float radius = 14f * density * i / (LEVELS - 1);
                    levels[i].setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
                }
            }
        }
        void draw(Canvas canvas, View target, View source, TelegramGlassSource shared, boolean top) {
            int width = target.getWidth(), height = target.getHeight();
            int paddedWidth = width + 2 * bleed, paddedHeight = height + 2 * bleed;
            source.getLocationOnScreen(sourceLocation);
            target.getLocationOnScreen(targetLocation);
            captured.setPosition(0, 0, paddedWidth, paddedHeight);
            Canvas recording = captured.beginRecording(paddedWidth, paddedHeight);
            try {
                recording.translate(bleed + sourceLocation[0] - targetLocation[0], bleed + sourceLocation[1] - targetLocation[1]);
                if (shared != null) shared.draw(recording);
                else source.draw(recording);
            } finally { captured.endRecording(); }

            if (gradientHeight != height || gradientTop != top) {
                for (int i = 0; i < LEVELS; i++) {
                    int[] colors = new int[LEVELS];
                    for (int stop = 0; stop < LEVELS; stop++) {
                        int level = top ? LEVELS - 1 - stop : stop;
                        colors[stop] = level == i ? Color.WHITE : Color.TRANSPARENT;
                    }
                    gradients[i] = new LinearGradient(0, 0, 0, height, colors, null, Shader.TileMode.CLAMP);
                }
                fade = new LinearGradient(0, 0, 0, height,
                        top ? Color.WHITE : Color.TRANSPARENT, top ? Color.TRANSPARENT : Color.WHITE, Shader.TileMode.CLAMP);
                gradientHeight = height; gradientTop = top;
            }
            int combined = canvas.saveLayer(0, 0, width, height, null);
            for (int i = 0; i < LEVELS; i++) {
                RenderNode node = levels[i];
                node.setPosition(0, 0, paddedWidth, paddedHeight);
                Canvas layer = node.beginRecording(paddedWidth, paddedHeight);
                try { layer.drawRenderNode(captured); } finally { node.endRecording(); }
                int blend = canvas.saveLayer(0, 0, width, height, merge);
                canvas.save();
                canvas.translate(-bleed, -bleed);
                canvas.drawRenderNode(node);
                canvas.restore();
                mask.setShader(gradients[i]);
                canvas.drawRect(0, 0, width, height, mask);
                canvas.restoreToCount(blend);
            }
            mask.setShader(fade);
            canvas.drawRect(0, 0, width, height, mask);
            canvas.restoreToCount(combined);
        }
        void release() {
            captured.discardDisplayList();
            for (RenderNode node : levels) node.discardDisplayList();
        }
    }
}
