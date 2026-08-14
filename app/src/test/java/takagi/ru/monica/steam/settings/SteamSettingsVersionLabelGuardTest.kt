package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSettingsVersionLabelGuardTest {
    @Test
    fun settingsVersionFollowsBuildConfigInsteadOfAStaleLiteral() {
        val gradle = projectFile("app/build.gradle").readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()
        val stringFiles = listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-zh/strings.xml",
            "app/src/main/res/values-ja/strings.xml",
            "app/src/main/res/values-ru/strings.xml",
            "app/src/main/res/values-vi/strings.xml"
        ).map(::projectFile)

        assertTrue(gradle.contains("def appVersionCode = 18"))
        assertTrue(gradle.contains('"' + "1.0.306" + '"'))
        assertTrue(screen.contains("val settingsVersionNumber = context.getString("))
        assertTrue(
            screen.contains(
                "BuildConfig.VERSION_NAME.ifBlank { BuildConfig.FULL_VERSION_NAME }"
            )
        )
        assertTrue(screen.contains("subtitle = settingsVersionNumber"))

        stringFiles.forEach { file ->
            val strings = file.readText()
            assertTrue(
                strings.contains(
                    "<string name=\"settings_version_number\">V%1\u0024s</string>"
                )
            )
            assertFalse(strings.contains("V1.0.302"))
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
