// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.animation.*;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import com.monica.telegramchat.TelegramUi;
public final class ImageViewEmoji extends ImageView {
    private float pressedProgress;
    private ValueAnimator backAnimator;
    public ImageViewEmoji(Context context) {
        super(context); TelegramUi.initialize(context);
        setScaleType(ScaleType.CENTER); setClickable(true); setFocusable(true);
    }
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(widthMeasureSpec));
        }
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.view.View");
        }
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(5.0f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }
        protected void onDraw(Canvas canvas) {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress += (float) Math.min(40, 1000f / (getDisplay() == null ? 60f : getDisplay().getRefreshRate())) / 100f;
                pressedProgress = Math.max(0, Math.min(1f, pressedProgress));
                invalidate();
            }
            float s = 0.8f + 0.2f * (1f - pressedProgress);
            canvas.save();
            canvas.scale(s, s, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            super.onDraw(canvas);
            canvas.restore();
        }
    @Override protected void onDetachedFromWindow() {
        if (backAnimator != null) { backAnimator.removeAllListeners(); backAnimator.cancel(); backAnimator = null; }
        pressedProgress = 0; super.onDetachedFromWindow();
    }
}
