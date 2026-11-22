package takagi.ru.monica.steam.friends.chat.info.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassDropdownMenu as DropdownMenu
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryRange
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialSearchTopBar
import takagi.ru.monica.steam.foundation.ui.SteamListChoiceChip as MonicaExpressiveFilterChip

/**
 * Chat-owned search header keeps the live hit count beside the search entry point.
 */
@Composable
internal fun HistorySearchTopBar(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    resultCount: Int,
    onBack: () -> Unit,
    onSearch: () -> Unit
) {
    TelegramSocialSearchTopBar(
        title = title,
        searchQuery = query,
        onSearchQueryChange = onQueryChange,
        isSearchExpanded = expanded,
        onSearchExpandedChange = onExpandedChange,
        searchHint = "搜索消息",
        modifier = Modifier.statusBarsPadding(),
        onBack = onBack,
        onSearch = onSearch,
        actions = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    "$resultCount 条",
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    )
}

/** One morphing chip per result bucket; the count rides inside the label. */
@Composable
internal fun CategoryChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    MonicaExpressiveFilterChip(
        selected = selected,
        onClick = onClick,
        label = if (count > 0) "$label $count" else label,
        // Material's own selected state: a check mark appears as the corners square off. Passing it
        // unconditionally put a tick on every chip, which read as "all filters are on".
        selectedLeadingIcon = if (selected) Icons.Rounded.Check else null
    )
}

/**
 * Tonal bottom bar. The two chips always show the active period and sender, which the old
 * icon-only row left entirely to guesswork.
 */
@Composable
internal fun HistoryBottomBar(
    range: SteamChatHistoryRange,
    sender: String?,
    senders: List<Pair<String, Int>>,
    newestFirst: Boolean,
    gridMode: Boolean,
    onRangeChange: (SteamChatHistoryRange) -> Unit,
    onSenderChange: (String?) -> Unit,
    onToggleSort: () -> Unit,
    onToggleView: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterMenuChip(
                icon = Icons.Rounded.DateRange,
                label = if (range == SteamChatHistoryRange.ANY_TIME) "时间" else range.label,
                active = range != SteamChatHistoryRange.ANY_TIME
            ) { dismiss ->
                SteamChatHistoryRange.values().forEach { option ->
                    MenuRow(option.label, option == range) { onRangeChange(option); dismiss() }
                }
            }
            FilterMenuChip(
                icon = Icons.Rounded.Person,
                label = sender ?: "发送者",
                active = sender != null
            ) { dismiss ->
                MenuRow("全部发送者", sender == null) { onSenderChange(null); dismiss() }
                senders.take(12).forEach { (name, count) ->
                    MenuRow("$name ($count)", name == sender) { onSenderChange(name); dismiss() }
                }
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = onToggleSort,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.SwapVert,
                    contentDescription = if (newestFirst) "最新优先" else "最早优先",
                    modifier = Modifier.size(20.dp)
                )
            }
            FilledTonalIconButton(
                onClick = onToggleView,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (gridMode) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                    contentDescription = if (gridMode) "以列表显示" else "以网格显示",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterMenuChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    content: @Composable (dismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
            },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            colors = if (active) {
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                AssistChipDefaults.assistChipColors()
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@Composable
private fun MenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
        } else null
    )
}

/** Floating hit walker: stays put while the list scrolls underneath. */
@Composable
internal fun MatchNavRail(
    position: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (total == 0) return
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        modifier = modifier.padding(end = 10.dp)
    ) {
        Column(
            Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onPrevious, enabled = position > 0, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, "上一条", Modifier.size(22.dp))
            }
            Text(
                "${position + 1}/$total",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onNext,
                enabled = position < total - 1,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, "下一条", Modifier.size(22.dp))
            }
        }
    }
}

/** Tonal empty state: the icon carries the tone, the copy explains why the list is blank. */
@Composable
internal fun HistoryEmptyState(title: String, subtitle: String? = null, onReset: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            title,
            Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                subtitle,
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (onReset != null) {
            FilledTonalButton(onClick = onReset, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Rounded.FilterAltOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("清除筛选", Modifier.padding(start = 8.dp))
            }
        }
    }
}
