package takagi.ru.monica.steam.friends.chat.ui

import java.io.File
import androidx.compose.ui.graphics.FilterQuality
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImageMode
import takagi.ru.monica.steam.friends.chat.richmedia.ui.staticSteamImageFilterQuality

class SteamChatEmoticonSizingTest {
    @Test
    fun remoteSteamImagesUseNearestNeighborForPixelEmoticons() {
        assertEquals(
            FilterQuality.None,
            staticSteamImageFilterQuality(SteamChatRemoteImageMode.EMOTICON)
        )
        assertEquals(
            FilterQuality.None,
            staticSteamImageFilterQuality(SteamChatRemoteImageMode.STICKER)
        )
        assertEquals(
            FilterQuality.High,
            staticSteamImageFilterQuality(SteamChatRemoteImageMode.CONTENT)
        )
        assertEquals(
            FilterQuality.High,
            staticSteamImageFilterQuality(SteamChatRemoteImageMode.ARTWORK)
        )
    }

    @Test
    fun remoteSteamImagesKeepAnimationAndStickerSizingPolicies() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRemoteImage.kt"
        ).readText()
        val decoder = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamRemoteImage.kt"
        ).readText()

        assertTrue(source.contains("ImageView.ScaleType.FIT_CENTER"))
        assertTrue(source.contains("ImageView.ScaleType.CENTER_CROP"))
        assertTrue(source.contains("ImageView.ScaleType.CENTER_INSIDE"))
        assertTrue(source.contains("ContentScale.Crop"))
        assertTrue(source.contains("ContentScale.Inside"))
        assertTrue(source.contains("setAutoPlay(true)"))
        assertTrue(source.contains("setVisible(true, false)"))
        assertTrue(source.contains("SteamAnimatedImageView"))
        assertTrue(source.contains("filterQuality = FilterQuality.None"))
        assertTrue(source.contains("drawImage("))
        assertTrue(decoder.contains("inScaled = false"))
        assertTrue(decoder.contains("BitmapFactory.decodeByteArray"))
        assertTrue(!decoder.contains("DENSITY_DEFAULT"))
    }

    @Test
    fun emoticonPickerUsesReadableCellsAndMinimalInnerPadding() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRichMediaPicker.kt"
        ).readText()

        assertTrue(source.contains("GridCells.Adaptive(56.dp)"))
        assertTrue(source.contains("Modifier.padding(1.dp)"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
