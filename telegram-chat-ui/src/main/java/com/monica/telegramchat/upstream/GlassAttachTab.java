// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import com.airbnb.lottie.*;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.monica.telegramchat.TelegramChatColors;
import static com.monica.telegramchat.TelegramUi.*;

/** Portable attachment-only subset of GlassTabView. See UPSTREAM.json. */
public final class GlassAttachTab extends FrameLayout {
    private final LottieAnimationView imageView;
    private final TextView textView;
    private final TextPaint defaultTextPaint;
    private final AnimatedFloat selection = new AnimatedFloat(this, 320, new DecelerateInterpolator());
    private boolean selected;
    private int forward, reverse;
    private int colorDefault, colorSelected, colorSelectedText;
    private int currentIconColor;
    private final LottieValueCallback<ColorFilter> colorFilter = new LottieValueCallback<ColorFilter>() {
        @Override public ColorFilter getValue(com.airbnb.lottie.value.LottieFrameInfo<ColorFilter> frame) {
            return new PorterDuffColorFilter(currentIconColor, PorterDuff.Mode.SRC_IN);
        }
    };
    public GlassAttachTab(Context context) {
        super(context);
        imageView = new LottieAnimationView(context);
        addView(imageView, frame(44, 44, -6));

        imageView.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        textView.setSingleLine();
        textView.setLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textView.setGravity(Gravity.CENTER);

        defaultTextPaint = new TextPaint(textView.getPaint());
        addView(textView, frame(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 28.33f));

        defaultTextPaint.setTextSize(dp(11));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        textView.setPadding(dp(8), 0, dp(8), 0);
        imageView.setLayoutParams(frame(24, 24, 4));
        selection.set(false, true);
        setClickable(true); setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private static LayoutParams frame(int width, int height, float top) {
        LayoutParams params = new LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        params.topMargin = dp(top); return params;
    }
    public void setAnimations(int forward, int reverse) {
        if (this.forward == forward && this.reverse == reverse) return;
        this.forward = forward; this.reverse = reverse;
        showAnimation(false);
    }
    private void showAnimation(boolean animated) {
        // Original checkPlayAnimation: choose filled/outline JSON by selection.
        imageView.cancelAnimation();
        imageView.setAnimation(selected ? forward : reverse);
        imageView.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER, colorFilter);
        if (animated) { imageView.setProgress(0); imageView.playAnimation(); }
        else imageView.setProgress(.99f);
    }
    public void setTabSelected(boolean selected, boolean animated) {
        if (this.selected == selected) return;
        this.selected = selected;
        selection.set(selected, !animated);
        textView.setTypeface(Typeface.create(selected ? "sans-serif-black" : "sans-serif-medium", Typeface.NORMAL));
        showAnimation(animated); invalidate();
    }
    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (forward != 0) setTabSelected(pressed, true);
    }
    public void setColors(TelegramChatColors colors) {
        colorDefault = colors.tabDefault; colorSelected = colors.tabSelected;
        colorSelectedText = colors.tabSelectedText; invalidate();
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        final float factor = selection.getValue();
        // Original updateColors blending; explicit palette replaces Theme service.
        currentIconColor = ColorUtils.blendARGB(colorDefault, colorSelected, factor);
        textView.setTextColor(ColorUtils.blendARGB(colorDefault, colorSelectedText, factor));
        super.dispatchDraw(canvas);
    }
    @Override protected void onDetachedFromWindow() {
        imageView.cancelAnimation(); selection.set(selected, true);
        super.onDetachedFromWindow();
    }
    public float measureAttachTabWidth() {
        final float textWidth = measureTextWidth();
        final float padding = lerp(dpf2(16), dp(8), MathUtils.clamp((textWidth - dp(40)) / dp(16), 0, 1));
        return Math.min(dp(84), (int) (textWidth + padding * 2));
    }
    public float measureTextWidth() {
        return defaultTextPaint.measureText(textView.getText().toString());
    }
    public void setText(CharSequence text) {
        textView.setText(text);
    }
}
