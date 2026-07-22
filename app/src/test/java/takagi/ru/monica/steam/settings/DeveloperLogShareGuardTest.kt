package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperLogShareGuardTest {

    @Test
    fun manifestRegistersApplicationScopedFileProviderForLogFiles() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:name=\"androidx.core.content.FileProvider\""))
        assertTrue(manifest.contains("android:authorities=\"\${applicationId}.fileprovider\""))
        assertTrue(manifest.contains("android:grantUriPermissions=\"true\""))
        assertTrue(manifest.contains("android:name=\"android.support.FILE_PROVIDER_PATHS\""))
        assertTrue(manifest.contains("android:resource=\"@xml/file_paths\""))
    }

    @Test
    fun developerLogsKeepFileSharingAndExposeClipboardCopy() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/DeveloperSettingsScreen.kt"
        ).readText()

        assertTrue(source.contains("FileProvider.getUriForFile"))
        assertTrue(source.contains("putExtra(Intent.EXTRA_STREAM, uri)"))
        assertTrue(source.contains("ClipboardManager"))
        assertTrue(source.contains("setPrimaryClip"))
        assertTrue(source.contains("R.string.developer_copy_logs"))
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
