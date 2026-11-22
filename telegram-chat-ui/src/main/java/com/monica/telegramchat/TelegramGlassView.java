/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import androidx.annotation.RequiresApi;
import android.graphics.RuntimeShader;
import com.monica.telegramchat.upstream.GlassStroke;

/**
 * Captures an actual, separate source View behind this overlay. API33+ runs Kyant AndroidLiquidGlass
 * refraction AGSL over the blurred RenderNode. API31-32 use platform blur;
 * older/software canvases use the supplied translucent tint. Source must not contain this view.
 */
public final class TelegramGlassView extends View {
    private View source;
    private int tintColor = 0xd9ffffff;
    private float cornerRadius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private Renderer renderer;
    private boolean dark = true, composerStyle, strokeEnabled = true, liquidStyle = true;
    private boolean frostedPanelStyle;
    /** Edge refraction depth. A full-bleed backdrop wants ~0 so it has no rim crease. */
    private float liquidThickness = 11f;
    private final float[] radii = new float[8];
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TelegramGlassSource sharedSource;

    public TelegramGlassView(Context context) {
        super(context);
        TelegramUi.initialize(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setSourceView(View source) {
        if (source == this) throw new IllegalArgumentException("Glass source must be a separate view");
        for (android.view.ViewParent parent = getParent(); parent != null; parent = parent.getParent()) {
            if (parent == source) throw new IllegalArgumentException("Glass source cannot contain its own overlay");
        }
        if (this.source == source) return;
        removeSourceListener();
        this.source = source;
        if (isAttachedToWindow()) addSourceListener();
        invalidate();
    }

    public void setLiquidStyle(boolean enabled) {
        if (liquidStyle != enabled) { liquidStyle = enabled; invalidate(); }
    }
    /** A broad Gaussian-only panel preserves its supplied tint instead of the control's scrim. */
    public void setFrostedPanelStyle(boolean enabled) {
        if (frostedPanelStyle != enabled) { frostedPanelStyle = enabled; invalidate(); }
    }
    public void setTintColor(int color) { if (tintColor != color) { tintColor = color; invalidate(); } }
    /** Original topPanelChatActivity / bottomPanelChatActivity stroke configuration. */
    public void setChatStyle(boolean composer) { if (composerStyle != composer) { composerStyle = composer; invalidate(); } }
    /** Full-bleed backdrops draw no rim; the floating pills keep theirs. */
    public void setStrokeEnabled(boolean enabled) { if (strokeEnabled != enabled) { strokeEnabled = enabled; invalidate(); } }

    /** Sets the liquid-glass edge thickness in dp; 0 removes the refraction crease on flat bars. */
    public void setLiquidThickness(float dp) {
        float next = Math.max(0, dp);
        if (liquidThickness != next) { liquidThickness = next; invalidate(); }
    }
    public void setDark(boolean dark) { if (this.dark != dark) { this.dark = dark; invalidate(); } }
    public void setCornerRadius(float pixels) { float next = Math.max(0, pixels); if (cornerRadius != next) { cornerRadius = next; invalidate(); } }
    public void refresh() {
        if (sharedSource != null) sharedSource.invalidate();
        else postInvalidateOnAnimation();
    }

    private void addSourceListener() {
        if (source != null && sharedSource == null && Build.VERSION.SDK_INT >= 31) {
            sharedSource = TelegramGlassSource.acquire(source, this);
        }
    }

    private void removeSourceListener() {
        if (sharedSource != null) { sharedSource.release(this); sharedSource = null; }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addSourceListener();
    }

    @Override protected void onDetachedFromWindow() {
        removeSourceListener();
        if (renderer != null) renderer.release();
        renderer = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        rect.set(0, 0, getWidth(), getHeight());
        clipPath.rewind();
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        boolean rendered = false;
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated() && source != null && source.getWidth() > 0) {
            if (renderer == null) renderer = new Renderer31();
            rendered = renderer.draw(canvas, this, source, sharedSource, cornerRadius, effectiveTint(),
                liquidStyle ? liquidThickness : 0f, frostedPanelStyle ? 18f : liquidStyle ? 6f : 10f);
        }
        if (!rendered) {
            paint.setColor(effectiveTint() | 0xff000000);
            canvas.drawRect(rect, paint);
        }
        // Original BlurredBackgroundDrawable.drawStroke, retaining distinct top/bottom colors.
        if (strokeEnabled && liquidStyle) {
            java.util.Arrays.fill(radii, cornerRadius);
            final float stroke = TelegramUi.dpf2(composerStyle ? .5f : .55f);
            strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(stroke);
            strokePaint.setColor(dark ? (composerStyle ? 0x28ffffff : 0x20ffffff) : 0xffffffff);
            GlassStroke.drawStroke(canvas, 0, 0, getWidth(), getHeight(), radii, stroke, true, strokePaint);
            strokePaint.setColor(dark ? 0x14ffffff : 0xffffffff);
            GlassStroke.drawStroke(canvas, 0, 0, getWidth(), getHeight(), radii, stroke, false, strokePaint);
        }
        canvas.restoreToCount(save);
    }

    private int effectiveTint() {
        return liquidStyle || frostedPanelStyle ? tintColor : (tintColor & 0x00ffffff) | 0xe0000000;
    }

    private interface Renderer {
        boolean draw(Canvas canvas, View target, View source, TelegramGlassSource shared, float radius, int tint, float thicknessDp, float blurDp);
        void release();
    }

    @RequiresApi(31)
    private static final class Renderer31 implements Renderer {
        private final RenderNode blurred = new RenderNode("Telegram blur source");
        private final RenderNode glass = new RenderNode("Telegram liquid glass");
        private final int[] sourceLocation = new int[2], targetLocation = new int[2];
        private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RuntimeShader liquid;
        private float lastBlurDp = -1f;
        private boolean shaderFailed;
        private int shaderWidth = -1, shaderHeight = -1;
        private float shaderRadius = -1, shaderThickness = -1;


        @Override public boolean draw(Canvas canvas, View target, View source, TelegramGlassSource shared, float radius, int tint, float thicknessDp, float blurDp) {
            if (lastBlurDp != blurDp) {
                float blur = TelegramUi.dpf2(blurDp);
                blurred.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
                lastBlurDp = blurDp;
            }
            int width = target.getWidth(), height = target.getHeight();
            source.getLocationOnScreen(sourceLocation);
            target.getLocationOnScreen(targetLocation);
            blurred.setPosition(0, 0, width, height);
            Canvas recording = blurred.beginRecording(width, height);
            try {
                recording.translate(sourceLocation[0] - targetLocation[0], sourceLocation[1] - targetLocation[1]);
                if (shared != null) shared.draw(recording);
                else source.draw(recording);
            } finally {
                blurred.endRecording();
            }
            // thicknessDp <= 0 opts out of the AGSL pass entirely: a full-width bar only wants the
            // plain blur, because the shader's edge refraction shows up as a dark crease.
            if (Build.VERSION.SDK_INT >= 33 && !shaderFailed && thicknessDp > 0f) {
                try {
                    glass.setPosition(0, 0, width, height);
                    if (liquid == null) liquid = new RuntimeShader(KyantGlassShader.SOURCE);
                    float thickness = Math.max(Math.min(TelegramUi.dpf2(thicknessDp), Math.min(width, height) / 4f), 1f);
                    if (shaderWidth != width || shaderHeight != height || shaderRadius != radius || shaderThickness != thickness) {
                        liquid.setFloatUniform("size", width, height);
                        liquid.setFloatUniform("offset", 0, 0);
                        liquid.setFloatUniform("cornerRadii", radius, radius, radius, radius);
                        liquid.setFloatUniform("refractionHeight", thickness);
                        liquid.setFloatUniform("refractionAmount", -thickness * 1.25f);
                        liquid.setFloatUniform("depthEffect", 0f);
                        glass.setRenderEffect(RenderEffect.createRuntimeShaderEffect(liquid, "content"));
                        shaderWidth = width; shaderHeight = height;
                        shaderRadius = radius; shaderThickness = thickness;
                    }
                    Canvas glassRecording = glass.beginRecording(width, height);
                    try { glassRecording.drawRenderNode(blurred); }
                    finally { glass.endRecording(); }
                    canvas.drawRenderNode(glass);
                    tintPaint.setColor(tint);
                    canvas.drawRect(0, 0, width, height, tintPaint);
                    return true;
                } catch (RuntimeException error) {
                    shaderFailed = true;
                    android.util.Log.w("TelegramGlass", "Runtime shader unavailable; using platform blur", error);
                }
            }
            canvas.drawRenderNode(blurred);
            tintPaint.setColor(tint);
            canvas.drawRect(0, 0, width, height, tintPaint);
            return true;
        }

        @Override public void release() {
            blurred.discardDisplayList();
            glass.discardDisplayList();
        }
    }
}
