package takagi.ru.monica.steam.library.filter.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLibraryFilterUiGuardTest {
    @Test
    fun libraryUsesGroupedSheetAndAccountScopedPersistence() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val sheet = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/filter/ui/SteamLibraryFilterSheet.kt"
        ).readText()
        val preferences = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/filter/data/SteamLibraryFilterPreferences.kt"
        ).readText()

        assertTrue(screen.contains("SteamLibraryFilterEntry("))
        assertTrue(screen.contains("SteamLibraryFilterSheet("))
        assertTrue(screen.contains("filterPreferences.load(state.selectedAccountId)"))
        assertTrue(screen.contains("filterPreferences.save(state.selectedAccountId, applied)"))
        assertTrue(sheet.contains("SteamLibraryOwnershipFilter.entries"))
        assertTrue(sheet.contains("SteamLibraryPlayStatusFilter.entries"))
        assertTrue(sheet.contains("SteamLibraryAchievementStatusFilter.entries"))
        assertTrue(sheet.contains("SteamLibraryPlaytimeFilter.entries"))
        assertTrue(sheet.contains("SteamLibrarySortOrder.entries"))
        assertTrue(sheet.contains("heightIn(min = 48.dp)"))
        assertTrue(preferences.contains("account_${'$'}{accountId ?: 0L}_"))
    }

    @Test
    fun filterEntryAndSheetStayCompactAndUseStableInteractionBoundary() {
        val sheet = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/filter/ui/SteamLibraryFilterSheet.kt"
        ).readText()

        // The entry must remain a compact control rather than a full-width hero card.
        assertTrue(sheet.contains("wrapContentWidth"))
        assertTrue(sheet.contains("heightIn(min = 48.dp)"))

        // Reuse Monica's stable sheet boundary: it disables partial expansion and
        // removes overscroll/gesture competition from nested filter content.
        assertTrue(sheet.contains("MonicaModalBottomSheet("))
        assertTrue(sheet.contains("fillMaxHeight(0.86f)"))

        // Preview count should be memoized once per pending selection, instead of
        // running a full sort separately from the header and apply button.
        assertTrue(sheet.contains("remember(pending, totalCount)"))
        assertTrue(!sheet.contains("filteredCount(pending)"))

        // Large nested cards were the source of the oversized visual hierarchy.
        assertTrue(!sheet.contains("surfaceContainerHigh"))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
