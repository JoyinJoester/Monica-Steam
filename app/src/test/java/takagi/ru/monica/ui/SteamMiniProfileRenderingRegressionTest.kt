package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamMiniProfileRenderingRegressionTest {
    @Test
    fun immersiveCardRequiresMediaToBeActuallyAvailable() {
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/TotpCodeCard.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val layer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/ui/SteamMiniProfileBackgroundLayer.kt"
        ).readText()

        assertTrue(card.contains("immersiveBackgroundVisible: Boolean"))
        assertTrue(card.contains("backgroundContent != null && immersiveBackgroundVisible"))
        assertTrue(screen.contains("onAvailabilityChanged ="))
        assertTrue(screen.contains("immersiveBackgroundVisible ="))
        assertTrue(layer.contains("onAvailabilityChanged: (Boolean) -> Unit"))
    }

    @Test
    fun immersiveForegroundUsesBrightColorsOverTheDarkScrim() {
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/TotpCodeCard.kt"
        ).readText()

        assertFalse(
            card.contains("immersiveAccentColor = MaterialTheme.colorScheme.primaryContainer")
        )
        assertFalse(
            card.contains("immersiveTertiaryColor = MaterialTheme.colorScheme.tertiaryContainer")
        )
        assertTrue(card.contains("immersiveAccentColor = Color.White.copy"))
        assertTrue(card.contains("immersiveTertiaryColor = Color.White.copy"))
    }

    @Test
    fun videoAndPosterUseTheSameNormalizedCenterCropModel() {
        val layer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/ui/SteamMiniProfileBackgroundLayer.kt"
        ).readText()

        assertTrue(layer.contains("calculateSteamMiniProfileCenterCrop("))
        assertTrue(layer.contains("transform.scaleX"))
        assertTrue(layer.contains("transform.scaleY"))
        assertFalse(layer.contains("setScale(scale, scale)"))
    }

    @Test
    fun listScrollingDoesNotDisableDynamicPlayback() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val layer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/ui/SteamMiniProfileBackgroundLayer.kt"
        ).readText()

        assertFalse(screen.contains("!lazyListState.isScrollInProgress"))
        assertTrue(screen.contains("allowMotion = !appSettings.reduceAnimations"))
        assertTrue(layer.contains("onRelease = SteamMiniProfileTextureView::release"))
        assertTrue(layer.contains("awaitCancellation()"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
