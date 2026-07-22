package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamDeveloperSettingsAccessGuardTest {

    @Test
    fun sharedSettingsRequireDeveloperAuthenticationByDefault() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()

        assertTrue(source.contains("requireDeveloperAuthentication: Boolean = true"))
        assertTrue(source.contains("if (!requireDeveloperAuthentication)"))
        assertTrue(source.contains("return@developerSettingsClick"))
    }

    @Test
    fun steamAdapterExplicitlyDisablesTheUnavailableAuthenticationGate() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()

        assertTrue(source.contains("requireDeveloperAuthentication = false"))
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
