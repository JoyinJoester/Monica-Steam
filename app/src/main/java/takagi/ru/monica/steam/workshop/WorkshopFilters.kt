package takagi.ru.monica.steam.workshop

import takagi.ru.monica.steam.foundation.ui.SteamListFilterChip as FilterChip

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WorkshopFilters(query: WorkshopQuery, tags: List<WorkshopTag>, dismiss: () -> Unit, apply: (WorkshopQuery) -> Unit) {
    var draft by remember(query) { mutableStateOf(query) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.workshop_filters), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.workshop_sort), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkshopSort.entries.filter { !draft.subscribedOnly || it != WorkshopSort.POPULAR }.forEach { sort ->
                    FilterChip(selected = draft.sort == sort, onClick = { draft = draft.copy(sort = sort) }, label = {
                        Text(stringResource(when (sort) {
                            WorkshopSort.POPULAR -> R.string.workshop_popular
                            WorkshopSort.MOST_SUBSCRIBED -> R.string.workshop_most_subscribed
                            WorkshopSort.NEWEST -> R.string.workshop_newest
                            WorkshopSort.UPDATED -> R.string.workshop_updated
                        }))
                    })
                }
            }
            if (draft.sort == WorkshopSort.POPULAR && !draft.subscribedOnly) {
                Text(stringResource(R.string.workshop_period), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to R.string.workshop_day, 7 to R.string.workshop_week, 30 to R.string.workshop_month, -1 to R.string.workshop_all_time).forEach { (days, label) ->
                        FilterChip(selected = draft.days == days, onClick = { draft = draft.copy(days = days) }, label = { Text(stringResource(label)) })
                    }
                }
            }
            Text(stringResource(R.string.workshop_tags), style = MaterialTheme.typography.titleSmall)
            if (tags.isEmpty()) Text(stringResource(R.string.workshop_no_tags))
            tags.groupBy { it.group }.forEach { (group, values) ->
                if (group.isNotBlank()) Text(group, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    values.forEach { tag ->
                        FilterChip(selected = tag.value in draft.tags, onClick = {
                            draft = draft.copy(tags = if (tag.value in draft.tags) draft.tags - tag.value else draft.tags + tag.value)
                        }, label = { Text(tag.label) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { draft = WorkshopQuery(search = query.search, subscribedOnly = query.subscribedOnly,
                    sort = if (query.subscribedOnly) WorkshopSort.UPDATED else WorkshopSort.POPULAR) }) { Text(stringResource(R.string.workshop_reset)) }
                Button(onClick = { apply(draft) }) { Text(stringResource(R.string.workshop_apply)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
