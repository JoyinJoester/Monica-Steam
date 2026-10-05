package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamDockColdSwitchPerformanceGuardTest {
    @Test
    fun progressiveBlurCachesGpuObjectsOutsideGraphicsLayerUpdates() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/ui/SteamDockProgressiveBlur.kt"
        ).readText()
        val modifierBody = source
            .substringAfter("internal fun Modifier.steamDockProgressiveBlur(")
            .substringBefore("private fun isRuntimeBlurProblematicDevice")
        val graphicsLayerBody = modifierBody
            .substringAfter("Modifier.graphicsLayer {")
            .substringBefore("\n        }")

        assertTrue(modifierBody.contains("remember { RuntimeShader(STEAM_DOCK_BLUR_SHADER) }"))
        assertTrue(modifierBody.contains("remember(shader)"))
        assertFalse(graphicsLayerBody.contains("RuntimeShader("))
        assertFalse(graphicsLayerBody.contains("createRuntimeShaderEffect"))
    }

    @Test
    fun dockRootPagesHaveDistinctAnimatedContentKeys() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val pageHost = source
            .substringAfter("label = \"monica_steam_page_transition\"")
            .substringBefore(") { page ->")
        assertTrue(pageHost.contains("contentKey = { page -> page }"))
        assertFalse(source.contains("MONICA_STEAM_DOCK_CONTENT_KEY"))
        assertTrue(pageHost.contains("steamDockPageTransition("))
        assertTrue(pageHost.contains("easyNotesScreenEnter(settings.reduceAnimations)"))
        assertTrue(pageHost.contains("easyNotesScreenExit(settings.reduceAnimations)"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
