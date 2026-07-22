package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultV2ArchiveRoutingGuardTest {
    @Test
    fun vaultArchiveCallbacksStayInsideVaultV2State() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/SimpleMainScreen.kt"
        ).readText()
        val archiveCallbacks = Regex(
            "onOpenVaultV2ArchivePage\\s*=\\s*\\{([\\s\\S]*?)\\n\\s*},|" +
                "onOpenArchivePage\\s*=\\s*\\{([\\s\\S]*?)\\n\\s*},"
        ).findAll(screen).map { it.value }.toList()

        assertTrue(archiveCallbacks.size >= 3)
        assertTrue(archiveCallbacks.all { it.contains("vaultV2PaneState.openArchiveView()") })
        assertFalse(archiveCallbacks.any { it.contains("CategoryFilter.Archived") })
        assertFalse(archiveCallbacks.any { it.contains("BottomNavItem.Passwords.key") })
    }

    @Test
    fun vaultOwnsSavedArchiveStateAndASeparateLightweightStream() {
        val state = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2PaneState.kt"
        ).readText()
        val pane = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()

        assertTrue(state.contains("var isArchiveView"))
        assertTrue(state.contains("fun openArchiveView()"))
        assertTrue(state.contains("fun closeArchiveView()"))
        assertTrue(viewModel.contains("val archivedPasswordsForUi"))
        assertTrue(pane.contains("passwordViewModel.archivedPasswordsForUi.collectAsState()"))
        assertTrue(pane.contains("state.isArchiveView"))
    }

    @Test
    fun vaultArchiveHasItsOwnTitleBackHandlingAndEmptyState() {
        val pane = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
        ).readText()

        assertTrue(pane.contains("BackHandler(enabled = state.isArchiveView"))
        assertTrue(pane.contains("R.string.archive_page_title"))
        assertTrue(pane.contains("R.string.archive_empty_hint"))
        assertTrue(pane.contains("state.closeArchiveView()"))
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
