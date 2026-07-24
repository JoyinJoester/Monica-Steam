/*
 * Rounded-column behavior adapted from Grit's WeekDayBreakdown.
 * Copyright (C) 2026 Shubham Gorai
 * Licensed under the GNU General Public License v3.0 or later.
 */
package takagi.ru.monica.steam.library.analytics.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.analytics.domain.SteamGameDistributionBucket
import takagi.ru.monica.steam.library.analytics.domain.SteamGameDistributionMode
import takagi.ru.monica.steam.library.analytics.domain.SteamGameDistributionRange
import takagi.ru.monica.steam.library.analytics.domain.steamGameDistribution
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
fun SteamGameDistributionCard(
    snapshot: SteamLibrarySnapshot,
    modifier: Modifier = Modifier
) {
    var mode by rememberSaveable { mutableStateOf(SteamGameDistributionMode.PLAYTIME) }
    val buckets = remember(snapshot.games, mode) { steamGameDistribution(snapshot.games, mode) }

    SteamAnalyticsCard(
        title = stringResource(R.string.steam_analytics_game_distribution),
        icon = Icons.Rounded.Equalizer,
        modifier = modifier
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            SteamGameDistributionMode.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = mode == item,
                    onClick = { mode = item },
                    shape = SegmentedButtonDefaults.itemShape(index, SteamGameDistributionMode.entries.size),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (item == SteamGameDistributionMode.PLAYTIME) {
                                R.string.steam_analytics_playtime
                            } else {
                                R.string.steam_analytics_price
                            }
                        ),
                        maxLines = 1
                    )
                }
            }
        }
        DistributionBars(
            buckets = buckets,
            modifier = Modifier.padding(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun DistributionBars(
    buckets: List<SteamGameDistributionBucket>,
    modifier: Modifier = Modifier
) {
    val max = buckets.maxOfOrNull(SteamGameDistributionBucket::gameCount)?.coerceAtLeast(1) ?: 1
    Row(
        modifier = modifier.fillMaxWidth().height(220.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        buckets.forEach { bucket ->
            val barHeight by animateDpAsState(
                targetValue = (bucket.gameCount.toFloat() / max * 150f).coerceAtLeast(12f).dp,
                animationSpec = tween(durationMillis = 450),
                label = "distribution-bar"
            )
            val isHighest = bucket.gameCount == max && bucket.gameCount > 0
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = bucket.gameCount.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = GoogleSansFlexFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (isHighest) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(
                            if (isHighest) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                )
                Text(
                    text = distributionShortLabel(bucket.range),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansFlexFontFamily,
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun distributionShortLabel(range: SteamGameDistributionRange): String = when (range) {
    SteamGameDistributionRange.UNPLAYED -> "0"
    SteamGameDistributionRange.UNDER_ONE_HOUR -> "1"
    SteamGameDistributionRange.ONE_TO_THREE_HOURS -> "3"
    SteamGameDistributionRange.THREE_TO_TEN_HOURS -> "10"
    SteamGameDistributionRange.TEN_TO_THIRTY_HOURS -> "30"
    SteamGameDistributionRange.THIRTY_TO_HUNDRED_HOURS -> "100"
    SteamGameDistributionRange.OVER_HUNDRED_HOURS -> "100+"
    SteamGameDistributionRange.FREE -> stringResource(R.string.steam_distribution_free)
    SteamGameDistributionRange.PRICE_UNDER_25 -> "25"
    SteamGameDistributionRange.PRICE_25_TO_50 -> "50"
    SteamGameDistributionRange.PRICE_50_TO_100 -> "100"
    SteamGameDistributionRange.PRICE_100_TO_200 -> "200"
    SteamGameDistributionRange.PRICE_200_TO_400 -> "400"
    SteamGameDistributionRange.PRICE_OVER_400 -> "400+"
    SteamGameDistributionRange.PRICE_UNKNOWN -> stringResource(R.string.steam_distribution_price_unknown)
}
