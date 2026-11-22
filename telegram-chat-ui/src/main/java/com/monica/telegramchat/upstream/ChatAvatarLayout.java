// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import static com.monica.telegramchat.TelegramUi.dp;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
public final class ChatAvatarLayout extends ViewGroup {
    private final int avatarSizeInDp = 42;
    private final boolean glassMode = true;
    private final int leftPadding = 1, rightAvatarPadding = 0;
    private final ImageView avatarImageView;
    private final TextView titleTextView, subtitleTextView;
    private View customAvatar;
    private final TypingDotsDrawable typingDots = new TypingDotsDrawable(true);
    private boolean typing;
    public ChatAvatarLayout(Context context) {
        super(context);
        avatarImageView = new ImageView(context);
        avatarImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImageView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        avatarImageView.setClipToOutline(true);
        addView(avatarImageView);
        titleTextView = text(context, 17.5f); titleTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        subtitleTextView = text(context, 13.5f);
        addView(titleTextView); addView(subtitleTextView);
        setClickable(true); setFocusable(true);
    }
    private static TextView text(Context context, float size) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size));
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }
    public View getSubtitleTextView() { return subtitleTextView; }
    public void setTitle(CharSequence text) { if (!TextUtils.equals(text, titleTextView.getText())) { titleTextView.setText(text); setContentDescription(text); } }
    public void setSubtitle(CharSequence text) { if (!TextUtils.equals(text, subtitleTextView.getText())) subtitleTextView.setText(text); subtitleTextView.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE); }
    public void setTyping(boolean value) {
        if (typing == value) return;
        typing = value;
        typingDots.setBounds(0, 0, dp(18), dp(18));
        subtitleTextView.setCompoundDrawablePadding(value ? dp(4) : 0);
        subtitleTextView.setCompoundDrawables(value ? typingDots : null, null, null, null);
        if (value && isAttachedToWindow()) typingDots.start(); else typingDots.stop();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (typing) typingDots.start(); }
    @Override protected void onDetachedFromWindow() { typingDots.stop(); super.onDetachedFromWindow(); }
    public void setAvatar(Drawable drawable) { avatarImageView.setImageDrawable(drawable); }
    public void setColors(int title, int subtitle) { titleTextView.setTextColor(title); subtitleTextView.setTextColor(subtitle); typingDots.setColor(subtitle); }
    public void setAvatarView(View view) {
        if (customAvatar == view) return;
        if (customAvatar != null) removeView(customAvatar);
        customAvatar = view;
        if (view != null) { if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view); addView(view); }
        requestLayout();
    }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int availableWidth = width - dp((avatarImageView.getVisibility() == VISIBLE ? 54 : 0) + 16);
        avatarImageView.measure(MeasureSpec.makeMeasureSpec(dp(avatarSizeInDp) - 2, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(avatarSizeInDp) - 2, MeasureSpec.EXACTLY));
        titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(24 + 8), MeasureSpec.AT_MOST));
        if (subtitleTextView != null) {
            subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.AT_MOST));

        }
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        if (customAvatar != null) customAvatar.measure(MeasureSpec.makeMeasureSpec(avatarImageView.getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(avatarImageView.getMeasuredHeight(), MeasureSpec.EXACTLY));
    }
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int actionBarHeight = getMeasuredHeight();
        final int viewTop = (actionBarHeight - avatarImageView.getMeasuredHeight() - 2) / 2 + 0;
        final int subtitleTop = viewTop + dp(glassMode ? 23.66f : 24);

        avatarImageView.layout(1 + leftPadding, 1 + viewTop, 1 + leftPadding + avatarImageView.getMeasuredWidth(), 1 + viewTop + avatarImageView.getMeasuredHeight());
        int l = leftPadding + (avatarImageView.getVisibility() == VISIBLE ? dp(glassMode ? 49.66f : 55) : dp(glassMode ? 13 : 1)) + rightAvatarPadding;

        if (getSubtitleTextView().getVisibility() != GONE) {
            titleTextView.layout(l, viewTop + dp(1.66f), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getMeasuredHeight() + dp(1.66f));
        } else {
            titleTextView.layout(l, viewTop + dp(11), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getMeasuredHeight() + dp(11));
        }
        subtitleTextView.layout(l, subtitleTop, l + subtitleTextView.getMeasuredWidth(), subtitleTop + subtitleTextView.getMeasuredHeight());
        if (customAvatar != null) customAvatar.layout(avatarImageView.getLeft(), avatarImageView.getTop(), avatarImageView.getRight(), avatarImageView.getBottom());
    }
}
