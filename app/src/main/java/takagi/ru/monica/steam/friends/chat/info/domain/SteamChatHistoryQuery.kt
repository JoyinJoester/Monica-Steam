package takagi.ru.monica.steam.friends.chat.info.domain

/** Telegram's search page narrows results by period and by sender before it renders them. */
enum class SteamChatHistoryRange(val label: String) {
    ANY_TIME("全部时间"),
    TODAY("今天"),
    LAST_WEEK("最近 7 天"),
    LAST_MONTH("最近 30 天");

    fun contains(timestampSeconds: Long, nowMillis: Long): Boolean {
        if (this == ANY_TIME) return true
        val window = when (this) {
            TODAY -> 1
            LAST_WEEK -> 7
            LAST_MONTH -> 30
            ANY_TIME -> return true
        }
        val startMillis = startOfDayMillis(nowMillis) - (window - 1) * DAY_MILLIS
        return timestampSeconds * 1000L >= startMillis
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}

/**
 * The whole filter state of the history page in one value. Keeping the narrowing rules here rather
 * than in the composable means the counters, the list and the media wall cannot drift apart.
 */
data class SteamChatHistoryQuery(
    val text: String = "",
    val category: SteamChatHistoryCategory = SteamChatHistoryCategory.ALL,
    val sender: String? = null,
    val range: SteamChatHistoryRange = SteamChatHistoryRange.ANY_TIME,
    val newestFirst: Boolean = true
) {
    fun apply(items: List<SteamChatHistoryItem>, nowMillis: Long = System.currentTimeMillis()): List<SteamChatHistoryItem> {
        val filtered = items.asSequence()
            .filter { category == SteamChatHistoryCategory.ALL || it.category == category }
            .filter { matchesScope(it, nowMillis) }
        return (if (newestFirst) filtered.sortedByDescending(SteamChatHistoryItem::timestamp)
        else filtered.sortedBy(SteamChatHistoryItem::timestamp)).toList()
    }

    /**
     * Chip counters must ignore the selected chip but respect every other filter, otherwise the tab
     * showing "3" would list zero rows. "全部" therefore counts the whole scoped set.
     */
    fun countsOf(items: List<SteamChatHistoryItem>, nowMillis: Long = System.currentTimeMillis()): Map<SteamChatHistoryCategory, Int> {
        val scoped = items.asSequence().filter { matchesScope(it, nowMillis) }
        val counts = scoped.groupingBy(SteamChatHistoryItem::category).eachCount()
        return counts + (SteamChatHistoryCategory.ALL to counts.values.sum())
    }

    /** Everything except the category chip: query text, sender and period. */
    private fun matchesScope(item: SteamChatHistoryItem, nowMillis: Long): Boolean =
        (sender == null || item.senderName == sender) &&
            range.contains(item.timestamp, nowMillis) &&
            item.matches(text)

    fun withCategory(value: SteamChatHistoryCategory) = copy(category = value)

    fun withSender(value: String?) = copy(sender = value)

    fun withRange(value: SteamChatHistoryRange) = copy(range = value)

    fun withText(value: String) = copy(text = value)
}

/** Senders present in [items] with how many matches each one has, most active first. */
fun steamChatHistorySenders(items: List<SteamChatHistoryItem>): List<Pair<String, Int>> =
    items.groupingBy(SteamChatHistoryItem::senderName)
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

private fun startOfDayMillis(millis: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = millis
    set(java.util.Calendar.HOUR_OF_DAY, 0)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis
