package takagi.ru.monica.steam.friends.chat.info.domain

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatHistoryQueryTest {

    /** Fixed at local noon so "1 hour ago" can never slip into the previous day. */
    private val now: Long = Calendar.getInstance().apply {
        set(2025, Calendar.OCTOBER, 21, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val hour = 3_600L
    private val day = 24 * hour

    private fun item(
        id: String,
        sender: String,
        body: String,
        category: SteamChatHistoryCategory,
        secondsAgo: Long
    ) = SteamChatHistoryItem(
        id = id,
        senderName = sender,
        body = body,
        timestamp = now / 1000 - secondsAgo,
        category = category,
        // Mirrors SteamChatHistoryClassifier.item: the sender name is searchable too.
        searchText = "$sender\n$body"
    )

    private val items = listOf(
        item("1", "Sonix", "截图 shot.png", SteamChatHistoryCategory.MEDIA, hour),
        item("2", "Sonix", "看这个链接 https://store.steampowered.com", SteamChatHistoryCategory.LINK, day),
        item("3", "Monica", "群里说一句", SteamChatHistoryCategory.TEXT, 3 * day),
        item("4", "Monica", "老图片 old.png", SteamChatHistoryCategory.MEDIA, 20 * day)
    )

    @Test
    fun categoryChipCountsIgnoreTheSelectedChipButKeepOtherFilters() {
        // "链接" is selected; the "媒体" chip must still report 2, not 0.
        val counts = SteamChatHistoryQuery(category = SteamChatHistoryCategory.LINK).countsOf(items, now)
        assertEquals(2, counts[SteamChatHistoryCategory.MEDIA])
        assertEquals(1, counts[SteamChatHistoryCategory.LINK])
        assertEquals(1, counts[SteamChatHistoryCategory.TEXT])
        assertEquals(4, counts[SteamChatHistoryCategory.ALL])
    }

    @Test
    fun searchingNarrowsChipCountsToo() {
        // "Monica" is both a sender name and part of the searchable text, so two rows survive.
        val counts = SteamChatHistoryQuery(text = "Monica").countsOf(items, now)
        assertEquals(2, counts[SteamChatHistoryCategory.ALL])
        assertEquals(1, counts[SteamChatHistoryCategory.MEDIA] ?: 0)
        assertEquals(1, counts[SteamChatHistoryCategory.TEXT] ?: 0)
        assertEquals(0, counts[SteamChatHistoryCategory.LINK] ?: 0)
    }

    @Test
    fun selectingACategoryNarrowsTheList() {
        val links = SteamChatHistoryQuery(category = SteamChatHistoryCategory.LINK).apply(items, now)
        assertEquals(listOf("2"), links.map(SteamChatHistoryItem::id))
    }

    @Test
    fun queryMatchesSenderAndBody() {
        assertEquals(2, SteamChatHistoryQuery(text = "Monica").apply(items, now).size)
        assertEquals(1, SteamChatHistoryQuery(text = "old.png").apply(items, now).size)
        assertEquals(0, SteamChatHistoryQuery(text = "找不到").apply(items, now).size)
    }

    @Test
    fun senderFilterKeepsOnlyThatPersonsMessages() {
        val fromSonix = SteamChatHistoryQuery(sender = "Sonix").apply(items, now)
        assertEquals(listOf("1", "2"), fromSonix.map(SteamChatHistoryItem::id))
    }

    @Test
    fun periodFilterCountsFromTheStartOfTheWindowDay() {
        // "今天" starts at midnight, so the 24-hours-ago row belongs to yesterday.
        assertEquals(
            listOf("1"),
            SteamChatHistoryQuery(range = SteamChatHistoryRange.TODAY)
                .apply(items, now).map(SteamChatHistoryItem::id)
        )
        assertEquals(
            listOf("1", "2", "3"),
            SteamChatHistoryQuery(range = SteamChatHistoryRange.LAST_WEEK)
                .apply(items, now).map(SteamChatHistoryItem::id)
        )
        assertEquals(4, SteamChatHistoryQuery(range = SteamChatHistoryRange.LAST_MONTH).apply(items, now).size)
    }

    @Test
    fun anyTimeKeepsEverything() {
        assertTrue(SteamChatHistoryRange.ANY_TIME.contains(0L, now))
        assertFalse(SteamChatHistoryRange.TODAY.contains((now - 5 * day * 1000) / 1000, now))
    }

    @Test
    fun sortFlipsBetweenNewestAndOldest() {
        assertEquals(
            listOf("1", "2", "3", "4"),
            SteamChatHistoryQuery().apply(items, now).map(SteamChatHistoryItem::id)
        )
        assertEquals(
            listOf("4", "3", "2", "1"),
            SteamChatHistoryQuery(newestFirst = false).apply(items, now).map(SteamChatHistoryItem::id)
        )
    }

    @Test
    fun senderListIsOrderedByMessageCount() {
        val counts = steamChatHistorySenders(items)
        assertEquals(setOf("Monica" to 2, "Sonix" to 2), counts.toSet())
        assertEquals("Sonix", steamChatHistorySenders(items.take(2)).first().first)
    }
}
