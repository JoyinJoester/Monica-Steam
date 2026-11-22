package takagi.ru.monica.steam.library.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.role
import androidx.compose.ui.state.ToggleableState
import takagi.ru.monica.ui.FolderActionsDialog
import takagi.ru.monica.ui.components.GroupedItemDefaults
import takagi.ru.monica.ui.folders.MonicaFolderBrowserPage
import takagi.ru.monica.ui.folders.MonicaFolderPath
import takagi.ru.monica.ui.folders.MonicaFolderRowData
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.ui.SteamGameLibraryRow
import takagi.ru.monica.ui.components.UnifiedCategoryFilterChipMenuDropdown
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionActionBarAction
import takagi.ru.monica.ui.gestures.SwipeActions

@Composable
internal fun steamCollectionsMenuLabel(selected: String?, state: SteamCollectionsState): String {
    val title = stringResource(R.string.steam_collections_title)
    val name = when (selected) {
        null -> null
        "" -> stringResource(R.string.steam_collections_unfiled)
        else -> state.snapshot?.collections?.firstOrNull { it.id == selected }?.name
    }
    return name?.let { "$title · $it" } ?: title
}

private sealed interface CollectionEditor {
    data class Name(val reviewed: SteamCollection?) : CollectionEditor
    data class Members(val reviewed: SteamCollection) : CollectionEditor
    data class Delete(val reviewed: SteamCollection) : CollectionEditor
}

