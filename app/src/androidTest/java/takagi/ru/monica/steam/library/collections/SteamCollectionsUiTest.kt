package takagi.ru.monica.steam.library.collections

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.SteamDockContentClearance
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.library.*
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamCollectionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val oldLocale = Locale.getDefault()
    private var originalConfiguration: Configuration? = null
    private var state by mutableStateOf(SteamCollectionsState(snapshot = SteamCollectionsSnapshot(1, listOf(
        SteamCloudCollectionEntry("user-collections.uc-a", """{"id":"uc-a","name":"Co-op","added":[620,999],"custom":42}""", false, 1, 1),
        SteamCloudCollectionEntry("user-collections.uc-dynamic", """{"id":"uc-dynamic","name":"Dynamic","added":[570],"filterSpec":{"tag":1}}""", false, 1, 1)
    ))))
    private var games by mutableStateOf(listOf(SteamGame(620, "Portal 2", 1, 0, iconHash = "2e478fc6874d06ae5baf0d147f6f21203291aa02"), SteamGame(570, "Dota 2", 1, 0, iconHash = "0bbb630d63262dd66d2fdd0f7d37e8661a410075"),
        SteamGame(12, "Family title", 1, 0, ownership = SteamGameOwnership.FAMILY_SHARED)))
    private var selected by mutableStateOf<String?>(null)
    private var account by mutableIntStateOf(1)
    private var scope: CoroutineScope? = null
    private var detail by mutableStateOf<SteamGame?>(null)
    private var backs = 0
    private var refreshes = 0
    private var fail = false
    private var writes = 0
    private var lastChange: SteamCollectionChange? = null
    private fun label(id: Int) = compose.activity.getString(id)
    @After fun cleanup() {
        scope?.cancel(); Locale.setDefault(oldLocale)
        originalConfiguration?.let { config -> compose.activityRule.scenario.onActivity {
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(config, it.resources.displayMetrics)
        } }
    }
    private fun launch(large: Boolean = false, glass: Boolean = false) {
        Locale.setDefault(Locale.ENGLISH)
        compose.activityRule.scenario.onActivity {
            originalConfiguration = Configuration(it.resources.configuration)
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(Configuration(it.resources.configuration).apply {
                setLocale(Locale.ENGLISH)
                fontScale = if (large) 1.5f else 1f
                densityDpi = (it.resources.displayMetrics.widthPixels * 160 / 360)
            }, it.resources.displayMetrics)
            assertEquals(if (large) 1.5f else 1f, it.resources.configuration.fontScale)
        }
        compose.setContent {
            scope = rememberCoroutineScope()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * (compose.activity.resources.displayMetrics.widthPixels / density.density / 360f), if (large) 1.5f else 1f),
                LocalReduceAnimations provides true, LocalSteamDockContentClearance provides SteamDockContentClearance, LocalSteamDesignStyle provides if (glass) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED) {
                MonicaTheme(darkTheme = large) {
                    Surface(Modifier.fillMaxSize()) {
                        val savedPages = rememberSaveableStateHolder()
                        if (detail != null) {
                            BackHandler { detail = null }
                            Button({ detail = null }, Modifier.testTag("detail_back")) { Text(detail!!.name) }
                        } else savedPages.SaveableStateProvider(account) {
                            SteamCollectionsScreen(state, games, selected, { selected = it },
                                onRefresh = { refreshes++ }, onChange = ::change, onBack = { backs++ },
                                onOpenGame = { detail = it })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun change(change: SteamCollectionChange) {
        writes++; lastChange = change
        state = state.copy(saving = true, saved = false, failure = null)
        scope!!.launch {
            delay(80)
            if (fail) { state = state.copy(saving = false, failure = SteamCollectionsFailure.CONFLICT); return@launch }
            val old = state.snapshot!!
            val entry = when (change) {
                is SteamCollectionChange.Create -> SteamCloudCollectionEntry("user-collections.uc-new", """{"id":"uc-new","name":"${change.name.trim()}","added":[]}""", false, 2, 2)
                is SteamCollectionChange.Rename -> change.reviewed.copy(value = changedCollectionValue(parseSteamCollection(change.reviewed)!!, change))
                is SteamCollectionChange.Membership -> change.reviewed.copy(value = changedCollectionValue(parseSteamCollection(change.reviewed)!!, change))
                is SteamCollectionChange.Delete -> change.reviewed.copy(deleted = true)
            }
            state = SteamCollectionsState(SteamCollectionsSnapshot(old.version + 1, old.entries.filterNot { it.key == entry.key } + entry), saved = true)
        }
    }
    private fun menu(labelId: Int) {
        compose.onNodeWithTag("collection_uc-a").performTouchInput { longClick() }
        val action = when (labelId) {
            R.string.steam_collections_rename -> R.string.rename_category
            R.string.steam_collections_delete -> R.string.delete
            else -> labelId
        }
        compose.onNodeWithText(label(action)).performClick()
    }
    private fun search(value: String, members: Boolean = false) {
        if (compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag(if (members) "collection_game_search_toggle" else "collection_search_toggle").performClick()
        }
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
    }
    private fun chooseAll() {
        compose.onNodeWithTag("collection_selector").performClick()
        compose.onNodeWithTag("collection_all").performClick()
    }
    private fun saved() { compose.waitUntil(5000) { state.saved }; compose.waitForIdle() }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Let the platform ripple finish before recording the resting layout.
        android.os.SystemClock.sleep(350)
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val target = File(compose.activity.getExternalFilesDir(null), "collections-page/$name.png").apply { parentFile!!.mkdirs() }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun createRenameDeleteThroughRealControls() {
        launch()
        compose.onNodeWithTag("collection_create").performClick()
        compose.onNodeWithTag("collection_name").performTextInput("Weekend")
        compose.onNodeWithTag("collection_save").performClick(); saved()
        assertEquals("Weekend", state.snapshot!!.collections.first { it.id == "uc-new" }.name)
        menu(R.string.steam_collections_rename)
        compose.onNodeWithTag("collection_name").performTextReplacement("Friends")
        compose.onNodeWithTag("collection_save").performClick(); saved()
        assertEquals("Friends", state.snapshot!!.collections.first { it.id == "uc-a" }.name)
        menu(R.string.steam_collections_delete)
        compose.onNodeWithText(compose.activity.getString(R.string.steam_collections_delete_hint, "Friends")).assertIsDisplayed()
        compose.onNodeWithTag("collection_delete_confirm").performClick(); saved()
        assertFalse(state.snapshot!!.collections.any { it.id == "uc-a" }); assertEquals(3, writes)
    }
    @Test fun batchesOwnedMembersAndPreservesUnlistedMembers() {
        launch(); menu(R.string.steam_collections_edit_games)
        compose.onNodeWithTag("collection_game_12").assertIsDisplayed().assertIsOff()
        compose.onNodeWithTag("collection_game_620").performClick()
        compose.onNodeWithTag("collection_game_570").performClick()
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
        assertEquals(mapOf(620 to false, 570 to true), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(570, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
    }
    @Test fun searchDoesNotDiscardPendingChoices() {
        launch(); menu(R.string.steam_collections_edit_games)
        compose.onNodeWithTag("collection_game_620").performClick()
        search("Dota", members = true)
        compose.onNodeWithTag("collection_game_620").assertDoesNotExist()
        compose.onNodeWithTag("collection_game_570").performClick()
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
        assertEquals(mapOf(620 to false, 570 to true), (lastChange as SteamCollectionChange.Membership).changes)
    }
    @Test fun cancelNameDoesNotWriteAndBlankNameIsDisabled() {
        launch(); compose.onNodeWithTag("collection_create").performClick()
        compose.onNodeWithTag("collection_save").assertIsNotEnabled()
        compose.onNodeWithTag("collection_name").performTextInput("Draft")
        compose.onNodeWithText(label(R.string.steam_collections_cancel)).performClick()
        assertEquals(0, writes); assertEquals(2, state.snapshot!!.collections.size)
    }
    @Test fun dynamicIsReadonlyAndOrdinarySelectionFilters() {
        launch()
        compose.onNodeWithTag("collection_uc-dynamic").assertIsNotEnabled()
        compose.onNodeWithTag("collection_uc-dynamic").assertIsNotEnabled(); assertNull(selected)
        compose.onNodeWithTag("collection_uc-a").performClick()
        assertEquals("uc-a", selected)
        assertEquals(setOf(620), collectionGames(games.map { it.appId }.toSet(), selected, state.snapshot))
    }
    @Test fun conflictKeepsDraftAndBlocksRepeatSave() {
        fail = true; launch(); menu(R.string.steam_collections_rename)
        compose.onNodeWithTag("collection_name").performTextReplacement("Draft")
        compose.onNodeWithTag("collection_save").performClick()
        compose.waitUntil(5000) { state.failure == SteamCollectionsFailure.CONFLICT }
        compose.onNodeWithTag("collection_name").assertTextContains("Draft")
        compose.onNodeWithTag("collection_save").assertIsNotEnabled(); assertEquals(1, writes)
    }
    @Test fun changingAccountRemovesPreviousEditor() {
        launch(); menu(R.string.steam_collections_rename)
        compose.onNodeWithTag("collection_name").performTextReplacement("Old account draft")
        compose.runOnIdle { account = 2; state = SteamCollectionsState(SteamCollectionsSnapshot(1, emptyList())) }
        compose.onNodeWithTag("collection_name").assertDoesNotExist(); assertEquals(0, writes)
    }
    @Test fun largeDarkManagerAndMembersRemainOperable() {
        launch(large = true, glass = true); screenshot("manager-dark-large")
        menu(R.string.steam_collections_edit_games)
        compose.onNodeWithTag("collection_game_570").performScrollTo().performClick()
        compose.onNodeWithTag("collection_members_save").assertIsEnabled(); screenshot("members-dark-large")
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
    }
    @Test fun lightManagerNativePreview() { launch(); screenshot("manager-light") }
    @Test fun unknownResultBlocksMutationsUntilRefresh() {
        state = state.copy(failure = SteamCollectionsFailure.UNKNOWN); launch()
        compose.onNodeWithTag("collection_create").assertIsNotEnabled()
        val before = refreshes
        compose.onNodeWithTag("collection_selector").performClick()
        compose.onNodeWithTag("collections_refresh").performScrollTo().performClick()
        assertEquals(before + 1, refreshes)
        compose.runOnIdle { state = state.copy(failure = null) }
        compose.onNodeWithTag("collection_create").assertIsEnabled(); assertEquals(0, writes)
    }
    @Test fun standaloneFoldersSearchBrowseAndBackHierarchy() {
        launch()
        compose.onNodeWithTag("collections_page").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        search("Co-op")
        compose.onNodeWithTag("collection_uc-a").assertIsDisplayed()
        compose.onNodeWithTag("collection_uc-dynamic").assertDoesNotExist()
        compose.onNodeWithTag("collection_uc-a").performClick()
        compose.onNodeWithTag("collection_browse_game_620").assertIsDisplayed()
        compose.onNodeWithTag("collection_browse_game_570").assertDoesNotExist()
        compose.onNodeWithTag("collections_back").performClick()
        compose.onNodeWithTag("collection_list").assertIsDisplayed()
        assertEquals(0, backs)
        compose.onNodeWithTag("collections_back").performClick()
        assertEquals(1, backs)
    }
    @Test fun categoryDropdownSwitchesAndReturnsToManager() {
        launch()
        chooseAll()
        compose.onNodeWithTag("collection_browse_game_620").assertIsDisplayed()
        compose.onNodeWithTag("collection_selector").performClick()
        compose.onNodeWithTag("collection_switch_uc-a").performClick()
        assertEquals("uc-a", selected)
        compose.onNodeWithTag("collection_browse_game_570").assertDoesNotExist()
        compose.onNodeWithTag("collection_selector").performClick()
        compose.onNodeWithText(label(R.string.steam_collections_manage)).performClick()
        compose.onNodeWithTag("collection_list").assertIsDisplayed()
    }
    @Test fun unfiledUsesOnlyOrdinaryMembershipAndCanOpenGame() {
        launch()
        compose.onNodeWithTag("collection_selector").performClick()
        compose.onNodeWithTag("collection_unfiled").performClick()
        assertEquals("", selected)
        compose.onNodeWithTag("collection_browse_game_620").assertDoesNotExist()
        compose.onNodeWithTag("collection_browse_game_570").performClick()
        assertEquals(570, detail?.appId)
    }
    @Test fun detailReturnRetainsFolderAndSearch() {
        launch()
        chooseAll()
        search("Portal")
        compose.onNodeWithTag("collection_browse_game_620").performClick()
        assertEquals(620, detail?.appId)
        compose.onNodeWithTag("detail_back").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Portal")
        compose.onNodeWithTag("collection_browse_game_620").assertIsDisplayed()
        compose.onNodeWithTag("collection_browse_game_570").assertDoesNotExist()
    }
    @Test fun visibleSelectAndClearPreserveHiddenChanges() {
        launch(); menu(R.string.steam_collections_edit_games)
        compose.onNodeWithTag("collection_game_620").performClick()
        search("Dota", members = true)
        compose.onNodeWithContentDescription(label(R.string.steam_collections_select_visible)).performClick()
        compose.onNodeWithContentDescription(label(R.string.steam_collections_select_visible)).performClick()
        compose.onNodeWithTag("collection_game_570").assertIsOn()
        compose.onNodeWithContentDescription(label(R.string.steam_collections_clear_visible)).performClick()
        compose.onNodeWithContentDescription(label(R.string.steam_collections_select_visible)).performClick()
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
        assertEquals(mapOf(620 to false, 570 to true), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(570, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
    }
    @Test fun dirtyMemberBackRequiresDiscardAndCancelPreservesDraft() {
        launch(); menu(R.string.steam_collections_edit_games)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithTag("collection_game_570").performClick()
        compose.onNodeWithTag("collection_members_back").performClick()
        compose.onNodeWithTag("collection_discard_confirm").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.steam_collections_cancel)).performClick()
        compose.onNodeWithTag("collection_game_570").assertIsOn()
        compose.onNodeWithTag("collection_members_back").performClick()
        compose.onNodeWithTag("collection_discard_confirm").performClick()
        compose.onNodeWithTag("collection_list").assertIsDisplayed()
        assertEquals(0, writes)
    }
    @Test fun systemBackClosesSearchThenBrowseThenParent() {
        launch()
        compose.onNodeWithTag("collection_uc-a").performClick()
        search("Portal")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("collection_browse_games").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("collection_list").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, backs) }
    }
    @Test fun lightMembersAndBrowseNativePreview() {
        launch()
        menu(R.string.steam_collections_edit_games); screenshot("members-light")
        compose.onNodeWithTag("collection_members_back").performClick()
        compose.onNodeWithTag("collection_uc-a").performClick(); screenshot("browse-light")
    }
    @Test fun largeBrowseLongNameAndDropdownRemainOperable() {
        val entry = state.snapshot!!.entries.first().copy(value = """{"id":"uc-a","name":"Weekend cooperative adventures with friends","added":[620,999]}""")
        state = state.copy(snapshot = SteamCollectionsSnapshot(1, listOf(entry)))
        launch(large = true, glass = true)
        compose.onNodeWithTag("collection_uc-a").performClick(); screenshot("browse-dark-large")
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Weekend cooperative adventures with friends", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
        compose.onNodeWithTag("collection_edit_games").assertIsDisplayed().performClick()
        compose.onNodeWithTag("collection_selection_bar").assertIsDisplayed(); screenshot("members-long-dark-large")
        compose.onNodeWithTag("collection_members_back").performClick()
        compose.onNodeWithTag("collection_selector").performClick(); screenshot("selector-dark-large")
        compose.onNodeWithText(label(R.string.steam_collections_manage)).performScrollTo().performClick()
        compose.onNodeWithTag("collection_list").assertIsDisplayed()
    }

    @Test fun returningToForegroundRefreshesWithoutDroppingMemberDraft() {
        launch(); menu(R.string.steam_collections_edit_games)
        compose.onNodeWithTag("collection_game_570").performClick()
        val before = refreshes
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(before + 1, refreshes) }
        compose.onNodeWithTag("collection_game_570").assertIsOn()
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
        assertEquals(mapOf(570 to true), (lastChange as SteamCollectionChange.Membership).changes)
    }
    @Test fun memberSaveStaysAboveDockClearance() {
        launch(large = true); menu(R.string.steam_collections_edit_games)
        val bounds = compose.onNodeWithTag("collection_members_save").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("Save must be above the dock region", bounds.bottom <= root.bottom - SteamDockContentClearance)
    }

    @Test fun foldersAndUnfiledGamesShareTheDirectoryPage() {
        launch()
        compose.onNodeWithTag("collection_uc-a").assertIsDisplayed()
        compose.onNodeWithTag("collection_browse_game_570").assertIsDisplayed()
        compose.onNodeWithTag("collection_browse_game_620").assertDoesNotExist()
        compose.onNodeWithTag("collection_create").assertIsDisplayed()
        compose.onNodeWithTag("collection_game_icon_570", useUnmergedTree = true).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
    }
    @Test fun longPressFolderUsesMonicaManagementDialog() {
        launch()
        compose.onNodeWithTag("collection_uc-a").performTouchInput { longClick() }
        compose.onNodeWithText(label(R.string.rename_category)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.delete)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.steam_collections_edit_games)).assertIsDisplayed()
        screenshot("folder-actions-light")
    }
    @Test fun breadcrumbReturnsToDirectoryAndKeepsGamesAtRoot() {
        launch(); compose.onNodeWithTag("collection_uc-a").performClick()
        compose.onNodeWithTag("collection_browse_game_620").assertIsDisplayed()
        compose.onNodeWithTag("collection_path_root").performClick()
        compose.onNodeWithTag("collection_uc-a").assertIsDisplayed()
        compose.onNodeWithTag("collection_browse_game_570").assertIsDisplayed()
        assertNull(selected); assertEquals(0, backs)
    }
    @Test fun longPressGameAddsToFolderWithoutChangingOtherMembers() {
        launch()
        compose.onNodeWithTag("collection_browse_game_570").performTouchInput { longClick() }
        assertNull(detail)
        compose.onNodeWithContentDescription(label(R.string.steam_collections_add_to)).performClick()
        compose.onNodeWithTag("collection_add_to_uc-a").performClick(); saved()
        assertEquals(mapOf(570 to true), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(620,570,999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
        compose.onNodeWithTag("collection_browse_selection_bar").assertDoesNotExist()
    }
    @Test fun containedGameRemovalRequiresConfirmationAndPreservesLibrary() {
        launch(); compose.onNodeWithTag("collection_uc-a").performClick()
        compose.onNodeWithTag("collection_browse_game_620").performTouchInput { longClick() }
        compose.onNodeWithContentDescription(label(R.string.steam_collections_remove_from)).performClick()
        assertEquals(0, writes)
        compose.onNodeWithTag("collection_remove_confirm").performClick(); saved()
        assertEquals(mapOf(620 to false), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
        assertTrue(games.any { it.appId == 620 })
    }
    @Test fun addingExistingMemberIsNoOp() {
        launch(); compose.onNodeWithTag("collection_uc-a").performClick()
        compose.onNodeWithTag("collection_browse_game_620").performTouchInput { longClick() }
        compose.onNodeWithContentDescription(label(R.string.steam_collections_add_to)).performClick()
        compose.onNodeWithTag("collection_add_to_uc-a").performClick()
        compose.onNodeWithTag("collection_browse_selection_bar").assertDoesNotExist()
        assertEquals(0, writes)
    }
    @Test fun familySharedLongPressCanAddAndRemoveFromCollection() {
        launch()
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput { longClick() }
        assertNull(detail)
        compose.onNodeWithTag("collection_browse_game_12").assertIsOn()
        compose.onNodeWithContentDescription(label(R.string.steam_collections_add_to)).performClick()
        compose.onNodeWithTag("collection_add_to_uc-a").performClick(); saved()
        assertEquals(mapOf(12 to true), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(620, 12, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
        compose.onNodeWithTag("collection_uc-a").performClick()
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput { longClick() }
        screenshot("family-browse-selected")
        compose.onNodeWithContentDescription(label(R.string.steam_collections_remove_from)).performClick()
        assertEquals(1, writes)
        compose.onNodeWithTag("collection_remove_confirm").performClick(); saved()
        assertEquals(mapOf(12 to false), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(620, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
        assertTrue(games.single { it.appId == 12 }.isFamilyShared)
    }
    @Test fun ordinarySelectionCanToggleAndSelectAllFamilySharedGames() {
        launch()
        compose.onNodeWithTag("collection_browse_game_570").performTouchInput { longClick() }
        compose.onNodeWithTag("collection_browse_game_12").performClick().assertIsOn()
        compose.onNodeWithTag("collection_browse_game_12").performClick().assertIsOff()
        compose.onNodeWithContentDescription(label(R.string.select_all)).performClick()
        compose.onNodeWithTag("collection_browse_game_12").assertIsOn()
        compose.onNodeWithContentDescription(label(R.string.steam_collections_add_to)).performClick()
        compose.onNodeWithTag("collection_add_to_uc-a").performClick(); saved()
        assertEquals(mapOf(570 to true, 12 to true), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(620, 570, 12, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
    }
    @Test fun familySharedMemberDraftSurvivesSearchAndReopensAfterSave() {
        launch(large = true, glass = true); menu(R.string.steam_collections_edit_games)
        search("Family", members = true)
        compose.onNodeWithContentDescription(label(R.string.steam_collections_select_visible)).performClick()
        compose.onNodeWithTag("collection_game_12").assertIsOn()
        screenshot("family-members-selected")
        search("Dota", members = true)
        compose.onNodeWithContentDescription(label(R.string.steam_collections_select_visible)).performClick()
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
        assertEquals(mapOf(12 to true, 570 to true), (lastChange as SteamCollectionChange.Membership).changes)
        menu(R.string.steam_collections_edit_games); search("Family", members = true)
        compose.onNodeWithTag("collection_game_12").assertIsOn()
        compose.onNodeWithContentDescription(label(R.string.steam_collections_clear_visible)).performClick()
        compose.onNodeWithTag("collection_game_12").assertIsOff()
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
        assertEquals(mapOf(12 to false), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(620, 570, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
    }
    @Test fun rightSwipeSelectsAndUnselectsSharedGamesWithoutOpeningDetails() {
        launch()
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput { swipeRight() }.assertIsOn()
        assertNull(detail); assertEquals(0, writes)
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput { swipeRight() }.assertIsOff()
        compose.onNodeWithTag("collection_browse_game_570").performTouchInput { swipeRight() }.assertIsOn()
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput { swipeRight() }.assertIsOn()
        screenshot("family-swipe-selected")
        compose.onNodeWithContentDescription(label(R.string.steam_collections_add_to)).performClick()
        compose.onNodeWithTag("collection_add_to_uc-a").performClick(); saved()
        assertEquals(mapOf(570 to true, 12 to true), (lastChange as SteamCollectionChange.Membership).changes)
    }
    @Test fun rightSwipeInMemberEditorPreservesHiddenChoicesAndCancelsSharedDraft() {
        launch(); menu(R.string.steam_collections_edit_games)
        compose.onNodeWithTag("collection_game_12").performTouchInput { swipeRight() }.assertIsOn()
        search("Dota", members = true)
        compose.onNodeWithTag("collection_game_570").performTouchInput { swipeRight() }.assertIsOn()
        search("Family", members = true)
        compose.onNodeWithTag("collection_game_12").performTouchInput { swipeRight() }.assertIsOff()
        compose.onNodeWithTag("collection_members_save").performClick(); saved()
        assertEquals(mapOf(570 to true), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(620, 570, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
    }
    @Test fun shortAndLeftSwipesDoNotSelectOrDeleteAndSavingDisablesSwipe() {
        launch()
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput {
            swipe(Offset(width * .2f, centerY), Offset(width * .3f, centerY), 300)
        }
        compose.onNodeWithTag("collection_browse_selection_bar").assertDoesNotExist()
        assertNull("Short swipe must not open game details", detail)
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("collection_browse_selection_bar").assertDoesNotExist()
        assertNull("Disabled left swipe must not open game details", detail)
        compose.runOnIdle { state = state.copy(saving = true) }
        compose.onNodeWithTag("collection_list").performScrollToNode(hasTestTag("collection_browse_game_12"))
        compose.onNodeWithTag("collection_browse_game_12").performTouchInput { swipeRight() }
        compose.onNodeWithTag("collection_browse_selection_bar").assertDoesNotExist()
        assertNull(detail); assertEquals(0, writes)
        assertEquals(setOf(620, 999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
    }
    @Test fun verticalSwipeScrollsThroughGamesWithoutSelectingThem() {
        games = games + (1..30).map { SteamGame(1000 + it, "Extra game $it", 0, 0) }
        launch()
        val list = compose.onNodeWithTag("collection_list")
        val before = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        list.performTouchInput { swipeUp() }
        compose.waitForIdle()
        val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(after > before)
        compose.onNodeWithTag("collection_browse_selection_bar").assertDoesNotExist()
        assertNull(detail); assertEquals(0, writes)
    }
    @Test fun selectionSearchPreservesHiddenGamesAndSkipsAlreadyIncludedIds() {
        launch(); chooseAll()
        compose.onNodeWithTag("collection_browse_game_620").performTouchInput { longClick() }
        search("Dota")
        compose.onNodeWithContentDescription(label(R.string.select_all)).performClick()
        compose.onNodeWithContentDescription(label(R.string.steam_collections_add_to)).performClick()
        compose.onNodeWithTag("collection_add_to_uc-a").performClick(); saved()
        assertEquals(mapOf(570 to true), (lastChange as SteamCollectionChange.Membership).changes)
        assertEquals(setOf(620,570,999), state.snapshot!!.collections.first { it.id == "uc-a" }.members)
    }

}
