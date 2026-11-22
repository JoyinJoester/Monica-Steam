/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import static com.monica.telegramchat.TelegramUi.dp;

/** Portable ActionBarMenuSubItem geometry, using the original packaged icons. */
public final class TelegramMenuRow extends FrameLayout {
    private final TextView labelView;
    public TelegramMenuRow(Context context, CharSequence label, int icon, TelegramChatColors colors, Runnable click) {
        super(context);
        setPadding(dp(18), 0, dp(18), 0);
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int side = rtl ? Gravity.RIGHT : Gravity.LEFT;
        ImageView image = new TelegramBounceImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER);
        if (icon != 0) image.setImageDrawable(TelegramDrawables.tinted(context, icon, colors.menuIcon));
        else image.setVisibility(INVISIBLE);
        addView(image, new FrameLayout.LayoutParams(dp(24), dp(40), Gravity.CENTER_VERTICAL | side));
        TextView text = new TextView(context);
        labelView = text;
        text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16); text.setTextColor(colors.menuText);
        text.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        text.setPadding(rtl || icon == 0 ? 0 : dp(43), 0, rtl && icon != 0 ? dp(43) : 0, 0);
        text.setText(label);
        addView(text, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        GradientDrawable mask = new GradientDrawable(); mask.setColor(0xffffffff); mask.setCornerRadius(dp(12));
        setBackground(new RippleDrawable(ColorStateList.valueOf(colors.dark ? 0x20ffffff : 0x18000000), null, mask));
        setContentDescription(label); setClickable(true); setFocusable(true);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setOnClickListener(v -> click.run());
    }

    @Override protected void onMeasure(int width, int height) {
        final float scale = getResources().getConfiguration().fontScale;
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(16) * scale);
        labelView.setSingleLine(scale <= 1.05f);
        labelView.setMaxLines(scale <= 1.05f ? 1 : 2);
        int minimum = dp(48);
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int required = Math.max(minimum, labelView.getMeasuredHeight() + dp(20));
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(required, MeasureSpec.EXACTLY));
    }
}
