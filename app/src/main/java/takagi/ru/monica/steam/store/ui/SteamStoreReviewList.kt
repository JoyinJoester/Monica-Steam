package takagi.ru.monica.steam.store.ui

import takagi.ru.monica.steam.foundation.ui.SteamListFilterChip as FilterChip

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamReviewFilterSelection
import takagi.ru.monica.steam.store.domain.SteamReviewSentimentFilter
import takagi.ru.monica.steam.store.domain.SteamReviewTimeFilter
import takagi.ru.monica.steam.store.domain.SteamStoreReviews
import takagi.ru.monica.steam.store.domain.SteamUserReview
import takagi.ru.monica.steam.richtext.ui.SteamRichText
import takagi.ru.monica.ui.components.MonicaModalBottomSheet

@Composable
internal fun SteamStoreReviewsSection(
    appId: Int,
    reviews: SteamStoreReviews,
    filters: SteamReviewFilterSelection,
    loadingMore: Boolean,
    loadError: String?,
    onFiltersChanged: (SteamReviewFilterSelection) -> Unit,
    onLoadMore: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    modifier: Modifier = Modifier,
    authorLoader: (suspend (String) -> takagi.ru.monica.steam.profile.SteamMiniProfileDecor?)? = null
) {
    var expanded by rememberSaveable(appId) { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable(appId) { mutableStateOf(false) }
    val visibleReviews = if (expanded) reviews.items else reviews.items.take(REVIEW_PREVIEW_COUNT)
    val effectiveError = loadError ?: if (reviews.pageLoadFailed && !loadingMore) {
        stringResource(R.string.steam_store_reviews_unavailable)
    } else null
    val canExpand = !expanded && (
        reviews.items.size > REVIEW_PREVIEW_COUNT || reviews.nextCursor != null
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SteamStoreReviewSummarySection(reviews = reviews)
        SteamStoreReviewFilters(
            filters = filters,
            enabled = !loadingMore,
            onClick = { showFilterSheet = true }
        )
        if (loadingMore && reviews.items.isEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        visibleReviews.forEach { review ->
            SteamStoreReviewCard(
                review = review,
                showFullBody = expanded,
                onOpenAuthor = onOpenAuthor,
                authorLoader = authorLoader
            )
        }
        if (reviews.items.isEmpty() && !loadingMore && effectiveError == null) {
            Text(stringResource(R.string.steam_store_reviews_empty), style = MaterialTheme.typography.bodyMedium)
        }
        effectiveError?.takeIf(String::isNotBlank)?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (reviews.pageLoadFailed || (effectiveError != null && reviews.nextCursor != null)) {
            OutlinedButton(onClick = onLoadMore, enabled = !loadingMore,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (loadingMore) R.string.steam_store_reviews_loading_more else R.string.steam_store_reviews_retry))
            }
        } else if (canExpand || (expanded && reviews.nextCursor != null)) {
            OutlinedButton(
                onClick = {
                    if (canExpand) {
                        expanded = true
                        if (reviews.items.size <= REVIEW_PREVIEW_COUNT &&
                            reviews.nextCursor != null
                        ) {
                            onLoadMore()
                        }
                    } else {
                        onLoadMore()
                    }
                },
                enabled = !loadingMore,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (loadingMore) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(
                        when {
                            loadingMore -> R.string.steam_store_reviews_loading_more
                            canExpand -> R.string.steam_store_reviews_show_more
                            else -> R.string.steam_store_reviews_load_more
                        }
                    )
                )
            }
        }
    }
    if (showFilterSheet) {
        SteamStoreReviewFilterSheet(
            filters = filters,
            enabled = !loadingMore,
            onApply = {
                showFilterSheet = false
                if (it != filters) onFiltersChanged(it)
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun SteamStoreReviewFilters(
    filters: SteamReviewFilterSelection,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (filters.isDefault) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (filters.isDefault) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.steam_store_reviews_filter_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = steamReviewFilterSummary(filters),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamStoreReviewFilterSheet(
    filters: SteamReviewFilterSelection,
    enabled: Boolean,
    onApply: (SteamReviewFilterSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var pending by remember(filters) { mutableStateOf(filters) }
    MonicaModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.steam_store_reviews_filter_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = steamReviewFilterSummary(pending),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { pending = SteamReviewFilterSelection() },
                    enabled = enabled && !pending.isDefault,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.steam_store_reviews_filter_reset))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SteamReviewChoiceGroup(
                    title = stringResource(R.string.steam_store_reviews_filter_sentiment)
                ) {
                    SteamReviewSentimentFilter.entries.forEach { option ->
                        SteamReviewChoiceChip(
                            selected = pending.sentiment == option,
                            enabled = enabled,
                            label = steamReviewSentimentLabel(option),
                            onClick = { pending = pending.copy(sentiment = option) }
                        )
                    }
                }
                SteamReviewChoiceGroup(
                    title = stringResource(R.string.steam_store_reviews_filter_time)
                ) {
                    SteamReviewTimeFilter.entries.forEach { option ->
                        SteamReviewChoiceChip(
                            selected = pending.time == option,
                            enabled = enabled,
                            label = steamReviewTimeLabel(option),
                            onClick = { pending = pending.copy(time = option) }
                        )
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Button(
                    onClick = { onApply(pending) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(20.dp, 10.dp).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.steam_store_reviews_filter_apply))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteamReviewChoiceGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun SteamReviewChoiceChip(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null
    )
}

@Composable
private fun steamReviewFilterSummary(filters: SteamReviewFilterSelection): String =
    "${steamReviewSentimentLabel(filters.sentiment)} · ${steamReviewTimeLabel(filters.time)}"

@Composable
private fun steamReviewSentimentLabel(filter: SteamReviewSentimentFilter): String = stringResource(
    when (filter) {
        SteamReviewSentimentFilter.ALL -> R.string.steam_store_reviews_filter_all
        SteamReviewSentimentFilter.POSITIVE -> R.string.steam_store_reviews_filter_positive
        SteamReviewSentimentFilter.NEGATIVE -> R.string.steam_store_reviews_filter_negative
    }
)

@Composable
private fun steamReviewTimeLabel(filter: SteamReviewTimeFilter): String = stringResource(
    when (filter) {
        SteamReviewTimeFilter.ALL_TIME -> R.string.steam_store_reviews_filter_all_time
        SteamReviewTimeFilter.RECENT_30_DAYS -> R.string.steam_store_reviews_filter_recent
    }
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SteamStoreReviewCard(
    review: SteamUserReview,
    showFullBody: Boolean,
    onOpenAuthor: (String) -> Unit,
    authorLoader: (suspend (String) -> takagi.ru.monica.steam.profile.SteamMiniProfileDecor?)?
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SteamReviewAuthor(review.authorSteamId, onOpenAuthor, authorLoader,
                votedUp = review.votedUp,
                metadata = listOfNotNull(reviewMetadata(review),
                    review.createdAt.takeIf { it > 0L }?.let(::formatReviewDate)).joinToString(" · "))
            SelectionContainer {
                SteamRichText(
                    source = review.body,
                    onOpenLink = { url -> openSteamReviewLink(context, url) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (showFullBody) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (review.votesUp > 0) {
                    ReviewLabel(
                        stringResource(R.string.steam_store_review_helpful, review.votesUp)
                    )
                }
                if (review.steamPurchase) {
                    ReviewLabel(stringResource(R.string.steam_store_review_steam_purchase))
                }
                if (review.receivedForFree) {
                    ReviewLabel(stringResource(R.string.steam_store_review_received_free))
                }
                if (review.writtenDuringEarlyAccess) {
                    ReviewLabel(stringResource(R.string.steam_store_review_early_access))
                }
            }

        }
    }
}

private fun openSteamReviewLink(context: android.content.Context, rawUrl: String) {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https", "steam")) return
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun ReviewLabel(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun reviewMetadata(review: SteamUserReview): String {
    val totalHours = review.playtimeForeverMinutes / 60f
    val reviewHours = review.playtimeAtReviewMinutes / 60f
    return when {
        review.playtimeForeverMinutes > 0 && review.playtimeAtReviewMinutes > 0 ->
            stringResource(
                R.string.steam_store_review_playtime_both,
                formatHours(totalHours),
                formatHours(reviewHours)
            )
        review.playtimeForeverMinutes > 0 -> stringResource(
            R.string.steam_store_review_playtime_total,
            formatHours(totalHours)
        )
        else -> stringResource(R.string.steam_store_review_steam_player)
    }
}

private fun formatHours(hours: Float): String =
    String.format(Locale.getDefault(), "%.1f", hours)

private fun formatReviewDate(timestampSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestampSeconds * 1_000L))

private const val REVIEW_PREVIEW_COUNT = 3
