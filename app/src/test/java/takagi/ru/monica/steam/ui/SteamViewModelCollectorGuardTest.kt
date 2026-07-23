package takagi.ru.monica.steam.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamViewModelCollectorGuardTest {
    @Test
    fun recentSecurityEventsHaveOneViewModelScopedCollector() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()
        val collectorCount = Regex("securityEventRepository.*?observeRecent\\(\\)\\.collect", RegexOption.DOT_MATCHES_ALL)
            .findAll(source)
            .count()
        val organizationBlock = source
            .substringAfter("fun updateOrganization(")
            .substringBefore("fun clearMessage(")

        assertEquals(1, collectorCount)
        assertFalse(organizationBlock.contains("observeRecent().collect"))
        assertTrue(source.substringBefore("fun selectStorageSource(").contains("observeRecent().collect"))
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
