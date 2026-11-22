// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.FrameLayout;
import com.monica.telegramchat.*;
import static com.monica.telegramchat.TelegramUi.dp;
public final class DialogCell extends FrameLayout {
    public static class CustomDialog {
        public String name;
        public String message;
        public int id;
        public int unread_count;
        public boolean pinned;
        public boolean muted;
        public int type;
        public int date;
        public boolean verified;
        public boolean isMedia;
        public int sent = -1;
    }
    private static final float BADGE_TEXT_PADDING = 6.333f, BADGE_TEXT_MIN_WIDTH = 8f, BADGE_GAP = 17f, BADGE_MARGIN = 15.666f;
    private final SocialTheme theme;
    private final SteamAvatarView avatar;
    private final TextPaint namePaint = new TextPaint(3), messagePaint = new TextPaint(3), timePaint = new TextPaint(3), countPaint = new TextPaint(3);
    private StaticLayout nameLayout, messageLayout, timeLayout, countLayout;
    private int nameLeft, nameWidth, timeLeft, countWidth, countLeft, messageWidth, messageLeft, typingLeft, buttonLeft, messageNameLeft;
    private final int messagePaddingStart = 72;
    private CustomDialog customDialog;
    private String formattedTime = "";
    private float textScale = 1f;
    public DialogCell(Context context, SocialTheme theme) {
        super(context); TelegramUi.initialize(context); this.theme = theme;
        setWillNotDraw(false); setFocusable(true); setClickable(true); setBackground(theme.selector());
        namePaint.setTextSize(dp(16)); namePaint.setTypeface(Typeface.create("sans-serif-medium", 0)); namePaint.setColor(theme.text);
        messagePaint.setTextSize(dp(15)); messagePaint.setColor(theme.secondary);
        timePaint.setTextSize(dp(12)); timePaint.setColor(theme.secondary);
        countPaint.setTextSize(dp(13)); countPaint.setTypeface(Typeface.create("sans-serif-medium", 0)); countPaint.setColor(theme.accentText);
        avatar = new SteamAvatarView(context); avatar.setRoundRadius(dp(28));
        addView(avatar, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.START, 10, 10, 10, 0));
    }
    public void setDialog(CustomDialog dialog, String time) {
        customDialog = dialog; formattedTime = time; avatar.setInitials(dialog.name, theme.accent, theme.accentText);
        // The host supplies the fully localized description after binding Steam data.
        buildLayout(); requestLayout(); invalidate();
    }
    /** Host-only adaptation: previous designs pass 1; Telegram mode passes the user's font scale. */
    public void setTextScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) scale = 1f;
        if (textScale == scale) return;
        textScale = scale;
        namePaint.setTextSize(dp(16) * scale); messagePaint.setTextSize(dp(15) * scale);
        timePaint.setTextSize(dp(12) * scale); countPaint.setTextSize(dp(13) * scale);
        buildLayout(); requestLayout(); invalidate();
    }
    public void setAvatar(Bitmap bitmap) { avatar.setBitmap(bitmap); }
    private TextPaint getTimeTextPaint() { return timePaint; }
    private boolean getIsPinned() { return customDialog != null && customDialog.pinned; }
    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(78), MeasureSpec.EXACTLY)); buildLayout();
        if (textScale > 1f) {
            int desired = dp(78);
            if (messageLayout != null) desired = Math.max(desired, messageTop() + messageLayout.getHeight() + dp(10));
            if (countLayout != null) desired = Math.max(desired, badgeTop() + badgeHeight() + dp(10));
            if (nameLayout != null) desired = Math.max(desired, dp(10) + nameLayout.getHeight() + dp(10));
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(resolveSize(desired, h), MeasureSpec.EXACTLY));
        }
    }
    private int messageTop() {
        return textScale <= 1f || nameLayout == null ? dp(32) : Math.max(dp(32), dp(10) + nameLayout.getHeight() + dp(4));
    }
    private int badgeTop() { return messageTop() + dp(13); }
    private int badgeHeight() {
        return textScale <= 1f || countLayout == null ? dp(23) : Math.max(dp(23), countLayout.getHeight() + dp(6));
    }
    private StaticLayout oneLine(CharSequence text, TextPaint paint, int width) {
        width = Math.max(dp(12), width);
        return new StaticLayout(TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END), paint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
    }
    public void buildLayout() {
        if (customDialog == null || getMeasuredWidth() <= 0) return;
        boolean drawTime = !formattedTime.isEmpty(); String timeString = formattedTime;
        nameLeft = dp(isRtl() ? 22 : messagePaddingStart + 6);
        int timeWidth;
        if (drawTime) {
            final TextPaint tp = getTimeTextPaint();
            timeWidth = (int) Math.ceil(tp.measureText(timeString));
            if (textScale > 1f) {
                // Keep a readable name slot when localized dates grow at accessibility sizes.
                timeWidth = Math.min(timeWidth, Math.max(dp(24), (getMeasuredWidth() - dp(messagePaddingStart + 35)) / 2));
                timeLayout = oneLine(timeString, tp, timeWidth);
            } else {
                timeLayout = new StaticLayout(timeString, tp, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            timeWidth += dp(getIsPinned() ? 24 : 0);
            if (!isRtl()) {
                timeLeft = getMeasuredWidth() - dp(15) - timeWidth;
            } else {
                timeLeft = dp(15);
            }
        } else {
            timeWidth = 0;
            timeLayout = null;
            timeLeft = 0;
        }

        if (!isRtl()) {
            nameWidth = getMeasuredWidth() - nameLeft - dp(14 + 8) - timeWidth;
        } else {
            nameWidth = getMeasuredWidth() - nameLeft - dp(messagePaddingStart + 5 + 8) - timeWidth;
            nameLeft += timeWidth;
        }

        nameLayout = oneLine(customDialog.name, namePaint, nameWidth);
        messageLeft = dp(isRtl() ? 16 : messagePaddingStart + 6);
        messageWidth = getMeasuredWidth() - dp(messagePaddingStart + 21);
        countLayout = null; countWidth = 0;
        String countString = customDialog.unread_count > 0 ? String.valueOf(customDialog.unread_count) : null;
        if (countString != null) {
                countWidth = Math.max(dp(BADGE_TEXT_MIN_WIDTH), (int) Math.ceil(countPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, countPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + dp(BADGE_GAP);
                messageWidth -= w;
                if (!isRtl()) {
                    countLeft = getMeasuredWidth() - dp(BADGE_MARGIN) - (countWidth + dp(BADGE_TEXT_PADDING * 2));
                } else {
                    countLeft = dp(BADGE_MARGIN);
                    messageLeft += w;
                    typingLeft += w;
                    buttonLeft += w;
                    messageNameLeft += w;
                }

        }
        messageWidth = Math.max(dp(12), messageWidth);
        messageLayout = StaticLayout.Builder.obtain(customDialog.message, 0, customDialog.message.length(), messagePaint, messageWidth)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
    }
    private boolean isRtl() { return getLayoutDirection() == LAYOUT_DIRECTION_RTL; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (nameLayout == null) return;
        drawLayout(canvas, nameLayout, nameLeft, dp(10));
        drawLayout(canvas, messageLayout, messageLeft, messageTop());
        drawLayout(canvas, timeLayout, timeLeft, dp(13));
        if (countLayout != null) {
            Paint badge = theme.badgePaint;
            RectF rect = new RectF(countLeft, badgeTop(), countLeft + countWidth + dp(BADGE_TEXT_PADDING * 2), badgeTop() + badgeHeight());
            canvas.drawRoundRect(rect, badgeHeight() / 2f, badgeHeight() / 2f, badge);
            float countTop = textScale <= 1f ? dp(48) : badgeTop() + (badgeHeight() - countLayout.getHeight()) / 2f;
            drawLayout(canvas, countLayout, countLeft + dp(BADGE_TEXT_PADDING), countTop);
        }
        if (getIsPinned()) {
            android.graphics.drawable.Drawable pin = getResources().getDrawable(com.monica.telegramchat.R.drawable.telegram_msg_pin, null).mutate();
            pin.setTint(theme.secondary);
            int left = isRtl() ? dp(15) : getWidth() - dp(31);
            pin.setBounds(left, dp(12), left + dp(16), dp(28)); pin.draw(canvas);
        }
    }
    private void drawLayout(Canvas canvas, StaticLayout layout, float x, float y) {
        if (layout == null) return; canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore();
    }
}
