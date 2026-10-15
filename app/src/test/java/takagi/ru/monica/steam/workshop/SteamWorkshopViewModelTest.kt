package takagi.ru.monica.steam.workshop

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamWorkshopViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun searchScansAllSubscriptionPagesAndKeepsFilters() = runTest(scheduler) {
        val calls = mutableListOf<Int>()
        val gateway = FakeGateway().apply { browse = { _, query, page ->
            calls += page
            assertEquals(setOf("Mod"), query.tags)
            WorkshopBatch(listOf(item(page.toString()).copy(title = if (page == 3) "Needle" else "Other", subscribed = true)), page, page < 3, 3)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(search = "needle", subscribedOnly = true, tags = setOf("Mod")))
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(listOf("3"), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        assertEquals(3, vm.state.value.scanned)
    }

    @Test fun cancelledOldSearchCannotOverwriteLatestResults() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply { browse = { _, query, page ->
            if (query.search == "old") withContext(NonCancellable) { gate.await() }
            WorkshopBatch(listOf(item(if (query.search == "old") "1" else "2")), page, false, 1)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(search = "old")); advanceTimeBy(351); runCurrent()
        vm.query(WorkshopQuery(search = "new")); advanceTimeBy(351); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals("2", vm.state.value.items.single().id)
    }

    @Test fun duplicateTapsSendOneWriteAndUseCapturedAccount() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val gateway = FakeGateway().apply { mutate = { account, _, _, _ -> writes += account.steamId; gate.await() } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.open(item()); advanceUntilIdle()
        vm.subscribe(true); vm.subscribe(true); runCurrent()
        assertEquals(listOf(account().steamId), writes)
        assertEquals(false, vm.state.value.selected?.subscribed)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(true, vm.state.value.selected?.subscribed)
        assertTrue(vm.state.value.pending.isEmpty())
    }

    @Test fun failedWriteDoesNotClaimSuccessAndRequiresReconciliation() = runTest(scheduler) {
        val gateway = FakeGateway().apply { mutate = { _, _, _, _ -> throw java.io.IOException("offline") } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.open(item()); advanceUntilIdle(); vm.subscribe(true); advanceUntilIdle()
        assertNull(vm.state.value.selected?.subscribed)
        assertFalse(vm.state.value.actionSucceeded)
        assertEquals(WorkshopFailure.NETWORK, vm.state.value.actionFailure)
        assertTrue(vm.state.value.pending.isEmpty())
    }

    @Test fun switchingDetailsDuringWriteDoesNotOverwriteAnotherItem() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply { mutate = { _, _, _, _ -> gate.await() } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.open(item("1")); advanceUntilIdle(); vm.subscribe(true); runCurrent()
        vm.open(item("2")); runCurrent(); gate.complete(Unit); advanceUntilIdle()
        assertEquals("2", vm.state.value.selected?.id)
        assertEquals(false, vm.state.value.selected?.subscribed)
        assertFalse(vm.state.value.actionSucceeded)
    }

    @Test fun expiredSessionRefreshesOnceAndRetriesRead() = runTest(scheduler) {
        val refreshes = mutableListOf<Boolean>()
        val gateway = FakeGateway().apply { detail = { account, id ->
            if (account.accessToken == "old") throw SteamApiException("expired", httpStatusCode = 401)
            item(id)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, SteamAccountSessionResolver { account, force ->
            refreshes += force
            if (force) account.copy(accessToken = "fresh") else account
        }, processingDispatcher = dispatcher)
        vm.open(item()); advanceUntilIdle()
        assertEquals(listOf(false, true), refreshes)
        assertNull(vm.state.value.detailFailure)
    }

    @Test fun guestCanBrowseButCannotReadPrivateSubscriptions() = runTest(scheduler) {
        val vm = SteamWorkshopViewModel(294100, null, FakeGateway(), processingDispatcher = dispatcher)
        vm.start(); advanceUntilIdle(); assertNull(vm.state.value.failure)
        vm.query(WorkshopQuery(subscribedOnly = true)); advanceUntilIdle()
        assertEquals(WorkshopFailure.LOGIN, vm.state.value.failure)
    }

    @Test fun loadMoreDeduplicatesAndRetryUsesSameFailedPage() = runTest(scheduler) {
        var attempts = 0
        val gateway = FakeGateway().apply { browse = { _, _, page ->
            if (page == 2 && attempts++ == 0) throw java.io.IOException()
            WorkshopBatch(if (page == 1) listOf(item("1")) else listOf(item("1"), item("2")), page, page == 1, 2)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.start(); advanceUntilIdle(); vm.loadMore(); advanceUntilIdle()
        assertEquals(WorkshopFailure.NETWORK, vm.state.value.failure)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf("1", "2"), vm.state.value.items.map { it.id })
    }

    @Test fun lateBrowseCannotRevertConfirmedSubscription() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply { browse = { _, _, page ->
            gate.await()
            WorkshopBatch(listOf(item()), page, false, 1)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.start(); runCurrent()
        vm.open(item()); runCurrent(); vm.subscribe(true); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(true, vm.state.value.items.single().subscribed)
    }

    @Test fun explicitRefreshCanObserveChangesMadeOnAnotherDevice() = runTest(scheduler) {
        val vm = SteamWorkshopViewModel(294100, account(), FakeGateway(), processingDispatcher = dispatcher)
        vm.open(item()); advanceUntilIdle(); vm.subscribe(true); advanceUntilIdle()
        assertEquals(true, vm.state.value.selected?.subscribed)
        vm.open(item()); advanceUntilIdle()
        assertEquals(false, vm.state.value.selected?.subscribed)
    }

    @Test fun unsubscriptionRestartsOffsetPagination() = runTest(scheduler) {
        val pages = mutableListOf<Int>()
        var removed = false
        val gateway = FakeGateway().apply {
            browse = { _, _, page ->
                pages += page
                WorkshopBatch(listOf(item(if (removed) "2" else "1").copy(subscribed = true)), page, false, 1)
            }
            detail = { _, id -> item(id).copy(subscribed = true) }
            mutate = { _, _, _, _ -> removed = true }
        }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(subscribedOnly = true, sort = WorkshopSort.UPDATED)); advanceUntilIdle()
        vm.open(item("1")); advanceUntilIdle(); vm.subscribe(false); advanceUntilIdle()
        assertEquals(listOf(1, 1), pages)
        assertEquals(listOf("2"), vm.state.value.items.map { it.id })
    }

    @Test fun dependencyNavigationReturnsToParentThenList() = runTest(scheduler) {
        val vm = SteamWorkshopViewModel(294100, account(), FakeGateway(), processingDispatcher = dispatcher)
        vm.open(item("1")); advanceUntilIdle()
        vm.openDependency(item("2")); advanceUntilIdle()
        vm.closeDetail(); advanceUntilIdle(); assertEquals("1", vm.state.value.selected?.id)
        vm.closeDetail(); assertNull(vm.state.value.selected)
    }

    @Test fun searchIsDebouncedAndOnlyLatestTextIsSent() = runTest(scheduler) {
        val searches = mutableListOf<String>()
        val gateway = FakeGateway().apply { browse = { _, q, page -> searches += q.search; WorkshopBatch(emptyList(), page, false, 0) } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(search = "a")); advanceTimeBy(100)
        vm.query(WorkshopQuery(search = "ab")); advanceTimeBy(100)
        vm.query(WorkshopQuery(search = "abc")); advanceUntilIdle()
        assertEquals(listOf("abc"), searches)
    }

    @Test fun leavingScreenStopsFurtherSubscriptionPages() = runTest(scheduler) {
        val pages = mutableListOf<Int>()
        val gateway = FakeGateway().apply { browse = { _, _, page ->
            pages += page
            WorkshopBatch(listOf(item(page.toString()).copy(subscribed = true)), page, page < 20, 20)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(subscribedOnly = true, search = "Mod"))
        advanceTimeBy(351); runCurrent()
        vm.stopBrowsing(); advanceUntilIdle()
        assertEquals(listOf(1), pages)
        assertFalse(vm.state.value.loadingMore)
    }

    @Test fun leavingScreenLetsRequestedWriteFinishWithoutRestartingScanning() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        val pages = mutableListOf<Int>()
        val gateway = FakeGateway().apply {
            browse = { _, _, page -> pages += page; WorkshopBatch(emptyList(), page, false, 0) }
            mutate = { _, _, _, _ -> gate.await() }
        }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(subscribedOnly = true, sort = WorkshopSort.UPDATED)); advanceUntilIdle()
        vm.open(item()); advanceUntilIdle(); vm.subscribe(true); runCurrent()
        vm.stopBrowsing(); gate.complete(Unit); advanceUntilIdle()
        assertEquals(true, vm.state.value.selected?.subscribed)
        assertTrue(vm.state.value.pending.isEmpty())
        assertEquals(listOf(1), pages)
    }

    @Test fun outgoingDockPageCannotStopAnotherVisibleWorkshopHost() = runTest(scheduler) {
        val pages = mutableListOf<Int>()
        val gateway = FakeGateway().apply { browse = { _, _, page ->
            pages += page
            WorkshopBatch(listOf(item(page.toString()).copy(subscribed = true)), page, page < 2, 2)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.start(); vm.start()
        vm.query(WorkshopQuery(subscribedOnly = true, search = "Mod"))
        advanceTimeBy(351); runCurrent()
        vm.stopBrowsing(); advanceUntilIdle()
        assertEquals(listOf(1, 2), pages)
        vm.stopBrowsing()
    }

    @Test fun completeSubscriptionListChangesSortWithoutRefetchingPages() = runTest(scheduler) {
        var calls = 0
        val gateway = FakeGateway().apply { browse = { _, _, page ->
            calls++
            WorkshopBatch(listOf(item("1").copy(subscribed = true, updated = 10, created = 1),
                item("2").copy(subscribed = true, updated = 1, created = 10)), page, false, 2)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(subscribedOnly = true, sort = WorkshopSort.UPDATED)); advanceUntilIdle()
        assertEquals(listOf("1", "2"), vm.state.value.items.map { it.id })
        vm.query(vm.state.value.query.copy(sort = WorkshopSort.NEWEST)); advanceUntilIdle()
        assertEquals(listOf("2", "1"), vm.state.value.items.map { it.id })
        assertEquals(1, calls)
        assertFalse(vm.state.value.loading)
        vm.refresh(); advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun staleBackgroundPageCannotPublishAfterQueryChanges() = runTest(scheduler) {
        val cpuScheduler = TestCoroutineScheduler()
        val cpu = StandardTestDispatcher(cpuScheduler)
        val gateway = FakeGateway().apply { browse = { _, query, page ->
            WorkshopBatch(listOf(item(if (query.search == "old") "1" else "2")), page, false, 1)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = cpu)
        vm.query(WorkshopQuery(search = "old")); advanceTimeBy(351); runCurrent()
        vm.query(WorkshopQuery(search = "new")); advanceTimeBy(351); runCurrent()
        cpuScheduler.advanceUntilIdle(); runCurrent()
        assertEquals(listOf("2"), vm.state.value.items.map { it.id })
    }

    @Test fun permissionDeniedDoesNotAskUserToReloginOrRefreshTheirToken() = runTest(scheduler) {
        for (failure in listOf(SteamApiException("access denied", eResult = 15),
            SteamApiException("forbidden", httpStatusCode = 403))) {
            var forcedRefreshes = 0
            val gateway = FakeGateway().apply { browse = { _, _, _ -> throw failure } }
            val vm = SteamWorkshopViewModel(550, account(), gateway,
                SteamAccountSessionResolver { account, force -> if (force) forcedRefreshes++; account },
                processingDispatcher = dispatcher)
            vm.query(WorkshopQuery(subscribedOnly = true)); advanceUntilIdle()
            assertEquals(WorkshopFailure.UNAVAILABLE, vm.state.value.failure)
            assertEquals(0, forcedRefreshes)
        }
    }

    @Test fun expiredCmSessionStillRefreshesAndRetriesSubscriptionRead() = runTest(scheduler) {
        var calls = 0
        val gateway = FakeGateway().apply { browse = { account, _, page ->
            calls++
            if (account?.accessToken == "old") throw SteamApiException("not logged on", eResult = 21)
            WorkshopBatch(listOf(item().copy(subscribed = true)), page, false, 1)
        } }
        val vm = SteamWorkshopViewModel(294100, account(), gateway,
            SteamAccountSessionResolver { account, force -> if (force) account.copy(accessToken = "fresh") else account },
            processingDispatcher = dispatcher)
        vm.query(WorkshopQuery(subscribedOnly = true)); advanceUntilIdle()
        assertEquals(2, calls)
        assertNull(vm.state.value.failure)
        assertEquals(1, vm.state.value.items.size)
    }

    @Test fun steamServiceFailuresAreNotReportedAsMalformedSubscriptionData() = runTest(scheduler) {
        for ((error, expected) in listOf(
            SteamApiException("service unavailable", eResult = 20) to WorkshopFailure.NETWORK,
            SteamApiException("timeout", eResult = 16) to WorkshopFailure.NETWORK,
            SteamApiException("bad gateway", httpStatusCode = 502) to WorkshopFailure.NETWORK,
            SteamApiException("request failed", eResult = 2) to WorkshopFailure.UNAVAILABLE
        )) {
            val gateway = FakeGateway().apply { browse = { _, _, _ -> throw error } }
            val vm = SteamWorkshopViewModel(550, account(), gateway, processingDispatcher = dispatcher)
            vm.query(WorkshopQuery(subscribedOnly = true)); advanceUntilIdle()
            assertEquals(expected, vm.state.value.failure)
        }
    }

    @Test fun personalSessionFailureStillLoadsPublicWallpaperDetailsWithoutEnablingSubscription() = runTest(scheduler) {
        var writes = 0
        val gateway = FakeGateway().apply {
            detail = { _, _ -> throw SteamApiException("expired", httpStatusCode = 401) }
            publicDetail = { id -> item(id).copy(description = "Public description", preview = "https://example.com/preview.jpg") }
            mutate = { _, _, _, _ -> writes++ }
        }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.open(item()); advanceUntilIdle()
        assertEquals("Public description", vm.state.value.selected?.description)
        assertEquals(WorkshopFailure.LOGIN, vm.state.value.detailFailure)
        assertNull(vm.state.value.selected?.subscribed)
        vm.subscribe(true); advanceUntilIdle()
        assertEquals(0, writes)
    }

    @Test fun failedPublicFallbackPreservesTheOriginalPersonalFailure() = runTest(scheduler) {
        val gateway = FakeGateway().apply {
            detail = { _, _ -> throw SteamApiException("forbidden", eResult = 15) }
            publicDetail = { throw java.io.IOException() }
        }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.open(item()); advanceUntilIdle()
        assertEquals(WorkshopFailure.UNAVAILABLE, vm.state.value.detailFailure)
        assertEquals("123", vm.state.value.selected?.id)
        assertFalse(vm.state.value.loadingDetail)
    }

    @Test fun closingDetailsCancelsPublicFallback() = runTest(scheduler) {
        val gate = CompletableDeferred<WorkshopItem>()
        val gateway = FakeGateway().apply {
            detail = { _, _ -> throw SteamApiException("expired", httpStatusCode = 401) }
            publicDetail = { gate.await() }
        }
        val vm = SteamWorkshopViewModel(294100, account(), gateway, processingDispatcher = dispatcher)
        vm.open(item()); runCurrent()
        vm.closeDetail(); gate.complete(item().copy(description = "late")); advanceUntilIdle()
        assertNull(vm.state.value.selected)
        assertNull(vm.state.value.detailFailure)
    }

    private class FakeGateway : SteamWorkshopGateway {
        var browse: suspend (SteamAccount?, WorkshopQuery, Int) -> WorkshopBatch = { _, _, page -> WorkshopBatch(emptyList(), page, false, 0) }
        var detail: suspend (SteamAccount, String) -> WorkshopItem = { _, id -> item(id) }
        var publicDetail: suspend (String) -> WorkshopItem = { throw WorkshopException(WorkshopFailure.LOGIN) }
        var mutate: suspend (SteamAccount, WorkshopItem, Boolean, Boolean) -> Unit = { _, _, _, _ -> }
        override suspend fun supportsWorkshop(appId: Int) = true
        override suspend fun browse(account: SteamAccount?, appId: Int, query: WorkshopQuery, page: Int) = browse(account, query, page)
        override suspend fun details(account: SteamAccount, appId: Int, id: String) = detail(account, id)
        override suspend fun publicDetails(appId: Int, id: String) = publicDetail(id)
        override suspend fun setSubscription(account: SteamAccount, item: WorkshopItem, subscribe: Boolean, dependencies: Boolean) = mutate(account, item, subscribe, dependencies)
    }

    companion object {
        private fun item(id: String = "123") = WorkshopItem(id, 294100, "Mod", canSubscribe = true, subscribed = false)
        internal fun account() = SteamAccount(1, "76561198000000001", "test", "Test", "", "", null, null, null,
            "old", "refresh", null, "{}", true, 0, 0, 0)
    }
}
