package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFamilyLibraryUiGuardTest {
    @Test
    fun libraryUsesTextAndIconBadgeAndOffersFamilyFilter() {
        val filters = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryFilters.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()

        assertTrue(filters.contains("FAMILY_SHARED"))
        assertTrue(filters.contains("searched.filter(SteamGame::isFamilyShared)"))
        assertTrue(screen.contains("private fun SteamFamilySharedBadge()"))
        assertTrue(screen.contains("Icons.Default.Groups"))
        assertTrue(screen.contains("R.string.steam_library_family_shared"))
        assertTrue(screen.contains("MaterialTheme.colorScheme.tertiaryContainer"))
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
