// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * Copyright Nikolai Kudashov, 2013-2018.
 * Extracted cursor and text watcher implementation; see UPSTREAM.json.
 */
package com.monica.telegramchat.upstream;

import static com.monica.telegramchat.TelegramUi.dp;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;
import com.monica.telegramchat.TelegramUi;

public class EditTextBoldCursor extends EditText {
    private GradientDrawable gradientDrawable;
    private ShapeDrawable cursorDrawable;
    private int cursorSize;
    private float cursorWidth = 2f;
    private float lineSpacingExtra;
    private boolean allowDrawCursor = true, forceCursorEnd, drawInMaim, cursorDrawn;
    private Rect rect = new Rect(), mTempRect;
    private CharSequence lastText;
    private int lastOffset;
    private List<TextWatcher> registeredTextWatchers = new ArrayList<>();
    private boolean isTextWatchersSuppressed;

    public EditTextBoldCursor(Context context) {
        super(context);
        TelegramUi.initialize(context);
        setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        cursorSize = dp(24);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursorDrawable = new ShapeDrawable() {

                @Override
                public void draw(Canvas canvas) {
                    if (drawInMaim) {
                        cursorDrawn = true;
                    } else {
                        super.draw(canvas);
                    }
                }

                @Override
                public int getIntrinsicHeight() {
                    return cursorSize + dp(20);
                }

                @Override
                public int getIntrinsicWidth() {
                    return dp(cursorWidth);
                }
            };
            cursorDrawable.setShape(new RectShape());
            gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xff54a1db, 0xff54a1db});

            setTextCursorDrawable(cursorDrawable);

        }
    }

    @Override protected void onDraw(Canvas canvas) {
        drawInMaim = true;
        super.onDraw(canvas);
        drawInMaim = false;
        // API26-28 retain the platform cursor; public cursor API begins at API29.
        if (cursorDrawable == null || getLayout() == null || getSelectionStart() < 0) return;
        if (cursorDrawn && allowDrawCursor) {
            try {
                canvas.save();
                int voffsetCursor = 0;
                if ((getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                    voffsetCursor = getTotalPaddingTop() - getExtendedPaddingTop();
                }
                canvas.translate(getPaddingLeft(), getExtendedPaddingTop() + voffsetCursor);
                Layout layout = getLayout();
                int line = layout.getLineForOffset(getSelectionStart());
                int lineCount = layout.getLineCount();
                updateCursorPosition();
                Rect bounds = gradientDrawable.getBounds();
                rect.left = bounds.left;
                rect.right = bounds.left + dp(cursorWidth);
                rect.bottom = bounds.bottom;
                rect.top = bounds.top;
                if (lineSpacingExtra != 0 && line < lineCount - 1) {
                    rect.bottom -= lineSpacingExtra;
                }
                rect.top = rect.centerY() - cursorSize / 2;
                rect.bottom = rect.top + cursorSize;
                gradientDrawable.setBounds(rect);
                drawCursor(canvas, gradientDrawable);
                canvas.restore();
                cursorDrawn = false;
            } catch (Throwable exception) {
                android.util.Log.w("TelegramCursor", "Cursor layout unavailable", exception);
            }
        }
    }

    @Override public void setLineSpacing(float add, float mult) {
        lineSpacingExtra = add;
        super.setLineSpacing(add, mult);
    }

    public void setHintText(CharSequence text) { setHint(text); }
    public void setHintColor(int color) { setHintTextColor(color); }

    public void addTextChangedListener(TextWatcher watcher) {
        registeredTextWatchers.add(watcher);
        if (isTextWatchersSuppressed) {
            return;
        }
        super.addTextChangedListener(watcher);
    }

    public void removeTextChangedListener(TextWatcher watcher) {
        registeredTextWatchers.remove(watcher);
        if (isTextWatchersSuppressed) {
            return;
        }
        super.removeTextChangedListener(watcher);
    }

    public void dispatchTextWatchersTextChanged() {
        for (TextWatcher w : registeredTextWatchers) {
            w.beforeTextChanged("", 0, length(), length());
            w.onTextChanged(getText(), 0, length(), length());
            w.afterTextChanged(getText());
        }
    }

    public void setTextWatchersSuppressed(boolean textWatchersSuppressed, boolean dispatchChanged) {
        if (isTextWatchersSuppressed == textWatchersSuppressed) return;
        isTextWatchersSuppressed = textWatchersSuppressed;

        if (textWatchersSuppressed) {
            for (TextWatcher w : registeredTextWatchers) {
                super.removeTextChangedListener(w);
            }
        } else {
            for (TextWatcher w : registeredTextWatchers) {
                super.addTextChangedListener(w);
                if (dispatchChanged) {
                    w.beforeTextChanged("", 0, length(), length());
                    w.onTextChanged(getText(), 0, length(), length());
                    w.afterTextChanged(getText());
                }
            }
        }
    }

    public boolean isTextWatchersSuppressed() {
        return isTextWatchersSuppressed;
    }

    public void setAllowDrawCursor(boolean value) {
        allowDrawCursor = value;
        invalidate();
    }

    public void setForceCursorEnd(boolean cursorEnd) {
        this.forceCursorEnd = cursorEnd;
        invalidate();
    }

    public void setCursorWidth(float width) {
        cursorWidth = width;
    }

    public void setCursorColor(int color) {
        if (cursorDrawable != null) {
            cursorDrawable.getPaint().setColor(color);
        }
        if (gradientDrawable != null) {
            gradientDrawable.setColor(color);
        }
        invalidate();
    }

    public void setCursorSize(int value) {
        cursorSize = value;
    }

    private boolean updateCursorPosition() {
        final Layout layout = getLayout();
        final int offset = forceCursorEnd ? layout.getText().length() : getSelectionStart();
        final int line = layout.getLineForOffset(offset);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);
        updateCursorPosition(top, bottom, layout.getPrimaryHorizontal(offset));

        lastText = layout.getText();
        lastOffset = offset;
        return true;
    }

    private int clampHorizontalPosition(final Drawable drawable, float horizontal) {
        horizontal = Math.max(0.5f, horizontal - 0.5f);
        if (mTempRect == null) {
            mTempRect = new Rect();
        }
        int drawableWidth = 0;
        if (drawable != null) {
            drawable.getPadding(mTempRect);
            drawableWidth = drawable.getIntrinsicWidth();
        } else {
            mTempRect.setEmpty();
        }
        int scrollX = getScrollX();
        float horizontalDiff = horizontal - scrollX;
        int viewClippedWidth = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        final int left;
        if (horizontalDiff >= (viewClippedWidth - 1f)) {
            left = viewClippedWidth + scrollX - (drawableWidth - mTempRect.right);
        } else if (Math.abs(horizontalDiff) <= 1f || (TextUtils.isEmpty(getText()) && (1024 * 1024 - scrollX) <= (viewClippedWidth + 1f) && horizontal <= 1f)) {
            left = scrollX - mTempRect.left;
        } else {
            left = (int) horizontal - mTempRect.left;
        }
        return left;
    }

    private void updateCursorPosition(int top, int bottom, float horizontal) {
        final int left = clampHorizontalPosition(gradientDrawable, horizontal);
        final int width = dp(cursorWidth);
        gradientDrawable.setBounds(left, top - mTempRect.top, left + width, bottom + mTempRect.bottom);
    }

    protected void drawCursor(Canvas canvas, GradientDrawable drawable) {
        drawable.draw(canvas);
    }
}