@Composable
internal fun SteamCollectionsScreen(
    state: SteamCollectionsState, games: List<SteamGame>, selected: String?,
    onSelect: (String?) -> Unit, onRefresh: () -> Unit,
    onChange: (SteamCollectionChange) -> Unit, onBack: () -> Unit,
    onOpenGame: (SteamGame) -> Unit = {}, modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refresh by rememberUpdatedState(onRefresh)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var editor by remember { mutableStateOf<CollectionEditor?>(null) }
    var actionFolder by remember { mutableStateOf<SteamCollection?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<Int>()) }
    var selectionReview by remember { mutableStateOf<SteamCollection?>(null) }
    var folderTargets by remember { mutableStateOf<List<SteamCollection>?>(null) }
    var removeSelection by remember { mutableStateOf(false) }
    val editable = state.snapshot != null && !state.loading && !state.saving && state.failure == null
    val current = state.snapshot?.collections?.firstOrNull { it.id == selected && it.editable }
    fun clearSelection() { selecting = false; selection = emptySet(); selectionReview = null }
    fun root() { browsing = false; query = ""; searching = false; clearSelection(); onSelect(null) }
    fun back() { if (state.saving) return; if (selecting) clearSelection() else if (browsing) root() else onBack() }
    fun choose(id: String?) { onSelect(id); browsing = true; query = ""; searching = false; menu = false; clearSelection() }
    LaunchedEffect(state.saved) {
        if (submitted && state.saved) { editor = null; submitted = false; clearSelection(); folderTargets = null; removeSelection = false }
    }
    LaunchedEffect(selected, state.snapshot) {
        if (browsing && selected != null && (selected == "" && state.snapshot?.canIdentifyUnfiled != true || selected != "" && current == null)) root()
    }
    BackHandler(enabled = editor == null && actionFolder == null && folderTargets == null && !removeSelection && !help) { back() }
    val member = editor as? CollectionEditor.Members
    if (member != null) {
        SteamCollectionMembersPage(member.reviewed, games, state, editable,
            onSave = { submitted = true; onChange(SteamCollectionChange.Membership(member.reviewed.entry, it)) },
            onDismiss = { if (!state.saving) { editor = null; submitted = false } }, modifier = modifier)
        return
    }
    val allIds = remember(games) { games.map { it.appId }.toSet() }
    val ids = remember(allIds, browsing, selected, query, state.snapshot) {
        if (browsing) collectionGames(allIds, selected, state.snapshot)
        else if (query.isNotBlank()) allIds else collectionGames(allIds, "", state.snapshot)
    }
    val visible = remember(games, ids, query, descending) {
        games.filter { it.appId in ids && it.name.contains(query, true) }.distinctBy { it.appId }
            .sortedBy { it.name.lowercase() }.let { if (descending) it.asReversed() else it }
    }
    val folders = state.snapshot?.collections.orEmpty().filter { it.name.contains(query, true) }.sortedBy { it.name.lowercase() }
        .let { if (descending) it.asReversed() else it }
    val folderRows = if (browsing) emptyList() else folders.map {
        MonicaFolderRowData(it.id, it.name,
            if (it.editable) stringResource(R.string.steam_collections_count, it.members.size)
            else stringResource(if (it.dynamic) R.string.steam_collections_dynamic else R.string.steam_collections_readonly), it.editable)
    }
    val rootLabel = stringResource(R.string.steam_collections_title)
    val currentLabel = when (selected) {
        null -> stringResource(R.string.steam_collections_all)
        "" -> stringResource(R.string.steam_collections_unfiled)
        else -> current?.name.orEmpty()
    }
    val paths = listOf(MonicaFolderPath("root", rootLabel, ::root)) +
        if (browsing) listOf(MonicaFolderPath(selected ?: "all", currentLabel) {}) else emptyList()
    MonicaFolderBrowserPage(
        title = stringResource(R.string.steam_library_title), query = query, onQueryChange = { query = it },
        searching = searching, onSearchingChange = { searching = it }, onBack = { back() }, paths = paths,
        folderRows = folderRows, onOpenFolder = { choose(it) },
        onFolderActions = { key -> if (editable) actionFolder = folders.firstOrNull { it.id == key && it.editable } },
        selectedFolderKey = actionFolder?.id, pageKey = "${if (browsing) selected ?: "all" else "root"}:${query.isNotBlank()}",
        modifier = modifier, listTag = if (browsing) "collection_browse_games" else "collection_list",
        searchHint = stringResource(R.string.steam_library_search_hint), status = { CollectionsStatus(state) },
        topMenu = {
            Box {
                IconButton({ menu = true }, Modifier.testTag("collection_selector")) { Icon(Icons.Default.MoreVert, stringResource(R.string.steam_collections_actions)) }
                UnifiedCategoryFilterChipMenuDropdown(menu, { menu = false }) {
                    Column(Modifier.width(300.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        DropdownMenuItem({ Text(stringResource(R.string.steam_collections_all)) }, { choose(null) }, Modifier.testTag("collection_all"))
                        DropdownMenuItem({ Text(stringResource(R.string.steam_collections_unfiled)) }, { choose("") }, Modifier.testTag("collection_unfiled"), enabled = state.snapshot?.canIdentifyUnfiled == true)
                        state.snapshot?.collections.orEmpty().forEach { c ->
                            DropdownMenuItem({ Text(c.name) }, { choose(c.id) }, Modifier.testTag("collection_switch_${c.id}"), enabled = c.editable)
                        }
                        DropdownMenuItem({ Text(stringResource(R.string.steam_collections_manage)) }, { menu = false; root() })
                        HorizontalDivider()
                        listOf(false to R.string.steam_collections_sort_ascending, true to R.string.steam_collections_sort_descending).forEach { (desc, label) ->
                            DropdownMenuItem({ Text(stringResource(label)) }, { descending = desc; menu = false }, trailingIcon = { if (descending == desc) Icon(Icons.Default.Check, null) })
                        }
                        DropdownMenuItem({ Text(stringResource(R.string.steam_collections_refresh)) }, { menu = false; onRefresh() }, Modifier.testTag("collections_refresh"), enabled = !state.loading && !state.saving)
                        DropdownMenuItem({ Text(stringResource(R.string.steam_collections_about)) }, { menu = false; help = true })
                    }
                }
            }
        },
        floatingAction = {
            if (!selecting && (!browsing || current != null)) FolderActionFab(
                if (browsing) "collection_edit_games" else "collection_create",
                stringResource(if (browsing) R.string.steam_collections_edit_games else R.string.steam_collections_create),
                if (browsing) Icons.Default.Add else Icons.Default.CreateNewFolder, editable) {
                editor = if (browsing) CollectionEditor.Members(current!!) else CollectionEditor.Name(null)
            }
        },
        selectionActions = if (!selecting) null else ({
            SelectionActionBar(selectedCount = selection.size, onExit = { if (!state.saving) clearSelection() },
                onSelectAll = { if (!state.saving) selection = selection + visible.map { it.appId } },
                actions = buildList {
                    add(SelectionActionBarAction(Icons.Default.Folder, stringResource(R.string.steam_collections_add_to),
                        editable && selection.isNotEmpty() && state.snapshot?.collections?.any { it.editable } == true) {
                        folderTargets = state.snapshot?.collections.orEmpty().filter { it.editable }.toList()
                    })
                    if (selectionReview != null) add(SelectionActionBarAction(Icons.Default.RemoveCircleOutline,
                        stringResource(R.string.steam_collections_remove_from), editable && selection.isNotEmpty()) { removeSelection = true })
                },
                modifier = Modifier.testTag("collection_browse_selection_bar"))
        }),
    ) {
        if (visible.isNotEmpty()) item("games-heading") {
            Text(stringResource(if (!browsing && query.isBlank()) R.string.steam_collections_unfiled else R.string.steam_collections_games),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp))
        }
        itemsIndexed(visible, key = { _, game -> "game:${game.appId}" }) { index, game ->
            FolderGameRow(game, "collection_browse_game_${game.appId}", index, visible.size,
                checked = if (selecting) game.appId in selection else null,
                enabled = !state.saving,
                onClick = { if (selecting) selection = if (game.appId in selection) selection - game.appId else selection + game.appId else onOpenGame(game) },
                onLongClick = {
                    if (!selecting) { selecting = true; selectionReview = current }
                    selection = selection + game.appId
                },
                onSwipeRight = {
                    if (!selecting) { selecting = true; selectionReview = current }
                    selection = if (game.appId in selection) selection - game.appId else selection + game.appId
                })
        }
        if (visible.isEmpty() && folderRows.isEmpty() && !state.loading) item("empty") {
            Text(stringResource(R.string.steam_collections_empty), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    actionFolder?.let { folder ->
        FolderActionsDialog(folder.name, { actionFolder = null },
            onRename = if (editable) ({ actionFolder = null; editor = CollectionEditor.Name(folder) }) else null,
            onDelete = if (editable) ({ actionFolder = null; editor = CollectionEditor.Delete(folder) }) else null,
            onEditContents = if (editable) ({ actionFolder = null; editor = CollectionEditor.Members(folder) }) else null,
            editContentsLabel = stringResource(R.string.steam_collections_edit_games), title = stringResource(R.string.steam_collections_actions))
    }
    folderTargets?.let { targets ->
        AlertDialog(onDismissRequest = { if (!state.saving) folderTargets = null }, title = { Text(stringResource(R.string.steam_collections_add_to)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                targets.forEach { folder -> TextButton({
                    val additions = selection.filterNot { it in folder.members }.associateWith { true }
                    if (additions.isEmpty()) { folderTargets = null; clearSelection() }
                    else { submitted = true; onChange(SteamCollectionChange.Membership(folder.entry, additions)) }
                },
                    enabled = editable, modifier = Modifier.fillMaxWidth().testTag("collection_add_to_${folder.id}")) { Text(folder.name) } }
                CollectionsStatus(state)
            } }, confirmButton = {}, dismissButton = { TextButton({ folderTargets = null }, enabled = !state.saving) { Text(stringResource(R.string.steam_collections_cancel)) } })
    }
    if (removeSelection && selectionReview != null) {
        val reviewed = selectionReview!!
        AlertDialog(onDismissRequest = { if (!state.saving) removeSelection = false }, title = { Text(stringResource(R.string.steam_collections_remove_from)) },
            text = { Column { Text(stringResource(R.string.steam_collections_remove_hint, selection.size, reviewed.name)); CollectionsStatus(state) } },
            confirmButton = { TextButton({ submitted = true; onChange(SteamCollectionChange.Membership(reviewed.entry, selection.associateWith { false })) },
                enabled = editable, modifier = Modifier.testTag("collection_remove_confirm")) { Text(stringResource(R.string.steam_collections_remove_from)) } },
            dismissButton = { TextButton({ removeSelection = false }, enabled = !state.saving) { Text(stringResource(R.string.steam_collections_cancel)) } })
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.steam_collections_about)) },
        text = { Text(stringResource(R.string.steam_collections_sync_hint)) }, confirmButton = { TextButton({ help = false }) { Text(stringResource(R.string.close)) } })
    when (val current = editor) {
        is CollectionEditor.Name -> {
            var name by remember(current) { mutableStateOf(current.reviewed?.name.orEmpty()) }
            val valid = runCatching { validCollectionName(name) }.isSuccess
            AlertDialog(
                onDismissRequest = { if (!state.saving) { editor = null; submitted = false } },
                title = { Text(stringResource(if (current.reviewed == null) R.string.steam_collections_create else R.string.steam_collections_rename)) },
                text = { Column {
                    OutlinedTextField(name, { name = it }, singleLine = true, enabled = !state.saving,
                        label = { Text(stringResource(R.string.steam_collections_name)) }, modifier = Modifier.testTag("collection_name"))
                    CollectionsStatus(state)
                } },
                confirmButton = { TextButton({ submitted = true; onChange(current.reviewed?.let { SteamCollectionChange.Rename(it.entry, name) } ?: SteamCollectionChange.Create(name)) },
                    enabled = editable && valid, modifier = Modifier.testTag("collection_save")) { Text(stringResource(R.string.steam_collections_save)) } },
                dismissButton = { TextButton({ editor = null; submitted = false }, enabled = !state.saving) { Text(stringResource(R.string.steam_collections_cancel)) } }
            )
        }
        is CollectionEditor.Delete -> AlertDialog(
            onDismissRequest = { if (!state.saving) { editor = null; submitted = false } },
            title = { Text(stringResource(R.string.steam_collections_delete)) },
            text = { Column { Text(stringResource(R.string.steam_collections_delete_hint, current.reviewed.name)); CollectionsStatus(state) } },
            confirmButton = { TextButton({ submitted = true; onChange(SteamCollectionChange.Delete(current.reviewed.entry)) }, enabled = editable,
                modifier = Modifier.testTag("collection_delete_confirm")) { Text(stringResource(R.string.steam_collections_delete)) } },
            dismissButton = { TextButton({ editor = null; submitted = false }, enabled = !state.saving) { Text(stringResource(R.string.steam_collections_cancel)) } }
        )
        is CollectionEditor.Members -> Unit
        null -> Unit
    }
}
@Composable
private fun SteamCollectionMembersPage(collection: SteamCollection, games: List<SteamGame>, state: SteamCollectionsState,
    editable: Boolean, onSave: (Map<Int, Boolean>) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable(collection.entry.key) { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var changes by remember(collection.entry) { mutableStateOf(emptyMap<Int, Boolean>()) }
    var discard by remember { mutableStateOf(false) }
    val libraryGames = remember(games) { games.distinctBy { it.appId }.sortedBy { it.name.lowercase() } }
    val visible = remember(libraryGames, query) { libraryGames.filter { it.name.contains(query, true) } }
    val count = libraryGames.count { changes[it.appId] ?: (it.appId in collection.members) }
    fun leave() { if (!state.saving) { if (changes.isNotEmpty()) discard = true else onDismiss() } }
    fun setVisible(include: Boolean) {
        if (state.saving) return
        changes = changes.toMutableMap().apply { visible.forEach { game ->
            if (include == (game.appId in collection.members)) remove(game.appId) else put(game.appId, include)
        } }
    }
    BackHandler { leave() }
    MonicaFolderBrowserPage(title = stringResource(R.string.steam_collections_edit_games), query = query, onQueryChange = { query = it },
        searching = searching, onSearchingChange = { searching = it }, onBack = { leave() },
        paths = listOf(MonicaFolderPath("root", stringResource(R.string.steam_collections_title)) { leave() }, MonicaFolderPath(collection.id, collection.name) {}),
        folderRows = emptyList(), onOpenFolder = {}, onFolderActions = {}, pageKey = "members:${collection.id}:${query.isNotBlank()}",
        modifier = modifier, pageTag = "collection_members_page", listTag = "collection_games", searchTag = "collection_game_search_toggle", backTag = "collection_members_back",
        searchHint = stringResource(R.string.steam_collections_search),
        status = { Text(stringResource(R.string.steam_collections_members_hint), style = MaterialTheme.typography.bodySmall); CollectionsStatus(state) },
        selectionActions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SelectionActionBar(selectedCount = count, onExit = { leave() }, onSelectAll = { setVisible(true) },
                    selectAllContentDescription = stringResource(R.string.steam_collections_select_visible),
                    exitContentDescription = stringResource(R.string.steam_collections_cancel), modifier = Modifier.weight(1f, fill = false).testTag("collection_selection_bar"),
                    actions = listOf(SelectionActionBarAction(Icons.Default.Deselect, stringResource(R.string.steam_collections_clear_visible), !state.saving) { setVisible(false) }))
                FolderActionFab("collection_members_save", stringResource(R.string.steam_collections_save), Icons.Default.Check, editable && changes.isNotEmpty()) { onSave(changes.toMap()) }
            }
        },
    ) {
        itemsIndexed(visible, key = { _, game -> "member:${game.appId}" }) { index, game ->
            val checked = changes[game.appId] ?: (game.appId in collection.members)
            FolderGameRow(game, "collection_game_${game.appId}", index, visible.size, checked, !state.saving,
                onClick = { changes = changes.toMutableMap().apply { val included = !checked
                    if (included == (game.appId in collection.members)) remove(game.appId) else put(game.appId, included)
                } })
        }
        if (visible.isEmpty()) item { Text(stringResource(R.string.steam_collections_empty), Modifier.padding(24.dp)) }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.steam_collections_discard_title)) },
        text = { Text(stringResource(R.string.steam_collections_discard_message)) },
        confirmButton = { TextButton({ discard = false; onDismiss() }, Modifier.testTag("collection_discard_confirm")) { Text(stringResource(R.string.steam_collections_discard_confirm)) } },
        dismissButton = { TextButton({ discard = false }) { Text(stringResource(R.string.steam_collections_cancel)) } })
}

