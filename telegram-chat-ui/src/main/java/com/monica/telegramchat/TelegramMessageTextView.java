/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import com.monica.telegramchat.upstream.MessageDrawable;
import com.monica.telegramchat.upstream.MessageTextLayout;
import com.monica.telegramchat.upstream.MsgClockDrawable;
import static com.monica.telegramchat.TelegramUi.dp;

/** Text-only ChatMessageCell extraction bridge. Bubble, layout and status assets are Telegram source. */
public final class TelegramMessageTextView extends View {
    public static final int DELIVERY_NONE = 0, DELIVERY_PENDING = 1, DELIVERY_SENT = 2, DELIVERY_READ = 3;
    private CharSequence text = "";
    private String time = "";
    private boolean outgoing, above, below, showBubble = true;
    private int deliveryState;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final MsgClockDrawable clock = new MsgClockDrawable();
    private MessageDrawable bubble;
    private Drawable check, halfCheck;
    private StaticLayout layout, timeLayout;
    private TelegramChatColors colors = TelegramChatColors.DARK_BLUE;
    private float textScale = 1f;
    private int textX, textY, timeX, timeY, statusX;
    private float downX, downY;
    private long downTime;
    private ClickableSpan downSpan;

    public TelegramMessageTextView(Context context) {
        super(context);
        TelegramUi.initialize(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setColors(colors);
    }

    public void setMessage(CharSequence text, String time, boolean outgoing, int deliveryState) {
        CharSequence value = text == null ? "" : text;
        String timestamp = time == null ? "" : time;
        if (this.text == value && this.time.equals(timestamp) && this.outgoing == outgoing && this.deliveryState == deliveryState) return;
        this.text = value; this.time = timestamp; this.outgoing = outgoing;
        this.deliveryState = deliveryState;
        setContentDescription(value + (timestamp.isEmpty() ? "" : ", " + timestamp));
        rebuildBubble(); updatePaints(); requestLayout(); invalidate();
    }
    public void setColors(TelegramChatColors colors) {
        if (this.colors == colors && bubble != null) return;
        this.colors = colors; updatePaints(); rebuildBubble(); invalidate();
    }
    public void setGrouped(boolean above, boolean below) {
        if (this.above != above || this.below != below) {
            this.above = above; this.below = below; rebuildBubble(); requestLayout(); invalidate();
        }
    }
    public void setTextScale(float scale) {
        scale = Math.max(.75f, Math.min(2f, scale));
        if (textScale != scale) { textScale = scale; updatePaints(); requestLayout(); invalidate(); }
    }
    public void setShowBubble(boolean show) { if (showBubble != show) { showBubble = show; requestLayout(); invalidate(); } }

    private void updatePaints() {
        // Theme.createChatResources: SharedConfig default fontSize=16; time paint=12dp.
        textPaint.setTextSize(dp(16 * textScale)); textPaint.setColor(outgoing ? colors.outgoingText : colors.text); textPaint.linkColor = colors.accent;
        timePaint.setTextSize(dp(12 * textScale));
        int color = outgoing ? colors.outgoingTime : colors.incomingTime;
        timePaint.setColor(color); clock.setColor(color);
        check = TelegramDrawables.tinted(getContext(), R.drawable.telegram_msg_check_s, color);
        halfCheck = TelegramDrawables.tinted(getContext(), R.drawable.telegram_msg_halfcheck_s, color);
    }
    private void rebuildBubble() {
        bubble = TelegramDrawables.message(outgoing, outgoing ? colors.outgoing : colors.incoming, above, below);
        bubble.setCallback(this);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        final int maxWidth = Math.max(dp(48), MeasureSpec.getSize(widthSpec));
        final int horizontal = showBubble ? dp(31) : 0;
        final int maxTextWidth = Math.max(1, maxWidth - horizontal);
        layout = MessageTextLayout.makeStaticLayout(text, textPaint, maxTextWidth, 1f, 0, true, Layout.Alignment.ALIGN_NORMAL);
        int textWidth = 0;
        for (int i = 0; i < layout.getLineCount(); i++) textWidth = Math.max(textWidth, (int)Math.ceil(layout.getLineWidth(i)));
        final int timeWidth = (int)Math.ceil(timePaint.measureText(time));
        final int statusWidth = outgoing && deliveryState != DELIVERY_NONE ? dp(20.5f) : 0;
        // ChatMessageCell: timeMore=timeWidth+6dp (+20.5dp for outgoing status).
        final int timeMore = TextUtils.isEmpty(time) && statusWidth == 0 ? 0 : timeWidth + dp(6) + statusWidth;
        final int last = layout.getLineCount() - 1;
        final int lastWidth = last < 0 ? 0 : (int)Math.ceil(layout.getLineWidth(last));
        final boolean separateTime = timeMore > 0 && (maxTextWidth - lastWidth < timeMore || last >= 0 && layout.getParagraphDirection(last) < 0);
        int contentWidth = Math.max(textWidth, separateTime ? timeMore : lastWidth + timeMore);
        int desiredWidth = Math.min(maxWidth, Math.max(dp(40), contentWidth + horizontal));
        final int actualWidth = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? maxWidth : desiredWidth;
        int layoutWidth = Math.max(1, actualWidth - horizontal);
        layout = MessageTextLayout.makeStaticLayout(text, textPaint, layoutWidth, 1f, 0, true, Layout.Alignment.ALIGN_NORMAL);
        textX = showBubble ? dp(outgoing || below ? 11 : 17) : 0;
        textY = showBubble ? dp(8) : 0;
        int height = layout.getHeight() + (showBubble ? dp(16) : 0) + (separateTime ? dp(14 * textScale) : 0);
        final int right = actualWidth - (showBubble ? dp(outgoing ? 15 : 11) : 0);
        timeLayout = MessageTextLayout.makeStaticLayout(time, timePaint, Math.max(1, timeWidth), 1, 0, true, Layout.Alignment.ALIGN_NORMAL);
        timeX = right - statusWidth - timeWidth;
        timeY = height - (showBubble ? dp(6) : 0) - timeLayout.getHeight();
        statusX = right - statusWidth + dp(3);
        setMeasuredDimension(actualWidth, resolveSize(Math.max(dp(28), height), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (showBubble) { bubble.setBounds(0, 0, getWidth(), getHeight()); bubble.draw(canvas); }
        if (layout != null) {
            canvas.save(); canvas.translate(textX, textY); layout.draw(canvas); canvas.restore();
        }
        if (timeLayout != null) {
            canvas.save(); canvas.translate(timeX, timeY); timeLayout.draw(canvas); canvas.restore();
        }
        if (outgoing && deliveryState != DELIVERY_NONE) {
            if (deliveryState == DELIVERY_PENDING) {
                clock.setBounds(statusX, timeY + dp(1), statusX + dp(12), timeY + dp(13));
                clock.draw(canvas);
                if (isAttachedToWindow()) postInvalidateDelayed(33);
            } else {
                int w = check.getIntrinsicWidth(), h = check.getIntrinsicHeight();
                check.setBounds(statusX, timeY + dp(1), statusX + w, timeY + dp(1) + h); check.draw(canvas);
                if (deliveryState == DELIVERY_READ) {
                    halfCheck.setBounds(statusX + dp(4), timeY + dp(1), statusX + dp(4) + halfCheck.getIntrinsicWidth(), timeY + dp(1) + halfCheck.getIntrinsicHeight());
                    halfCheck.draw(canvas);
                }
            }
        }
    }

    private ClickableSpan linkAt(float x, float y) {
        if (!(text instanceof Spanned) || layout == null) return null;
        x -= textX; y -= textY;
        if (y < 0 || y > layout.getHeight()) return null;
        int line = layout.getLineForVertical((int)y);
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null;
        int offset = layout.getOffsetForHorizontal(line, x);
        ClickableSpan[] spans = ((Spanned)text).getSpans(offset, offset, ClickableSpan.class);
        return spans.length > 0 ? spans[0] : null;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); downTime = event.getEventTime();
            downSpan = linkAt(downX, downY);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && downSpan != null) {
            ClickableSpan hit = linkAt(event.getX(), event.getY());
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (hit == downSpan && Math.abs(event.getX() - downX) < slop && Math.abs(event.getY() - downY) < slop
                    && event.getEventTime() - downTime < ViewConfiguration.getLongPressTimeout()) {
                hit.onClick(this); downSpan = null; return true;
            }
            downSpan = null;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) downSpan = null;
        return super.onTouchEvent(event) || downSpan != null;
    }
    @Override protected boolean verifyDrawable(Drawable who) { return who == bubble || super.verifyDrawable(who); }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.TextView"); info.setText(text);
    }
}
