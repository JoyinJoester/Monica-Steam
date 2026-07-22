package takagi.ru.monica.ui.vaultv2

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultV2ArchiveTopBarStateTest {
    @Test
    fun archiveBackLivesInTheActionPillInsteadOfTheTitleArea() {
        val pane = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
        ).readText()
        val topBar = pane.substringAfter("ExpressiveTopBar(")
            .substringBefore("VaultV2QuickStatusBar(")

        assertFalse(topBar.contains("navigationIcon = if (state.isArchiveView)"))
        val archiveActionIndex = topBar.indexOf("if (state.isArchiveView)")
        val searchActionIndex = topBar.indexOf("IconButton(onClick = { isSearchExpanded = true })")
        assertTrue(archiveActionIndex >= 0)
        assertTrue(searchActionIndex > archiveActionIndex)
        assertTrue(topBar.contains("IconButton(onClick = state::closeArchiveView)"))
        assertTrue(topBar.contains("imageVector = Icons.Default.Lock"))
    }

    @Test
    fun closingArchiveRestoresTheStorageFilterCapturedOnEntry() {
        val state = VaultV2PaneState(
            scrollIndex = 0,
            scrollOffset = 0,
            fastScrollRequestKey = 0,
            fastScrollProgress = 0f,
            scrollToTopRequestKey = 0,
            storageFilterType = VAULT_V2_STORAGE_FILTER_KEEPASS_GROUP,
            storageFilterPrimaryId = 42L,
            storageFilterSecondaryKey = "Root/Work",
            hasInitializedStorageFilter = true,
            selectionCount = 0,
            isArchiveView = false,
            archiveReturnStorageFilterType = null,
            archiveReturnStorageFilterPrimaryId = null,
            archiveReturnStorageFilterSecondaryKey = null,
        )

        state.openArchiveView()
        state.updateStorageFilter(VAULT_V2_STORAGE_FILTER_ALL)
        state.closeArchiveView()

        assertFalse(state.isArchiveView)
        assertEquals(VAULT_V2_STORAGE_FILTER_KEEPASS_GROUP, state.storageFilterType)
        assertEquals(42L, state.storageFilterPrimaryId)
        assertEquals("Root/Work", state.storageFilterSecondaryKey)
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
