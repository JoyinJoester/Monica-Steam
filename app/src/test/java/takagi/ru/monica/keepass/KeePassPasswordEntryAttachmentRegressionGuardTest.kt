package takagi.ru.monica.keepass

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeePassPasswordEntryAttachmentRegressionGuardTest {

    @Test
    fun passwordUpdatesReuseExistingEntrySoAttachmentsSurviveSave() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/KeePassKdbxService.kt"
        ).readText()

        val updateBody = source.substringAfter("private fun updateEntry(")
            .substringBefore("private fun updateSecureItemInternal(")
        val rebuildBody = source.substringAfter("private fun buildUpdatedEntry(")
            .substringBefore("private fun applyPasswordEntryPresentation(")

        assertTrue(
            "Updating a KeePass password entry must remember the matched Entry before removing it, " +
                "otherwise Entry.binaries is lost when the entry is rebuilt.",
            updateBody.contains("firstMatchedContext")
        )
        assertTrue(
            "Updating a KeePass password entry must patch the existing Entry, not replace it with a fresh Entry.",
            updateBody.contains("fieldPatch.applyTo(existing)")
        )
        assertTrue(
            "KeePass field patch must copy the existing Entry so attachments in Entry.binaries survive.",
            source.contains("return entry.copy(")
        )
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}
