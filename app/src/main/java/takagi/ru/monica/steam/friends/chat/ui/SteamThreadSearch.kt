package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.info.domain.*
import takagi.ru.monica.steam.friends.chat.info.ui.HistoryRow
import takagi.ru.monica.steam.friends.chat.info.ui.steamHistoryCategoryLabel
import takagi.ru.monica.steam.friends.chat.info.ui.steamHistoryRangeLabel
import takagi.ru.monica.steam.friends.chat.telegram.TelegramGlassSurface

@Stable
internal class SteamThreadSearchState {
    var active by mutableStateOf(false)
    var text by mutableStateOf("")
    var category by mutableStateOf(SteamChatHistoryCategory.ALL)
    var sender by mutableStateOf<String?>(null)
    var range by mutableStateOf(SteamChatHistoryRange.ANY_TIME)
    var showResults by mutableStateOf(false)
    val jump = SteamChatJumpRequest()
    fun close() { active = false; showResults = false }
}

@Composable
internal fun steamThreadSearchResults(state: SteamThreadSearchState, items: List<SteamChatHistoryItem>): List<SteamChatHistoryItem> {
    val results = remember(items, state.text, state.category, state.sender, state.range) {
        SteamChatHistoryQuery(state.text, state.category, state.sender, state.range).apply(items)
    }
    LaunchedEffect(state.active, state.text, state.category, state.sender, state.range) {
        if (state.active) {
            results.firstOrNull()?.let { state.jump.request(it.id) } ?: state.jump.clear()
        }
    }
    // Live updates do not reset an existing selection; a removed hit gets a valid replacement.
    LaunchedEffect(state.active, results) {
        if (state.active && results.none { it.id == state.jump.messageId }) {
            results.firstOrNull()?.let { state.jump.request(it.id) } ?: state.jump.clear()
        }
    }
    BackHandler(state.active && !state.showResults) { state.close() }
    return results
}

@Composable
internal fun SteamThreadSearchHeader(state: SteamThreadSearchState, items: List<SteamChatHistoryItem>, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var filtersOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { focus.clearFocus(); keyboard?.hide(); state.close() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.steam_chat_close_search))
            }
            takagi.ru.monica.steam.foundation.ui.SteamListSearchField(
                state.text, { state.text = it }, stringResource(R.string.steam_thread_search),
                Modifier.weight(1f), autoFocus = true,
                onSearch = { focus.clearFocus(); keyboard?.hide() })
            IconButton(onClick = { filtersOpen = true }) {
                    Icon(Icons.Default.FilterList, stringResource(R.string.steam_thread_search_filters))
                }
                DropdownMenu(expanded = filtersOpen, onDismissRequest = { filtersOpen = false }) {
                    SteamChatHistoryRange.entries.forEach { range ->
                        DropdownMenuItem(text = { Text((if (state.range == range) "✓ " else "") + steamHistoryRangeLabel(range)) },
                            onClick = { state.range = range; filtersOpen = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.steam_thread_all_senders)) },
                        onClick = { state.sender = null; filtersOpen = false })
                    steamChatHistorySenders(items).forEach { (sender, _) ->
                        DropdownMenuItem(text = { Text((if (state.sender == sender) "✓ " else "") + sender) },
                            onClick = { state.sender = sender; filtersOpen = false })
                    }
                }
        }
        takagi.ru.monica.steam.foundation.ui.SteamListFilterRow(
            SteamChatHistoryCategory.filters, state.category, { state.category = it },
            label = { steamHistoryCategoryLabel(it) })
        if (state.sender != null || state.range != SteamChatHistoryRange.ANY_TIME) {
            Text(listOfNotNull(state.sender, state.range.takeUnless { it == SteamChatHistoryRange.ANY_TIME }?.let { steamHistoryRangeLabel(it) })
                .joinToString(" · "), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamThreadSearchBottom(state: SteamThreadSearchState, results: List<SteamChatHistoryItem>) {
    val index = results.indexOfFirst { it.id == state.jump.messageId }
    val focus = LocalFocusManager.current
    TelegramGlassSurface(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), cornerRadius = 28.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = index >= 0 && index < results.lastIndex, onClick = { state.jump.request(results[index + 1].id) }) {
                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.steam_thread_older_match))
            }
            IconButton(enabled = index > 0, onClick = { state.jump.request(results[index - 1].id) }) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.steam_thread_newer_match))
            }
            Text(if (results.isEmpty()) stringResource(R.string.steam_thread_no_matches) else "${index + 1} / ${results.size}",
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { focus.clearFocus(); state.showResults = true }, enabled = results.isNotEmpty()) {
                Text(stringResource(R.string.steam_thread_results_list))
            }
        }
    }
    if (state.showResults) ModalBottomSheet(onDismissRequest = { state.showResults = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            items(results, key = SteamChatHistoryItem::id) { item ->
                HistoryRow(item, state.text, item.id == state.jump.messageId) {
                    state.jump.request(item.id)
                    state.showResults = false
                }
            }
        }
    }
}
