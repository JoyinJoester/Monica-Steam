package takagi.ru.monica.ui.folders

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.GroupedItemDefaults
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.navigation.ui.steamWindowTopPadding

internal data class MonicaFolderRowData(val key: String, val title: String, val subtitle: String, val enabled: Boolean = true)
internal data class MonicaFolderPath(val key: String, val title: String, val onOpen: () -> Unit)

/**
 * Folder presentation extracted from Monica Android's VaultV2List/VaultV2FolderRow
 * and PasswordQuickFolderBreadcrumbPath. The owner supplies rows and operations;
 * this layer contains no password, database or cloud persistence.
 */
@Composable
internal fun MonicaFolderBrowserPage(
    title: String, query: String, onQueryChange: (String) -> Unit,
    searching: Boolean, onSearchingChange: (Boolean) -> Unit,
    onBack: () -> Unit, paths: List<MonicaFolderPath>,
    folderRows: List<MonicaFolderRowData>, onOpenFolder: (String) -> Unit,
    onFolderActions: (String) -> Unit, pageKey: String,
    modifier: Modifier = Modifier, pageTag: String = "collections_page",
    listTag: String = "collection_list", searchTag: String = "collection_search_toggle",
    backTag: String = "collections_back", searchHint: String = "",
    selectedFolderKey: String? = null,
    topMenu: @Composable () -> Unit = {},
    status: @Composable ColumnScope.() -> Unit = {},
    floatingAction: @Composable () -> Unit = {},
    selectionActions: (@Composable () -> Unit)? = null,
    rows: LazyListScope.() -> Unit,
) {
    val lists = rememberSaveableStateHolder()
    val clearance = LocalSteamDockContentClearance.current
    val reduce = LocalReduceAnimations.current
    SteamGlassScaffold(
        modifier = modifier.fillMaxSize().testTag(pageTag), contentWindowInsets = WindowInsets(0,0,0,0),
        topBar = {
            Column {
                ExpressiveTopBar(title, query, onQueryChange, searching, onSearchingChange,
                    searchHint = searchHint, compact = true, collapsedTitleEndPadding = 100.dp,
                    titleOverflow = TextOverflow.Ellipsis, modifier = Modifier.steamWindowTopPadding(),
                    navigationIcon = { IconButton(onBack, Modifier.testTag(backTag)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    actions = {
                        IconButton({ onSearchingChange(true) }, Modifier.testTag(searchTag)) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                        topMenu()
                    })
                Column(Modifier.fillMaxWidth().animateContentSize(tween(if (reduce) 0 else 200)).padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("collection_path"), verticalAlignment = Alignment.CenterVertically) {
                        paths.forEachIndexed { i, path ->
                            if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp))
                            TextButton(path.onOpen, Modifier.testTag("collection_path_${path.key}")) {
                                if (i == 0) { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                                Text(path.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    status()
                }
            }
        },
        floatingActionButton = { Box(Modifier.padding(bottom = clearance)) { floatingAction() } },
        bottomBar = {
            if (selectionActions != null) Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = clearance + 16.dp), contentAlignment = Alignment.CenterStart) { selectionActions() }
        },
    ) { padding ->
        lists.SaveableStateProvider(pageKey) {
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag(listTag),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = clearance + 88.dp),
                verticalArrangement = Arrangement.spacedBy(GroupedItemDefaults.Spacing)) {
                itemsIndexed(folderRows, key = { _, row -> "folder:${row.key}" }, contentType = { _, _ -> "folder" }) { index, row ->
                    MonicaFolderRow(row, index, folderRows.size, selectedFolderKey == row.key,
                        { onOpenFolder(row.key) }, { onFolderActions(row.key) })
                }
                rows()
            }
        }
    }
}

/** Same 68dp row, circular icon, typography and grouped shape as VaultV2FolderRow. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonicaFolderRow(row: MonicaFolderRowData, index: Int, count: Int, selected: Boolean, onOpen: () -> Unit, onActions: () -> Unit) {
    val shape = GroupedItemDefaults.shape(index, count)
    Surface(Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag("collection_${row.key}").clip(shape)
        .combinedClickable(enabled = row.enabled, onClick = onOpen, onLongClick = onActions,
            onLongClickLabel = stringResource(R.string.more_options)),
        shape = shape, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(if (row.enabled) Icons.Default.Folder else Icons.Default.Lock, null, Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(if (row.enabled) Icons.AutoMirrored.Filled.ArrowRight else Icons.Default.Lock, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
