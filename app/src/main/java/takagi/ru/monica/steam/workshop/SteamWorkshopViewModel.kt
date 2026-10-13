package takagi.ru.monica.steam.workshop

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

internal data class WorkshopUiState(
    val query: WorkshopQuery = WorkshopQuery(),
    val items: List<WorkshopItem> = emptyList(),
    val tags: List<WorkshopTag> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val total: Int = 0,
    val scanned: Int = 0,
    val failure: WorkshopFailure? = null,
    val selected: WorkshopItem? = null,
    val loadingDetail: Boolean = false,
    val detailFailure: WorkshopFailure? = null,
    val pending: Set<String> = emptySet(),
    val actionFailure: WorkshopFailure? = null,
    val actionSucceeded: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val bulk: WorkshopBulkState? = null,
    val bulkDialogVisible: Boolean = false,
    val imported: WorkshopImportState? = null,
    val export: WorkshopExportState? = null
) {
    /** Known matching states never enter a queue; unknown states are checked by the worker. */
    fun selectedBulkIds(subscribe: Boolean): List<String> {
        if (subscribe && query.subscribedOnly) return emptyList()
        return items.filter { item ->
            item.id in selectedIds && if (subscribe) item.subscribed != true && item.canSubscribeHere
                else item.subscribed != false
        }.map { it.id }
    }
}

