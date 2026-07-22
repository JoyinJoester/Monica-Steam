package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordArchiveReturnFilterGuardTest {

    @Test
    fun archiveUsesDedicatedOpenAndCloseActions() {
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val listContent = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListContent.kt"
        ).readText()
        val topSection = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListTopSection.kt"
        ).readText()

        assertTrue(viewModel.contains("fun openArchiveView()"))
        assertTrue(viewModel.contains("fun closeArchiveView()"))
        assertTrue(listContent.contains("viewModel.closeArchiveView()"))
        assertTrue(topSection.contains("onClick = { viewModel.closeArchiveView() }"))
        assertTrue(topSection.contains("onOpenArchive = viewModel::openArchiveView"))
    }

    @Test
    fun temporaryArchiveDoesNotPersistAllAsTheLastPasswordFilter() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val persistence = source.substringAfter("private fun persistCategoryFilter(")
        val archivedBranch = persistence.substringAfter("is CategoryFilter.Archived ->")
            .substringBefore("is CategoryFilter.Local ->")

        assertFalse(archivedBranch.contains("updateLastPasswordCategoryFilter"))
    }

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
