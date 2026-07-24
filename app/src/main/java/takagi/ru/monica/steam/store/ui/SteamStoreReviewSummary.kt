package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamReviewSummary
import takagi.ru.monica.steam.store.domain.SteamStoreReviews

@Composable
internal fun SteamStoreReviewSummarySection(
    reviews: SteamStoreReviews,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summaries = listOfNotNull(
        reviews.overall?.let { stringResource(R.string.steam_store_reviews_overall) to it },
        reviews.recent?.let { stringResource(R.string.steam_store_reviews_recent) to it }
    )
    if (summaries.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.steam_store_reviews_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 2.dp, top = 10.dp)
            )
            TextButton(onClick = onOpenAll) {
                Text(stringResource(R.string.steam_store_reviews_view_all))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                val stackSummaries = maxWidth < 360.dp && summaries.size > 1
                if (stackSummaries) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        summaries.forEachIndexed { index, (label, summary) ->
                            SteamReviewMetric(label, summary, Modifier.fillMaxWidth())
                            if (index < summaries.lastIndex) HorizontalDivider()
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        summaries.forEach { (label, summary) ->
                            SteamReviewMetric(label, summary, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamReviewMetric(
    label: String,
    summary: SteamReviewSummary,
    modifier: Modifier = Modifier
) {
    val accent = steamReviewAccent(summary.score)
    val numberFormat = NumberFormat.getIntegerInstance()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = steamReviewSentiment(summary.score),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(
                R.string.steam_store_reviews_positive_percent,
                summary.positivePercent
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            progress = { summary.positivePercent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Text(
            text = stringResource(
                R.string.steam_store_reviews_count,
                numberFormat.format(summary.positive),
                numberFormat.format(summary.total)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun steamReviewAccent(score: Int): Color = when (score) {
    in 1..4 -> MaterialTheme.colorScheme.error
    5 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun steamReviewSentiment(score: Int): String = stringResource(
    when (score) {
        1 -> R.string.steam_store_reviews_overwhelmingly_negative
        2 -> R.string.steam_store_reviews_very_negative
        3 -> R.string.steam_store_reviews_negative
        4 -> R.string.steam_store_reviews_mostly_negative
        5 -> R.string.steam_store_reviews_mixed
        6 -> R.string.steam_store_reviews_mostly_positive
        7 -> R.string.steam_store_reviews_positive
        8 -> R.string.steam_store_reviews_very_positive
        9 -> R.string.steam_store_reviews_overwhelmingly_positive
        else -> R.string.steam_store_reviews_unrated
    }
)
