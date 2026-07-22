package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamTokenProgressPullLayoutGuardTest {

    @Test
    fun steamProgressBarStaysOutsideThePullOffsetContainer() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val codeContent = source.substringAfter("private fun SteamCodeContent(")
            .substringBefore("internal fun reconcileSteamAccountsAfterSourceUpdate(")
        val progressBarIndex = codeContent.indexOf("UnifiedProgressBar(")
        val pullOffsetIndex = codeContent.indexOf(
            ".offset { IntOffset(0, pullToSearch.currentOffset.toInt()) }"
        )

        assertTrue(progressBarIndex >= 0)
        assertTrue(pullOffsetIndex > progressBarIndex)
        assertFalse(
            codeContent.substringBefore("UnifiedProgressBar(")
                .contains("pullToSearch.currentOffset")
        )
    }

    @Test
    fun pullOffsetStillWrapsBothEmptyAndPopulatedTokenContent() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val codeContent = source.substringAfter("private fun SteamCodeContent(")
            .substringBefore("internal fun reconcileSteamAccountsAfterSourceUpdate(")
        val pullContent = codeContent.substringAfter(
            ".offset { IntOffset(0, pullToSearch.currentOffset.toInt()) }"
        )

        assertTrue(pullContent.contains("if (localAccounts.isEmpty())"))
        assertTrue(pullContent.contains("pullToSearch.onVerticalDrag(dragAmount)"))
        assertTrue(pullContent.contains(".nestedScroll(pullToSearch.nestedScrollConnection)"))
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
