package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToSearchStateGuardTest {

    @Test
    fun pullToSearchHasThresholdHapticNestedScrollAndEmptyStateCallbacks() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/common/pull/PullToSearchState.kt"
        ).readText()

        assertTrue(source.contains("currentOffset >= searchTriggerDistance"))
        assertTrue(source.contains("performLightClick()"))
        assertTrue(source.contains("nestedScrollConnection"))
        assertTrue(source.contains("onVerticalDrag"))
        assertTrue(source.contains("collapsePullOffsetSmoothly"))
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
