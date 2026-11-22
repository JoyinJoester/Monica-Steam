// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.animation.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.monica.telegramchat.TelegramUi;
import com.monica.telegramchat.PickerTheme;
import static com.monica.telegramchat.TelegramUi.dp;
public final class PhotoAttachPhotoCell extends FrameLayout {
    private final FrameLayout container, checkFrame;
    private final ImageView imageView;
    private final TextView videoTextView;
    private final CheckBox2 checkBox;
    private AnimatorSet animator;
    private int itemSize;
    private boolean itemSizeChanged, isVertical, isLast;
    public PhotoAttachPhotoCell(Context context, PickerTheme.ResourcesProvider colors) {
        super(context); TelegramUi.initialize(context);
        container = new FrameLayout(context);
        addView(container, new LayoutParams(dp(80), dp(80)));
        imageView = new ImageView(context); imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(imageView, new LayoutParams(-1, -1));
        videoTextView = new TextView(context); videoTextView.setTextColor(0xffffffff);
        videoTextView.setTextSize(12); videoTextView.setShadowLayer(dp(2), 0, 0, 0xff000000);
        LayoutParams time = new LayoutParams(-2, dp(20), Gravity.BOTTOM | Gravity.RIGHT);
        time.rightMargin = dp(6); container.addView(videoTextView, time);
        checkFrame = new FrameLayout(context);
        addView(checkFrame, new LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.RIGHT));
        checkBox = new CheckBox2(context, 24, colors); checkBox.setDrawUnchecked(true);
        addView(checkBox, new LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.RIGHT));
        setClickable(true); setFocusable(true);
        checkBox.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        videoTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setBitmap(Bitmap bitmap) { imageView.setImageBitmap(bitmap); }
    public void setDuration(String duration) { videoTextView.setText(duration); }
    public void setPickerColors(int background, int accent) { setBackgroundColor(background); }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setCheckable(true); info.setChecked(checkBox.isChecked());
    }
    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.cancel(); animator = null; }
        super.onDetachedFromWindow();
    }
    public void setItemSize(int size) {
        itemSize = size;

        LayoutParams layoutParams = (LayoutParams) container.getLayoutParams();
        layoutParams.width = layoutParams.height = itemSize;

        layoutParams = (LayoutParams) checkFrame.getLayoutParams();
        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        layoutParams.leftMargin = 0;

        layoutParams = (LayoutParams) checkBox.getLayoutParams();
        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        layoutParams.leftMargin = 0;
        layoutParams.rightMargin = layoutParams.topMargin = dp(5);
        checkBox.setDrawBackgroundAsArc(6);

        itemSizeChanged = true;
    }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (itemSizeChanged) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize + dp(3), MeasureSpec.EXACTLY));
        } else {
            if (isVertical) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(dp(80), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(80 + (isLast ? 0 : 6)), MeasureSpec.EXACTLY));
            } else {
                super.onMeasure(MeasureSpec.makeMeasureSpec(dp(80 + (isLast ? 0 : 6)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(80), MeasureSpec.EXACTLY));
            }
        }
    }
    public void setChecked(int num, boolean checked, boolean animated) {
        checkBox.setChecked(num, checked, animated);
        if (itemSizeChanged) {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (animated) {
                animator = new AnimatorSet();
                animator.playTogether(
                        ObjectAnimator.ofFloat(container, View.SCALE_X, checked ? 0.787f : 1.0f),
                        ObjectAnimator.ofFloat(container, View.SCALE_Y, checked ? 0.787f : 1.0f));
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator != null && animator.equals(animation)) {
                            animator = null;
                            if (!checked) {
                                setBackgroundColor(0);
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animator != null && animator.equals(animation)) {
                            animator = null;
                        }
                    }
                });
                animator.start();
            } else {
                container.setScaleX(checked ? 0.787f : 1.0f);
                container.setScaleY(checked ? 0.787f : 1.0f);
            }
        }
    }
}
