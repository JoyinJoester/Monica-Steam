package takagi.ru.monica.steam.space.data

import java.security.MessageDigest
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.space.domain.SteamSpaceAchievement
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.domain.SteamSpaceFormatException
import takagi.ru.monica.steam.space.domain.SteamSpaceMedia

internal data class SteamSpaceNewsEntry(
    val event: SteamSpaceEvent,
    val publishedFileId: String? = null
)

internal data class SteamSpaceNews(
    val entries: List<SteamSpaceNewsEntry>,
    val rawCount: Int,
    val oldestEpochSeconds: Long?,
    val newestEpochSeconds: Long?,
    val partial: Boolean
)

internal data class SteamSpacePublishedFile(
    val id: String,
    val creator: String?,
    val readable: Boolean,
    val title: String?,
    val body: String?,
    val media: SteamSpaceMedia?,
    val partial: Boolean
)

/** Field numbers come from Steam's public UserNews, Player and PublishedFile protobufs. */
internal object SteamSpaceParser {
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    const val MAX_EVENTS = 200

    fun news(bytes: ByteArray): SteamSpaceNews = decode {
        val fields = read(bytes)
        if (bytes.isNotEmpty() && fields.none { it.number == 1 || it.number == 2 }) invalid()
        val news = fields.messages(1)
        if (news.size > MAX_EVENTS) invalid()
        val achievements = achievementMap(fields.messages(2))
        var partial = false
        val times = mutableListOf<Long>()
        val events = news.mapNotNull { payload ->
            val item = read(payload)
            val time = item.uint(2)?.takeIf { it > 0L }
            time?.let(times::add)
            val actor = item.fixedId(3)?.takeIf(::steamId)
            val type = item.uint(1)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
            if (time == null || actor == null || type == null) {
                partial = true
                return@mapNotNull null
            }
            val kind = when (type) {
                2 -> SteamSpaceEventKind.ACHIEVEMENT
                3 -> SteamSpaceEventKind.GAME_ADDED
                9 -> SteamSpaceEventKind.WISHLISTED
                10 -> SteamSpaceEventKind.REVIEW
                13 -> SteamSpaceEventKind.SCREENSHOT
                14 -> SteamSpaceEventKind.VIDEO
                16 -> SteamSpaceEventKind.STATUS
                30 -> SteamSpaceEventKind.FIRST_PLAY
                1002 -> SteamSpaceEventKind.ANNOUNCEMENT
                else -> SteamSpaceEventKind.UNKNOWN
            }
            val game = item.fixedId(5)?.toULongOrNull()
            // A GameID is not always an AppID: non-Steam shortcuts encode a type in its upper bits.
            val gameApp = game?.takeIf { it in 1uL..0xFFFFFFuL }?.toInt()
            val rawApps = item.filter { it.number == 13 }.flatMap { field ->
                when (field.wireType) {
                    0 -> listOf(field.asLong)
                    2 -> SteamProtoReader.decodePackedVarints(requireNotNull(field.bytes))
                    else -> invalid()
                }
            }
            val apps = (rawApps + listOfNotNull(gameApp?.toLong()))
                .filter { it in 1..Int.MAX_VALUE.toLong() }.distinct().take(32).map(Long::toInt)
            val names = item.filter { it.number == 8 }.map { field ->
                if (field.wireType != 2) invalid()
                field.asString
            }
            val badges = names.take(32).mapNotNull { name -> achievements[gameApp to name] }
            val published = item.fixedId(11)
            val announcement = item.fixedId(10)
            val source = when {
                kind == SteamSpaceEventKind.STATUS -> SteamSpaceLinks.status(actor, time)
                kind in setOf(SteamSpaceEventKind.SCREENSHOT, SteamSpaceEventKind.VIDEO) && published != null ->
                    SteamSpaceLinks.publishedFile(published)
                kind == SteamSpaceEventKind.REVIEW && apps.isNotEmpty() -> SteamSpaceLinks.review(actor, apps.first())
                kind == SteamSpaceEventKind.ANNOUNCEMENT && announcement != null ->
                    SteamSpaceLinks.announcement(actor, announcement)
                else -> SteamSpaceLinks.profile(actor)
            }
            val incomplete = kind in setOf(SteamSpaceEventKind.UNKNOWN, SteamSpaceEventKind.REVIEW, SteamSpaceEventKind.ANNOUNCEMENT) ||
                (kind == SteamSpaceEventKind.ACHIEVEMENT && (badges.size != names.size || names.isEmpty())) ||
                (kind in setOf(SteamSpaceEventKind.SCREENSHOT, SteamSpaceEventKind.VIDEO) && published == null) ||
                rawApps.size > 32 || names.size > 32
            // Announcements use their actual post time for display, eventtime only for pagination.
            val shownTime = if (kind == SteamSpaceEventKind.ANNOUNCEMENT) item.uint(14)?.takeIf { it > 0 } else null
            partial = partial || incomplete
            SteamSpaceNewsEntry(
                event = SteamSpaceEvent(
                    id = digest(payload), kind = kind, actorSteamId = actor,
                    occurredAtEpochSeconds = shownTime ?: time, appIds = apps,
                    achievements = badges, sourceUrl = source, partial = incomplete, rawEventType = type
                ),
                publishedFileId = published
            )
        }.distinctBy { it.event.id }
        SteamSpaceNews(events, news.size, times.minOrNull(), times.maxOrNull(), partial)
    }

    fun status(bytes: ByteArray, actor: String, post: Long): Pair<String?, Boolean> = decode {
        val fields = read(bytes)
        if (fields.isEmpty()) invalid()
        val expectedAccountId = actor.toULong() and 0xFFFFFFFFuL
        if (fields.uint(1)?.toULong() != expectedAccountId || fields.uint64(2) != post.toULong()) invalid()
        if (fields.bool(4)) return@decode null to false
        val original = fields.string(3) ?: invalid()
        text(original) to (original.length > TEXT_LIMIT)
    }

