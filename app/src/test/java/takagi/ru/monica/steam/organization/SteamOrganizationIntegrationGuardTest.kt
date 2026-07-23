package takagi.ru.monica.steam.organization

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamOrganizationIntegrationGuardTest {
    @Test
    fun organizationFieldsUseEncryptedRepositoryWrites() {
        val repository = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountRepository.kt"
        ).readText()
        val update = repository.substringAfter("suspend fun updateOrganization(")
            .substringBefore("suspend fun markHealthChecked")

        assertTrue(update.contains("groupName = groupName"))
        assertTrue(update.contains("tagsJson = encrypt("))
        assertTrue(update.contains("note = encrypt("))
        assertTrue(update.contains("pinned = pinned"))
    }

    @Test
    fun productionUiKeepsEditorWithoutRenderingHomepageFiltersAndSupportsBothStorageBranches() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val components = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/organization/ui/SteamOrganizationComponents.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/presentation/SteamViewModel.kt"
        ).readText()

        assertTrue(screen.contains("SteamOrganizationEditorDialog("))
        val codeContent = screen
            .substringAfter("private fun SteamCodeContent(")
            .substringBefore("private fun SteamAccountDetailContent(")
        assertFalse(codeContent.contains("SteamOrganizationFilterBar("))
        assertTrue(screen.contains("SteamOrganizationSummary(account)"))
        assertTrue(components.contains("Modifier.size(48.dp)"))
        val update = viewModel.substringAfter("fun updateOrganization(")
            .substringBefore("fun clearMessage")
        assertTrue(update.contains("SteamStorageSource.Local"))
        assertTrue(update.contains("is SteamStorageSource.Mdbx"))
        assertTrue(update.contains("repository.updateOrganization("))
        assertTrue(update.contains("store.upsertAccount("))
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
