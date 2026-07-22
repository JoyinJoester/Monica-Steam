package takagi.ru.monica.ui.vaultv2

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultV2EntryCacheIntegrationGuardTest {

    @Test
    fun `pane seeds and updates both retained list stages`() {
        val pane = source("ui/vaultv2/VaultV2Pane.kt")

        assertTrue(pane.contains("state.computedListSnapshots.seed("))
        assertTrue(pane.contains("state.computedListSnapshots.update("))
        assertTrue(pane.contains("state.visibleListSnapshots.seed("))
        assertTrue(pane.contains("state.visibleListSnapshots.update("))
        assertTrue(pane.contains("initialHasComputed = computedSnapshotSeed.hasSnapshot"))
        assertTrue(pane.contains("initialHasComputed = visibleSnapshotSeed.hasSnapshot"))
        assertTrue(
            pane.contains(
                "visibleListStateAsync.hasComputed && normalizedQuery.isBlank()"
            )
        )
        assertTrue(
            pane.contains(
                "showVaultLoadingIndicator = sectionedItems.isEmpty() && isVaultListLoading"
            )
        )
        assertTrue(pane.contains("shouldShowVaultV2InitialLoading("))
        assertTrue(pane.contains("hasRetainedSnapshot = visibleSnapshotSeed.hasSnapshot"))
        assertTrue(pane.contains("visibleListHasComputed = visibleListStateAsync.hasComputed"))
    }

    @Test
    fun `retained snapshots are memory only and cleared when vault locks`() {
        val state = source("ui/vaultv2/VaultV2PaneState.kt")
        val mainScreen = source("ui/SimpleMainScreen.kt")

        val saverBlock = state.substringAfter("val Saver:").substringBefore("restore =")
        assertFalse(saverBlock.contains("computedListSnapshots"))
        assertFalse(saverBlock.contains("visibleListSnapshots"))
        assertTrue(mainScreen.contains("vaultV2PaneState.clearRetainedListSnapshots()"))
        assertTrue(mainScreen.contains("if (!isPasswordVaultAuthenticated)"))
    }

    @Test
    fun `secondary vault sources expose retained StateFlows`() {
        val bankCards = source("viewmodel/BankCardViewModel.kt")
        val documents = source("viewmodel/DocumentViewModel.kt")
        val notes = source("viewmodel/NoteViewModel.kt")
        val pane = source("ui/vaultv2/VaultV2Pane.kt")

        assertTrue(bankCards.contains("val allCards: StateFlow<List<SecureItem>>"))
        assertTrue(documents.contains("val allDocuments: StateFlow<List<SecureItem>>"))
        assertTrue(notes.contains("val allNotes: StateFlow<List<SecureItem>>"))
        assertTrue(pane.contains("bankCardViewModel.allCards.collectAsState()"))
        assertTrue(pane.contains("documentViewModel.allDocuments.collectAsState()"))
        assertTrue(pane.contains("noteViewModel.allNotes.collectAsState()"))
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(
            directory,
            "app/src/main/java/takagi/ru/monica/$relativePath"
        ).readText()
    }
}
