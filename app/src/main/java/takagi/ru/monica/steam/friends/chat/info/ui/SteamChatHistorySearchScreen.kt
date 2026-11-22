package takagi.ru.monica.steam.friends.chat.info.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassScaffold as Scaffold
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryQuery
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryRange
import takagi.ru.monica.steam.friends.chat.info.domain.steamChatHistorySenders
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialTheme

/**
 * Chat history search, laid out with Material 3 Expressive pieces: a large collapsing title whose
 * action pill grows into the search field, morphing filter chips, and a tonal bottom bar. Tapping a
 * hit closes this page and asks the thread to scroll to that exact message.
 */
@Composable
internal fun SteamChatHistorySearchScreen(
    items: List<SteamChatHistoryItem>,
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialCategory: SteamChatHistoryCategory = SteamChatHistoryCategory.ALL,
    initialGridMode: Boolean = false,
    initialQuery: String = "",
    initialSearchExpanded: Boolean = false
) {
    TelegramSocialTheme {
        var text by rememberSaveable { mutableStateOf(initialQuery) }
        var category by rememberSaveable { mutableStateOf(initialCategory) }
        var sender by rememberSaveable { mutableStateOf<String?>(null) }
        var rangeName by rememberSaveable { mutableStateOf(SteamChatHistoryRange.ANY_TIME.name) }
        var newestFirst by rememberSaveable { mutableStateOf(true) }
        var gridMode by rememberSaveable { mutableStateOf(initialGridMode) }
        var searchExpanded by rememberSaveable { mutableStateOf(initialSearchExpanded) }
        var selection by remember { mutableIntStateOf(0) }
        // Scrolling to the first hit on open hid the day header above it; only follow the
        // selection once the reader has actually moved the match rail.
        var selectionMoved by remember { mutableStateOf(false) }

        val range = SteamChatHistoryRange.valueOf(rangeName)
        val filter = SteamChatHistoryQuery(text, category, sender, range, newestFirst)
        val results = remember(items, filter) { filter.apply(items) }
        val counts = remember(items, filter) { filter.countsOf(items) }
        val entries = remember(results) { historyEntries(results) }
        val senders = remember(items) { steamChatHistorySenders(items) }
        val mediaHits = remember(results) { results.filter { it.mediaUrl != null } }
        val listState = rememberLazyListState()
        val focus = LocalFocusManager.current

        LaunchedEffect(results) { selection = selection.coerceIn(0, results.lastIndex.coerceAtLeast(0)) }
        LaunchedEffect(selection, entries) {
            if (!selectionMoved) return@LaunchedEffect
            val row = entries.indexOfFirst { it is HistoryEntry.Hit && it.position == selection }
            if (row >= 0) listState.animateScrollToItem(row)
        }

        val filtered = text.isNotBlank() || category != SteamChatHistoryCategory.ALL ||
            sender != null || range != SteamChatHistoryRange.ANY_TIME

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                HistorySearchTopBar(
                    title = "聊天记录",
                    query = text,
                    onQueryChange = { text = it; selection = 0 },
                    expanded = searchExpanded,
                    onExpandedChange = {
                        searchExpanded = it
                        if (!it) focus.clearFocus()
                    },
                    resultCount = results.size,
                    onBack = onBack,
                    onSearch = {
                        focus.clearFocus()
                        results.getOrNull(selection)?.let { onOpenMessage(it.id) }
                        searchExpanded = false
                    }
                )
            },
            bottomBar = {
                HistoryBottomBar(
                    range = range,
                    sender = sender,
                    senders = senders,
                    newestFirst = newestFirst,
                    gridMode = gridMode,
                    onRangeChange = { rangeName = it.name; selection = 0 },
                    onSenderChange = { sender = it; selection = 0 },
                    onToggleSort = { newestFirst = !newestFirst },
                    onToggleView = { gridMode = !gridMode }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                CategoryStrip(
                    counts = counts,
                    selected = category,
                    onSelect = { category = it; selection = 0 }
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        results.isEmpty() -> HistoryEmptyState(
                            title = historyEmptyMessage(text, category, filter),
                            subtitle = if (text.isNotBlank()) "换个关键词，或放宽下面的筛选。" else null,
                            onReset = if (filtered) {
                                {
                                    text = ""
                                    category = SteamChatHistoryCategory.ALL
                                    sender = null
                                    rangeName = SteamChatHistoryRange.ANY_TIME.name
                                    selection = 0
                                }
                            } else null
                        )
                        gridMode -> MediaWall(mediaHits, onOpenMessage)
                        else -> ResultList(
                            entries = entries,
                            query = text,
                            selection = selection,
                            listState = listState,
                            onOpenMessage = onOpenMessage
                        )
                    }
                    // The rail walks list hits; the media wall shows a different subset, so a "3/8"
                    // counter over 2 tiles would be a lie. Grid browsing is plain scrolling. A single
                    // hit has nowhere to walk to, so the rail would just be a stranded "1/1".
                    if (!gridMode && results.size > 1) {
                        MatchNavRail(
                            position = selection,
                            total = results.size,
                            onPrevious = {
                                selection = (selection - 1).coerceAtLeast(0)
                                selectionMoved = true
                            },
                            onNext = {
                                selection = (selection + 1).coerceAtMost(results.lastIndex)
                                selectionMoved = true
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }
}

private sealed interface HistoryEntry {
    data class Day(val label: String) : HistoryEntry
    data class Hit(val item: SteamChatHistoryItem, val position: Int) : HistoryEntry
}

/** Flattens day headers and hits into one list so match navigation can scroll by index. */
private fun historyEntries(results: List<SteamChatHistoryItem>): List<HistoryEntry> {
    val entries = ArrayList<HistoryEntry>(results.size + 4)
    var lastLabel: String? = null
    results.forEachIndexed { index, item ->
        val label = historyDayLabel(item.timestamp)
        if (label != lastLabel) {
            entries += HistoryEntry.Day(label)
            lastLabel = label
        }
        entries += HistoryEntry.Hit(item, index)
    }
    return entries
}

private fun historyEmptyMessage(
    query: String,
    category: SteamChatHistoryCategory,
    filtered: SteamChatHistoryQuery
): String = when {
    query.isNotBlank() -> "没有找到「${query.trim()}」"
    filtered.sender != null -> "这个发送者在当前范围里没有消息"
    filtered.range != SteamChatHistoryRange.ANY_TIME -> "这个时间段里没有消息"
    category != SteamChatHistoryCategory.ALL -> "这个会话还没有${category.label}内容"
    else -> "这个会话还没有可以检索的消息"
}

/** Morphing filter chips that carry their own result counters. */
@Composable
private fun CategoryStrip(
    counts: Map<SteamChatHistoryCategory, Int>,
    selected: SteamChatHistoryCategory,
    onSelect: (SteamChatHistoryCategory) -> Unit
) {
    takagi.ru.monica.steam.foundation.ui.SteamListFilterRow(
        SteamChatHistoryCategory.filters, selected, onSelect,
        label = { it.label }, count = { counts[it] ?: 0 })
}

@Composable
private fun ResultList(
    entries: List<HistoryEntry>,
    query: String,
    selection: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenMessage: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // The floating match rail sits on the right edge; rows already inset themselves for it.
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(
            entries,
            key = { entry ->
                when (entry) {
                    is HistoryEntry.Day -> "day-${entry.label}"
                    is HistoryEntry.Hit -> "hit-${entry.item.id}"
                }
            }
        ) { entry ->
            when (entry) {
                is HistoryEntry.Day -> HistoryDayHeader(entry.label)
                is HistoryEntry.Hit -> HistoryRow(
                    item = entry.item,
                    query = query,
                    selected = entry.position == selection,
                    onClick = { onOpenMessage(entry.item.id) }
                )
            }
        }
    }
}

@Composable
private fun MediaWall(items: List<SteamChatHistoryItem>, onOpenMessage: (String) -> Unit) {
    if (items.isEmpty()) {
        HistoryEmptyState(title = "当前筛选里没有图片")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { it.id }) { item ->
            HistoryMediaCell(item) { onOpenMessage(item.id) }
        }
    }
}
