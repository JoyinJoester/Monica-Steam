package takagi.ru.monica.steam.workshop

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonObject
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.network.cm.SteamCmClient

internal class SteamWorkshopService(
    private val api: SteamApiClient = SteamApiClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cm: SteamCmGateway = SteamCmClient(),
    private val diagnostics: (String) -> Unit = SteamDiagLogger::append
) : SteamWorkshopGateway {
    override suspend fun supportsWorkshop(appId: Int): Boolean = withContext(io) {
        require(appId > 0)
        supportCache[appId]?.let { (timestamp, supported) ->
            if (System.currentTimeMillis() - timestamp < 3_600_000L) return@withContext supported
        }
        val result = api.steamStoreGetJson(appId, "US", "english").obj(appId.toString())
        if (!result.bool("success")) throw WorkshopException(WorkshopFailure.UNAVAILABLE)
        val supported = result.obj("data").array("categories").any { it.jsonObject.int("id") == 30 }
        if (supportCache.size >= 128) supportCache.clear()
        supportCache[appId] = System.currentTimeMillis() to supported
        supported
    }

    override suspend fun browse(account: SteamAccount?, appId: Int, query: WorkshopQuery, page: Int): WorkshopBatch = withContext(io) {
        require(appId > 0 && page > 0)
        if (query.subscribedOnly) {
            val authenticated = authenticatedAccount(account)
            val request = SteamProtoWriter().apply {
                writeFixed64(1, authenticated.steamId.toLong())
                writeVarint(2, appId.toLong())
                writeVarint(4, page.toLong())
                writeVarint(5, SteamWorkshopProtocol.SUBSCRIPTION_PAGE_SIZE.toLong())
                writeString(6, "mysubscriptions")
                writeString(7, "lastupdated")
                query.tags.sorted().forEach { writeString(10, it) }
                writeBool(20, true)
                writeBool(24, true)
                writeBool(32, true)
            }
            call("GetUserFiles", request, authenticated) { SteamWorkshopProtocol.userFiles(it, appId, page) }
                .also { batch ->
                    report("workshop_page page=$page total=${batch.total} visible=${batch.items.size} more=${batch.hasMore}")
                }
        } else {
            // Public browsing does not require credentials; personal actions use the selected account.
            SteamWorkshopParser.browse(
                api.communityGetText("/workshop/browse/", browseParameters(appId, query, page)), appId, page
            )
        }
    }

    override suspend fun details(account: SteamAccount, appId: Int, id: String): WorkshopItem = withContext(io) {
        require(appId > 0 && id.toULongOrNull()?.let { it > 0uL } == true)
        val authenticated = authenticatedAccount(account)
        val request = SteamProtoWriter().apply {
            writeFixed64(1, id.toULong().toLong())
            writeBool(2, true)
            writeBool(3, true)
            writeBool(4, true)
            writeBool(6, true)
            writeVarint(14, appId.toLong())
            writeBool(15, true)
        }
        val item = call("GetDetails", request, authenticated) { SteamWorkshopProtocol.details(it, appId, id) }
        val subscribed = call("AreFilesInSubscriptionList",
            SteamWorkshopProtocol.statusRequest(appId, listOf(id)), authenticated) { SteamWorkshopProtocol.status(it, id) }
        val children = item.children.take(50)
        val dependencies = if (children.isEmpty()) emptyList() else try {
            val childRequest = SteamProtoWriter().apply {
                children.forEach { writeFixed64(1, it.toULong().toLong()) }
                writeBool(8, true)
                writeVarint(14, appId.toLong())
                writeBool(15, true)
            }
            call("GetDetails", childRequest, authenticated) { SteamWorkshopProtocol.dependencies(it, appId, children) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() } // Deleted/private dependencies still have their official links.
        item.copy(subscribed = subscribed, dependencies = dependencies)
    }

    override suspend fun publicDetails(appId: Int, id: String): WorkshopItem =
        takagi.ru.monica.steam.workshop.download.WorkshopDownloadClient().details(appId, id).item

    override suspend fun setSubscription(account: SteamAccount, item: WorkshopItem, subscribe: Boolean, dependencies: Boolean) = withContext(io) {
        require(item.id.toULongOrNull()?.let { it > 0uL } == true && item.appId > 0)
        if (subscribe && (!item.canSubscribe || item.banned || item.fileType != 0)) {
            throw WorkshopException(WorkshopFailure.UNAVAILABLE)
        }
        val authenticated = authenticatedAccount(account)
        call(if (subscribe) "Subscribe" else "Unsubscribe",
            SteamWorkshopProtocol.subscriptionRequest(item, subscribe, dependencies), authenticated) { Unit }
        // Never turn an ambiguous empty HTTP response into an optimistic success.
        val confirmed = call("AreFilesInSubscriptionList",
            SteamWorkshopProtocol.statusRequest(item.appId, listOf(item.id)), authenticated) { SteamWorkshopProtocol.status(it, item.id) }
        if (confirmed != subscribe) throw WorkshopException(WorkshopFailure.UNCONFIRMED)
    }

    // These PublishedFile methods require Web API privileges that user tokens do not
    // provide. Use the same account-scoped authenticated CM session as Steam chat.
    private fun <T> call(method: String, request: SteamProtoWriter, account: SteamAccount, decode: (ByteArray) -> T): T {
        val response = try {
            cm.callService(account, "PublishedFile.$method#1", request.toByteArray())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val apiError = error as? SteamApiException
            report("workshop_cm method=$method stage=request failure=${error.workshopFailure()} " +
                "http=${apiError?.httpStatusCode ?: -1} eresult=${apiError?.eResult ?: -1}")
            throw error
        }
        return try {
            decode(response)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            report("workshop_cm method=$method stage=decode failure=${error.workshopFailure()} bytes=${response.size}")
            throw error
        }
    }

    override suspend fun inspectItems(account: SteamAccount, appId: Int, ids: List<String>): List<WorkshopItem> = withContext(io) {
        require(appId > 0 && ids.size in 1..50 && ids.distinct().size == ids.size && ids.all(WorkshopShareCode::validId))
        val authenticated = authenticatedAccount(account)
        val request = SteamProtoWriter().apply {
            ids.forEach { writeFixed64(1, it.toULong().toLong()) }
            writeBool(2, true)
            writeBool(6, true)
            writeVarint(14, appId.toLong())
            writeBool(15, true)
        }
        val items = call("GetDetails", request, authenticated) { SteamWorkshopProtocol.items(it, appId, ids) }
        if (items.isEmpty()) return@withContext emptyList()
        val availableIds = items.map { it.id }
        val statuses = call("AreFilesInSubscriptionList", SteamWorkshopProtocol.statusRequest(appId, availableIds), authenticated) {
            SteamWorkshopProtocol.statuses(it, availableIds)
        }
        items.map { it.copy(subscribed = statuses.getValue(it.id)) }
    }

    suspend fun gameName(appId: Int): String = withContext(io) {
        val result = api.steamStoreGetJson(appId, "US", "english").obj(appId.toString())
        if (!result.bool("success")) throw WorkshopException(WorkshopFailure.UNAVAILABLE)
        result.obj("data").text("name")
    }

    // Only fixed operation names, status codes and counts; never account IDs,
    // credentials, queries, item contents or server-provided exception messages.
    private fun report(message: String) { runCatching { diagnostics(message) } }

    private fun authenticatedAccount(account: SteamAccount?): SteamAccount {
        if (account?.hasRealSteamId != true || account.accessToken.isNullOrBlank()) {
            throw WorkshopException(WorkshopFailure.LOGIN)
        }
        return account
    }

    companion object {
        private val supportCache = ConcurrentHashMap<Int, Pair<Long, Boolean>>()
        internal fun browseParameters(appId: Int, query: WorkshopQuery, page: Int): Map<String, String> = linkedMapOf(
            "appid" to appId.toString(), "section" to "readytouseitems", "browsesort" to query.sort.wire,
            "p" to page.toString(), "num_per_page" to "30", "days" to query.days.toString(),
            "searchtext" to query.search.trim(), "l" to if (Locale.getDefault().language == "zh") "schinese" else "english"
        ).apply { query.tags.sorted().forEachIndexed { index, tag -> put("requiredtags[$index]", tag) } }
    }
}
