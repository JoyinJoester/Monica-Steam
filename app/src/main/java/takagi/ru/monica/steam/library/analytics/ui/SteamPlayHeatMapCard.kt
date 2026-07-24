/*
 * Adapted from Grit's HabitHeatMap and CardArrows components.
 * Copyright (C) 2026 Shubham Gorai
 * Licensed under the GNU General Public License v3.0 or later.
 */
package takagi.ru.monica.steam.library.analytics.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.HeatMapCalendar
import com.kizitonwose.calendar.compose.heatmapcalendar.rememberHeatMapCalendarState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.analytics.domain.SteamPlayActivityDay
import takagi.ru.monica.steam.library.analytics.domain.SteamPlayActivityHistory
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamPlayHeatMapCard(
    history: SteamPlayActivityHistory?,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val endMonth = remember(today) { YearMonth.from(today) }
    val startMonth = remember(endMonth) { endMonth.minusMonths(11) }
    val state = rememberHeatMapCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = endMonth,
        firstDayOfWeek = DayOfWeek.MONDAY
    )
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val dayData = remember(history?.days) {
        history?.days.orEmpty().mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) to day }.getOrNull()
        }.toMap()
    }
    val maxMinutes = remember(dayData) {
        dayData.values.maxOfOrNull(SteamPlayActivityDay::totalMinutes)?.coerceAtLeast(1) ?: 1
    }
    var selectedDay by remember { mutableStateOf<SteamPlayActivityDay?>(null) }

    SteamAnalyticsCard(
        title = stringResource(R.string.steam_analytics_game_heatmap),
        icon = Icons.Rounded.CalendarMonth,
        modifier = modifier,
        header = {
            IconButton(
                onClick = {
                    val target = state.firstVisibleMonth.yearMonth.minusMonths(1)
                    scope.launch { state.animateScrollToMonth(maxOf(startMonth, target)) }
                }
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.previous))
            }
            IconButton(
                onClick = {
                    val target = state.firstVisibleMonth.yearMonth.plusMonths(1)
                    scope.launch { state.animateScrollToMonth(minOf(endMonth, target)) }
                }
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.next))
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(top = 26.dp)) {
                DayOfWeek.values().forEachIndexed { index, day ->
                    val shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                        DayOfWeek.values().lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                        else -> RoundedCornerShape(4.dp)
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            HeatMapCalendar(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.shapes.medium
                    ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                monthHeader = { month ->
                    Text(
                        text = month.yearMonth.month.getDisplayName(
                            TextStyle.SHORT,
                            Locale.getDefault()
                        ) + " " + month.yearMonth.year,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GoogleSansFlexFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                dayContent = { day, _ ->
                    if (day.date <= today) {
                        val activity = dayData[day.date]
                        val ratio = (activity?.totalMinutes ?: 0).toFloat() / maxMinutes
                        val label = if (activity == null) {
                            day.date.toString()
                        } else {
                            "${day.date}, ${formatPlayDuration(activity.totalMinutes)}"
                        }
                        Box(
                            modifier = Modifier
                                .padding(1.dp)
                                .size(28.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    if (activity == null) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = (0.24f + ratio * 0.76f).coerceIn(0.24f, 1f)
                                        )
                                    }
                                )
                                .semantics { contentDescription = label }
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = activity?.let {
                                        {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedDay = it
                                        }
                                    }
                                )
                        )
                    } else {
                        Spacer(Modifier.padding(1.dp).size(28.dp))
                    }
                }
            )
        }
        Text(
            text = stringResource(R.string.steam_analytics_local_tracking_note),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    selectedDay?.let { day ->
        SteamPlayDaySheet(day = day, onDismiss = { selectedDay = null })
    }
}

@Composable
private fun SteamPlayDaySheet(
    day: SteamPlayActivityDay,
    onDismiss: () -> Unit
) {
    val date = remember(day.date) { LocalDate.parse(day.date) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = GoogleSansFlexFontFamily,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = stringResource(
                    R.string.steam_analytics_day_total,
                    formatPlayDuration(day.totalMinutes)
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            day.games.forEach { game ->
                ListItem(
                    headlineContent = {
                        Text(game.gameName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Text(
                            formatPlayDuration(game.minutes),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = GoogleSansFlexFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
        }
    }
}

private fun formatPlayDuration(minutes: Int): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}
