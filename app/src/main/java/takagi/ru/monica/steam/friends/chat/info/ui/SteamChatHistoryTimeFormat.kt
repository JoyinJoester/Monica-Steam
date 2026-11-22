package takagi.ru.monica.steam.friends.chat.info.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val monthDayFormat = SimpleDateFormat("M月d日", Locale.CHINA)
private val fullDateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

internal fun formatHistoryClock(timestampSeconds: Long): String =
    clockFormat.format(Date(timestampSeconds * 1000L))

/** Telegram groups results under "今天 / 昨天 / 3月5日" headers. */
internal fun historyDayLabel(timestampSeconds: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val day = startOfHistoryDay(timestampSeconds * 1000L)
    val today = startOfHistoryDay(nowMillis)
    return when {
        day == today -> "今天"
        day == today - DAY_MILLIS -> "昨天"
        sameHistoryYear(day, today) -> monthDayFormat.format(Date(day))
        else -> fullDateFormat.format(Date(day))
    }
}

private fun startOfHistoryDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun sameHistoryYear(first: Long, second: Long): Boolean {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = first
    val firstYear = calendar.get(Calendar.YEAR)
    calendar.timeInMillis = second
    return firstYear == calendar.get(Calendar.YEAR)
}
