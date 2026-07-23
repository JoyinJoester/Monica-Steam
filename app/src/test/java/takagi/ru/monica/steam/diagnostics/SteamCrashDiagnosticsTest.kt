package takagi.ru.monica.steam.diagnostics

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCrashDiagnosticsTest {
    @Test
    fun launcherInstallsPersistentCrashHandlerBeforeActivityStartup() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/diagnostics/SteamCrashDiagnostics.kt"
        )

        assertTrue(source.exists())
        val diagnostics = source.readText()
        val installIndex = activity.indexOf("SteamCrashDiagnostics.install(")
        val superIndex = activity.indexOf("super.onCreate(savedInstanceState)")
        assertTrue(installIndex >= 0)
        assertTrue(superIndex >= 0)
        assertTrue(installIndex < superIndex)
        assertTrue(diagnostics.contains("setDefaultUncaughtExceptionHandler"))
        assertTrue(diagnostics.contains("previousHandler?.uncaughtException"))
        assertTrue(diagnostics.contains("readLastCrash("))
        assertTrue(diagnostics.contains("MAX_CRASH_BYTES"))
        assertTrue(diagnostics.contains("renameTo("))
    }

    @Test
    fun exportsReadPersistedCrashAndExplicitLogcatBuffers() {
        val developer = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/DeveloperSettingsScreen.kt"
        ).readText()
        val steam = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/diagnostics/SteamSupportLogExporter.kt"
        ).readText()

        assertTrue(developer.contains("SteamCrashDiagnostics.readLastCrash("))
        assertTrue(developer.contains("SteamCrashDiagnostics.clear("))
        assertTrue(steam.contains("SteamCrashDiagnostics.readLastCrash("))
        listOf("\"crash\"", "\"main\"", "\"system\"").forEach { buffer ->
            assertTrue(developer.contains("-b"))
            assertTrue(developer.contains(buffer))
            assertTrue(steam.contains("-b"))
            assertTrue(steam.contains(buffer))
        }
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