@Composable
private fun FolderActionFab(tag: String, description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    FloatingActionButton(onClick = { if (enabled) onClick() }, modifier = Modifier.testTag(tag).then(if (enabled) Modifier else Modifier.semantics { disabled() }),
        containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)) { Icon(icon, description) }
}

@Composable
private fun FolderGameRow(game: SteamGame, tag: String, position: Int, total: Int, checked: Boolean?, enabled: Boolean = true,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null, onSwipeRight: () -> Unit = onClick) {
    val shape = GroupedItemDefaults.shape(position, total)
    SwipeActions(onSwipeLeft = {}, onSwipeRight = onSwipeRight, enabled = enabled, allowSwipeLeft = false, contentShape = shape) {
        Surface(Modifier.fillMaxWidth().testTag(tag).clip(shape).then(if (checked == null) Modifier else Modifier.semantics(mergeDescendants = true) {
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off; role = Role.Checkbox
        }), shape = shape, color = if (checked == true) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
            SteamGameLibraryRow(game, onClick, onLongClick = onLongClick, selected = checked, showDivider = false, enabled = enabled, smallIcon = true)
        }
    }
}

@Composable
private fun CollectionsStatus(state: SteamCollectionsState) {
    if (state.loading || state.saving) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(stringResource(if (state.saving) R.string.steam_collections_saving else R.string.steam_collections_loading), style = MaterialTheme.typography.bodySmall)
    }
    val message = when (state.failure) {
        SteamCollectionsFailure.LOGIN -> R.string.steam_collections_error_login
        SteamCollectionsFailure.UNAVAILABLE -> R.string.steam_collections_error_unavailable
        SteamCollectionsFailure.CONFLICT -> R.string.steam_collections_error_conflict
        SteamCollectionsFailure.UNKNOWN -> R.string.steam_collections_error_unknown
        SteamCollectionsFailure.INVALID -> R.string.steam_collections_error_invalid
        SteamCollectionsFailure.NETWORK -> R.string.steam_collections_error_network
        null -> if (state.saved) R.string.steam_collections_saved else null
    }
    message?.let { Text(stringResource(it), Modifier.padding(vertical = 8.dp),
        color = if (state.failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
}
