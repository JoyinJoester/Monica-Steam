package takagi.ru.monica.steam.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.liquidglass.render.*
import top.yukonga.miuix.kmp.blur.drawBackdrop
import kotlin.math.ceil

class LiquidGlassLensDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()

    @Test fun retainedZeroRefractionIsPixelIdenticalAndPressedLensStillRefracts() {
        assumeTrue(isSteamLiquidGlassRuntimeSupported())
        val retained = mutableStateOf(false)
        val progress = mutableFloatStateOf(0f)
        var hasEffect = false
        rule.setContent {
            val source = rememberSteamLiquidGlassBackdrop()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().steamLiquidGlassBackdropSource(source, true)) {
                    for (x in 0..30) for (y in 0..50) {
                        drawRect(if ((x+y)%2 == 0) Color(0xFF93C5FD) else Color(0xFFFFDCA8),
                            Offset(x*size.width/31f, y*size.height/51f), Size(size.width/31f, size.height/51f))
                    }
                }
                Box(Modifier.align(Alignment.Center).size(128.dp, 56.dp).testTag("lens")
                    .drawBackdrop(backdrop = source.delegate, shape = { CircleShape }, effects = {
                        padding = ceil(14.dp.toPx())
                        liquidGlassLens(10.dp.toPx()*progress.floatValue, 14.dp.toPx()*progress.floatValue,
                            depthEffect = true, chromaticAberration = 0.5f, retainZeroRefraction = retained.value)
                        hasEffect = renderEffect != null
                    }))
            }
        }
        fun pixels(): IntArray {
            rule.waitForIdle()
            val bitmap = rule.onNodeWithTag("lens").captureToImage().asAndroidBitmap()
            return IntArray(bitmap.width*bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
        }
        val original = pixels()
        assertFalse(hasEffect)
        rule.runOnIdle { retained.value = true }
        val identity = pixels()
        assertTrue("The shader is present before the first press", hasEffect)
        assertArrayEquals("Zero-strength refraction must preserve the sampled image", original, identity)
        rule.runOnIdle { progress.floatValue = 1f }
        val pressed = pixels()
        assertTrue("Retaining the shader must preserve visible refraction and dispersion",
            identity.indices.count { identity[it] != pressed[it] } > 100)
        rule.runOnIdle { progress.floatValue = 0f }
        assertArrayEquals(identity, pixels())
    }
}
