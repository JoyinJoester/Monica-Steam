package takagi.ru.monica.steam.store.filters.ui

import takagi.ru.monica.steam.navigation.liquidglass.ui.*

import takagi.ru.monica.steam.navigation.liquidglass.ui.SteamGlassModalBottomSheet as ModalBottomSheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.key
import takagi.ru.monica.steam.foundation.ui.SteamListFilterRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterMetadata
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterOption
import takagi.ru.monica.steam.store.filters.domain.SteamStoreFilterSelection
import takagi.ru.monica.steam.store.filters.domain.SteamStoreTagOption

private enum class StoreFilterSection { PRICE, LANGUAGES, TAGS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SteamStoreAdvancedFilterSheet(
    selection: SteamStoreFilterSelection,
    metadata: SteamStoreFilterMetadata?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onApply: (SteamStoreFilterSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(selection) { mutableStateOf(selection) }
    var tagsExpanded by rememberSaveable { mutableStateOf(false) }
    var tagQuery by rememberSaveable { mutableStateOf("") }
    var languageQuery by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(StoreFilterSection.PRICE) }
    val selectedTagOptions = remember(metadata, draft.tagIds) {
        metadata?.tags.orEmpty().filter { it.id in draft.tagIds }
    }
    val visibleTagOptions = remember(metadata, draft.tagIds, tagQuery, tagsExpanded) {
        val all = metadata?.tags.orEmpty()
        val matching = if (tagQuery.isBlank()) {
            all.take(if (tagsExpanded) MAX_SEARCHED_TAGS else DEFAULT_VISIBLE_TAGS)
        } else {
            all.filter { option ->
                option.label.contains(tagQuery.trim(), ignoreCase = true)
            }.take(MAX_SEARCHED_TAGS)
        }
        (selectedTagOptions + matching).distinctBy(SteamStoreTagOption::id)
    }

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)

        ) {
            SteamSheetHeader(
                title = stringResource(R.string.steam_store_advanced_filters),
                subtitle = stringResource(R.string.steam_store_active_filter_count, draft.activeCount),
                onDismiss = onDismiss
            )

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            SteamListFilterRow(StoreFilterSection.entries, section, { section = it },
                label = { stringResource(when(it) {
                    StoreFilterSection.PRICE -> R.string.steam_store_filter_price
                    StoreFilterSection.LANGUAGES -> R.string.steam_store_filter_languages
                    StoreFilterSection.TAGS -> R.string.steam_store_filter_tags
                }) }, modifier = Modifier.testTag("store-filter-sections"), count = {
                    when(it) {
                        StoreFilterSection.PRICE -> if(draft.maxPrice != null) 1 else 0
                        StoreFilterSection.LANGUAGES -> draft.supportedLanguageIds.size
                        StoreFilterSection.TAGS -> draft.tagIds.size
                    }
                })

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (metadata == null && loading) {
                    item(key = "filter_loading") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(R.string.steam_store_filter_metadata_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (error != null) {
                    item(key = "filter_error") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.steam_store_filter_metadata_error),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(error, style = MaterialTheme.typography.bodyMedium)
                                FilledTonalButton(
                                    onClick = onRetry,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.steam_store_retry))
                                }
                            }
                        }
                    }
                }
                metadata?.let { options ->
                    if (section == StoreFilterSection.PRICE) item(key = "filter_price") {
                        FilterSectionCard(
                            title = stringResource(R.string.steam_store_filter_price)
                        ) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                StoreOptionChip(stringResource(R.string.steam_store_filter_any_price), draft.maxPrice == null,
                                    { draft = draft.copy(maxPrice = null) }, role = androidx.compose.ui.semantics.Role.RadioButton,
                                    modifier = Modifier.fillMaxWidth())
                                options.priceOptions.forEach { option ->
                                    StoreOptionChip(option.label, draft.maxPrice == option.value,
                                        { draft = draft.copy(maxPrice = option.value) }, role = androidx.compose.ui.semantics.Role.RadioButton,
                                        modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    if (section == StoreFilterSection.LANGUAGES) item(key = "filter_languages") {
                        FilterSectionCard(title = stringResource(R.string.steam_store_filter_languages)) {
                            SteamSheetSearchField(languageQuery, { languageQuery = it }, stringResource(R.string.search))
                            options.languages.filter { languageQuery.isBlank() || it.label.contains(languageQuery.trim(),true) }
                                .chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { option ->
                                        StoreOptionChip(option.label, option.value in draft.supportedLanguageIds,
                                            { draft = draft.copy(supportedLanguageIds = draft.supportedLanguageIds.toggle(option.value)) },
                                            modifier = Modifier.weight(1f))
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (section == StoreFilterSection.TAGS) item(key = "filter_tags") {
                        FilterSectionCard(
                            title = stringResource(R.string.steam_store_filter_tags),
                            action = {
                                TextButton(
                                    onClick = { tagsExpanded = !tagsExpanded },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        stringResource(
                                            if (tagsExpanded) {
                                                R.string.steam_store_filter_collapse_tags
                                            } else {
                                                R.string.steam_store_filter_expand_tags
                                            }
                                        )
                                    )
                                    Icon(
                                        imageVector = if (tagsExpanded) {
                                            Icons.Default.ExpandLess
                                        } else {
                                            Icons.Default.ExpandMore
                                        },
                                        contentDescription = null
                                    )
                                }
                            }
                        ) {
                            SteamSheetSearchField(tagQuery, { tagQuery = it },
                                stringResource(R.string.steam_store_filter_tag_search))
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                visibleTagOptions.forEach { option ->
                                    StoreOptionChip(option.label, option.id in draft.tagIds,
                                        { draft = draft.copy(tagIds = draft.tagIds.toggle(option.id)) })
                                }
                            }
                        }

                    }
                }
            }

            SteamSheetFooter {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { draft = SteamStoreFilterSelection() },
                        modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.steam_store_filter_reset))
                    }
                    Button(onClick = { onApply(draft.normalized()) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp)) {
                        Text(stringResource(R.string.steam_store_filter_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SteamSheetSection(title, action = action) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun StoreOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    role: androidx.compose.ui.semantics.Role = androidx.compose.ui.semantics.Role.Checkbox,
    modifier: Modifier = Modifier
) {
    SteamSheetChoice(label, selected, onClick, role = role, modifier = modifier)
}

@Composable
internal fun SteamStoreActiveFilterSummary(
    selection: SteamStoreFilterSelection,
    metadata: SteamStoreFilterMetadata?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!selection.isActive) return
    val priceLabel = selection.maxPrice?.let { value ->
        metadata?.priceOptions?.firstOrNull { it.value == value }?.label ?: value
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        priceLabel?.let { label ->
            item(key = "summary_price") {
                FilterSummaryPill(
                    stringResource(R.string.steam_store_filter_price_summary, label)
                )
            }
        }
        if (selection.supportedLanguageIds.isNotEmpty()) {
            item(key = "summary_languages") {
                FilterSummaryPill(
                    stringResource(
                        R.string.steam_store_filter_language_summary,
                        selection.supportedLanguageIds.size
                    )
                )
            }
        }
        if (selection.tagIds.isNotEmpty()) {
            item(key = "summary_tags") {
                FilterSummaryPill(
                    stringResource(
                        R.string.steam_store_filter_tag_summary,
                        selection.tagIds.size
                    )
                )
            }
        }
        item(key = "summary_clear") {
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.steam_store_filter_clear))
            }
        }
    }
}

@Composable
private fun FilterSummaryPill(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamStoreTagBadges(
    labels: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3
) {
    if (labels.isEmpty() || maxVisible <= 0) return
    val visible = labels.distinct().take(maxVisible)
    val remaining = (labels.distinct().size - visible.size).coerceAtLeast(0)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { label ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    maxLines = 1
                )
            }
        }
        if (remaining > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.steam_store_filter_more_tags, remaining),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private const val DEFAULT_VISIBLE_TAGS = 12
private const val MAX_SEARCHED_TAGS = 60
