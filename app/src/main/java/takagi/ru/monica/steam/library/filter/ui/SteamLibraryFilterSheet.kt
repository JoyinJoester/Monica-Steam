package takagi.ru.monica.steam.library.filter.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryAchievementStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryFilterSelection
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryOwnershipFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlayStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlaytimeFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibrarySortOrder
import takagi.ru.monica.ui.components.MonicaModalBottomSheet

/** A compact entry control matching the former library split button density. */
@Composable
internal fun SteamLibraryFilterEntry(
    selection: SteamLibraryFilterSelection,
    filteredCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = selection.activeChoiceCount > 0
    Surface(
        onClick = onClick,
        modifier = modifier
            .wrapContentWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = stringResource(R.string.steam_library_filter_and_sort),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.steam_library_filter_and_sort),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.steam_library_filter_result_count,
                    filteredCount,
                    totalCount
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SteamLibraryFilterSheet(
    selection: SteamLibraryFilterSelection,
    totalCount: Int,
    filteredCount: (SteamLibraryFilterSelection) -> Int,
    onApply: (SteamLibraryFilterSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var pending by remember(selection) { mutableStateOf(selection) }
    // The count is preview-only. Memoizing it keeps a tap from performing the
    // same expensive work once for the title and again for the apply button.
    val previewCount = remember(pending, totalCount) { filteredCount.invoke(pending) }

    MonicaModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)

        ) {
            SteamSheetHeader(
                title = stringResource(R.string.steam_library_filter_title),
                subtitle = stringResource(R.string.steam_library_filter_result_count, previewCount, totalCount),
                onDismiss = onDismiss
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item(key = "ownership") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_ownership)
                    ) {
                        SteamLibraryOwnershipFilter.entries.forEachIndexed { index, option ->
                            SteamLibraryChoiceChip(
                                selected = pending.ownership == option,
                                label = steamLibraryOwnershipLabel(option),
                                onClick = { pending = pending.copy(ownership = option) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = steamSheetRowShape(index, SteamLibraryOwnershipFilter.entries.size)
                            )
                        }
                    }
                }
                item(key = "play_status") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_play_status)
                    ) {
                        SteamLibraryPlayStatusFilter.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.playStatus == option,
                                label = steamLibraryPlayStatusLabel(option),
                                onClick = { pending = pending.copy(playStatus = option) }
                            )
                        }
                    }
                }
                item(key = "achievement_status") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_achievements)
                    ) {
                        SteamLibraryAchievementStatusFilter.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.achievementStatus == option,
                                label = steamLibraryAchievementStatusLabel(option),
                                onClick = { pending = pending.copy(achievementStatus = option) }
                            )
                        }
                    }
                }
                item(key = "playtime") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_playtime)
                    ) {
                        SteamLibraryPlaytimeFilter.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.playtime == option,
                                label = steamLibraryPlaytimeLabel(option),
                                onClick = { pending = pending.copy(playtime = option) }
                            )
                        }
                    }
                }
                item(key = "features") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_features)
                    ) {
                        SteamLibraryChoiceChip(
                            selected = pending.requiresSteamCloud,
                            label = stringResource(R.string.steam_library_filter_steam_cloud),
                            onClick = {
                                pending = pending.copy(
                                    requiresSteamCloud = !pending.requiresSteamCloud
                                )
                            },
                            role = androidx.compose.ui.semantics.Role.Checkbox
                        )
                    }
                }
                item(key = "sort") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_sort)
                    ) {
                        SteamLibrarySortOrder.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.sortOrder == option,
                                label = steamLibrarySortLabel(option),
                                onClick = { pending = pending.copy(sortOrder = option) }
                            )
                        }
                    }
                }
            }
            SteamSheetFooter {
                TextButton(
                    onClick = { pending = SteamLibraryFilterSelection() },
                    enabled = pending != SteamLibraryFilterSelection(),
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.steam_library_filter_reset)) }
                Button(onClick = { onApply(pending) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp)) {
                    Text(stringResource(R.string.steam_library_filter_apply, previewCount))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteamLibraryChoiceGroup(
    title: String,
    content: @Composable FlowRowScope.() -> Unit
) {
    SteamSheetSection(title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SteamLibraryChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    role: androidx.compose.ui.semantics.Role = androidx.compose.ui.semantics.Role.RadioButton
) {
    SteamSheetChoice(label, selected, onClick, modifier, shape,
        role = role)
}

@Composable
private fun steamLibraryOwnershipLabel(option: SteamLibraryOwnershipFilter): String =
    stringResource(
        when (option) {
            SteamLibraryOwnershipFilter.ALL -> R.string.steam_library_filter_ownership_all
            SteamLibraryOwnershipFilter.OWNED -> R.string.steam_library_filter_ownership_owned
            SteamLibraryOwnershipFilter.FAMILY_SHARED ->
                R.string.steam_library_filter_family_shared
        }
    )

@Composable
private fun steamLibraryPlayStatusLabel(option: SteamLibraryPlayStatusFilter): String =
    stringResource(
        when (option) {
            SteamLibraryPlayStatusFilter.ALL -> R.string.steam_library_filter_status_all
            SteamLibraryPlayStatusFilter.UNPLAYED -> R.string.steam_library_filter_unplayed
            SteamLibraryPlayStatusFilter.PLAYED -> R.string.steam_library_filter_played
            SteamLibraryPlayStatusFilter.RECENT -> R.string.steam_library_filter_recent
        }
    )

@Composable
private fun steamLibraryAchievementStatusLabel(
    option: SteamLibraryAchievementStatusFilter
): String = stringResource(
    when (option) {
        SteamLibraryAchievementStatusFilter.ALL -> R.string.steam_library_filter_achievement_all
        SteamLibraryAchievementStatusFilter.PERFECT -> R.string.steam_library_filter_perfect
        SteamLibraryAchievementStatusFilter.INCOMPLETE ->
            R.string.steam_library_filter_achievement_incomplete
        SteamLibraryAchievementStatusFilter.NO_ACHIEVEMENTS ->
            R.string.steam_library_filter_achievement_none
    }
)

@Composable
private fun steamLibraryPlaytimeLabel(option: SteamLibraryPlaytimeFilter): String =
    stringResource(
        when (option) {
            SteamLibraryPlaytimeFilter.ANY -> R.string.steam_library_filter_playtime_any
            SteamLibraryPlaytimeFilter.UNDER_TWO_HOURS ->
                R.string.steam_library_filter_playtime_under_two
            SteamLibraryPlaytimeFilter.TWO_TO_TWENTY_HOURS ->
                R.string.steam_library_filter_playtime_two_to_twenty
            SteamLibraryPlaytimeFilter.OVER_TWENTY_HOURS ->
                R.string.steam_library_filter_playtime_over_twenty
        }
    )

@Composable
private fun steamLibrarySortLabel(option: SteamLibrarySortOrder): String = stringResource(
    when (option) {
        SteamLibrarySortOrder.SMART -> R.string.steam_library_sort_smart
        SteamLibrarySortOrder.RECENT_PLAYTIME -> R.string.steam_library_sort_recent_playtime
        SteamLibrarySortOrder.TOTAL_PLAYTIME -> R.string.steam_library_sort_total_playtime
        SteamLibrarySortOrder.NAME_ASCENDING -> R.string.steam_library_sort_name_ascending
        SteamLibrarySortOrder.NAME_DESCENDING -> R.string.steam_library_sort_name_descending
    }
)