    fun publishedFiles(bytes: ByteArray): Map<String, SteamSpacePublishedFile> = decode {
        val records = read(bytes).messages(1)
        if (records.size > MAX_EVENTS) invalid()
        records.mapNotNull { payload ->
            val fields = read(payload)
            val id = fields.uint64(2)?.takeIf { it > 0uL }?.toString() ?: return@mapNotNull null
            val visibility = fields.uint(21) ?: 0L
            // Match the client: public/friends-only may be shown, private/unlisted/unknown may not.
            val readable = fields.uint(1) == 1L && visibility in 0L..1L && !fields.bool(28)
            val originalTitle = fields.string(16)
            val originalBody = fields.string(18) ?: fields.string(17)
            val rawImage = fields.string(11)
            val image = SteamSpaceLinks.image(rawImage)
            id to SteamSpacePublishedFile(
                id = id, creator = fields.fixedId(3), readable = readable,
                title = if (readable) originalTitle?.let(::text) else null,
                body = if (readable) originalBody?.let(::text) else null,
                media = if (readable && image != null) SteamSpaceMedia(
                    url = image,
                    width = fields.uint(43)?.takeIf { it in 1..16_384 }?.toInt(),
                    height = fields.uint(44)?.takeIf { it in 1..16_384 }?.toInt(),
                    sensitive = fields.bool(46) || fields.bool(65) || fields.bool(66)
                ) else null,
                partial = (!rawImage.isNullOrBlank() && image == null) ||
                    (originalTitle?.length ?: 0) > TEXT_LIMIT || (originalBody?.length ?: 0) > TEXT_LIMIT
            )
        }.toMap()
    }

    fun appNames(bytes: ByteArray): Map<Int, String> = decode {
        val items = read(bytes).messages(1)
        if (items.size > MAX_EVENTS) invalid()
        items.mapNotNull { payload ->
            val fields = read(payload)
            val canonicalId = fields.uint(2)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
            val appId = fields.uint(9)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
            // StoreItem.id is canonical; a redundant appid can be present with its zero default.
            if (canonicalId != null && appId != null && canonicalId != appId) return@mapNotNull null
            val id = (canonicalId ?: appId)?.toInt()
            val name = fields.string(6)?.takeIf { it.isNotBlank() && it.length <= 512 }
            if (fields.uint(3) != 1L || id == null || name == null || (fields.uint(1) ?: 0L) != 0L) null
            else id to name
        }.toMap()
    }

    private fun achievementMap(payloads: List<ByteArray>): Map<Pair<Int?, String>, SteamSpaceAchievement> {
        if (payloads.size > MAX_EVENTS) invalid()
        val result = mutableMapOf<Pair<Int?, String>, SteamSpaceAchievement>()
        payloads.forEach { payload ->
            val fields = read(payload)
            val appId = fields.uint(1)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: return@forEach
            val values = fields.messages(2)
            if (values.size > 1_000) invalid()
            values.forEach achievement@ { value ->
                val item = read(value)
                val name = item.string(1)?.takeIf { it.isNotBlank() && it.length <= 512 } ?: return@achievement
                val label = item.string(2)?.takeIf(String::isNotBlank) ?: return@achievement
                result[appId to name] = SteamSpaceAchievement(
                    name, text(label), item.string(3)?.let(::text), SteamSpaceLinks.achievementIcon(appId, item.string(4))
                )
            }
        }
        return result
    }

    private fun read(bytes: ByteArray): List<SteamProtoField> {
        if (bytes.size > MAX_RESPONSE_BYTES) invalid()
        return SteamProtoReader(bytes).parseAll()
    }

    private fun List<SteamProtoField>.messages(number: Int): List<ByteArray> = filter { it.number == number }.map {
        if (it.wireType != 2) invalid()
        requireNotNull(it.bytes)
    }
    private fun List<SteamProtoField>.one(number: Int): SteamProtoField? {
        val fields = filter { it.number == number }
        if (fields.size > 1) invalid()
        return fields.firstOrNull()
    }
    private fun List<SteamProtoField>.uint(number: Int): Long? = one(number)?.let {
        if (it.wireType != 0 || it.asLong !in 0..0xFFFFFFFFL) invalid()
        it.asLong
    }
    private fun List<SteamProtoField>.uint64(number: Int): ULong? = one(number)?.let {
        if (it.wireType != 0) invalid()
        it.asLong.toULong()
    }
    private fun List<SteamProtoField>.fixedId(number: Int): String? = one(number)?.let {
        if (it.wireType != 1) invalid()
        it.asFixed64UnsignedString.takeUnless { value -> value == "0" }
    }
    private fun List<SteamProtoField>.string(number: Int): String? = one(number)?.let {
        if (it.wireType != 2) invalid()
        it.asString
    }
    private fun List<SteamProtoField>.bool(number: Int): Boolean = uint(number)?.let { it != 0L } ?: false
    private fun steamId(id: String): Boolean {
        val value = id.toULongOrNull() ?: return false
        val type = (value shr 52) and 15uL
        return id.length == 17 && value shr 56 == 1uL && (type == 1uL || type == 7uL)
    }
    private fun text(value: String): String = value.take(TEXT_LIMIT)
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun invalid(): Nothing = throw SteamSpaceFormatException()
    private inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (_: IllegalArgumentException) {
        invalid()
    } catch (_: IllegalStateException) {
        invalid()
    }
    private const val TEXT_LIMIT = 8_000
}
