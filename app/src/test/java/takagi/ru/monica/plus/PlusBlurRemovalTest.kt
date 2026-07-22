package takagi.ru.monica.plus

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class PlusBlurRemovalTest {
    @Test
    fun productionSourcesDoNotExposeOrImplementPlusBlur() {
        val forbiddenTokens = listOf(
            "plusBlur",
            "PlusBlur",
            "MonicaPlusBlur",
            "MonicaBlurMode",
            "MonicaBlurIntensity",
            "plus_blur",
            "plus_feature_blur_effects",
            "dev.chrisbanes.haze",
            "extensions_visual_effects"
        )
        val sourceFiles = projectFile("app/src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .toList()
        val buildFiles = listOf(
            projectFile("app/build.gradle"),
            projectFile("gradle/libs.versions.toml")
        )

        (sourceFiles + buildFiles).forEach { file ->
            val text = file.readText()
            forbiddenTokens.forEach { token ->
                assertFalse("${file.path} still contains $token", text.contains(token))
            }
        }
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
