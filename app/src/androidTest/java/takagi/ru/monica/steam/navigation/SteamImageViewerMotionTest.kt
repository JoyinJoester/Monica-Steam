package takagi.ru.monica.steam.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.foundation.media.steamImageThumbnail
import takagi.ru.monica.steam.store.ui.gallery.SteamStoreScreenshotViewer
import takagi.ru.monica.ui.LocalReduceAnimations

class SteamImageViewerMotionTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val urls = listOf("https://community.fastly.steamstatic.com/steamcommunity/public/images/synthetic-flight-0.png",
        "https://community.fastly.steamstatic.com/steamcommunity/public/images/synthetic-flight-1.png")
    private val opened = mutableStateOf(false)
    private var closes = 0
    private val showThumbnail = mutableStateOf(true)
    private fun mount(reduced: Boolean = false) {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(60, 160, 220))
            val canvas = android.graphics.Canvas(this)
            canvas.drawRect(0f, 0f, 160f, 180f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 170, 50) })
        }
        for (url in urls) {
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            val file = File(instrumentation.targetContext.cacheDir, "steam_library_images/$key.img")
            file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val image = bitmap.asImageBitmap()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalReduceAnimations provides reduced) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                if (showThumbnail.value) Image(image, "Synthetic thumbnail", Modifier.size(200.dp, 112.dp).testTag("thumbnail")
                    .steamImageThumbnail(urls[0], image, ContentScale.Crop)
                    .clickable { opened.value = true }, contentScale = ContentScale.Crop)
            }
            if (opened.value) SteamStoreScreenshotViewer("Synthetic gallery", urls, 0) { opened.value = false; closes++ }
        } } }
    }
    private fun open() { compose.onNodeWithTag("thumbnail").performTouchInput { click() } }
    private fun back() {
        // Wait for the actual Dialog to own window focus; do not let Espresso select the Activity root.
        compose.onNodeWithTag("steam-image-viewer").assertExists()
        compose.waitUntil(5000) {
            instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
                try { root.packageName == instrumentation.targetContext.packageName && !compose.activity.hasWindowFocus() }
                finally { root.recycle() }
            } == true
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
    private fun frame(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(120) // Observe the committed Dialog surface while the animation clock is frozen.
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("No device frame")
        val file = File(instrumentation.targetContext.filesDir, "image-motion/$name.png")
        file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun actualThumbnailFliesIntoViewerAndReturnsBeforeDismissal() {
        mount(); compose.mainClock.autoAdvance = false
        frame("before"); open(); compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("steam-image-flight").assertExists(); frame("enter-64")
        compose.mainClock.advanceTimeBy(112); frame("enter-176")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("steam-image-flight").assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.steam_store_screenshot_next)).assertIsDisplayed()
        back(); compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("steam-image-viewer").assertExists()
        compose.onNodeWithTag("steam-image-flight").assertExists(); assertEquals(0, closes); frame("exit-80")
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("steam-image-viewer").assertDoesNotExist()
        compose.onNodeWithTag("thumbnail").assertIsDisplayed(); assertEquals(1, closes); frame("restored")
    }
    @Test fun dismissDuringEntryRemovesOnlyTheViewerAndAllowsReopening() {
        mount(); compose.mainClock.autoAdvance = false
        open(); compose.mainClock.advanceTimeBy(64); back(); compose.mainClock.advanceTimeBy(350)
        assertEquals(1, closes); compose.onNodeWithTag("thumbnail").assertIsDisplayed()
        open(); compose.mainClock.advanceTimeBy(400); back(); compose.mainClock.advanceTimeBy(350)
        assertEquals(2, closes)
    }
    @Test fun unmountedThumbnailFallsBackToFadeAndViewerDisposalRestoresAnchor() {
        mount(); open()
        compose.runOnIdle { showThumbnail.value = false }
        compose.onNodeWithTag("thumbnail").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        back(); compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("steam-image-flight").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(350); assertEquals(1, closes)
        compose.runOnIdle { showThumbnail.value = true }
        compose.mainClock.autoAdvance = true
        open(); compose.onNodeWithTag("steam-image-viewer").assertIsDisplayed()
        compose.runOnIdle { opened.value = false }
        compose.onNodeWithTag("thumbnail").assertIsDisplayed()
        open(); back(); compose.waitForIdle(); assertEquals(2, closes)
    }
    @Test fun differentGalleryPageFadesInsteadOfReturningToTheWrongThumbnail() {
        mount(); open()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.steam_store_screenshot_next)).performClick()
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        back(); compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("steam-image-flight").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(350); assertEquals(1, closes)
    }
    @Test fun reducedMotionOpensAndClosesWithoutImageFlight() {
        mount(reduced = true); open()
        compose.onNodeWithTag("steam-image-flight").assertDoesNotExist()
        compose.onNodeWithTag("steam-image-viewer").assertIsDisplayed()
        back(); compose.waitForIdle(); assertEquals(1, closes)
    }
}
