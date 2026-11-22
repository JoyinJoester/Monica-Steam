// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.*;
import androidx.core.view.ViewCompat;
import com.monica.telegramchat.TelegramUi;
import com.monica.telegramchat.SocialTheme;
public final class HeaderCell extends FrameLayout {
    private final TextView textView;
    public HeaderCell(Context context, SocialTheme theme) {
        super(context); TelegramUi.initialize(context);
        final int height = 40, topMargin = 7, padding = 20, bottomMargin = 0;
        final boolean text2 = false;

            textView = new TextView(getContext());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setMinHeight(TelegramUi.dp(height - topMargin));
            textView.setTextColor(theme.accent);
            
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, ((getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, topMargin, padding, text2 ? 0 : bottomMargin));

        ViewCompat.setAccessibilityHeading(this, true);
    }
    public void setText(CharSequence text) { textView.setText(text); }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
}
