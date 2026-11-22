// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import android.animation.ValueAnimator;
import android.view.View;
import static com.monica.telegramchat.TelegramUi.lerp;
public final class ComposerActionAnimator {
    public static ValueAnimator animate(View view, boolean show) {
        final float fromAlpha = view.getAlpha();
        final float toAlpha = show ? 1.0f : 0.0f;
        final float fromScaleX = /*show ? 1 :*/ view.getScaleX();
        final float toScaleX = show ? 1.0f : 0.1f;
        final float fromScaleY = /*show ? 1 :*/ view.getScaleY();
        final float toScaleY = show ? 1.0f : 0.1f;
        ValueAnimator a = ValueAnimator.ofFloat(0, 1);
        a.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            view.setAlpha(lerp(fromAlpha, toAlpha, t));
            view.setScaleX(lerp(fromScaleX, toScaleX, t));
            view.setScaleY(lerp(fromScaleY, toScaleY, t));
        });
        return a;
    }
}
