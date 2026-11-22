package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.ui.SteamChatMessageBubble
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

/** Local synthetic image bytes only; no real Steam account or message transmission. */
class SteamChatImageMessageUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val own = "76561198000000001"
    private val partner = "76561198000000002"
    private val urls = (0..9).map { "https://community.fastly.steamstatic.com/steamcommunity/public/images/synthetic-tg-photo-$it.png" }
    private var longClicks = 0
    private var retries = 0
    private val files = mutableListOf<File>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun body(count: Int = 1, dimensions: Boolean = true) = (0 until count).joinToString("\n") {
        val width = if (it == 0) 360 else if (it == 9) 800 else 420
        val height = if (it == 0) 640 else if (it == 9) 450 else 600
        "[img src=${urls[it]}${if (dimensions) " width=$width height=$height" else ""}][/img]"
    }

    @Before fun seedLocalImages() {
        for ((index, url) in urls.withIndex()) {
            val width = if (index == 0) 360 else if (index == 9) 800 else 420
            val height = if (index == 0) 640 else if (index == 9) 450 else 600
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = if (index % 2 == 0) 0xff235e6c.toInt() else 0xffb86332.toInt()
            canvas.drawColor(paint.color)
            paint.color = 0xffefc379.toInt()
            canvas.drawCircle(width * .68f, height * .25f, width * .2f, paint)
            paint.color = 0xff15323c.toInt()
            val path = android.graphics.Path().apply {
                moveTo(0f, height * .8f); lineTo(width * .42f, height * .4f)
                lineTo(width.toFloat(), height * .88f); lineTo(width.toFloat(), height.toFloat())
                lineTo(0f, height.toFloat()); close()
            }
            canvas.drawPath(path, paint)
            paint.color = android.graphics.Color.WHITE; paint.textSize = 28f
            canvas.drawText("PHOTO ${index + 1}", 20f, height - 32f, paint)
            val chatFile = steamRemoteImageCacheFile(instrumentation.targetContext, url)
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            val viewerFile = File(instrumentation.targetContext.cacheDir, "steam_library_images/$key.img")
            for (file in listOf(chatFile, viewerFile).distinct()) {
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                files.add(file)
            }
            bitmap.recycle()
        }
    }
    @After fun removeOnlyFixtureBytes() { files.forEach { it.delete() } }

    private fun mount(raw: String = body(), outgoing: Boolean = false, caption: String = "",
        delivery: SteamChatDeliveryState = SteamChatDeliveryState.SENT, liquid: Boolean = false,
        reduced: Boolean = false, font: Float = 1f, width: Int = 360, reply: Boolean = false) {
        val message = SteamChatMessage(partner, if (outgoing) own else partner, 1700000000, 1,
            raw + if (caption.isEmpty()) "" else "\n$caption", deliveryState = delivery)
        compose.setContent { MonicaTheme(darkTheme = liquid) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalSteamDesignStyle provides if (liquid) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED,
                LocalReduceAnimations provides reduced, LocalDensity provides Density(density.density, font)) {
                Box(Modifier.fillMaxSize().background(if (liquid) Color(0xff172733) else Color(0xffe6edf0)).statusBarsPadding(),
                    contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.width(width.dp).padding(16.dp)) {
                        SteamChatMessageBubble(message,
                            if (reply) message.copy(body = "Synthetic reply", ordinal = 0) else null,
                            own, false, false, { retries++ }, { longClicks++ })
                    }
                }
            }
        } }
    }
    private fun photo(index: Int = 0) = compose.onNodeWithTag("steam-chat-photo-$index", useUnmergedTree = true)
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync(); Thread.sleep(150)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.filesDir, "tg-images/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    private fun closeViewer() {
        compose.onNodeWithContentDescription(label(R.string.steam_chat_image_viewer_close)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("steam-image-viewer").assertDoesNotExist()
    }

    @Test fun incomingPhotoUsesRealPortraitRatioAndOverlaysTime() {
        mount(); photo().assertIsDisplayed()
        val image = photo().fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("steam-chat-photo-metadata", true).fetchSemanticsNode().boundsInRoot
        assertEquals(360f / 640f, image.width / image.height, .01f)
        assertTrue(image.contains(metadata.topLeft) && image.contains(metadata.bottomRight))
        compose.onNodeWithTag("steam-chat-photo-caption-metadata", true).assertDoesNotExist()
        capture("m3-portrait")
    }

    @Test fun outgoingLandscapeKeepsWideImageAndWhiteDeliveryOverlay() {
        mount("[img src=${urls[9]} width=800 height=450][/img]", outgoing = true, liquid = true, font = 1.5f)
        val image = photo().fetchSemanticsNode().boundsInRoot
        assertEquals(800f / 450f, image.width / image.height, .01f)
        compose.onNodeWithContentDescription(label(R.string.steam_chat_sent)).assertExists()
        capture("glass-landscape-large")
    }

    @Test fun decodedSizeUpdatesPhotoWithoutMetadata() {
        mount(body(dimensions = false))
        compose.waitUntil(5000) {
            val image = photo().fetchSemanticsNode().boundsInRoot
            kotlin.math.abs(image.width / image.height - 360f / 640f) < .02f
        }
    }

    @Test fun responsiveSteamUrlWrappedImageDisplaysPhotoAndOpensViewer() {
        val source = urls[0]
        mount("[img src=$source thumbnail_src=${source}?imw=512&&ima=fit&impolicy=Letterbox&imcolor=%23000000&letterbox=false " +
            "srcset=\"${source}?imw=1024&&ima=fit&impolicy=Letterbox&imcolor=%23000000&letterbox=false 1024w\" " +
            "width=1260 height=2800][url=$source]$source[/url][/img]", liquid = true, reduced = true)
        photo().assertIsDisplayed()
        compose.onNodeWithTag("steam-chat-photo-metadata", true).assertExists()
        compose.onNodeWithTag("steam-chat-photo-caption-metadata", true).assertDoesNotExist()
        capture("wrapped-steam-photo")
        photo().performTouchInput { click() }
        compose.onNodeWithTag("steam-image-viewer").assertIsDisplayed()
        compose.onNodeWithText("1 / 1").assertExists()
        closeViewer()
    }

    @Test fun threePhotoAlbumMatchesPortraitMosaicAndOpensSelectedPage() {
        mount(body(3), liquid = true, reduced = true)
        val first = photo(0).fetchSemanticsNode().boundsInRoot
        val second = photo(1).fetchSemanticsNode().boundsInRoot
        val third = photo(2).fetchSemanticsNode().boundsInRoot
        assertTrue(first.height > second.height * 1.9f)
        assertTrue(second.left >= first.right && third.top >= second.bottom)
        capture("glass-album")
        photo(1).performTouchInput { click() }
        compose.onNodeWithTag("steam-image-viewer").assertIsDisplayed()
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithContentDescription(label(R.string.steam_chat_image_viewer_next)).performClick()
        compose.onNodeWithText("3 / 3").assertExists()
        closeViewer()
        photo(2).performTouchInput { click() }
        compose.onNodeWithText("3 / 3").assertExists()
        closeViewer()
    }

    @Test fun longPressUsesExistingMessageActionWithoutOpeningViewer() {
        mount(body(3))
        photo(2).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, longClicks) }
        compose.onNodeWithTag("steam-image-viewer").assertDoesNotExist()
    }

    @Test fun failedPhotoRetryDoesNotOpenImageOrLongPressAction() {
        mount(outgoing = true, delivery = SteamChatDeliveryState.FAILED_RETRYABLE)
        compose.onNodeWithContentDescription(label(R.string.steam_chat_retry_send)).performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, longClicks) }
        compose.onNodeWithTag("steam-image-viewer").assertDoesNotExist()
    }

    @Test fun captionLongPressUsesMessageActionAndPreservesRetry() {
        mount(caption = "Synthetic caption", outgoing = true, delivery = SteamChatDeliveryState.FAILED_RETRYABLE)
        compose.onNodeWithText("Synthetic caption").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, longClicks); assertEquals(0, retries) }
        compose.onNodeWithContentDescription(label(R.string.steam_chat_retry_send)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("steam-image-viewer").assertDoesNotExist()
    }

    @Test fun spoilerFirstClickRevealsAlbumAndSecondClickOpensImage() {
        mount("[spoiler]${body(3)}[/spoiler]", reduced = true)
        photo().assertDoesNotExist()
        compose.onNodeWithTag("steam-chat-photo-spoiler").performTouchInput { click() }
        photo().assertIsDisplayed()
        compose.onNodeWithTag("steam-image-viewer").assertDoesNotExist()
        photo(2).performTouchInput { click() }
        compose.onNodeWithText("3 / 3").assertExists(); closeViewer()
    }

    @Test fun captionPlacesTimeBelowPhotoAtLargeFontAndSmallWidth() {
        mount(body(2), outgoing = true, caption = "A synthetic caption that wraps across several lines.", font = 1.5f, width = 280)
        compose.onNodeWithText("A synthetic caption that wraps across several lines.").assertIsDisplayed()
        val grid = compose.onNodeWithTag("steam-chat-photo-grid", true).fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("steam-chat-photo-caption-metadata", true).fetchSemanticsNode().boundsInRoot
        assertTrue(metadata.top >= grid.bottom)
        compose.onNodeWithTag("steam-chat-photo-metadata", true).assertDoesNotExist()
        capture("m3-caption-large")
    }

    @Test fun albumContainsAllTenImagesAndReplyPreviewSurvives() {
        mount(body(10), reply = true)
        for (index in 0..9) photo(index).assertExists()
        compose.onNodeWithText("Synthetic reply").assertExists()
    }

    @Test fun reducedMotionViewerDoesNotCreateImageFlight() {
        mount(outgoing = true, reduced = true, liquid = true)
        photo().performTouchInput { click() }
        compose.onNodeWithTag("steam-image-flight").assertDoesNotExist()
        compose.onNodeWithTag("steam-image-viewer").assertIsDisplayed(); closeViewer()
    }

    @Test fun actualChatPhotoFliesFromAndReturnsToItsOwnTile() {
        mount()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("synthetic-tg-photo-0.png", true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        photo().performTouchInput { click() }; compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("steam-image-flight").assertExists()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription(label(R.string.steam_chat_image_viewer_close)).performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("steam-image-flight").assertExists()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("steam-image-viewer").assertDoesNotExist(); photo().assertIsDisplayed()
    }
}
