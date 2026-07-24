package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamMiniProfileBackgroundGuardTest {
    @Test
    fun featureIsOffByDefaultAndWiredThroughExtensions() {
        val appSettings = projectFile(
            "app/src/main/java/takagi/ru/monica/data/AppSettings.kt"
        ).readText()
        val settingsManager = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/SettingsManager.kt"
        ).readText()
        val extensions = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/ExtensionsScreen.kt"
        ).readText()

        assertTrue(appSettings.contains("steamMiniProfileBackgroundEnabled: Boolean = false"))
        assertTrue(settingsManager.contains("STEAM_MINI_PROFILE_BACKGROUND_ENABLED_KEY"))
        assertTrue(
            settingsManager.contains(
                "preferences[STEAM_MINI_PROFILE_BACKGROUND_ENABLED_KEY] ?: false"
            )
        )
        assertTrue(extensions.contains("steam_mini_profile_background_title"))
        assertTrue(extensions.contains("onSteamMiniProfileBackgroundEnabledChange"))
    }

    @Test
    fun immersiveBackgroundRemainsOptInAndSteamOnly() {
        val totpCard = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/TotpCodeCard.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(
            totpCard.contains(
                "backgroundContent: (@Composable BoxScope.() -> Unit)? = null"
            )
        )
        assertEquals(
            2,
            Regex("backgroundContent\\s*=").findAll(steamScreen).count()
        )
        assertTrue(steamScreen.contains("appSettings.steamMiniProfileBackgroundEnabled"))
        assertTrue(steamScreen.contains("account.hasRealSteamId"))
        assertTrue(steamScreen.contains("SteamMiniProfileBackgroundLayer("))
    }

    @Test
    fun dynamicPlaybackIsBoundedSilentAndLifecycleAware() {
        val layer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/ui/SteamMiniProfileBackgroundLayer.kt"
        ).readText()

        assertTrue(layer.contains("Semaphore(2)"))
        assertTrue(layer.contains("PowerManager.ACTION_POWER_SAVE_MODE_CHANGED"))
        assertTrue(layer.contains("Lifecycle.State.RESUMED"))
        assertTrue(layer.contains("MediaPlayer"))
        assertTrue(layer.contains("setVolume(0f, 0f)"))
        assertTrue(layer.contains("private const val MAX_BYTES = 8 * 1024 * 1024"))
    }

    @Test
    fun mediaPlayerTeardownDoesNotSynchronouslyStopOrResetOnTheUiThread() {
        val layer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/ui/SteamMiniProfileBackgroundLayer.kt"
        ).readText()
        val releasePlayer = layer
            .substringAfter("private fun releasePlayer()")
            .substringBefore("\n    }\n}")

        assertFalse(releasePlayer.contains("current.stop()"))
        assertFalse(releasePlayer.contains("current.reset()"))
        assertTrue(releasePlayer.contains("current.release()"))
    }

    @Test
    fun diskCacheKeepsItsHardLimits() {
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/SteamMiniProfileBackground.kt"
        ).readText()

        assertTrue(
            cache.contains(
                "DEFAULT_MAX_SINGLE_VIDEO_BYTES = 12L * 1024L * 1024L"
            )
        )
        assertTrue(
            cache.contains(
                "DEFAULT_MAX_CACHE_BYTES = 64L * 1024L * 1024L"
            )
        )
        assertTrue(cache.contains("POSTER_MAX_WIDTH = 768"))
        assertTrue(cache.contains("Semaphore(2)"))
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
