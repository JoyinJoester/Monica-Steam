package takagi.ru.monica.steam.navigation.ui

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

private const val STEAM_DOCK_BLUR_SHADER = """
    uniform shader content;
    uniform float blurRadius;
    uniform float height;
    uniform float contentHeight;

    half4 main(float2 fragCoord) {
        float progress = 1.0 - clamp((contentHeight - fragCoord.y) / height, 0.0, 1.0);
        progress = pow(progress, 1.5);
        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;
        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 4;
        float offsetScale = radius / float(SAMPLES);
        float radiusSq = radius * radius;

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                float distSq = dot(offset, offset);
                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
"""

internal fun Modifier.steamDockProgressiveBlur(
    enabled: Boolean,
    blurRadius: Float,
    height: Float
): Modifier = composed {
    if (!enabled || height <= 0f) return@composed this

    val context = LocalContext.current
    val overlayColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)
    val canUseRuntimeBlur = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isPowerSaveMode(context) &&
            !isRuntimeBlurProblematicDevice()
    }

    val blurModifier = if (canUseRuntimeBlur && blurRadius > 0f) {
        Modifier.graphicsLayer {
            val shader = RuntimeShader(STEAM_DOCK_BLUR_SHADER)
            shader.setFloatUniform("blurRadius", blurRadius)
            shader.setFloatUniform("height", height)
            shader.setFloatUniform("contentHeight", size.height)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    this
        .then(blurModifier)
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, overlayColor),
                    startY = size.height - height
                )
            )
        }
}

private fun isPowerSaveMode(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isPowerSaveMode == true
}

private fun isRuntimeBlurProblematicDevice(): Boolean {
    return Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
        Build.VERSION.SDK_INT <= 35
}
