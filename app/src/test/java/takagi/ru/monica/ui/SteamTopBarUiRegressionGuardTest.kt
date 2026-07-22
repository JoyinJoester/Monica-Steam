package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamTopBarUiRegressionGuardTest {

    @Test
    fun rootCapsuleUsesStorageSearchAndMoreInThatOrder() {
        val source = steamSource()
        val topBar = source.substringAfter("private fun SteamRootTopBar(")
            .substringBefore("private fun SteamDetailTopBar(")

        val folder = topBar.indexOf("Icons.Default.Folder")
        val search = topBar.indexOf("Icons.Default.Search")
        val more = topBar.indexOf("Icons.Default.MoreVert")

        assertTrue(folder >= 0)
        assertTrue(search > folder)
        assertTrue(more > search)
        assertFalse(topBar.contains("Icons.Default.Add"))
        assertFalse(topBar.contains("Icons.Default.Refresh"))
    }

    @Test
    fun steamUsesRealSearchStateAndExtensiblePageMenu() {
        val source = steamSource()

        assertTrue(source.contains("searchQuery = steamSearchQuery"))
        assertTrue(source.contains("filterSteamAccounts("))
        assertTrue(source.contains("filterSteamConfirmations("))
        assertTrue(source.contains("SteamSection.entries.forEach"))
        assertTrue(source.contains("rememberPullToSearchState("))
        assertTrue(source.contains("PasswordTopActionsDropdownMenu("))
        assertTrue(source.contains("val reorderEnabled = selectionMode && !isSearchActive"))
        assertTrue(source.contains("filteredSteamAccounts.map { it.id }"))
        assertTrue(source.contains("viewModel.selectConfirmations("))
        assertTrue(source.contains("filteredSteamConfirmations.map { it.id }.toSet()"))
        assertTrue(source.contains("LaunchedEffect(selectedSection, uiState.storageSource, detailAccountId)"))
    }

    @Test
    fun steamPullSearchDoesNotDrawAnExtraIndicatorBehindTheTopBar() {
        val source = steamSource()

        assertFalse(source.contains("PullGestureIndicator"))
        assertFalse(source.contains("PullActionVisualState"))
    }

    @Test
    fun expandedSteamSearchConsumesBackBeforeOuterNavigation() {
        val source = steamSource()
        val backHandler = source
            .substringAfter("BackHandler(enabled = isSteamSearchExpanded && detailAccount == null)")
            .substringBefore("}")

        assertTrue(backHandler.contains("clearSteamSearch()"))
        assertTrue(backHandler.contains("focusManager.clearFocus()"))
    }

    @Test
    fun pullToSearchRemainsDraggableAfterSearchExpands() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/common/pull/PullToSearchState.kt"
        ).readText()
        val verticalDrag = source.substringAfter("fun onVerticalDrag(dragAmount: Float)")
            .substringBefore("val onDragEnd")
        val postScroll = source.substringAfter("override fun onPostScroll(")
            .substringBefore("override suspend fun onPreFling")

        assertFalse(verticalDrag.contains("if (isSearchExpanded) return"))
        assertFalse(postScroll.contains("!isSearchExpanded &&"))
    }

    private fun steamSource(): String = projectFile(
        "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
    ).readText()

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
