// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;

import androidx.core.graphics.ColorUtils;

import com.monica.telegramchat.TelegramUi;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MessageDrawable extends Drawable {

    private Shader gradientShader;
    private int currentBackgroundHeight;
    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint selectedPaint;
    private int currentColor;
    private int currentGradientColor1;
    private int currentGradientColor2;
    private int currentGradientColor3;
    private boolean currentAnimateGradient;

    private RectF rect = new RectF();
    private Matrix matrix = new Matrix();
    private int currentType;
    public boolean isSelected;
    private Path path;

    public Path getPath() {
        return path;
    }

    private Rect backupRect = new Rect();

    private BubblePalette resourcesProvider;
    private final boolean isOut;

    private int topY;
    private boolean isTopNear;
    private boolean isBottomNear;
    private boolean botButtonsBottom;
    public boolean themePreview;

    private float bubbleRadius = 17f;

    public void setBubbleRadius(float radiusDp) { bubbleRadius = Math.max(0, radiusDp); invalidateSelf(); }

    public Integer overrideColor = null;
    public boolean shadow = true;
    private int[] currentShadowDrawableRadius = new int[]{-1, -1, -1, -1};
    private Bitmap[] shadowDrawableBitmap = new Bitmap[4];
    private Drawable[] shadowDrawable = new Drawable[4];
    private int[] shadowDrawableColor = new int[]{0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff};

    private int[][] currentBackgroundDrawableRadius = new int[][]{
            {-1, -1, -1, -1},
            {-1, -1, -1, -1},
            {-1, -1, -1, -1},
            {-1, -1, -1, -1}
    };
    private Drawable[][] backgroundDrawable = new Drawable[4][4];
    private int[][] backgroundDrawableColor = new int[][]{
            {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff},
            {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff},
            {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff},
            {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff}
    };

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_MEDIA = 1;
    public static final int TYPE_PREVIEW = 2;

    Drawable transitionDrawable;
    int transitionDrawableColor;
    private int alpha;
    private boolean drawFullBubble;

    public MessageDrawable crossfadeFromDrawable;
    public float crossfadeProgress;
    public boolean isCrossfadeBackground;
    public boolean lastDrawWithShadow;
    private Bitmap crosfadeFromBitmap;
    private Shader crosfadeFromBitmapShader;

    PathDrawParams pathDrawCacheParams;
    private int overrideRoundRadius;
    private float overrideRounding;
    public boolean forceInvalidatePath;

    public MessageDrawable(int type, boolean out, boolean selected) {
        this(type, out, selected, null);
    }

    public MessageDrawable(int type, boolean out, boolean selected, BubblePalette resourcesProvider) {
        super();
        this.resourcesProvider = resourcesProvider;
        isOut = out;
        currentType = type;
        isSelected = selected;
        path = new Path();
        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        alpha = 255;
    }

    public boolean hasGradient() {
        return gradientShader != null && BubblePalette.shouldDrawGradientIcons;
    }

    public void applyMatrixScale() {
        // Moving wallpaper gradients are intentionally outside this standalone UI module.
    }

    public Shader getGradientShader() {
        return gradientShader;
    }

    public Matrix getMatrix() {
        return matrix;
    }

    protected int getColor(int key) {
        if (currentType == TYPE_PREVIEW) {
            return BubblePalette.DEFAULT.getColor(key);
        }
        if (resourcesProvider != null) {
            return resourcesProvider.getColor(key);
        }
        return BubblePalette.DEFAULT.getColor(key);
    }

    protected int getCurrentColor(int key) {
        if (currentType == TYPE_PREVIEW) {
            return BubblePalette.DEFAULT.getColor(key);
        }
        return resourcesProvider != null ? resourcesProvider.getCurrentColor(key) : BubblePalette.DEFAULT.getCurrentColor(key);
    }

    public void setBotButtonsBottom(boolean botButtonsBottom) {
        this.botButtonsBottom = botButtonsBottom;
    }

    public void setTop(int top, int backgroundWidth, int backgroundHeight, boolean topNear, boolean bottomNear) {
        setTop(top, backgroundWidth, backgroundHeight, backgroundHeight, 0, 0, topNear, bottomNear);
    }

    public void setTop(int top, int backgroundWidth, int backgroundHeight, int heightOffset, int blurredViewTopOffset, int blurredViewBottomOffset, boolean topNear, boolean bottomNear) {
        if (crossfadeFromDrawable != null) {
            crossfadeFromDrawable.setTop(top, backgroundWidth, backgroundHeight, heightOffset, blurredViewTopOffset, blurredViewBottomOffset, topNear, bottomNear);
        }
        int color = overrideColor != null ? overrideColor : getColor(isOut
                ? (isSelected ? BubblePalette.key_chat_outBubbleSelected : BubblePalette.key_chat_outBubble)
                : (isSelected ? BubblePalette.key_chat_inBubbleSelected : BubblePalette.key_chat_inBubble));
        paint.setColor(color);
        paint.setAlpha(alpha);
        currentBackgroundHeight = backgroundHeight;
        topY = top;
        isTopNear = topNear;
        isBottomNear = bottomNear;
    }

    public void setTopBottomNear(boolean topNear, boolean bottomNear) {
        isTopNear = topNear;
        isBottomNear = bottomNear;
    }

    public int getTopY() {
        return topY;
    }

    private int dp(float value) {
        if (currentType == TYPE_PREVIEW) {
            return (int) Math.ceil(3 * value);
        } else {
            return TelegramUi.dp(value);
        }
    }

    public Paint getPaint() {
        return paint;
    }

    public Drawable[] getShadowDrawables() {
        return shadowDrawable;
    }

    public Drawable getBackgroundDrawable() {
        int newRad;
        if (overrideRoundRadius != 0) {
            newRad = overrideRoundRadius;
        } else if (overrideRounding > 0) {
            newRad = 0;
        } else {
            newRad = dp(bubbleRadius);
        }
        int idx;
        if (isTopNear && isBottomNear) {
            idx = 3;
        } else if (isTopNear) {
            idx = 2;
        } else if (isBottomNear) {
            idx = 1;
        } else {
            idx = 0;
        }
        int idx2;
        if (isSelected && botButtonsBottom) {
            idx2 = 3;
        } else if (isSelected) {
            idx2 = 1;
        } else if (botButtonsBottom) {
            idx2 = 2;
        } else {
            idx2 = 0;
        }
        int color;
        if (overrideColor != null) {
            color = overrideColor;
        } else if (isSelected) {
            color = getColor(isOut ? BubblePalette.key_chat_outBubbleSelected : BubblePalette.key_chat_inBubbleSelected);
        } else {
            color = getColor(isOut ? BubblePalette.key_chat_outBubble : BubblePalette.key_chat_inBubble);
        }

        boolean drawWithShadow = shadow && gradientShader == null && !isSelected && !isCrossfadeBackground;
        int shadowColor = getColor(isOut ? BubblePalette.key_chat_outBubbleShadow : BubblePalette.key_chat_inBubbleShadow);
        if (lastDrawWithShadow != drawWithShadow || currentBackgroundDrawableRadius[idx2][idx] != newRad || (drawWithShadow && shadowDrawableColor[idx] != shadowColor) || backgroundDrawableColor[idx2][idx] != color) {
            currentBackgroundDrawableRadius[idx2][idx] = newRad;
            try {
                Bitmap bitmap = Bitmap.createBitmap(dp(50), dp(40), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);

                backupRect.set(getBounds());

                if (drawWithShadow) {
                    shadowDrawableColor[idx] = shadowColor;

                    Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                    LinearGradient gradientShader = new LinearGradient(0, 0, 0, dp(40), new int[]{0x155F6569, 0x295F6569}, null, Shader.TileMode.CLAMP);
                    shadowPaint.setShader(gradientShader);
                    shadowPaint.setColorFilter(new PorterDuffColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY));

                    shadowPaint.setShadowLayer(2, 0, 1, 0xffffffff);
                    if (TelegramUi.density > 1) {
                        setBounds(-1, -1, bitmap.getWidth() + 1, bitmap.getHeight() + 1);
                    } else {
                        setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    }
                    draw(canvas, shadowPaint);

                    if (TelegramUi.density > 1) {
                        shadowPaint.setColor(0);
                        shadowPaint.setShadowLayer(0, 0, 0, 0);
                        shadowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                        setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        draw(canvas, shadowPaint);
                    }
                }

                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setColor(color);
                setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                draw(canvas, shadowPaint);

                backgroundDrawable[idx2][idx] = new NinePatchDrawable(bitmap, getByteBuffer(bitmap.getWidth() / 2 - 1, bitmap.getWidth() / 2 + 1, bitmap.getHeight() / 2 - 1, bitmap.getHeight() / 2 + 1, color).array(), new Rect(), null);
                setBounds(backupRect);
            } catch (Throwable ignore) {

            }
        }
        lastDrawWithShadow = drawWithShadow;
        backgroundDrawableColor[idx2][idx] = color;
        return backgroundDrawable[idx2][idx];
    }

    public Drawable getTransitionDrawable(int color) {
        if (transitionDrawable == null) {
            Bitmap bitmap = Bitmap.createBitmap(dp(50), dp(40), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            backupRect.set(getBounds());

            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(0xffffffff);
            setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            draw(canvas, shadowPaint);

            transitionDrawable = new NinePatchDrawable(bitmap, getByteBuffer(bitmap.getWidth() / 2 - 1, bitmap.getWidth() / 2 + 1, bitmap.getHeight() / 2 - 1, bitmap.getHeight() / 2 + 1, Color.WHITE).array(), new Rect(), null);
            setBounds(backupRect);
        }
        if (transitionDrawableColor != color) {
            transitionDrawableColor = color;
            transitionDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }

        return transitionDrawable;
    }



    public Drawable getShadowDrawable() {
        if (isCrossfadeBackground) {
            return null;
        }
        if (gradientShader == null && !isSelected && crossfadeFromDrawable == null) {
            return null;
        }
        int newRad = dp(bubbleRadius);
        int idx;
        if (isTopNear && isBottomNear) {
            idx = 3;
        } else if (isTopNear) {
            idx = 2;
        } else if (isBottomNear) {
            idx = 1;
        } else {
            idx = 0;
        }
        boolean forceSetColor = false;
        if (currentShadowDrawableRadius[idx] != newRad) {
            currentShadowDrawableRadius[idx] = newRad;
            if (shadowDrawableBitmap[idx] != null) {
                shadowDrawableBitmap[idx].recycle();
            }
            try {
                Bitmap bitmap = Bitmap.createBitmap(dp(50), dp(40), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);

                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                LinearGradient gradientShader = new LinearGradient(0, 0, 0, dp(40), new int[]{0x155F6569, 0x295F6569}, null, Shader.TileMode.CLAMP);
                shadowPaint.setShader(gradientShader);

                shadowPaint.setShadowLayer(2, 0, 1, 0xffffffff);
                if (TelegramUi.density > 1) {
                    setBounds(-1, -1, bitmap.getWidth() + 1, bitmap.getHeight() + 1);
                } else {
                    setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                }
                draw(canvas, shadowPaint);

                int centralColorHint = NinePatchBuilder.NO_COLOR;
                if (TelegramUi.density > 1) {
                    centralColorHint = NinePatchBuilder.TRANSPARENT_COLOR;
                    shadowPaint.setColor(0);
                    shadowPaint.setShadowLayer(0, 0, 0, 0);
                    shadowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    draw(canvas, shadowPaint);
                }

                shadowDrawableBitmap[idx] = bitmap;
                shadowDrawable[idx] = new NinePatchDrawable(bitmap, getByteBuffer(bitmap.getWidth() / 2 - 1, bitmap.getWidth() / 2 + 1, bitmap.getHeight() / 2 - 1, bitmap.getHeight() / 2 + 1, centralColorHint).array(), new Rect(), null);
                forceSetColor = true;
            } catch (Throwable ignore) {

            }
        }
        int color = getColor(isOut ? BubblePalette.key_chat_outBubbleShadow : BubblePalette.key_chat_inBubbleShadow);
        if (shadowDrawable[idx] != null && (shadowDrawableColor[idx] != color || forceSetColor)) {
            shadowDrawable[idx].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            shadowDrawableColor[idx] = color;
        }
        return shadowDrawable[idx];
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        for (Bitmap bitmap : shadowDrawableBitmap) {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        Arrays.fill(shadowDrawableBitmap, null);
        Arrays.fill(shadowDrawable, null);
        Arrays.fill(currentShadowDrawableRadius, -1);
    }

    private static ByteBuffer getByteBuffer(int x1, int x2, int y1, int y2, int centralColorHint) {
        return NinePatchBuilder.createNinePatchChunk(x1, x2, y1, y2, 0, 0, 0, 0, centralColorHint);
    }

    public void drawCached(Canvas canvas, PathDrawParams patchDrawCacheParams, Paint paintToUse) {
        this.pathDrawCacheParams = patchDrawCacheParams;
        if (crossfadeFromDrawable != null) {
            crossfadeFromDrawable.pathDrawCacheParams = patchDrawCacheParams;
        }
        draw(canvas, paintToUse);
        this.pathDrawCacheParams = null;
        if (crossfadeFromDrawable != null) {
            crossfadeFromDrawable.pathDrawCacheParams = null;
        }
    }

    public void drawCached(Canvas canvas, PathDrawParams patchDrawCacheParams) {
        drawCached(canvas, patchDrawCacheParams, null);
    }

    @Override
    public void draw(Canvas canvas) {
        if (crossfadeFromDrawable != null) {
            crossfadeFromDrawable.draw(canvas);
            setAlpha((int) (255 * crossfadeProgress));
            draw(canvas, null);
            setAlpha(255);
        } else {
            draw(canvas, null);
        }
    }

    public void draw(Canvas canvas, Paint paintToUse) {
        Rect bounds = getBounds();
        if (paintToUse == null && paint.getStyle() == Paint.Style.FILL && gradientShader == null && overrideRoundRadius == 0 && overrideRounding <= 0) {
            Drawable background = getBackgroundDrawable();
            if (background != null) {
                background.setBounds(bounds);
                background.draw(canvas);
                return;
            }
        }

        int padding = dp(2);
        int rad;
        int nearRad;
        if (overrideRoundRadius != 0) {
            rad = overrideRoundRadius;
            nearRad = overrideRoundRadius;
        } else if (overrideRounding > 0) {
            rad = TelegramUi.lerp(dp(bubbleRadius), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
            nearRad = TelegramUi.lerp(dp(Math.min(6, bubbleRadius)), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
        } else if (currentType == TYPE_PREVIEW) {
            rad = dp(6);
            nearRad = dp(6);
        } else {
            rad = dp(bubbleRadius);
            nearRad = dp(Math.min(6, bubbleRadius));
        }
        int smallRad = dp(6);

        Paint p = paintToUse == null ? paint : paintToUse;

        if (paintToUse == null && gradientShader != null) {
            matrix.reset();
            applyMatrixScale();
            matrix.postTranslate(0, -topY);
            gradientShader.setLocalMatrix(matrix);
        }

        int top = Math.max(bounds.top, 0);
        boolean drawFullBottom, drawFullTop;
        if (pathDrawCacheParams != null && bounds.height() < currentBackgroundHeight) {
            drawFullBottom = true;
            drawFullTop = true;
        } else {
            drawFullBottom = true; //currentType == TYPE_MEDIA ? topY + bounds.bottom - smallRad * 2 < currentBackgroundHeight : topY + bounds.bottom - rad < currentBackgroundHeight;
            drawFullTop = true; // topY + rad * 2 >= 0;
        }
        Path path;
        boolean invalidatePath;
        if (pathDrawCacheParams != null) {
            path = pathDrawCacheParams.path;
            invalidatePath = pathDrawCacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop);
        } else {
            path = this.path;
            invalidatePath = true;
        }
        if (invalidatePath || overrideRoundRadius != 0) {
            generatePath(path, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, paintToUse != null);
        }

        canvas.drawPath(path, p);
        if (gradientShader != null && isSelected && paintToUse == null) {
            int color = getColor(BubblePalette.key_chat_outBubbleGradientSelectedOverlay);
            selectedPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * alpha / 255f)));
            canvas.drawPath(path, selectedPaint);
        }
    }

    public Path makePath() {
        return makePath(pathDrawCacheParams);
    }

    public Path makePath(PathDrawParams pathDrawCacheParams) {
        Rect bounds = getBounds();
        int padding = dp(2);
        int rad;
        int nearRad;
        if (overrideRoundRadius != 0) {
            rad = overrideRoundRadius;
            nearRad = overrideRoundRadius;
        } else if (overrideRounding > 0) {
            rad = TelegramUi.lerp(dp(bubbleRadius), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
            nearRad = TelegramUi.lerp(dp(Math.min(6, bubbleRadius)), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
        } else if (currentType == TYPE_PREVIEW) {
            rad = dp(6);
            nearRad = dp(6);
        } else {
            rad = dp(bubbleRadius);
            nearRad = dp(Math.min(6, bubbleRadius));
        }
        int smallRad = dp(6);
        int top = Math.max(bounds.top, 0);
        boolean drawFullBottom, drawFullTop;
        if (pathDrawCacheParams != null && bounds.height() < currentBackgroundHeight) {
            drawFullBottom = true;
            drawFullTop = true;
        } else {
            drawFullBottom = currentType == TYPE_MEDIA ? topY + bounds.bottom - smallRad * 2 < currentBackgroundHeight : topY + bounds.bottom - rad < currentBackgroundHeight;
            drawFullTop = topY + rad * 2 >= 0;
        }
        Path path;
        boolean invalidatePath;
        if (pathDrawCacheParams != null) {
            path = pathDrawCacheParams.path;
            invalidatePath = pathDrawCacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop);
        } else {
            path = this.path;
            invalidatePath = true;
        }
        if (invalidatePath || overrideRoundRadius != 0) {
            generatePath(path, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, true);
        }
        return path;
    }

    private void generatePath(Path path, Rect bounds, int padding, int rad, int smallRad, int nearRad, int top, boolean drawFullBottom, boolean drawFullTop, boolean customPaint) {
        path.rewind();
        int heightHalf = (bounds.height() - padding) >> 1;
        if (rad > heightHalf) {
            rad = heightHalf;
        }
        if (isOut) {
            // LEFT-BOTTOM <- RIGHT-BOTTOM
            if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                int radToUse = botButtonsBottom ? nearRad : rad;
                if (currentType == TYPE_MEDIA) {
                    path.moveTo(bounds.right - dp(8) - radToUse, bounds.bottom - padding);
                } else {
                    path.moveTo(bounds.right - dp(2.6f), bounds.bottom - padding);
                }
                path.lineTo(bounds.left + padding + radToUse, bounds.bottom - padding);
                rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2, bounds.left + padding + radToUse * 2, bounds.bottom - padding);
                path.arcTo(rect, 90, 90, false);
            } else {
                path.moveTo(bounds.right - dp(8), top - topY + currentBackgroundHeight);
                path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight);
            }
            if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) {
                // LEFT-BOTTOM -> LEFT-TOP
                path.lineTo(bounds.left + padding, bounds.top + padding + rad);
                rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + rad * 2, bounds.top + padding + rad * 2);
                path.arcTo(rect, 180, 90, false);

                // LEFT-TOP -> RIGHT-TOP
                int radToUse = isTopNear ? nearRad : rad;
                if (currentType == TYPE_MEDIA) {
                    path.lineTo(bounds.right - padding - radToUse, bounds.top + padding);
                    rect.set(bounds.right - padding - radToUse * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + radToUse * 2);
                } else {
                    path.lineTo(bounds.right - dp(8) - radToUse, bounds.top + padding);
                    rect.set(bounds.right - dp(8) - radToUse * 2, bounds.top + padding, bounds.right - dp(8), bounds.top + padding + radToUse * 2);
                }
                path.arcTo(rect, 270, 90, false);
            } else {
                // LEFT-BOTTOM -> LEFT-TOP
                path.lineTo(bounds.left + padding, top - topY - dp(2));

                // LEFT-TOP -> RIGHT-TOP
                if (currentType == TYPE_MEDIA) {
                    path.lineTo(bounds.right - padding, top - topY - dp(2));
                } else {
                    path.lineTo(bounds.right - dp(8), top - topY - dp(2));
                }
            }
            // RIGHT-TOP -> RIGHT-BOTTOM
            if (currentType == TYPE_MEDIA) {
                if (customPaint || drawFullBottom) {
                    int radToUse = isBottomNear ? nearRad : rad;

                    path.lineTo(bounds.right - padding, bounds.bottom - padding - radToUse);
                    rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2, bounds.right - padding, bounds.bottom - padding);
                    path.arcTo(rect, 0, 90, false);
                } else {
                    path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight);
                }
            } else {
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                    path.lineTo(bounds.right - dp(8), bounds.bottom - padding - smallRad - dp(3));
                    rect.set(bounds.right - dp(8), bounds.bottom - padding - smallRad * 2 - dp(9), bounds.right - dp(7) + smallRad * 2, bounds.bottom - padding - dp(1));
                    path.arcTo(rect, 180, -83, false);
                } else {
                    path.lineTo(bounds.right - dp(8), top - topY + currentBackgroundHeight);
                }
            }
        } else {
            if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                int radToUse = botButtonsBottom ? nearRad : rad;

                if (currentType == TYPE_MEDIA) {
                    path.moveTo(bounds.left + dp(8) + radToUse, bounds.bottom - padding);
                } else {
                    path.moveTo(bounds.left + dp(2.6f), bounds.bottom - padding);
                }
                path.lineTo(bounds.right - padding - radToUse, bounds.bottom - padding);
                rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2, bounds.right - padding, bounds.bottom - padding);
                path.arcTo(rect, 90, -90, false);
            } else {
                path.moveTo(bounds.left + dp(8), top - topY + currentBackgroundHeight);
                path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight);
            }
            if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) {
                path.lineTo(bounds.right - padding, bounds.top + padding + rad);
                rect.set(bounds.right - padding - rad * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + rad * 2);
                path.arcTo(rect, 0, -90, false);

                int radToUse = isTopNear ? nearRad : rad;
                if (currentType == TYPE_MEDIA) {
                    path.lineTo(bounds.left + padding + radToUse, bounds.top + padding);
                    rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + radToUse * 2, bounds.top + padding + radToUse * 2);
                } else {
                    path.lineTo(bounds.left + dp(8) + radToUse, bounds.top + padding);
                    rect.set(bounds.left + dp(8), bounds.top + padding, bounds.left + dp(8) + radToUse * 2, bounds.top + padding + radToUse * 2);
                }
                path.arcTo(rect, 270, -90, false);
            } else {
                path.lineTo(bounds.right - padding, top - topY - dp(2));
                if (currentType == TYPE_MEDIA) {
                    path.lineTo(bounds.left + padding, top - topY - dp(2));
                } else {
                    path.lineTo(bounds.left + dp(8), top - topY - dp(2));
                }
            }
            if (currentType == TYPE_MEDIA) {
                if (customPaint || drawFullBottom) {
                    int radToUse = isBottomNear || botButtonsBottom ? nearRad : rad;

                    path.lineTo(bounds.left + padding, bounds.bottom - padding - radToUse);
                    rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2, bounds.left + padding + radToUse * 2, bounds.bottom - padding);
                    path.arcTo(rect, 180, -90, false);
                } else {
                    path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight);
                }
            } else {
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                    path.lineTo(bounds.left + dp(8), bounds.bottom - padding - smallRad - dp(3));
                    rect.set(bounds.left + dp(7) - smallRad * 2, bounds.bottom - padding - smallRad * 2 - dp(9), bounds.left + dp(8), bounds.bottom - padding - dp(1));
                    path.arcTo(rect, 0, 83, false);
                } else {
                    path.lineTo(bounds.left + dp(8), top - topY + currentBackgroundHeight);
                }
            }
        }
        path.close();
    }

    public void setDrawFullBubble(boolean drawFullBuble) {
        this.drawFullBubble = drawFullBuble;
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha != alpha || this.paint.getAlpha() != alpha) {
            this.alpha = alpha;
            paint.setAlpha(alpha);
            if (isOut) {
                selectedPaint.setAlpha((int) (Color.alpha(getColor(BubblePalette.key_chat_outBubbleGradientSelectedOverlay)) * (alpha / 255.0f)));
            }
        }
        if (gradientShader == null) {
            Drawable background = getBackgroundDrawable();
            if (background != null && background.getAlpha() != alpha) {
                background.setAlpha(alpha);
            }
        }
    }

    @Override
    public void setColorFilter(int color, PorterDuff.Mode mode) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        if (crossfadeFromDrawable != null) {
            crossfadeFromDrawable.setBounds(left, top, right, bottom);
        }
    }

    public void setRoundRadius(int radius) {
        this.overrideRoundRadius = radius;
    }

    public void setRoundingRadius(float rounding) {
        this.overrideRounding = rounding;
    }

    public void setResourceProvider(BubblePalette resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public static class PathDrawParams {
        Path path = new Path();
        Rect lastRect = new Rect();
        boolean lastDrawFullTop;
        boolean lastDrawFullBottom;

        public boolean invalidatePath(Rect bounds, boolean drawFullBottom, boolean drawFullTop) {
            boolean invalidate = lastRect.isEmpty() || lastRect.top != bounds.top || lastRect.bottom != bounds.bottom || lastRect.right != bounds.right || lastRect.left != bounds.left || lastDrawFullTop != drawFullTop || lastDrawFullBottom != drawFullBottom || !drawFullTop || !drawFullBottom;
            lastDrawFullTop = drawFullTop;
            lastDrawFullBottom = drawFullBottom;
            lastRect.set(bounds);
            return invalidate;
        }

        public Path getPath() {
            return path;
        }
    }
}
