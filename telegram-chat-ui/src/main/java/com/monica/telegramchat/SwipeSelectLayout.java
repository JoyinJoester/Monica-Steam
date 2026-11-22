/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Hosts a single dialog cell and reports a rightward swipe.
 *
 * <p>The cell keeps owning taps and long presses: this container only intercepts once the gesture
 * is unambiguously horizontal, so a vertical drag still scrolls the hosting list and a tap still
 * reaches the cell. While dragging, the cell follows the finger and springs back on release, which
 * is what makes the gesture feel like a swipe instead of an invisible hit area.
 */
public final class SwipeSelectLayout extends FrameLayout {
    public interface OnSwipeSelectListener {
        void onSwipeSelect();
    }

    private static final float SWIPE_THRESHOLD_DP = 64f;
    /** The cell stops following past this offset so it never runs off the row. */
    private static final float MAX_TRAVEL_DP = 96f;

    private final float swipeThreshold;
    private final float maxTravel;
    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean swiping;
    private OnSwipeSelectListener listener;

    public SwipeSelectLayout(Context context) {
        super(context);
        swipeThreshold = TelegramUi.dp(SWIPE_THRESHOLD_DP);
        maxTravel = TelegramUi.dp(MAX_TRAVEL_DP);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
    }

    public void setOnSwipeSelectListener(OnSwipeSelectListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                swiping = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!swiping && dx > touchSlop * 2 && dx > Math.abs(dy) * 1.5f) {
                    swiping = true;
                    // Keep the displacement the finger already travelled, so the cell does not
                    // jump when the gesture is handed over from the list.
                    moveCell(dx);
                    return true;
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!swiping) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                moveCell(event.getX() - downX);
                break;
            case MotionEvent.ACTION_UP: {
                float dx = event.getX() - downX;
                swiping = false;
                springBack();
                if (dx > swipeThreshold && listener != null) {
                    listener.onSwipeSelect();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                swiping = false;
                springBack();
                break;
            default:
                break;
        }
        return true;
    }

    private void moveCell(float dx) {
        if (getChildCount() == 0) {
            return;
        }
        getChildAt(0).setTranslationX(Math.max(0f, Math.min(dx, maxTravel)));
    }

    private void springBack() {
        if (getChildCount() == 0) {
            return;
        }
        getChildAt(0).animate()
                .translationX(0f)
                .setDuration(140L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}
