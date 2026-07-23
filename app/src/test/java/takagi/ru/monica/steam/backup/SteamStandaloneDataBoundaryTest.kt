package takagi.ru.monica.steam.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStandaloneDataBoundaryTest {
    @Test
    fun standaloneSettingsExposeFullThemeSelection() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val buildScript = projectFile("app/build.gradle").readText()

        assertTrue(activity.contains("SettingsViewModel(settingsManager)"))
        assertTrue(settings.contains("ColorSchemeSelectionScreen("))
        assertTrue(settings.contains("CustomColorSettingsScreen("))
        assertTrue(settings.contains("R.string.color_scheme"))
        assertTrue(buildScript.contains("implementation libs.material"))
        assertFalse(buildScript.contains("compileOnly libs.material"))
    }

    @Test
    fun standaloneWebDavOnlyHandlesMaFileZip() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/backup/ui/SteamBackupScreen.kt"
        ).readText()
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/backup/SteamMaFileWebDavService.kt"
        ).readText()

        assertTrue(screen.contains("SteamMaFileZipCodec"))
        assertTrue(screen.contains("SteamMaFileWebDavService"))
        assertTrue(service.contains("REMOTE_DIRECTORY = \"steam/mafiles\""))
        assertTrue(service.contains("application/zip"))
        assertFalse(screen.contains("createAndUploadBackup"))
        assertFalse(screen.contains("uploadBackup("))
        assertFalse(screen.contains("downloadBackup("))
        assertFalse(screen.contains("BackupPreferences"))
        assertFalse(screen.contains("Mdbx"))
        assertFalse(service.contains("WebDavHelper"))
    }

    @Test
    fun standaloneMdbxRouteSupportsLocalAndWebDav() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val manager = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(activity.contains("localOnly = false"))
        assertTrue(activity.contains("MdbxLocalCreateScreen("))
        assertTrue(activity.contains("MdbxLocalOpenScreen("))
        assertTrue(activity.contains("MdbxWebDavCreateScreen("))
        assertTrue(activity.contains("MdbxWebDavOpenScreen("))
        assertFalse(activity.contains("MdbxOneDriveCreateScreen("))
        assertFalse(activity.contains("MdbxOneDriveOpenScreen("))
        assertTrue(activity.contains("oneDriveEnabled = false"))
        assertTrue(manager.contains("showWebDavSource = !localOnly"))
        assertTrue(manager.contains("showOneDriveSource = !localOnly && oneDriveEnabled"))
        assertTrue(steamScreen.contains("MdbxSourceType.LOCAL_INTERNAL"))
        assertTrue(steamScreen.contains("MdbxSourceType.LOCAL_EXTERNAL"))
        assertTrue(steamScreen.contains("MdbxSourceType.REMOTE_WEBDAV"))
        assertFalse(steamScreen.contains("MdbxSourceType.REMOTE_ONEDRIVE"))
    }

    @Test
    fun standaloneMdbxShipsItsRuntimeParser() {
        val buildScript = projectFile("app/build.gradle").readText()

        assertTrue(buildScript.contains("implementation 'app.keemobile:kotpass:0.10.0'"))
        assertFalse(buildScript.contains("compileOnly 'app.keemobile:kotpass:0.10.0'"))
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
