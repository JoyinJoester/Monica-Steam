package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentNumberPreviewUsageGuardTest {

    @Test
    fun allDocumentListSurfacesUseSharedPreviewMask() {
        val aggregate = source("app/src/main/java/takagi/ru/monica/ui/password/PasswordAggregateListContent.kt")
        val card = source("app/src/main/java/takagi/ru/monica/ui/components/DocumentCard.kt")
        val vault = source("app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt")

        assertTrue(aggregate.contains("maskDocumentNumberForPreview"))
        assertFalse(aggregate.contains("subtitleSecondary = data?.documentNumber.orEmpty()"))
        assertTrue(card.contains("maskDocumentNumberForPreview"))
        assertTrue(vault.contains("maskDocumentNumberForPreview"))
        assertFalse(vault.contains("vaultV2MaskedDocumentNumber"))
    }

    private fun source(relativePath: String): String = projectFile(relativePath).readText()

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
