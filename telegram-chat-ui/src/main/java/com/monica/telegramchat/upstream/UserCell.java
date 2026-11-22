// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.monica.telegramchat.*;
import static com.monica.telegramchat.TelegramUi.dp;
public final class UserCell extends FrameLayout {
    private final SocialTheme theme;
    private final SteamAvatarView avatarImageView;
    private final TextView nameTextView, statusTextView;
    private final ImageView imageView;
    private boolean needDivider;
    private float textScale = 1f;
    public UserCell(Context context, SocialTheme theme) {
        super(context); TelegramUi.initialize(context); this.theme = theme;
        final int padding = 0, checkbox = 0, additionalPadding = 0;
        avatarImageView = new SteamAvatarView(context);
        avatarImageView.setRoundRadius(dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, ((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? 0 : 7 + padding, 6, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? 7 + padding : 0, 0));
        setClipChildren(false);

        nameTextView = new TextView(context);
        nameTextView.setTextColor(theme.getColor(SocialTheme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setIncludeFontPadding(true); nameTextView.setSingleLine(); nameTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameTextView.setGravity(((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, ((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? 28 + (checkbox == 2 ? 18 : 0) + additionalPadding : (64 + padding), 10, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? (64 + padding) : 28 + (checkbox == 2 ? 18 : 0) + additionalPadding, 0));


        statusTextView = new TextView(context);
        statusTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        statusTextView.setIncludeFontPadding(true); statusTextView.setSingleLine(); statusTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        statusTextView.setGravity(((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, ((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? 28 + additionalPadding : (64 + padding), 32, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? (64 + padding) : 28 + additionalPadding, 0));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(theme.getColor(SocialTheme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(LayoutParams.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, ((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? 0 : 16, 0, (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? 16 : 0, 0));


        setFocusable(true);
        setClickable(true);
        setBackground(theme.selector());
    }
    public void bind(String name, String status, boolean online, boolean divider) {
        nameTextView.setText(name); statusTextView.setText(status);
        statusTextView.setTextColor(online ? theme.accent : theme.secondary);
        avatarImageView.setInitials(name, theme.accent, theme.accentText);
        needDivider = divider; setWillNotDraw(!divider);
        setContentDescription(name + ", " + status);
    }
    public void setAvatar(android.graphics.Bitmap bitmap) { avatarImageView.setBitmap(bitmap); }
    public void setTextScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) scale = 1f;
        if (textScale == scale) return;
        textScale = scale;
        nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(16) * scale);
        statusTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(15) * scale);
        requestLayout();
    }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Platform TextView needs its measured font/fallback padding, unlike SimpleTextView.
        // A fixed 20dp label clips the descent at fractional display densities.
        int width = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        LayoutParams title = (LayoutParams) nameTextView.getLayoutParams();
        LayoutParams status = (LayoutParams) statusTextView.getLayoutParams();
        status.topMargin = title.topMargin + nameTextView.getMeasuredHeight() + dp(2);
        int required = Math.max(dp(58), status.topMargin + statusTextView.getMeasuredHeight() + dp(6));
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(
            resolveSize(required + (needDivider ? 1 : 0), heightMeasureSpec), MeasureSpec.EXACTLY));
    }
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? 0 : dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - ((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? dp(68) : 0), getMeasuredHeight() - 1, theme.dividerPaint);
        }
    }
}
