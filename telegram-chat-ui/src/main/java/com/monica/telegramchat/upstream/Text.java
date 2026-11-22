// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.graphics.*;
import android.os.Build;
import android.text.*;
import static com.monica.telegramchat.TelegramUi.dp;
public final class Text {
    public final TextPaint paint;
    private StaticLayout layout;
    private float width, left, maxWidth = 9999, lineSpacingAdd, ellipsizeWidth = -1;
    private int maxLines = 1;
    private Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
    private boolean doNotSave;
    public Text(CharSequence text, float textSizeDp, Typeface typeface) {
        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(dp(textSizeDp));
        paint.setTypeface(typeface);
        setText(text);
    }
    public void setText(CharSequence text) {
        if (maxLines > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, (int) Math.max(maxWidth, 1)).setAlignment(align).setMaxLines(maxLines).setLineSpacing(lineSpacingAdd, 1.0f).build();
        } else {
            layout = new StaticLayout(text.toString().replace("\n", " "), paint, (int) Math.max(maxWidth, 1), align, 1f, lineSpacingAdd, false);
        }
        if (align == Layout.Alignment.ALIGN_CENTER) {
            width = layout.getWidth();
            left = 0;
        } else {
            width = 0;
            left = layout.getWidth();
            for (int i = 0; i < layout.getLineCount(); ++i) {
                width = Math.max(width, layout.getLineWidth(i));
                left = Math.min(left, layout.getLineLeft(i));
            }
        }

    }
    public Text multiline(int maxLines) {
        this.maxLines = maxLines;
        setText(layout.getText());
        return this;
    }
    public boolean isMultiline() {
        return this.maxLines > 1;
    }
    public Text align(Layout.Alignment align) {
        if (this.align != align) {
            this.align = align;
            setText(layout.getText());
        }
        return this;
    }
    public Text setMaxWidth(float maxWidth) {
        this.maxWidth = maxWidth;
        setText(layout.getText());
        return this;
    }
    public int getLineCount() {
        return layout.getLineCount();
    }
    public float getWidth() {
        return ellipsizeWidth >= 0 ? Math.min(ellipsizeWidth, width) : width;
    }
    public float getHeight() {
        return layout.getHeight();
    }
    public CharSequence getText() {
        if (layout == null || layout.getText() == null) {
            return "";
        }
        return layout.getText();
    }
    public void draw(Canvas canvas, float x, float cy, int color, float alpha) {
        if (layout == null) {
            return;
        }
        paint.setColor(color);
        paint.linkColor = color;
        final int wasAlpha = paint.getAlpha();
        if (alpha != 1f) {
            paint.setAlpha((int) (wasAlpha * alpha));
        }
        if (!doNotSave) {
            canvas.save();
        }
        canvas.translate(x, cy - (isMultiline() ? 0 : layout.getHeight() / 2f));
        draw(canvas);
        if (!doNotSave) {
            canvas.restore();
        }
        paint.setAlpha(wasAlpha);
    }
    public void draw(Canvas canvas) { canvas.save(); canvas.translate(-left, 0); layout.draw(canvas); canvas.restore(); }
}
