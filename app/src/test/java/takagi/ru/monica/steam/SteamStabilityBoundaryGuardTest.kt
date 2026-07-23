package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryUiState
import takagi.ru.monica.steam.library.steamLibraryAchievementRequestIsCurrent
import takagi.ru.monica.steam.library.steamLibraryFailureReason

class SteamStabilityBoundaryGuardTest {
    @Test
    fun backgroundEntryPointsContainProcessCrashBoundaries() {
        val receiver = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/alerts/SteamAlertReceiver.kt"
        ).readText()
        val widgets = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/quickaccess/SteamWidgetProviders.kt"
        ).readText()
        val scanner = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/scanner/ui/SteamQrScannerScreen.kt"
        ).readText()

        assertTrue(receiver.contains("catch (error: Throwable)"))
        assertTrue(receiver.contains("pendingResult.finish()"))
        assertTrue(widgets.contains("runCatching"))
        assertTrue(widgets.contains("pendingResult?.finish()"))
        assertTrue(scanner.contains("catch (cancelled: CancellationException)"))
    }

    @Test
    fun libraryAchievementResultIsScopedToAccountGameAndGeneration() {
        val state = SteamLibraryUiState(
            selectedAccountId = 7L,
            selectedGame = takagi.ru.monica.steam.library.SteamGame(
                appId = 570,
                name = "Dota 2",
                playtimeForeverMinutes = 0,
                playtimeRecentMinutes = 0
            )
        )

        assertTrue(steamLibraryAchievementRequestIsCurrent(state, 7L, 570, 4L, 4L))
        assertFalse(steamLibraryAchievementRequestIsCurrent(state, 8L, 570, 4L, 4L))
        assertFalse(steamLibraryAchievementRequestIsCurrent(state, 7L, 730, 4L, 4L))
        assertFalse(steamLibraryAchievementRequestIsCurrent(state, 7L, 570, 3L, 4L))
    }

    @Test
    fun unexpectedLibraryExceptionsBecomeRecoverableFailureReasons() {
        assertEquals(
            SteamLibraryFailureReason.SESSION_REQUIRED,
            steamLibraryFailureReason(IllegalArgumentException("missing token"))
        )
        assertEquals(
            SteamLibraryFailureReason.NETWORK,
            steamLibraryFailureReason(IllegalStateException("socket closed"))
        )
    }

    @Test
    fun nullableUiAssertionsAreNotUsedAtImageBoundaries() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/SteamStoreScreen.kt"
        ).readText()
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamLibraryScreen.kt"
        ).readText()

        assertFalse(store.contains("image!!"))
        assertFalse(library.contains("requireNotNull(bitmap)"))
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
