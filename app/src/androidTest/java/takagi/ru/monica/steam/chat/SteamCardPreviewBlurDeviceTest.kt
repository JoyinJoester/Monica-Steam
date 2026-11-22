package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.SteamCardPreviewHost
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichCard
import takagi.ru.monica.ui.LocalReduceAnimations

/** Pixel evidence on a native View behind the production card, not a mocked blur effect. */
class SteamCardPreviewBlurDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = mutableStateOf("first")
    private var stripe = 1
    private fun mount() {
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalReduceAnimations provides true) {
            stripe = with(LocalDensity.current) { 6.dp.roundToPx() }
            SteamCardPreviewHost(scope.value) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { context -> object : View(context) {
                        private val paint = Paint()
                        override fun onDraw(canvas: Canvas) {
                            for (x in 0..width step stripe) {
                                paint.color = if ((x / stripe) % 2 == 0) Color.WHITE else Color.BLACK
                                canvas.drawRect(x.toFloat(), 0f, (x + stripe).toFloat(), height.toFloat(), paint)
                            }
                        }
                    } }, modifier = Modifier.fillMaxSize())
                    Column(Modifier.statusBarsPadding().padding(24.dp)) {
                        SteamChatRichCard("Game invitation", "Synthetic preview", Icons.Default.SportsEsports,
                            null, "No account or network", "Native backdrop should blur", null, emptyList())
                    }
                }
            }
        } } }
    }
    private fun capture(name: String): Bitmap {
        compose.waitForIdle(); instrumentation.waitForIdleSync(); Thread.sleep(150)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(instrumentation.targetContext.filesDir, "card-blur").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    private fun contrast(bitmap: Bitmap): Int {
        val values = (2 until stripe * 2).map { x -> Color.red(bitmap.getPixel(x, bitmap.height / 2)) }
        return values.max() - values.min()
    }
    @Test fun nativeBackgroundHasActualGaussianBlurAndRestoresAfterClosing() {
        mount()
        val before = capture("before")
        compose.onNode(hasText("Synthetic preview") and hasClickAction()).performTouchInput { longClick() }
        compose.onNodeWithTag("steam-card-preview").assertIsDisplayed()
        compose.onNodeWithText("Native backdrop should blur").assertIsDisplayed()
        val after = capture("open")
        assertTrue("Native striped source must be visible before preview", contrast(before) > 200)
        assertTrue("Dim alone cannot satisfy the Gaussian blur contract", contrast(after) < contrast(before) / 4)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.steam_preview_close)).performClick()
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
        val restored = capture("restored")
        assertTrue("Closing must restore the native source", contrast(restored) > 200)
        println("CARD_GAUSSIAN_CONTRAST before=${contrast(before)} open=${contrast(after)} restored=${contrast(restored)}")
        listOf(before, after, restored).forEach { it.recycle() }
    }
    @Test fun changingPreviewScopeImmediatelyDropsTheOldCard() {
        mount()
        compose.onNode(hasText("Synthetic preview") and hasClickAction()).performTouchInput { longClick() }
        compose.onNodeWithTag("steam-card-preview").assertExists()
        compose.runOnIdle { scope.value = "second" }
        compose.onNodeWithTag("steam-card-preview").assertDoesNotExist()
    }
}
