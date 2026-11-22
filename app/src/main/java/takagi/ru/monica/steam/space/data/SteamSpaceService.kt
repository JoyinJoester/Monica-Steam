package takagi.ru.monica.steam.space.data

import java.util.concurrent.CancellationException
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.space.domain.SteamSpaceCursor
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.domain.SteamSpaceFormatException
import takagi.ru.monica.steam.space.domain.SteamSpaceGateway
import takagi.ru.monica.steam.space.domain.SteamSpacePage

/** Account-scoped official read operations; enrichment is bounded and optional. */
class SteamSpaceService(
    private val cm: SteamCmGateway = SteamCmClient(),
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val actorLookup: (SteamAccount, List<String>) -> Map<String, SteamSpaceActor> = ::loadSteamSpaceActors,
    private val appLookup: (SteamAccount, List<Int>, String) -> Map<Int, String> = SteamSpaceAppNames()::load
) : SteamSpaceGateway {
    override fun fetchPage(account: SteamAccount, language: String, cursor: SteamSpaceCursor?): SteamSpacePage {
        if (!account.hasRealSteamId || account.accessToken.isNullOrBlank()) {
            throw SteamApiException("A Steam session is required", authenticationRequired = true)
        }
        require(cursor == null || (cursor.accountId == account.id && cursor.accountSteamId == account.steamId &&
            cursor.beforeEpochSeconds in 1..0xFFFFFFFFL)) { "Activity cursor does not belong to this account" }
        val deadline = monotonicNanos() + ENRICHMENT_BUDGET_NANOS
        val response = call(account, NEWS_METHOD, SteamProtoWriter().apply {
            writeVarint(1, PAGE_SIZE.toLong())
            writeVarint(2, if (cursor == null) 1L else 0L)
            writeVarint(3, cursor?.beforeEpochSeconds ?: 0L)
            writeString(4, language.takeIf { it in LANGUAGES } ?: "english")
            writeVarint(5, FILTER_FLAGS)
            writeVarint(6, 0L)
        })
        val news = SteamSpaceParser.news(response)
        val oldest = news.oldestEpochSeconds
        if (cursor != null && news.newestEpochSeconds?.let { it > cursor.beforeEpochSeconds } == true) {
            throw SteamSpaceFormatException()
        }
        var partial = news.partial
        val appIds = news.entries.filter { it.event.kind != SteamSpaceEventKind.UNKNOWN }
            .flatMap { it.event.appIds }.distinct()
        // Resolve names before optional image details can consume the enrichment budget.
        val names = if (appIds.isEmpty()) emptyMap() else optional(deadline) {
            appLookup(account, appIds.take(MAX_FILES), language.takeIf { it in LANGUAGES } ?: "english")
                .filterKeys { it in appIds }
        }.orEmpty()
        if (appIds.any { names[it].isNullOrBlank() }) partial = true
        val files = news.entries.filter {
            it.event.kind == SteamSpaceEventKind.SCREENSHOT || it.event.kind == SteamSpaceEventKind.VIDEO
        }.mapNotNull(SteamSpaceNewsEntry::publishedFileId).distinct()
        // One bounded details batch; never one request per image or unbounded parallel requests.
        val loadedFiles = if (files.isEmpty()) emptyMap() else optional(deadline) {
            SteamSpaceParser.publishedFiles(call(account, FILE_METHOD, SteamProtoWriter().apply {
                files.take(MAX_FILES).forEach { writeFixed64(1, it.toULong().toLong()) }
                writeBool(8, true)
                writeBool(15, true)
            }))
        }.also { if (it == null || files.size > MAX_FILES) partial = true }.orEmpty()
        val actorIds = news.entries.map { it.event.actorSteamId }.distinct().take(MAX_FILES)
        val actors = if (actorIds.isEmpty()) emptyMap() else optional(deadline) {
            actorLookup(account, actorIds).filterKeys { it in actorIds }
        }.also { if (it == null) partial = true }.orEmpty()
        var statusRequests = 0
        val events = news.entries.mapNotNull { entry ->
            val actor = actors[entry.event.actorSteamId]
            var event = entry.event.copy(actorName = actor?.name.orEmpty(), actorAvatarUrl = actor?.avatarUrl.orEmpty(),
                appNames = names.filterKeys { it in entry.event.appIds },
                partial = entry.event.partial || (entry.event.kind != SteamSpaceEventKind.UNKNOWN &&
                    entry.event.appIds.any { names[it].isNullOrBlank() }))
            if (event.kind in GAME_KINDS) {
                val gameNames = event.appIds.mapNotNull(names::get)
                event = event.copy(
                    title = gameNames.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    appNames = names.filterKeys { it in event.appIds },
                    partial = event.partial || event.appIds.isEmpty() || gameNames.size != event.appIds.size
                )
            }
            if (event.kind == SteamSpaceEventKind.STATUS) {
                if (statusRequests++ >= MAX_STATUS_REQUESTS) {
                    event = event.copy(partial = true)
                } else {
                    val status = optional(deadline) {
                        SteamSpaceParser.status(call(account, STATUS_METHOD, SteamProtoWriter().apply {
                            writeUint64(1, event.actorSteamId)
                            writeUint64(2, event.occurredAtEpochSeconds)
                        }), event.actorSteamId, event.occurredAtEpochSeconds)
                    }
                    if (status != null && status.first == null) return@mapNotNull null // Deleted on Steam.
                    event = event.copy(body = status?.first, partial = status == null || status.second)
                }
            }
            if (event.kind == SteamSpaceEventKind.SCREENSHOT || event.kind == SteamSpaceEventKind.VIDEO) {
                val file = entry.publishedFileId?.let(loadedFiles::get)
                if (file != null && !file.readable) {
                    partial = true
                    return@mapNotNull null // Respect privacy/removal; no stale title or preview survives.
                }
                event = if (file == null || file.creator != event.actorSteamId) event.copy(partial = true) else event.copy(
                    title = file.title, body = file.body, media = listOfNotNull(file.media),
                    partial = event.partial || file.partial || file.media == null
                )
            }
            partial = partial || event.partial
            event
        }
        val next = oldest?.takeIf { news.rawCount >= PAGE_SIZE && it > 1L }?.minus(1L)?.let { before ->
            if (cursor != null && before >= cursor.beforeEpochSeconds) null else
                SteamSpaceCursor(account.id, account.steamId, before)
        }
        if (news.rawCount >= PAGE_SIZE && next == null) partial = true
        return SteamSpacePage(events, next, partial)
    }

    private fun call(account: SteamAccount, method: String, request: SteamProtoWriter): ByteArray {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Activity request cancelled")
        return cm.callService(account, method, request.toByteArray()).also {
            if (it.size > SteamSpaceParser.MAX_RESPONSE_BYTES) throw SteamSpaceFormatException()
        }
    }

    /** Missing enrichment is partial data; authentication and cancellation must reach the owner. */
    private inline fun <T> optional(deadline: Long, block: () -> T): T? = try {
        if (monotonicNanos() >= deadline) null else block()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: InterruptedException) {
        Thread.currentThread().interrupt()
        throw CancellationException("Activity request cancelled")
    } catch (failure: SteamApiException) {
        if (failure.authenticationRequired || failure.eResult in setOf(5, 15, 21, 27, 63, 65, 401, 403) ||
            failure.httpStatusCode in setOf(401, 403)) throw failure
        null
    } catch (_: Exception) {
        null
    }

    companion object {
        const val PAGE_SIZE = 20
        internal const val MAX_STATUS_REQUESTS = 8
        internal const val MAX_FILES = 40
        internal const val NEWS_METHOD = "UserNews.GetUserNews#1"
        internal const val STATUS_METHOD = "Player.GetPostedStatus#1"
        internal const val FILE_METHOD = "PublishedFile.GetDetails#1"
        internal const val APPS_METHOD = "StoreBrowse.GetItems#1"
        private const val ENRICHMENT_BUDGET_NANOS = 20_000_000_000L
        // Same event categories and filter-bit mapping used by the Steam library activity client.
        internal const val FILTER_FLAGS = 2L or 4L or 128L or 256L or 512L or 1024L or 4096L or 131072L
        private val LANGUAGES = setOf("english", "schinese", "tchinese", "japanese", "russian", "vietnamese")
        private val GAME_KINDS = setOf(
            SteamSpaceEventKind.GAME_ADDED, SteamSpaceEventKind.WISHLISTED,
            SteamSpaceEventKind.FIRST_PLAY, SteamSpaceEventKind.REVIEW
        )
    }
}