/** One instance per storage origin, account and game. Writes cannot migrate to another account. */
internal class SteamWorkshopViewModel(
    private val appId: Int,
    private var account: SteamAccount?,
    private val gateway: SteamWorkshopGateway,
    private val resolver: SteamAccountSessionResolver? = null,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bulkController: WorkshopBulkController = InMemoryWorkshopBulkController(appId, account, gateway, resolver),
    presetRepository: WorkshopPresetRepository = InMemoryWorkshopPresetRepository()
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkshopUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var detailGeneration = 0L
    private var page = 0
    private var started = false
    private var visibleHosts = 0
    private var listJob: Job? = null
    private var detailJob: Job? = null
    private val confirmed = mutableMapOf<String, Boolean>()
    private val confirmationVersions = mutableMapOf<String, Long>()
    private var mutationVersion = 0L
    private val detailHistory = mutableListOf<WorkshopItem>()
    private var importJob: Job? = null
    private var importGeneration = 0L
    private var exportJob: Job? = null
    private var exportGeneration = 0L
    private var observedBulkId: String? = null
    private var appliedBulkResults = 0

    val presets = WorkshopPresetManager(appId, viewModelScope, presetRepository, processingDispatcher,
        read = { next -> authenticated { gateway.browse(it, appId, WorkshopQuery(subscribedOnly = true, sort = WorkshopSort.UPDATED), next) } },
        inspect = { ids -> authenticated { gateway.inspectItems(it, appId, ids) } },
        canReadSubscriptions = { bulkController.state.value?.running != true && state.value.pending.isEmpty() && state.value.export?.loading != true },
        onSaved = ::resetContentForPresets)

    init {
        viewModelScope.launch {
            bulkController.state.collect { bulk ->
                val previous = state.value.bulk
                if (bulk?.requestId != observedBulkId) {
                    observedBulkId = bulk?.requestId
                    appliedBulkResults = 0
                }
                bulk?.results?.drop(appliedBulkResults)?.forEach { result ->
                    when (result.outcome) {
                        WorkshopBulkOutcome.UPDATED, WorkshopBulkOutcome.UNCHANGED -> {
                            val current = state.value
                            val item = current.items.firstOrNull { it.id == result.id }
                                ?: current.selected?.takeIf { it.id == result.id }
                                ?: current.imported?.entries?.firstOrNull { it.id == result.id }?.item
                                ?: WorkshopItem(result.id, appId, result.title)
                            recordSubscription(item, bulk.subscribes(result.id))
                        }
                        WorkshopBulkOutcome.FAILED -> recordUnknown(result.id)
                        WorkshopBulkOutcome.UNAVAILABLE -> Unit
                    }
                }
                appliedBulkResults = bulk?.results?.size ?: 0
                mutableState.update { it.copy(bulk = bulk) }
                // Subscription removals shift offset pagination. Re-read once when this queue ends.
                if (previous?.running == true && bulk?.running == false && started && state.value.query.subscribedOnly) refresh()
            }
        }
    }

    fun start() {
        visibleHosts++
        if (started) return
        started = true
        refresh()
        state.value.selected?.let(::open)
        state.value.imported?.let { openImport(it.share, true) }
    }

    /** Stop scanning when leaving the screen; already requested writes still finish on their account. */
    fun stopBrowsing() {
        // Library and Store can briefly share this account/game model during a Dock transition.
        visibleHosts = (visibleHosts - 1).coerceAtLeast(0)
        if (visibleHosts > 0) return
        started = false
        listJob?.cancel()
        detailJob?.cancel()
        importJob?.cancel()
        importGeneration++
        dismissExport()
        presets.stopReading()
        generation++
        detailGeneration++
        mutableState.update { it.copy(loading = false, loadingMore = false, loadingDetail = false,
            imported = it.imported?.copy(loading = false)) }
    }

    fun updateAccount(updated: SteamAccount?) {
        val previous = account ?: return
        if (updated?.id != previous.id || updated.steamId != previous.steamId) return
        if (updated.accessToken == previous.accessToken && updated.refreshToken == previous.refreshToken) return
        account = updated
        bulkController.updateAccount(updated)
        if (state.value.failure == WorkshopFailure.LOGIN) refresh()
        if (state.value.detailFailure == WorkshopFailure.LOGIN && state.value.pending.isEmpty()) state.value.selected?.let(::open)
    }

    fun query(query: WorkshopQuery) {
        if (query == state.value.query) return
        mutableState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
        started = true
        val current = state.value
        if (query.subscribedOnly && current.query.subscribedOnly &&
            query.search == current.query.search && query.tags == current.query.tags &&
            !current.loading && !current.loadingMore && !current.hasMore &&
            current.failure == null && page > 0) {
            // All matching subscriptions are already loaded; sorting needs no Steam round trips.
            listJob?.cancel()
            val version = ++generation
            mutableState.update { it.copy(query = query, loading = true) }
            listJob = viewModelScope.launch {
                while (version == generation) {
                    val snapshot = state.value.items
                    val ordered = withContext(processingDispatcher) { WorkshopSubscriptionOrder.sorted(snapshot, query.sort) }
                    if (version != generation) return@launch
                    if (state.value.items !== snapshot) continue
                    mutableState.update { it.copy(items = ordered, loading = false) }
                    break
                }
            }
            return
        }
        val debounce = query.search != state.value.query.search
        mutableState.update { it.copy(query = query) }
        loadFirst(clear = true, debounce = debounce)
    }

    fun refresh() = loadFirst(clear = false, debounce = false)

    private fun loadFirst(clear: Boolean, debounce: Boolean) {
        listJob?.cancel()
        val version = ++generation
        page = 0
        mutableState.update { it.copy(items = if (clear) emptyList() else it.items,
            loading = true, loadingMore = false, failure = null, hasMore = false, scanned = 0,
            total = if (clear) 0 else it.total) }
        listJob = viewModelScope.launch {
            if (debounce) delay(350)
            fetchPages(version, first = true)
        }
    }

    fun loadMore() {
        if (state.value.loading || state.value.loadingMore || !state.value.hasMore) return
        val version = generation
        mutableState.update { it.copy(loadingMore = true, failure = null) }
        listJob = viewModelScope.launch { fetchPages(version, first = false) }
    }

    private suspend fun fetchPages(version: Long, first: Boolean) {
        val query = state.value.query
        var replace = first
        try {
            do {
                val next = page + 1
                val requestVersion = mutationVersion
                val batch = if (query.subscribedOnly) authenticated { gateway.browse(it, appId, query, next) }
                    else gateway.browse(null, appId, query, next)
                if (version != generation) return
                if (batch.page != next) throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
                var merged: List<WorkshopItem>
                while (true) {
                    val snapshot = state.value.items
                    val mutations = mutationVersion
                    val versions = confirmationVersions.toMap()
                    val statuses = confirmed.toMap()
                    merged = withContext(processingDispatcher) {
                        val incoming = batch.items.map { item ->
                            if ((versions[item.id] ?: 0L) > requestVersion) item.copy(subscribed = statuses[item.id]) else item
                        }.filter { !query.subscribedOnly || (it.subscribed == true && it.matchesSearch(query.search)) }
                        mergeWorkshopPage(if (replace) emptyList() else snapshot, incoming, query)
                    }
                    if (version != generation) return
                    // Navigation, details or writes may change the list while CPU work runs.
                    if (mutations == mutationVersion && state.value.items === snapshot) break
                }
                page = next
                mutableState.update { it.copy(
                    items = merged,
                    tags = batch.tags.ifEmpty { it.tags }, total = batch.total,
                    scanned = (if (replace) 0 else it.scanned) + batch.items.size,
                    loading = false, loadingMore = false, hasMore = batch.hasMore, failure = null
                ) }
                replace = false
                // Steam GetUserFiles has no text-search parameter. Scan every subscription page,
                // showing matches progressively instead of searching only the first loaded page.
                val keepSearching = query.scansSubscriptions() && batch.hasMore
                if (!keepSearching) break
                mutableState.update { it.copy(loadingMore = true) }
                delay(350)
            } while (version == generation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (version == generation) mutableState.update {
                it.copy(loading = false, loadingMore = false, failure = error.workshopFailure())
            }
        }
    }

    fun open(item: WorkshopItem) {
        if (item.appId != appId) return
        detailJob?.cancel()
        val version = ++detailGeneration
        val requestVersion = mutationVersion
        mutableState.update { it.copy(selected = item, loadingDetail = true, detailFailure = null,
            actionFailure = null, actionSucceeded = false) }
        detailJob = viewModelScope.launch {
            try {
                val detail = if (account == null) gateway.publicDetails(appId, item.id)
                    else authenticated { gateway.details(it, appId, item.id) }
                if (version == detailGeneration) mutableState.update { current ->
                    val resolved = if ((confirmationVersions[item.id] ?: 0L) > requestVersion)
                        detail.copy(subscribed = confirmed[item.id]) else detail
                    current.copy(selected = resolved, loadingDetail = false,
                        items = current.items.map { if (it.id == resolved.id) resolved else it })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == detailGeneration) mutableState.update {
                    it.copy(loadingDetail = false, detailFailure = error.workshopFailure())
                }
            }
        }
    }

    fun closeDetail() {
        if (detailHistory.isNotEmpty()) {
            open(detailHistory.removeAt(detailHistory.lastIndex))
            return
        }
        detailJob?.cancel()
        detailGeneration++
        mutableState.update { it.copy(selected = null, loadingDetail = false, detailFailure = null) }
    }

    fun openDependency(item: WorkshopItem) {
        val parent = state.value.selected ?: return
        if (parent.id == item.id || item.appId != appId) return
        detailHistory += parent
        open(item)
    }

    fun subscribe(subscribe: Boolean, dependencies: Boolean = false) {
        if (bulkController.state.value?.running == true || state.value.export?.loading == true || presets.state.value.readingSubscriptions) return
        val item = state.value.selected ?: return
        if (item.id in state.value.pending || state.value.loadingDetail || state.value.detailFailure != null || item.subscribed == null) return
        if (subscribe == item.subscribed) return
        mutableState.update { it.copy(pending = it.pending + item.id, actionFailure = null, actionSucceeded = false) }
        viewModelScope.launch {
            try {
                authenticated { gateway.setSubscription(it, item, subscribe, dependencies) }
                recordSubscription(item, subscribe)
                mutableState.update { it.copy(actionSucceeded = it.selected?.id == item.id) }
                // Removing a row shifts Steam's offset pagination; restart to avoid skipping a row.
                if (started && state.value.query.subscribedOnly) refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                recordUnknown(item.id)
                mutableState.update { current -> current.copy(
                    selected = current.selected?.let { if (it.id == item.id) it.copy(subscribed = null) else it },
                    actionFailure = if (current.selected?.id == item.id) error.workshopFailure() else current.actionFailure
                ) }
            } finally {
                mutableState.update { it.copy(pending = it.pending - item.id) }
            }
        }
    }

    fun toggleSelection(id: String) {
        if (state.value.bulk?.running == true || state.value.items.none { it.id == id }) return
        mutableState.update { it.copy(selectionMode = true,
            selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id) }
    }

    fun beginSelection() { mutableState.update { it.copy(selectionMode = true) } }
    fun endSelection() { mutableState.update { it.copy(selectionMode = false, selectedIds = emptySet()) } }
    fun selectLoaded() {
        if (state.value.bulk?.running != true) mutableState.update {
            it.copy(selectionMode = true, selectedIds = it.items.mapTo(linkedSetOf()) { row -> row.id })
        }
    }

    fun exportSelected() = exportIds(state.value.selectedIds.toList())

    fun exportPreset(preset: WorkshopPreset) { if (preset.share.appId == appId) exportIds(preset.share.itemIds) }

    fun saveExportAsPreset() {
        val share = state.value.export?.share ?: return
        dismissExport()
        presets.fromShare(share)
    }

    fun saveImportAsPreset() { state.value.imported?.share?.let(presets::fromShare) }

    fun showPresets() {
        resetContentForPresets()
        presets.show()
    }

    private fun resetContentForPresets() {
        closeImport()
        detailJob?.cancel()
        detailGeneration++
        detailHistory.clear()
        mutableState.update { it.copy(selected = null, loadingDetail = false, selectionMode = false, selectedIds = emptySet()) }
    }

    fun applyPreset() {
        val preview = presets.state.value.preview?.takeIf { it.canApply } ?: return
        if (bulkController.state.value?.running == true || state.value.pending.isNotEmpty() || state.value.export?.loading == true) return
        val plan = preview.plan ?: return
        presets.dismissPreview()
        showBulk()
        bulkController.startPreset(plan)
    }

    private fun exportIds(ids: List<String>) {
        if (state.value.export?.loading == true || bulkController.state.value?.running == true || presets.state.value.readingSubscriptions) return
        val version = ++exportGeneration
        mutableState.update { it.copy(export = WorkshopExportState(count = ids.size)) }
        exportJob = viewModelScope.launch {
            try {
                val code = withContext(processingDispatcher) { WorkshopShareCode.encode(WorkshopShare(appId, ids)) }
                if (version != exportGeneration) return@launch
                mutableState.update { it.copy(export = WorkshopExportState(loading = false, count = ids.size, code = code,
                    share = WorkshopShare(appId, ids))) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: WorkshopShareCodeException) {
                if (version == exportGeneration) mutableState.update { it.copy(export = WorkshopExportState(loading = false, problem = error.problem)) }
            }
        }
    }

    fun exportSubscriptions() {
        if (state.value.export?.loading == true || bulkController.state.value?.running == true || state.value.pending.isNotEmpty() ||
            presets.state.value.readingSubscriptions) return
        val version = ++exportGeneration
        mutableState.update { it.copy(export = WorkshopExportState()) }
        exportJob = viewModelScope.launch {
            try {
                val snapshot = readAllWorkshopSubscriptions(appId, read = { next ->
                    authenticated { gateway.browse(it, appId, WorkshopQuery(subscribedOnly = true, sort = WorkshopSort.UPDATED), next) }
                }, progress = { count ->
                    if (version == exportGeneration) mutableState.update { it.copy(export = WorkshopExportState(count = count)) }
                })
                val share = WorkshopShare(appId, snapshot.ids)
                val code = withContext(processingDispatcher) { WorkshopShareCode.encode(share) }
                if (version != exportGeneration) return@launch
                mutableState.update { it.copy(export = WorkshopExportState(loading = false, count = share.itemIds.size, code = code, share = share)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: WorkshopShareCodeException) {
                if (version == exportGeneration) mutableState.update { it.copy(export = WorkshopExportState(loading = false, problem = error.problem)) }
            } catch (error: Exception) {
                if (version == exportGeneration) mutableState.update { it.copy(export = WorkshopExportState(loading = false, failure = error.workshopFailure())) }
            }
        }
    }

    fun dismissExport() {
        exportJob?.cancel()
        exportGeneration++
        mutableState.update { it.copy(export = null) }
    }

    fun openImport(share: WorkshopShare, preserveSelection: Boolean = false) {
        if (share.appId != appId || state.value.bulk?.running == true) return
        presets.hide()
        importJob?.cancel()
        val version = ++importGeneration
        val chosen = state.value.imported?.takeIf { preserveSelection && it.share == share }?.selectedIds
        detailJob?.cancel()
        detailGeneration++
        detailHistory.clear()
        mutableState.update { it.copy(selected = null, loadingDetail = false, selectionMode = false, selectedIds = emptySet(),
            imported = WorkshopImportState(share, selectedIds = chosen ?: share.itemIds.toSet())) }
        importJob = viewModelScope.launch {
            try {
                for (chunk in share.itemIds.chunked(20)) {
                    val items = authenticated { gateway.inspectItems(it, appId, chunk) }
                    if (version != importGeneration) return@launch
                    if (items.any { it.appId != appId || it.id !in chunk }) throw WorkshopException(WorkshopFailure.INVALID_RESPONSE)
                    val details = items.associateBy { it.id }
                    val entries = chunk.associateWith { WorkshopImportEntry(it, details[it], loaded = true) }
                    val unavailable = entries.values.filterNot { it.selectable }.mapTo(hashSetOf()) { it.id }
                    mutableState.update { current -> current.copy(imported = current.imported?.let {
                        it.copy(entries = it.entries.map { row -> entries[row.id] ?: row }, selectedIds = it.selectedIds - unavailable)
                    }) }
                    if (chunk.last() != share.itemIds.last()) delay(350)
                }
                mutableState.update { it.copy(imported = it.imported?.copy(loading = false)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == importGeneration) mutableState.update {
                    it.copy(imported = it.imported?.copy(loading = false, failure = error.workshopFailure()))
                }
            }
        }
    }

    fun retryImport() { state.value.imported?.let { openImport(it.share, true) } }
    fun closeImport() {
        importJob?.cancel()
        importGeneration++
        mutableState.update { it.copy(imported = null) }
    }
    fun toggleImported(id: String) {
        val imported = state.value.imported ?: return
        if (imported.loading || id !in imported.selectableIds || state.value.bulk?.running == true) return
        mutableState.update { it.copy(imported = imported.copy(selectedIds =
            if (id in imported.selectedIds) imported.selectedIds - id else imported.selectedIds + id)) }
    }
    fun selectImported(all: Boolean) {
        val imported = state.value.imported ?: return
        if (!imported.loading && state.value.bulk?.running != true) mutableState.update {
            it.copy(imported = imported.copy(selectedIds = if (all) imported.selectableIds else emptySet()))
        }
    }
    fun subscribeImported(all: Boolean, dependencies: Boolean) {
        val imported = state.value.imported ?: return
        if (imported.loading || imported.failure != null) return
        val ids = if (all) imported.selectableIds else imported.selectedIds.intersect(imported.selectableIds)
        runBulk(ids.toList(), true, dependencies)
    }
    fun manageSelected(subscribe: Boolean, dependencies: Boolean = false) =
        runBulk(state.value.selectedBulkIds(subscribe), subscribe, dependencies)
    fun stopBulk() = bulkController.stop()
    fun showBulk() { mutableState.update { it.copy(bulkDialogVisible = true) } }
    fun dismissBulk() {
        mutableState.update { it.copy(bulkDialogVisible = false) }
        if (bulkController.state.value?.running != true) bulkController.dismissFinished()
    }
    fun retryBulk() {
        if (state.value.pending.isNotEmpty() || state.value.export?.loading == true || presets.state.value.readingSubscriptions) return
        if (bulkController.state.value?.canRetry == true) {
            showBulk()
            bulkController.retry()
        }
    }

    private fun runBulk(requestedIds: List<String>, subscribe: Boolean, dependencies: Boolean) {
        val ids = requestedIds.distinct()
        if (ids.size !in 1..WorkshopShareCode.MAX_ITEMS || ids.any { !WorkshopShareCode.validId(it) } || state.value.bulk?.running == true ||
            state.value.pending.isNotEmpty() || state.value.export?.loading == true || presets.state.value.readingSubscriptions) return
        showBulk()
        bulkController.start(ids, subscribe, dependencies)
    }

    private fun recordSubscription(item: WorkshopItem, subscribed: Boolean) {
        confirmed[item.id] = subscribed
        confirmationVersions[item.id] = ++mutationVersion
        val resolved = item.copy(subscribed = subscribed)
        mutableState.update { current -> current.copy(
            selected = current.selected?.let { if (it.id == item.id) resolved else it },
            selectedIds = current.selectedIds - item.id,
            items = current.items.map { if (it.id == item.id) resolved else it }
                .filter { !current.query.subscribedOnly || it.subscribed != false },
            imported = current.imported?.let { imported -> imported.copy(
                entries = imported.entries.map { if (it.id == item.id) it.copy(item = resolved, loaded = true) else it },
                selectedIds = if (subscribed) imported.selectedIds - item.id else imported.selectedIds)
            }
        ) }
    }

    private fun recordUnknown(id: String) {
        confirmed.remove(id)
        confirmationVersions[id] = ++mutationVersion
        mutableState.update { current -> current.copy(
            selected = current.selected?.let { if (it.id == id) it.copy(subscribed = null) else it },
            items = current.items.map { if (it.id == id) it.copy(subscribed = null) else it },
            imported = current.imported?.let { imported -> imported.copy(entries = imported.entries.map { row ->
                if (row.id == id) row.copy(item = row.item?.copy(subscribed = null)) else row
            }) }
        ) }
    }

    private suspend fun <T> authenticated(block: suspend (SteamAccount) -> T): T {
        val original = account ?: throw WorkshopException(WorkshopFailure.LOGIN)
        val resolved = resolver?.resolve(original, false) ?: original
        if (resolved.id != original.id || resolved.steamId != original.steamId) throw WorkshopException(WorkshopFailure.LOGIN)
        account = resolved
        return try { block(resolved) } catch (error: SteamApiException) {
            if (error.workshopFailure() != WorkshopFailure.LOGIN || resolver == null) throw error
            val fresh = resolver.resolve(resolved, true)
            if (fresh.id != original.id || fresh.steamId != original.steamId) throw WorkshopException(WorkshopFailure.LOGIN)
            account = fresh
            block(fresh)
        }
    }
}

internal fun Throwable.workshopFailure(): WorkshopFailure = when {
    this is WorkshopException -> reason
    this is SteamApiException && (httpStatusCode == 429 || eResult == 84) -> WorkshopFailure.RATE_LIMIT
    this is SteamApiException && (httpStatusCode == 401 || eResult in listOf(5, 21, 27)) -> WorkshopFailure.LOGIN
    this is SteamApiException && (httpStatusCode == 403 || eResult == 15) -> WorkshopFailure.UNAVAILABLE
    this is SteamApiException && (httpStatusCode in listOf(408, 500, 502, 503, 504) ||
        eResult in listOf(3, 10, 16, 20, 35, 37, 38, 48, 55)) -> WorkshopFailure.NETWORK
    this is SteamApiException -> WorkshopFailure.UNAVAILABLE
    this is java.io.IOException -> WorkshopFailure.NETWORK
    else -> WorkshopFailure.INVALID_RESPONSE
}
