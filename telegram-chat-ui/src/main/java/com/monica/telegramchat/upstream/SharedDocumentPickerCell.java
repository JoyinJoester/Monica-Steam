// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.monica.telegramchat.PickerTheme;
import com.monica.telegramchat.TelegramUi;
import static com.monica.telegramchat.TelegramUi.dp;
public final class SharedDocumentPickerCell extends FrameLayout {
    private ImageView placeholderImageView;
    private TextView extTextView, nameTextView, dateTextView;
    public SharedDocumentPickerCell(Context context, PickerTheme.ResourcesProvider colors) {
        super(context); TelegramUi.initialize(context);
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
placeholderImageView = new ImageView(context);
            addView(placeholderImageView, frame(42, 42, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 0 : 15, 12, rtl ? 15 : 0, 0));
                extTextView = new TextView(context);
        extTextView.setTextColor(0xffffffff);
        extTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        extTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        extTextView.setLines(1);
        extTextView.setMaxLines(1);
        extTextView.setSingleLine(true);
        extTextView.setGravity(Gravity.CENTER);
        extTextView.setEllipsize(TextUtils.TruncateAt.END);
        extTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

            addView(extTextView, frame(32, -2, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 0 : 20, 28, rtl ? 20 : 0, 0));
                nameTextView = new TextView(context);
        nameTextView.setTextColor(colors.getColor(PickerTheme.key_text));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);


            nameTextView.setLines(1);
            nameTextView.setMaxLines(1);
            nameTextView.setSingleLine(true);
            addView(nameTextView, frame(-1, -2, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 8 : 72, 9, rtl ? 72 : 8, 0));
                dateTextView = new TextView(context);
        dateTextView.setTextColor(colors.getColor(PickerTheme.key_secondaryText));
        dateTextView.setLines(1);
        dateTextView.setMaxLines(1);
        dateTextView.setSingleLine(true);
        dateTextView.setEllipsize(TextUtils.TruncateAt.END);
        dateTextView.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            addView(dateTextView, frame(-1, -2, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 8 : 72, 34, rtl ? 72 : 8, 0));
        
        GradientDrawable icon = new GradientDrawable(); icon.setColor(colors.getColor(PickerTheme.key_checkbox)); icon.setCornerRadius(dp(4));
        placeholderImageView.setBackground(icon);
        setClickable(true); setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setDocument(String name, String extension, String detail) {
        nameTextView.setText(name); extTextView.setText(extension); dateTextView.setText(detail);
        setContentDescription(name + ", " + detail);
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY));
    }
    private static LayoutParams frame(int w, int h, int gravity, float l, float t, float r, float b) {
        LayoutParams p = new LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h), gravity);
        p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p;
    }
}
