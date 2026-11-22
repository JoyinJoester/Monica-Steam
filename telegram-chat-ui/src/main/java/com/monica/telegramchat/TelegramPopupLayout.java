/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.animation.*;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.annotation.Keep;
import com.monica.telegramchat.upstream.PopupEnterAnimator;
import java.util.HashMap;
import static com.monica.telegramchat.TelegramUi.dp;

/** Native portable layout for Telegram's original cascading popup reveal. */
public final class TelegramPopupLayout extends FrameLayout {
    public boolean startAnimationPending, shownFromBottom, updateAnimation, clipChildren;
    public int lastStartedChild;
    public float backScaleY = 1;
    public final HashMap<View, Integer> positions = new HashMap<>();
    private int backAlpha = 255;
    private final Drawable popupBackground;
    private final LinearLayout rows;
    private final TelegramGlassView glass;
    private boolean hasGlassSource;
    private Animator animator;
    private boolean closing;
    private boolean reduceAnimations;
    private final Runnable enter = () -> {
        if (isAttachedToWindow() && !closing) animator = PopupEnterAnimator.startAnimation(this);
    };

    public TelegramPopupLayout(Context context, TelegramChatColors colors) {
        super(context);
        TelegramUi.initialize(context);
        popupBackground = context.getDrawable(R.drawable.telegram_popup_fixed_alert2).mutate();
        popupBackground.setColorFilter(colors.menuBackground, PorterDuff.Mode.MULTIPLY);
        setWillNotDraw(false);
        glass = new TelegramGlassView(context);
        glass.setCornerRadius(dp(20));
        glass.setTintColor(colors.glassTint);
        glass.setDark(colors.dark);
        glass.setVisibility(GONE);
        LayoutParams glassParams = new LayoutParams(-1, -1);
        glassParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        addView(glass, glassParams);
        rows = new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL);
        LayoutParams params = new LayoutParams(-1, -2);
        params.setMargins(dp(8),dp(8),dp(8),dp(8));
        addView(rows, params);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        // A bare MATCH_PARENT View consumes AT_MOST's whole height. The glass is decoration:
        // measure only the rows first, then fit the backdrop inside that resolved content size.
        measureChildWithMargins(rows, widthSpec, 0, heightSpec, 0);
        LayoutParams rowParams = (LayoutParams) rows.getLayoutParams();
        int width = rows.getMeasuredWidth() + rowParams.leftMargin + rowParams.rightMargin
                + getPaddingLeft() + getPaddingRight();
        int height = rows.getMeasuredHeight() + rowParams.topMargin + rowParams.bottomMargin
                + getPaddingTop() + getPaddingBottom();
        int state = rows.getMeasuredState();
        setMeasuredDimension(resolveSizeAndState(Math.max(width, getSuggestedMinimumWidth()), widthSpec, state),
                resolveSizeAndState(Math.max(height, getSuggestedMinimumHeight()), heightSpec,
                        state << MEASURED_HEIGHT_STATE_SHIFT));
        if (glass.getVisibility() != GONE) {
            LayoutParams glassParams = (LayoutParams) glass.getLayoutParams();
            int glassWidth = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight()
                    - glassParams.leftMargin - glassParams.rightMargin);
            int glassHeight = Math.max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom()
                    - glassParams.topMargin - glassParams.bottomMargin);
            glass.measure(MeasureSpec.makeMeasureSpec(glassWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(glassHeight, MeasureSpec.EXACTLY));
        }
    }

    public void setGlassSource(View source, boolean liquidStyle) {
        hasGlassSource = source != null;
        glass.setSourceView(source);
        glass.setLiquidStyle(liquidStyle);
        glass.setVisibility(hasGlassSource ? VISIBLE : GONE);
        invalidate();
    }
    public void addAction(CharSequence text, int icon, TelegramChatColors colors, boolean enabled, Runnable click) {
        TelegramMenuRow row = new TelegramMenuRow(getContext(), text, icon, colors, () -> dismiss(click));
        row.setEnabled(enabled);
        rows.addView(row, new LinearLayout.LayoutParams(-1, LayoutParams.WRAP_CONTENT));
    }
    public void setReduceAnimations(boolean value) {
        if (reduceAnimations == value) return;
        reduceAnimations = value;
        if (value) {
            removeCallbacks(enter);
            cancelAnimator();
            showImmediately();
            invalidate();
        }
    }
    public int getItemsCount() { return rows.getChildCount(); }
    public View getItemAt(int index) { return rows.getChildAt(index); }
    @Keep public float getBackScaleY() { return backScaleY; }
    @Keep public void setBackScaleY(float value) { backScaleY = value; invalidate(); }
    @Keep public int getBackAlpha() { return backAlpha; }
    @Keep public void setBackAlpha(int value) { backAlpha = value; glass.setAlpha(value / 255f); invalidate(); }

    /**
     * Puts the popup into the exact state the cascade animation starts from.
     *
     * <p>The window can lay out and draw its very first frame before the posted animation starts.
     * Upstream Telegram sets these values in {@code ActionBarPopupWindow.showAtLocation} before the
     * window is added; doing it only in {@code onAttachedToWindow} leaves one frame where the menu is
     * already fully open, and the cascade then visibly snaps back, which reads as a flash on open.
     */
    public void prepareForAnimation() {
        removeCallbacks(enter);
        cancelAnimator();
        closing = false;
        setAlpha(1f);
        setTranslationY(0f);
        startAnimationPending = true;
        backScaleY = 0f;
        backAlpha = 0;
        glass.setAlpha(0f);
        clipChildren = true;
        for (int a = 0; a < rows.getChildCount(); a++) {
            rows.getChildAt(a).setAlpha(0f);
            rows.getChildAt(a).setTranslationY(0f);
        }
        if (reduceAnimations || !ValueAnimator.areAnimatorsEnabled()) showImmediately();
        invalidate();
    }

    private void showImmediately() {
        startAnimationPending = false;
        backScaleY = 1f;
        backAlpha = 255;
        glass.setAlpha(1f);
        for (int a = 0; a < rows.getChildCount(); a++) {
            View child = rows.getChildAt(a);
            child.setTranslationY(0f);
            child.setAlpha(child.isEnabled() ? 1f : 0.5f);
        }
    }

    private void cancelAnimator() {
        if (animator != null) {
            // Cancelling an exit must never dispatch its deferred action on a later attachment.
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prepareForAnimation();
        if (startAnimationPending) post(enter);
    }
    public void dismiss(Runnable after) {
        if (closing) return;
        closing = true;
        removeCallbacks(enter);
        cancelAnimator();
        startAnimationPending = false;
        if (reduceAnimations || !ValueAnimator.areAnimatorsEnabled()) {
            if (isAttachedToWindow()) after.run();
            return;
        }
        AnimatorSet exit = new AnimatorSet();
        // Original ActionBarPopupWindow.dismiss translation/fade branch.
        exit.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, dp(shownFromBottom ? 5 : -5)),
                ObjectAnimator.ofFloat(this, View.ALPHA, 0f));
        exit.setDuration(150);
        exit.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { if (isAttachedToWindow()) after.run(); }
        });
        animator = exit; exit.start();
    }
    @Override protected void onDraw(Canvas canvas) {
        if (hasGlassSource) return;
        int height = Math.round(getHeight() * backScaleY);
        popupBackground.setBounds(0, shownFromBottom ? getHeight()-height : 0, getWidth(), shownFromBottom ? getHeight() : height);
        popupBackground.setAlpha(backAlpha); popupBackground.draw(canvas);
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        if (clipChildren) {
            int height = Math.round(getHeight() * backScaleY);
            canvas.clipRect(0, shownFromBottom ? getHeight()-height : 0, getWidth(), shownFromBottom ? getHeight() : height);
        }
        super.dispatchDraw(canvas); canvas.restoreToCount(save);
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(enter);
        cancelAnimator();
        startAnimationPending = false;
        super.onDetachedFromWindow();
    }
}
