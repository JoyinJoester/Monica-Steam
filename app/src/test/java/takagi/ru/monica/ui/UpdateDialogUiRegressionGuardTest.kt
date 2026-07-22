package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDialogUiRegressionGuardTest {

    @Test
    fun releaseNotesScrollWhileDownloadProgressStaysInDedicatedSection() {
        val source = settingsSource()

        assertTrue(source.contains("updateDialogScrollState"))
        assertTrue(source.contains("verticalScroll(updateDialogScrollState)"))
        assertTrue(source.contains("weight(1f, fill = false)"))
        assertTrue(source.contains("UpdateDownloadProgressSection("))
        assertTrue(source.contains("heightIn(max = updateDialogContentMaxHeight)"))
    }

    private fun settingsSource(): String = projectFile(
        "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
    ).readText()

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
