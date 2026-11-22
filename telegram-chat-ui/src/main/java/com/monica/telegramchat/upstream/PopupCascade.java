// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
import androidx.core.math.MathUtils;
public final class PopupCascade {
    public static float cascade(float fullAnimationT, float position, float count, float waveLength) {
        if (count <= 0) return fullAnimationT;
        final float waveDuration = 1f / count * Math.min(waveLength, count);
        final float waveOffset = position / count * (1f - waveDuration);
        return MathUtils.clamp((fullAnimationT - waveOffset) / waveDuration, 0, 1);
    }
}
