package takagi.ru.monica.steam.space.domain

import kotlinx.serialization.Serializable

/** Only identity and display language are retained; never a SteamAccount or its credentials. */
@Serializable
data class SteamSpaceCacheKey(
    val accountId: Long,
    val accountSteamId: String,
    val language: String,
    val temporary: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class SteamSpaceCachedFeed(
    val key: SteamSpaceCacheKey,
    val items: List<SteamSpaceEvent>,
    val nextCursor: SteamSpaceCursor?,
    val partial: Boolean,
    val pageCount: Int,
    val visitedCursors: Set<SteamSpaceCursor>,
    val fetchedAtMillis: Long
)

interface SteamSpaceFeedCache {
    /** Memory only; this method never initializes disk storage or encryption. */
    fun peek(key: SteamSpaceCacheKey): SteamSpaceCachedFeed?
    /** Publish a completed result to memory without disk or cipher work. */
    fun remember(feed: SteamSpaceCachedFeed) {}
    /** Blocking disk boundary: call from a background dispatcher. */
    fun load(key: SteamSpaceCacheKey): SteamSpaceCachedFeed?
    /** Blocking disk boundary: call from a background dispatcher. */
    fun save(feed: SteamSpaceCachedFeed)
}

/** Small per-owner cache, also used as the production cache's fast memory tier. */
class SteamSpaceMemoryFeedCache(
    private val clockMillis: () -> Long = System::currentTimeMillis
) : SteamSpaceFeedCache {
    private val feeds = LinkedHashMap<SteamSpaceCacheKey, SteamSpaceCachedFeed>(4, 0.75f, true)

    override fun peek(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? = synchronized(feeds) {
        discardExpired()
        feeds[key]?.detachedCopy()
    }

    override fun load(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? = peek(key)

    override fun save(feed: SteamSpaceCachedFeed) {
        remember(feed)
    }

    override fun remember(feed: SteamSpaceCachedFeed) {
        offer(feed)
    }

    internal fun offer(feed: SteamSpaceCachedFeed): Boolean {
        if (!feed.isValidAt(clockMillis())) return false
        val copy = feed.detachedCopy()
        return synchronized(feeds) {
            discardExpired()
            val previous = feeds[copy.key]
            if (previous != null && !copy.canReplace(previous)) return@synchronized false
            feeds[copy.key] = copy
            while (feeds.size > MAX_ENTRIES) {
                val oldest = feeds.entries.iterator()
                oldest.next()
                oldest.remove()
            }
            true
        }
    }

    private fun discardExpired() {
        val now = clockMillis()
        feeds.entries.removeAll { !it.value.isValidAt(now) }
    }

    companion object {
        const val MAX_ENTRIES = 4
        const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
        internal const val MAX_STRING_CHARACTERS = 512 * 1024
    }
}

internal fun SteamSpaceCachedFeed.detachedCopy(): SteamSpaceCachedFeed = copy(
    items = items.map { event ->
        event.copy(appIds = event.appIds.toList(), appNames = event.appNames.toMap(),
            media = event.media.toList(), achievements = event.achievements.toList())
    },
    visitedCursors = visitedCursors.toSet()
)

/** A late first-page write cannot replace a newer refresh or a longer page chain. */
internal fun SteamSpaceCachedFeed.canReplace(previous: SteamSpaceCachedFeed): Boolean = when {
    fetchedAtMillis > previous.fetchedAtMillis -> true
    fetchedAtMillis < previous.fetchedAtMillis -> false
    this == previous -> true
    else -> pageCount > previous.pageCount &&
        items.size >= previous.items.size && items.take(previous.items.size) == previous.items &&
        visitedCursors.containsAll(previous.visitedCursors)
}

internal fun SteamSpaceCacheKey.isValid(): Boolean =
    accountSteamId.length == 17 && accountSteamId.all(Char::isDigit) &&
        language in setOf("english", "schinese", "tchinese", "japanese", "russian", "vietnamese") && createdAt >= 0L

internal fun SteamSpaceCachedFeed.isValidAt(now: Long): Boolean {
    if (!key.isValid() || fetchedAtMillis < 0L || fetchedAtMillis > now + 1_000L ||
        now - fetchedAtMillis >= SteamSpaceMemoryFeedCache.MAX_AGE_MILLIS ||
        pageCount !in 1..20 || items.size > 400 || visitedCursors.size > 20) return false
    fun validCursor(cursor: SteamSpaceCursor): Boolean =
        cursor.accountId == key.accountId && cursor.accountSteamId == key.accountSteamId && cursor.beforeEpochSeconds > 0L
    if (nextCursor?.let { !validCursor(it) || it in visitedCursors } == true ||
        visitedCursors.any { !validCursor(it) }) return false
    if (items.map { it.id }.toSet().size != items.size) return false
    var characters = 0L
    fun count(value: String?) { characters += value?.length ?: 0 }
    for (event in items) {
        if (event.id.isBlank() || event.appIds.size > 32 || event.media.size > 40 || event.achievements.size > 32) return false
        if (event.appNames.size > 32 || event.appNames.keys.any { it !in event.appIds }) return false
        count(event.id); count(event.actorSteamId); count(event.title); count(event.body); count(event.sourceUrl)
        event.appNames.values.forEach { count(it) }
        event.media.forEach { count(it.url) }
        event.achievements.forEach { count(it.name); count(it.displayName); count(it.description); count(it.iconUrl) }
        if (characters > SteamSpaceMemoryFeedCache.MAX_STRING_CHARACTERS) return false
    }
    return true
}
