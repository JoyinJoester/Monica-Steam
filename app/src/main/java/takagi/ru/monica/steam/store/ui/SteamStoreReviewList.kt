package takagi.ru.monica.steam.store.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreReviews
import takagi.ru.monica.steam.store.domain.SteamUserReview

@Composable
internal fun SteamStoreReviewsSection(
    appId: Int,
    reviews: SteamStoreReviews,
    loadingMore: Boolean,
    loadError: String?,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(appId) { mutableStateOf(false) }
    val visibleReviews = if (expanded) reviews.items else reviews.items.take(REVIEW_PREVIEW_COUNT)
    val canExpand = !expanded && (
        reviews.items.size > REVIEW_PREVIEW_COUNT || reviews.nextCursor != null
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SteamStoreReviewSummarySection(
            reviews = reviews,
            onOpenAll = if (canExpand) ({ expanded = true }) else null
        )
        visibleReviews.forEach { review ->
            SteamStoreReviewCard(review = review, showFullBody = expanded)
        }
        loadError?.takeIf(String::isNotBlank)?.let { error ->
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
        if (expanded && reviews.nextCursor != null) {
            OutlinedButton(
                onClick = onLoadMore,
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
                    stringResource(
                        if (loadingMore) {
                            R.string.steam_store_reviews_loading_more
                        } else {
                            R.string.steam_store_reviews_load_more
                        }
                    )
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SteamStoreReviewCard(
    review: SteamUserReview,
    showFullBody: Boolean
) {
    val accent = if (review.votedUp) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val iconContainer = if (review.votedUp) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = iconContainer, contentColor = accent) {
                    Icon(
                        imageVector = if (review.votedUp) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                if (review.votedUp) {
                                    R.string.steam_store_review_recommended
                                } else {
                                    R.string.steam_store_review_not_recommended
                                }
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                        review.createdAt.takeIf { it > 0L }?.let { createdAt ->
                            Text(
                                text = formatReviewDate(createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = reviewMetadata(review),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = review.body,
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
