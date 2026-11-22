package takagi.ru.monica.steam.friends.chat.info.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.SteamListFilterRow
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryCategory
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassHost

/** The profile owns one lazy scroll container; media rows never nest a scrolling grid. */
internal fun LazyListScope.steamSharedHistory(
    history: List<SteamChatHistoryItem>,
    selected: SteamChatHistoryCategory,
    onSelect: (SteamChatHistoryCategory) -> Unit,
    onOpenMessage: (String) -> Unit
) {
    val counts = history.groupingBy { it.category }.eachCount()
    val categories = (SteamChatHistoryCategory.shortcuts + SteamChatHistoryCategory.STICKER)
        .filter { counts.getOrDefault(it, 0) > 0 }.ifEmpty { listOf(SteamChatHistoryCategory.MEDIA) }
    val category = selected.takeIf { it in categories } ?: categories.first()
    val entries = history.filter { it.category == category }.sortedByDescending { it.timestamp }
    item("shared-history-header") {
        ChatContentHeader(categories, category, counts, onSelect)
    }
    if (entries.isEmpty()) {
        item("shared-history-empty") {
            Text(stringResource(R.string.steam_thread_no_shared_content), Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val formatter = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
        entries.groupBy { formatter.format(Date(it.timestamp * 1000)) }.forEach { (month, contents) ->
            item("shared-month-$category-$month") { HistoryDayHeader(month) }
            if (category == SteamChatHistoryCategory.MEDIA || category == SteamChatHistoryCategory.STICKER) {
                items(contents.chunked(3), key = { "shared-media-${it.first().id}" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { entry ->
                            Box(Modifier.weight(1f)) { HistoryMediaCell(entry) { onOpenMessage(entry.id) } }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else items(contents, key = { "shared-item-${it.id}" }) { entry ->
                Box(Modifier.padding(vertical = 4.dp)) { ChatContentRow(entry) { onOpenMessage(entry.id) } }
            }
        }
    }
}

@Composable
internal fun ChatContentHeader(
    categories: List<SteamChatHistoryCategory>,
    selected: SteamChatHistoryCategory,
    counts: Map<SteamChatHistoryCategory, Int>,
    onSelect: (SteamChatHistoryCategory) -> Unit
) {
    // The section has a flat page background. Record that independent sibling only;
    // the title, labels and lenses remain overlays and never sample themselves.
    SteamGlassHost(Modifier.fillMaxWidth().testTag("chat-content-header"),
        backdropColor = MaterialTheme.colorScheme.background, content = {}, overlay = {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.steam_thread_shared_content),
                    Modifier.padding(horizontal = 20.dp).testTag("chat-content-title"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                SteamListFilterRow(categories, selected, onSelect,
                    label = { steamHistoryCategoryLabel(it) }, count = { counts[it] ?: 0 },
                    floatingSelection = true, fillWidth = false, modifier = Modifier.testTag("chat-content-filters"))
            }
        })
}
