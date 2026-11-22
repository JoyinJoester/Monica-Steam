// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
public final class MessageTextLayout {
    public static StaticLayout makeStaticLayout(CharSequence text_, TextPaint paint, int width, float lineSpacingMult, float lineSpacingAdd, boolean dontIncludePad, Layout.Alignment alignment) {
        if (width <= 0) width = 1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final CharSequence text = /* Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                PrecomputedText.create(text_, new PrecomputedText.Params.Builder(paint).build()) :*/ text_;

            StaticLayout.Builder builder =
                    StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                            .setLineSpacing(lineSpacingAdd, lineSpacingMult)
                            .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                            .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                            .setAlignment(alignment);
            if (dontIncludePad) {
                builder.setIncludePad(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setUseLineSpacingFromFallbacks(false);
            }
            StaticLayout layout = builder.build();

            boolean realWidthLarger = false;
            for (int l = 0; l < layout.getLineCount(); ++l) {
                if (layout.getLineRight(l) > width) {
                    realWidthLarger = true;
                    break;
                }
            }
            if (realWidthLarger) {
                builder = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                                .setLineSpacing(lineSpacingAdd, lineSpacingMult)
                                .setBreakStrategy(StaticLayout.BREAK_STRATEGY_SIMPLE)
                                .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                                .setAlignment(alignment);
                if (dontIncludePad) {
                    builder.setIncludePad(false);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setUseLineSpacingFromFallbacks(false);
                }
                layout = builder.build();
            }

            return layout;
        } else {
            return new StaticLayout(text_, paint, width, alignment, lineSpacingMult, lineSpacingAdd, false);
        }
    }
}
