package takagi.ru.monica.webdav

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRuntimeDependencyTest {
    @Test
    fun sardineImplementationIsPackagedAtRuntime() {
        val buildFile = projectFile("app/build.gradle").readText()
        val dependency = "com.github.thegrizzlylabs:sardine-android:0.8"

        assertTrue(
            "WebDAV implementation must be present in installed builds.",
            buildFile.contains("implementation '$dependency'")
        )
        assertFalse(
            "compileOnly reproduces OkHttpSardine NoClassDefFoundError at runtime.",
            buildFile.contains("compileOnly '$dependency'")
        )
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
