package takagi.ru.monica.steam.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.liquidglass.render.*
import kotlin.math.abs
import java.io.File

class LiquidGlassInnerShadowDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private fun crop(): Bitmap {
        rule.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val presented=java.util.concurrent.CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback { presented.countDown() }
            rule.activity.window.decorView.invalidate()
        }
        assertTrue("A hardware frame must be committed before comparing pixels",
            presented.await(5,java.util.concurrent.TimeUnit.SECONDS))
        val bounds = rule.onNodeWithTag("shadow").fetchSemanticsNode().boundsInRoot
        val origin = IntArray(2)
        rule.runOnUiThread { rule.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(origin) }
        val screen = instrumentation.uiAutomation.takeScreenshot()
        return Bitmap.createBitmap(screen, bounds.left.toInt()+origin[0],bounds.top.toInt()+origin[1],
            bounds.width.toInt(),bounds.height.toInt()).also { screen.recycle() }
    }
    private fun difference(a: Bitmap, b: Bitmap): Long {
        var total = 0L
        for (x in 0 until minOf(a.width,b.width)) for (y in 0 until minOf(a.height,b.height)) {
            val left=a.getPixel(x,y); val right=b.getPixel(x,y)
            for (shift in listOf(0,8,16)) total += abs(((left shr shift) and 255)-((right shr shift) and 255))
        }
        return total
    }
    private fun save(name: String, bitmap: Bitmap) {
        val file=File(rule.activity.getExternalFilesDir(null),"dock-shadow-validation/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun cachedMaskRespondsToAlphaColorOffsetAndSize() {
        val alpha=mutableFloatStateOf(1f)
        val color=mutableStateOf(Color.Black)
        val offset=mutableStateOf(DpOffset(0.dp,12.dp))
        val width=mutableStateOf(160.dp)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.White),contentAlignment=Alignment.Center) {
                    Box(Modifier.size(width.value,120.dp).testTag("shadow")
                        .liquidGlassInnerShadow(RoundedCornerShape(16.dp)) {
                            LiquidGlassInnerShadow(6.dp,offset.value,color.value,alpha.floatValue)
                        })
                }
            }
        }
        val original=crop()
        rule.runOnIdle { alpha.floatValue=0f }
        val transparent=crop()
        assertTrue("Zero alpha removes the shadow",difference(original,transparent)>1_000)
        rule.runOnIdle { alpha.floatValue=1f }
        val restored=crop()
        save("original",original); save("transparent",transparent); save("restored",restored)
        assertEquals("Reusing the mask restores identical pixels",0L,difference(original,restored))
        rule.runOnIdle { color.value=Color.Red; offset.value=DpOffset(12.dp,0.dp) }
        val changed=crop()
        assertTrue("Changed color and offset require a fresh mask",difference(original,changed)>1_000)
        rule.runOnIdle { width.value=210.dp }
        val resized=crop()
        assertTrue("Layer recording uses the resized bounds",resized.width>original.width)
        listOf(original,transparent,restored,changed,resized).forEach { it.recycle() }
    }

    @Test fun mutableGenericOutlineDoesNotReuseOldPath() {
        val path=Path()
        val narrow=mutableStateOf(false)
        val shape=object: Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size,layoutDirection:LayoutDirection,density:Density):Outline {
                path.reset()
                path.addRect(Rect(0f,0f,if(narrow.value) size.width/2 else size.width,size.height))
                return Outline.Generic(path)
            }
        }
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.White),contentAlignment=Alignment.Center) {
                Box(Modifier.size(160.dp,120.dp).testTag("shadow").liquidGlassInnerShadow(shape) {
                    LiquidGlassInnerShadow(6.dp,DpOffset(12.dp,0.dp),Color.Black)
                })
            }
        }
        val full=crop()
        rule.runOnIdle { narrow.value=true }
        val half=crop()
        assertTrue("A mutable generic path must change the recorded shadow",difference(full,half)>1_000)
        full.recycle(); half.recycle()
    }
}
