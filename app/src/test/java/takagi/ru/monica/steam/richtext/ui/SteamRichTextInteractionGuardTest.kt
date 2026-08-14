package takagi.ru.monica.steam.richtext.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamRichTextInteractionGuardTest {
    @Test
    fun spoilerTextIsRevealedOnlyWhileItsRenderedRangeIsPressed() {
        val richText = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/richtext/ui/SteamRichText.kt"
        ).readText()
        val reviews = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreReviewList.kt"
        ).readText()

        assertTrue(richText.contains("awaitEachGesture"))
        assertTrue(richText.contains("getOffsetForPosition(down.position)"))
        assertTrue(richText.contains("pressedSpoiler = spoiler"))
        assertTrue(richText.contains("pressedSpoiler = null"))
        assertTrue(richText.contains("HapticFeedbackType.LongPress"))
        assertTrue(richText.contains("revealedSpoilers = setOfNotNull(pressedSpoiler)"))
        assertTrue(reviews.contains("SteamRichText("))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
