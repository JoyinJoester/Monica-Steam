package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the root/back-stack contract copied from Monica Android. */
class MonicaSteamBackBehaviorTest {
    @Test
    fun dockPagesUseMonicaDoubleBackExitInsteadOfJumpingHome() {
        val source = activitySource()

        assertFalse(source.contains("BackHandler(enabled = currentPage != homePage)"))
        assertTrue(source.contains("var backPressedOnce by remember"))
        assertTrue(source.contains("BackHandler(enabled = true)"))
        assertTrue(source.contains("if (pageHistory.isNotEmpty())"))
        assertTrue(source.contains("if (currentPage.isDockPage())"))
        assertTrue(source.contains("R.string.press_back_again_to_exit"))
        assertTrue(source.contains("Toast.LENGTH_SHORT"))
        assertTrue(source.contains("delay(MONICA_BACK_EXIT_TIMEOUT_MS)"))
        assertTrue(source.contains("MONICA_BACK_EXIT_TIMEOUT_MS = 2_000L"))
        assertTrue(source.contains("this@MonicaSteamActivity.finish()"))
    }

    @Test
    fun secondaryPagesStillNavigateUpThroughSavedHistory() {
        val source = activitySource()

        assertTrue(source.contains("val parent = pageHistory.lastOrNull()"))
        assertTrue(source.contains("pageHistory = pageHistory.dropLast(1)"))
        assertTrue(source.contains("currentPage = parent"))
        assertTrue(source.contains("navigateBack()"))
    }

    private fun activitySource(): String = projectFile(
        "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
    ).readText()

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
