package takagi.ru.monica.steam.friends.chat.richmedia.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatMediaViewerIntegrationTest {
    @Test
    fun trustedChatImagesOpenTheSharedInAppViewer() {
        val attachment = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatImageMessage.kt"
        ).readText()
        val viewer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamFullscreenImageViewer.kt"
        ).readText()
        val controls = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamFullscreenImageViewerControls.kt"
        ).readText()
        val remoteImage = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRemoteImage.kt"
        ).readText()

        assertTrue(attachment.contains("SteamImageDownloadPolicy.isAllowedUrl(source.url)"))
        assertTrue(attachment.contains("SteamFullscreenImageViewer("))
        assertTrue(attachment.contains("selectedImage = index"))
        assertTrue(attachment.contains("images = sources.map { it.url }"))
        assertFalse(attachment.contains("SteamStoreScreenshotViewer"))
        assertTrue(viewer.contains("HorizontalPager("))
        assertTrue(viewer.contains("SteamImageDownloader("))
        assertTrue(controls.contains("Icons.Default.Download"))
        assertTrue(remoteImage.contains("SteamRemoteImageCache.isAllowedSteamImageUrl"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
