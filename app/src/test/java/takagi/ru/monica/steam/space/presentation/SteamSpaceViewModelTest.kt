package takagi.ru.monica.steam.space.presentation

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.space.domain.SteamSpaceCursor
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.domain.SteamSpaceGateway
import takagi.ru.monica.steam.space.domain.SteamSpacePage
import takagi.ru.monica.steam.space.domain.SteamSpaceCacheKey
import takagi.ru.monica.steam.space.domain.SteamSpaceCachedFeed
import takagi.ru.monica.steam.space.domain.SteamSpaceFeedCache
import takagi.ru.monica.steam.space.domain.SteamSpaceMemoryFeedCache

@OptIn(ExperimentalCoroutinesApi::class)
class SteamSpaceViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val models = mutableListOf<SteamSpaceViewModel>()
    private val modelStore = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() {
        val parentJobs = models.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        try {
            modelStore.clear()
            // A transport's finally/latch precedes runInterruptible's continuation. Keep Main
            // installed until every ViewModel parent and its IO children have actually completed.
            runBlocking {
                withTimeout(5_000L) {
                    while (parentJobs.any { !it.isCompleted }) {
                        scheduler.runCurrent()
                        // Real IO can enqueue its Main continuation after the previous drain.
                        delay(1L)
                    }
                    parentJobs.joinAll()
                }
            }
            assertTrue("all ViewModel jobs finished before resetMain", parentJobs.all { it.isCompleted })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun firstPagePreservesServerOrderAndDeduplicatesStableIds() = runTest(scheduler) {
        val vm = model { account, _, _ ->
            SteamSpacePage(listOf(event("new"), event("old"), event("new")), cursor(account))
        }
        vm.selectAccount(account())
        assertTrue(vm.uiState.value.loading)
        advanceUntilIdle()
        assertEquals(listOf("new", "old"), vm.uiState.value.items.map { it.id })
        assertEquals(account().id, vm.uiState.value.accountId)
        assertEquals(ACCOUNT_A, vm.uiState.value.accountSteamId)
        assertFalse(vm.uiState.value.loading)
        assertTrue(vm.uiState.value.loaded)
        assertNull(vm.uiState.value.failure)
    }

    @Test fun absentOrNonSteamAccountNeverLoads() = runTest(scheduler) {
        var calls = 0
        val vm = model { _, _, _ -> calls++; page("unexpected") }
        vm.selectAccount(null)
        assertEquals(SteamSpaceFailureReason.ACCOUNT_REQUIRED, vm.uiState.value.failure)
        vm.selectAccount(account().copy(steamId = "local-only"))
        advanceUntilIdle()
        assertEquals(0, calls)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(SteamSpaceFailureReason.ACCOUNT_REQUIRED, vm.uiState.value.failure)
    }

    @Test fun switchedAccountRejectsLateNonCancellableSessionResolution() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        val requestedAccounts = mutableListOf<String>()
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { account, _, _ ->
                requestedAccounts += account.steamId
                page(account.steamId)
            },
            sessionResolver = SteamAccountSessionResolver { account, _ ->
                if (account.steamId == ACCOUNT_A) withContext(NonCancellable) { gate.await() }
                account
            },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        runCurrent()
        vm.selectAccount(account(steamId = ACCOUNT_B))
        assertTrue(vm.uiState.value.items.isEmpty())
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(ACCOUNT_B), requestedAccounts)
        assertEquals(listOf(ACCOUNT_B), vm.uiState.value.items.map { it.id })
        assertEquals(ACCOUNT_B, vm.uiState.value.accountSteamId)
    }

    @Test fun leavingRevokesPendingResultAndReentryStartsFresh() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        var resolutions = 0
        var calls = 0
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ -> calls++; page("fresh") },
            sessionResolver = SteamAccountSessionResolver { account, _ ->
                if (++resolutions == 1) withContext(NonCancellable) { gate.await() }
                account
            },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        runCurrent()
        vm.deactivate()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(SteamSpaceUiState(), vm.uiState.value)
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("fresh", vm.uiState.value.items.single().id)
    }

    @Test fun bothAccountIdAndSteamIdIsolateTheFeed() = runTest(scheduler) {
        val vm = model { account, _, _ -> page("${account.id}:${account.steamId}") }
        vm.selectAccount(account())
        advanceUntilIdle()
        vm.selectAccount(account(id = 2L))
        assertTrue(vm.uiState.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals("2:$ACCOUNT_A", vm.uiState.value.items.single().id)
        vm.selectAccount(account(id = 2L, steamId = ACCOUNT_B))
        assertTrue(vm.uiState.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals("2:$ACCOUNT_B", vm.uiState.value.items.single().id)
    }

    @Test fun responseReturningAfterAccountChangeCannotPublishOldCards() = runTest(scheduler) {
        lateinit var vm: SteamSpaceViewModel
        vm = model { selected, _, _ ->
            if (selected.steamId == ACCOUNT_A) {
                // A synchronous transport may finish after the UI has replaced its owner.
                vm.selectAccount(account(steamId = ACCOUNT_B))
                page("late-A")
            } else page("current-B")
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(ACCOUNT_B, vm.uiState.value.accountSteamId)
        assertEquals(listOf("current-B"), vm.uiState.value.items.map { it.id })
        assertNull(vm.uiState.value.failure)
    }

    @Test fun lateAuthenticationFailureAfterLeavingCannotTriggerTokenRefresh() = runTest(scheduler) {
        val forces = mutableListOf<Boolean>()
        lateinit var vm: SteamSpaceViewModel
        vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ ->
                vm.deactivate()
                throw SteamApiException("expired after leaving", httpStatusCode = 401)
            },
            sessionResolver = SteamAccountSessionResolver { account, force -> forces += force; account },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(listOf(false), forces)
        assertEquals(SteamSpaceUiState(), vm.uiState.value)
    }

    @Test fun sameAccountTokenUpdateKeepsFeedAndNextRequestUsesNewToken() = runTest(scheduler) {
        val tokens = mutableListOf<String?>()
        val vm = model { account, _, _ ->
            tokens += account.accessToken
            page("kept", cursor(account))
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        vm.selectAccount(account().copy(accessToken = "rotated"))
        advanceUntilIdle()
        assertEquals(listOf("old-token"), tokens)
        assertEquals("kept", vm.uiState.value.items.single().id)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("old-token", "rotated"), tokens)
    }

    @Test fun refreshFailureRetainsOriginalPageAndMarksItStaleThenRetryReplacesIt() = runTest(scheduler) {
        var attempt = 0
        val vm = model { account, _, _ ->
            when (++attempt) {
                1 -> page("original", cursor(account))
                2 -> throw IOException("offline fixture")
                else -> page("replacement")
            }
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        val originalCursor = vm.uiState.value.nextCursor
        vm.refresh()
        assertTrue(vm.uiState.value.refreshing)
        assertEquals("original", vm.uiState.value.items.single().id)
        advanceUntilIdle()
        assertEquals(SteamSpaceFailureReason.NETWORK, vm.uiState.value.failure)
        assertEquals(originalCursor, vm.uiState.value.nextCursor)
        assertTrue(vm.uiState.value.stale)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("replacement"), vm.uiState.value.items.map { it.id })
        assertNull(vm.uiState.value.failure)
        assertFalse(vm.uiState.value.stale)
    }

    @Test fun failedMoreCanRetrySameCursorWithoutDroppingOrDuplicatingCards() = runTest(scheduler) {
        var moreAttempts = 0
        val vm = model { account, _, cursor ->
            if (cursor == null) page("first", cursor(account)) else {
                if (++moreAttempts == 1) throw IOException("offline fixture")
                SteamSpacePage(listOf(event("first"), event("second")), null)
            }
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        val next = vm.uiState.value.nextCursor
        vm.loadMore()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(1, moreAttempts)
        assertEquals(next, vm.uiState.value.nextCursor)
        assertEquals(listOf("first"), vm.uiState.value.items.map { it.id })
        assertEquals(SteamSpaceFailureReason.NETWORK, vm.uiState.value.loadMoreFailure)
        assertNull(vm.uiState.value.failure)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, moreAttempts)
        assertEquals(listOf("first", "second"), vm.uiState.value.items.map { it.id })
        assertNull(vm.uiState.value.loadMoreFailure)
        assertNull(vm.uiState.value.nextCursor)
    }

    @Test fun refreshSupersedesPendingMoreEvenIfItsResolverIgnoresCancellation() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        var resolutions = 0
        var initialCalls = 0
        var moreCalls = 0
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { account, _, cursor ->
                if (cursor == null) page("fresh-${++initialCalls}", cursor(account))
                else { moreCalls++; page("late-more") }
            },
            sessionResolver = SteamAccountSessionResolver { account, _ ->
                if (++resolutions == 2) withContext(NonCancellable) { gate.await() }
                account
            },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        vm.loadMore()
        runCurrent()
        vm.refresh()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, moreCalls)
        assertEquals(listOf("fresh-2"), vm.uiState.value.items.map { it.id })
        assertFalse(vm.uiState.value.loadingMore)
        assertFalse(vm.uiState.value.refreshing)
    }

    @Test fun duplicateForeignAndNonDecreasingCursorsTerminateAsPartial() = runTest(scheduler) {
        val badCursors = listOf(
            cursor(account()),
            cursor(account(), 101L),
            cursor(account(id = 2)),
            cursor(account(steamId = ACCOUNT_B)),
            cursor(account(), 0L)
        )
        badCursors.forEach { bad ->
            var calls = 0
            val vm = model { account, _, cursor ->
                calls++
                if (cursor == null) page("first", cursor(account)) else page("second", bad)
            }
            vm.selectAccount(account())
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertNull(vm.uiState.value.nextCursor)
            assertTrue(vm.uiState.value.partial)
            assertEquals(listOf("first", "second"), vm.uiState.value.items.map { it.id })
            vm.deactivate()
        }
    }

    @Test fun pageLimitStopsEvenIfServerOnlyRepeatsAnEventWithNewCursors() = runTest(scheduler) {
        var calls = 0
        val vm = model { account, _, cursor ->
            calls++
            page("same", cursor(account, (cursor?.beforeEpochSeconds ?: 1_000L) - 1))
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        repeat(SteamSpaceViewModel.MAX_PAGES + 2) {
            vm.loadMore()
            advanceUntilIdle()
        }
        assertEquals(SteamSpaceViewModel.MAX_PAGES, calls)
        assertEquals(1, vm.uiState.value.items.size)
        assertNull(vm.uiState.value.nextCursor)
        assertTrue(vm.uiState.value.partial)
    }

    @Test fun itemLimitDoesNotPretendTruncatedFeedIsComplete() = runTest(scheduler) {
        val vm = model { _, _, _ ->
            SteamSpacePage((1..450).map { event(it.toString()) }, null)
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(SteamSpaceViewModel.MAX_ITEMS, vm.uiState.value.items.size)
        assertTrue(vm.uiState.value.partial)
        assertNull(vm.uiState.value.nextCursor)
    }

    @Test fun authenticationFailureUsesSharedResolverOnceBeforeRetry() = runTest(scheduler) {
        val forces = mutableListOf<Boolean>()
        val tokens = mutableListOf<String?>()
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { account, _, _ ->
                tokens += account.accessToken
                if (account.accessToken != "fresh-token") throw SteamApiException("expired", eResult = 27)
                page("resolved")
            },
            sessionResolver = SteamAccountSessionResolver { account, force ->
                forces += force
                if (force) account.copy(accessToken = "fresh-token") else account
            },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(listOf(false, true), forces)
        assertEquals(listOf("old-token", "fresh-token"), tokens)
        assertEquals("resolved", vm.uiState.value.items.single().id)
        assertNull(vm.uiState.value.failure)
    }

    @Test fun sessionResolutionAuthenticationErrorAlsoGetsOneForceRefresh() = runTest(scheduler) {
        val forces = mutableListOf<Boolean>()
        var gatewayCalls = 0
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { account, _, _ ->
                gatewayCalls++
                assertEquals("fresh-token", account.accessToken)
                page("resolved")
            },
            sessionResolver = SteamAccountSessionResolver { account, force ->
                forces += force
                if (!force) throw SteamApiException("expired resolution", httpStatusCode = 401)
                account.copy(accessToken = "fresh-token")
            },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(listOf(false, true), forces)
        assertEquals(1, gatewayCalls)
        assertNull(vm.uiState.value.failure)
    }

    @Test fun pageTimeoutInterruptsBlockingTransportAndClearsLoading() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ ->
                entered.countDown()
                try {
                    Thread.sleep(30_000L)
                    page("unexpected")
                } catch (error: InterruptedException) {
                    interrupted.set(true)
                    throw error
                } finally {
                    stopped.countDown()
                }
            },
            requestTimeoutMillis = 1_000L
        )
        try {
            vm.selectAccount(account())
            assertTrue("transport started", entered.await(3L, TimeUnit.SECONDS))
            val failed = withTimeout(5_000L) { vm.uiState.first { !it.loading } }
            assertTrue(stopped.await(3L, TimeUnit.SECONDS))
            assertTrue(interrupted.get())
            assertEquals(SteamSpaceFailureReason.NETWORK, failed.failure)
            assertFalse(failed.refreshing)
            assertFalse(failed.loaded)
            assertTrue(failed.items.isEmpty())
        } finally {
            vm.deactivate()
        }
    }

    @Test fun leavingInterruptsBlockingTransportWithoutPublishingAnError() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ ->
                entered.countDown()
                try {
                    Thread.sleep(30_000L)
                    page("unexpected")
                } finally {
                    stopped.countDown()
                }
            }
        )
        try {
            vm.selectAccount(account())
            assertTrue("transport started", entered.await(3L, TimeUnit.SECONDS))
            vm.deactivate()
            assertTrue("transport interrupted", stopped.await(3L, TimeUnit.SECONDS))
            assertEquals(SteamSpaceUiState(), vm.uiState.value)
        } finally {
            vm.deactivate()
        }
    }

    @Test fun inlineCacheMissKeepsTheNetworkJobCancellableWhenLeaving() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val dispatches = AtomicInteger()
        val inlineCacheThenIo = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                // Complete the cache dispatch before withContext can suspend, as a fast IO
                // read can do in production. The following transport really runs on IO.
                if (dispatches.getAndIncrement() == 0) block.run()
                else Dispatchers.IO.dispatch(context, block)
            }
        }
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ ->
                entered.countDown()
                try {
                    check(release.await(30L, TimeUnit.SECONDS))
                    page("unexpected")
                } catch (error: InterruptedException) {
                    interrupted.set(true)
                    throw error
                } finally {
                    stopped.countDown()
                }
            },
            ioDispatcher = inlineCacheThenIo
        )
        try {
            vm.selectAccount(account())
            assertTrue("transport started after the inline cache miss", entered.await(3L, TimeUnit.SECONDS))
            vm.deactivate()
            assertTrue("leaving interrupts the retained network job", stopped.await(3L, TimeUnit.SECONDS))
            assertTrue("transport was interrupted rather than released", interrupted.get())
            assertEquals(SteamSpaceUiState(), vm.uiState.value)
        } finally {
            // Let the old implementation finish after the expected cancellation assertion fails.
            release.countDown()
            vm.deactivate()
        }
    }

    @Test fun allAuthenticationSignalsRetryOnlyOnceAndRemainDistinctFromEmptyFeed() = runTest(scheduler) {
        val errors = listOf(
            SteamApiException("fixture", httpStatusCode = 401),
            SteamApiException("fixture", httpStatusCode = 403),
            SteamApiException("fixture", authenticationRequired = true),
            SteamApiException("fixture", eResult = 21),
            SteamApiException("fixture", eResult = 15)
        )
        errors.forEach { error ->
            var calls = 0
            val forces = mutableListOf<Boolean>()
            val vm = trackedViewModel(
                gateway = SteamSpaceGateway { _, _, _ -> calls++; throw error },
                sessionResolver = SteamAccountSessionResolver { account, force -> forces += force; account },
                ioDispatcher = dispatcher
            )
            vm.selectAccount(account())
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(listOf(false, true), forces)
            assertEquals(SteamSpaceFailureReason.SESSION_REQUIRED, vm.uiState.value.failure)
            assertFalse(vm.uiState.value.loaded)
            assertFalse(vm.uiState.value.loading)
            vm.deactivate()
        }
    }

    @Test fun ordinaryFailuresAndCancellationDoNotForceSessionRefresh() = runTest(scheduler) {
        val forces = mutableListOf<Boolean>()
        var calls = 0
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ -> calls++; throw CancellationException("fixture") },
            sessionResolver = SteamAccountSessionResolver { account, force -> forces += force; account },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf(false), forces)
        assertNull(vm.uiState.value.failure)
        vm.deactivate()
        val failed = model { _, _, _ -> throw IllegalStateException("bad response") }
        failed.selectAccount(account())
        advanceUntilIdle()
        assertEquals(SteamSpaceFailureReason.UNAVAILABLE, failed.uiState.value.failure)
    }

    @Test fun resolverCannotSubstituteAnotherAccount() = runTest(scheduler) {
        var calls = 0
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ -> calls++; page("unexpected") },
            sessionResolver = SteamAccountSessionResolver { account, _ -> account.copy(steamId = ACCOUNT_B) },
            ioDispatcher = dispatcher
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(ACCOUNT_A, vm.uiState.value.accountSteamId)
        assertEquals(SteamSpaceFailureReason.UNAVAILABLE, vm.uiState.value.failure)
    }

    @Test fun paginationKeepsItsOriginalLanguageUntilRefresh() = runTest(scheduler) {
        var language = "japanese"
        val requested = mutableListOf<String>()
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { account, lang, cursor ->
                requested += lang
                page("${requested.size}", if (cursor == null) cursor(account) else null)
            },
            ioDispatcher = dispatcher,
            languageProvider = { language }
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        language = "russian"
        vm.loadMore()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("japanese", "japanese", "russian"), requested)
    }

    @Test fun localeMappingUsesSupportedSteamLanguageNames() {
        assertEquals("schinese", steamSpaceLanguage(Locale.forLanguageTag("zh-Hans-SG")))
        assertEquals("tchinese", steamSpaceLanguage(Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals("tchinese", steamSpaceLanguage(Locale.TAIWAN))
        assertEquals("japanese", steamSpaceLanguage(Locale.JAPAN))
        assertEquals("russian", steamSpaceLanguage(Locale.forLanguageTag("ru-RU")))
        assertEquals("vietnamese", steamSpaceLanguage(Locale.forLanguageTag("vi-VN")))
        assertEquals("english", steamSpaceLanguage(Locale.forLanguageTag("fr-FR")))
    }

    @Test fun reentryImmediatelyRestoresCompletedPagesAndDoesNotRequestAgain() = runTest(scheduler) {
        var calls = 0
        val vm = model { account, _, cursor ->
            calls++
            page("page-$calls", if (cursor == null) cursor(account, 100) else cursor(account, 90))
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        val saved = vm.uiState.value
        vm.deactivate()
        assertEquals(SteamSpaceUiState(), vm.uiState.value)
        vm.selectAccount(account())
        assertEquals(saved, vm.uiState.value)
        assertFalse(vm.uiState.value.loading)
        advanceUntilIdle()
        assertEquals(2, calls)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(3, calls)
        // The restored visited-cursor set still rejects this repeated cursor.
        assertNull(vm.uiState.value.nextCursor)
        assertTrue(vm.uiState.value.partial)
        assertEquals(listOf("page-1", "page-2", "page-3"), vm.uiState.value.items.map { it.id })
    }

    @Test fun freshFeedWithUnresolvedAppNamesRevalidatesOnReentryWithoutClearingIt() = runTest(scheduler) {
        var calls = 0
        val event = SteamSpaceEvent("game", SteamSpaceEventKind.FIRST_PLAY, ACCOUNT_A, 1800000000,
            appIds = listOf(1091500), sourceUrl = "https://steamcommunity.com/profiles/$ACCOUNT_A/")
        val vm = model { _, _, _ ->
            calls++
            SteamSpacePage(listOf(if (calls == 1) event else event.copy(appNames = mapOf(1091500 to "Cyberpunk 2077"))), null)
        }
        vm.selectAccount(account())
        advanceUntilIdle()
        vm.deactivate()
        vm.selectAccount(account())
        assertEquals(listOf(event), vm.uiState.value.items)
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("Cyberpunk 2077", vm.uiState.value.items.single().appNames[1091500])
        vm.deactivate()
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun expiredFeedRemainsVisibleWhileRefreshingAndAfterNetworkFailure() = runTest(scheduler) {
        var now = System.currentTimeMillis()
        var calls = 0
        val vm = trackedViewModel(
            gateway = SteamSpaceGateway { _, _, _ ->
                if (++calls == 1) page("cached") else throw IOException("offline fixture")
            }, ioDispatcher = dispatcher, nowMillis = { now },
            cache = SteamSpaceMemoryFeedCache(clockMillis = { now })
        )
        vm.selectAccount(account())
        advanceUntilIdle()
        vm.deactivate()
        now += SteamSpaceViewModel.CACHE_FRESH_MILLIS
        vm.selectAccount(account())
        assertEquals(listOf("cached"), vm.uiState.value.items.map { it.id })
        assertTrue(vm.uiState.value.refreshing)
        assertFalse(vm.uiState.value.loading)
        assertTrue(vm.uiState.value.stale)
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(listOf("cached"), vm.uiState.value.items.map { it.id })
        assertEquals(SteamSpaceFailureReason.NETWORK, vm.uiState.value.failure)
    }

    @Test fun successfulEmptyPageIsCachedButManualRefreshStillFetches() = runTest(scheduler) {
        var calls = 0
        val vm = model { _, _, _ -> calls++; SteamSpacePage(emptyList(), null) }
        vm.selectAccount(account())
        advanceUntilIdle()
        vm.deactivate()
        vm.selectAccount(account())
        assertTrue(vm.uiState.value.loaded)
        assertFalse(vm.uiState.value.loading)
        advanceUntilIdle()
        assertEquals(1, calls)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun cacheSeparatesAccountIdentityAndLanguageAndReturnsToPreviousFeed() = runTest(scheduler) {
        var language = "english"
        val requests = mutableListOf<String>()
        val vm = trackedViewModel(gateway = SteamSpaceGateway { account, lang, _ ->
            val identity = "${account.id}:${account.steamId}:${account.createdAt}:$lang"
            requests += identity
            page(identity)
        }, ioDispatcher = dispatcher, languageProvider = { language })
        val a = account()
        val b = account(id = 2)
        vm.selectAccount(a)
        advanceUntilIdle()
        val original = vm.uiState.value.items
        vm.selectAccount(b)
        assertTrue(vm.uiState.value.items.isEmpty())
        advanceUntilIdle()
        vm.selectAccount(a)
        assertEquals(original, vm.uiState.value.items)
        assertEquals(2, requests.size)
        language = "japanese"
        vm.selectAccount(a)
        assertTrue(vm.uiState.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals(3, requests.size)
        language = "english"
        vm.selectAccount(a)
        assertEquals(original, vm.uiState.value.items)
        vm.selectAccount(a.copy(createdAt = 123))
        assertTrue(vm.uiState.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals(4, requests.size)
    }

    @Test fun mdbxReloadUsesStableSourceIdentityDespiteProjectedCreationTime() = runTest(scheduler) {
        var calls = 0
        val vm = model { _, _, _ -> calls++; page("mdbx-cached") }
        val external = account(id = -701).copy(createdAt = 111)
        vm.selectAccount(external)
        advanceUntilIdle()
        vm.deactivate()
        vm.selectAccount(external.copy(createdAt = 222, updatedAt = 222))
        assertTrue(vm.uiState.value.loaded)
        assertEquals("mdbx-cached", vm.uiState.value.items.single().id)
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun cacheReadReturningAfterAccountChangeCannotPublishOrStartOldRequest() = runTest(scheduler) {
        val calls = mutableListOf<String>()
        lateinit var vm: SteamSpaceViewModel
        val cache = object : SteamSpaceFeedCache {
            override fun peek(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? = null
            override fun load(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? {
                if (key.accountSteamId != ACCOUNT_A) return null
                vm.selectAccount(account(steamId = ACCOUNT_B))
                return SteamSpaceCachedFeed(key, listOf(event("late-cache")), null, false, 1, emptySet(), System.currentTimeMillis())
            }
            override fun save(feed: SteamSpaceCachedFeed) = Unit
        }
        vm = trackedViewModel(gateway = SteamSpaceGateway { account, _, _ ->
            calls += account.steamId; page("current")
        }, ioDispatcher = dispatcher, cache = cache)
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(listOf(ACCOUNT_B), calls)
        assertEquals(listOf("current"), vm.uiState.value.items.map { it.id })
        assertEquals(ACCOUNT_B, vm.uiState.value.accountSteamId)
    }

    @Test fun diskCacheFailureDoesNotDiscardSuccessfulNetworkPage() = runTest(scheduler) {
        val cache = object : SteamSpaceFeedCache {
            override fun peek(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? = null
            override fun load(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? = throw IOException("cache read")
            override fun save(feed: SteamSpaceCachedFeed) { throw IOException("cache write") }
        }
        val vm = trackedViewModel(gateway = SteamSpaceGateway { _, _, _ -> page("server") },
            ioDispatcher = dispatcher, cache = cache)
        vm.selectAccount(account())
        advanceUntilIdle()
        assertEquals(listOf("server"), vm.uiState.value.items.map { it.id })
        assertTrue(vm.uiState.value.loaded)
        assertNull(vm.uiState.value.failure)
    }

    @Test fun successfulFeedIsVisibleAndReusableBeforeSlowDiskWriteFinishes() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val memory = SteamSpaceMemoryFeedCache()
        val cache = object : SteamSpaceFeedCache by memory {
            override fun save(feed: SteamSpaceCachedFeed) {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            }
        }
        var calls = 0
        val vm = trackedViewModel(gateway = SteamSpaceGateway { _, _, _ -> calls++; page("immediate") }, cache = cache)
        try {
            vm.selectAccount(account())
            withTimeout(3_000L) { vm.uiState.first { it.loaded } }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertEquals("immediate", vm.uiState.value.items.single().id)
            vm.deactivate()
            assertEquals(SteamSpaceUiState(), vm.uiState.value)
            vm.selectAccount(account())
            assertTrue(vm.uiState.value.loaded)
            assertFalse(vm.uiState.value.loading)
            assertEquals(1, calls)
        } finally {
            release.countDown()
            vm.deactivate()
        }
    }

    @Test fun interruptibleSlowCacheReadFallsBackToNetwork() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val interrupted = AtomicBoolean(false)
        val cache = object : SteamSpaceFeedCache {
            override fun peek(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? = null
            override fun load(key: SteamSpaceCacheKey): SteamSpaceCachedFeed? {
                try { Thread.sleep(30_000L) } finally { interrupted.set(true) }
                return null
            }
            override fun save(feed: SteamSpaceCachedFeed) = Unit
        }
        val vm = trackedViewModel(gateway = SteamSpaceGateway { _, _, _ -> page("network") }, cache = cache)
        try {
            vm.selectAccount(account())
            val loaded = withTimeout(4_000L) { vm.uiState.first { it.loaded } }
            assertTrue(interrupted.get())
            assertEquals("network", loaded.items.single().id)
            assertNull(loaded.failure)
        } finally {
            vm.deactivate()
        }
    }

    private fun model(block: (SteamAccount, String, SteamSpaceCursor?) -> SteamSpacePage) =
        trackedViewModel(SteamSpaceGateway(block), ioDispatcher = dispatcher)

    @Test fun cachedLibraryNamesRepairCachedAchievementFeedEvenWhenNewsNetworkFails() = runTest(scheduler) {
        val selected = account()
        val key = SteamSpaceCacheKey(selected.id, selected.steamId, "english", false, selected.createdAt)
        val missing = event("achievement").copy(kind = SteamSpaceEventKind.ACHIEVEMENT, appIds = listOf(815370), partial = true)
        val cache = SteamSpaceMemoryFeedCache(clockMillis = { 1_001L }).apply {
            remember(SteamSpaceCachedFeed(key, listOf(missing), null, true, 1, emptySet(), 1_000L))
        }
        val vm = SteamSpaceViewModel(SteamSpaceGateway { _, _, _ -> throw IOException("offline") },
            ioDispatcher = dispatcher, languageProvider = { "english" }, cache = cache, nowMillis = { 1_001L },
            libraryNamesLookup = { owner -> assertEquals(selected.id, owner.id); mapOf(815370 to "绿色地狱") })
        modelStore.put("library-name-offline", vm); models += vm
        vm.selectAccount(selected); advanceUntilIdle()
        assertEquals("绿色地狱", vm.uiState.value.items.single().appNames[815370])
        assertEquals(SteamSpaceFailureReason.NETWORK, vm.uiState.value.failure)
        assertTrue(vm.uiState.value.loaded)
    }

    @Test fun lateLibraryNameLookupNeverLabelsAnotherAccountFeed() = runTest(scheduler) {
        val first = account()
        val second = account(2, ACCOUNT_B)
        val gate = CompletableDeferred<Unit>()
        val vm = SteamSpaceViewModel(SteamSpaceGateway { _, _, _ -> SteamSpacePage(listOf(event("a").copy(
            kind = SteamSpaceEventKind.ACHIEVEMENT, appIds = listOf(815370))), null) },
            ioDispatcher = dispatcher, libraryNamesLookup = { owner ->
                if (owner.id == first.id) { gate.await(); mapOf(815370 to "Wrong account label") } else emptyMap()
            })
        modelStore.put("library-name-account", vm); models += vm
        vm.selectAccount(first); runCurrent(); vm.selectAccount(second); gate.complete(Unit); advanceUntilIdle()
        assertEquals(second.id, vm.uiState.value.accountId)
        assertTrue(vm.uiState.value.items.single().appNames.isEmpty())
    }

    private fun trackedViewModel(
        gateway: SteamSpaceGateway,
        sessionResolver: SteamAccountSessionResolver? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        languageProvider: () -> String = { steamSpaceLanguage(Locale.getDefault()) },
        requestTimeoutMillis: Long = 45_000L,
        cache: SteamSpaceFeedCache = SteamSpaceMemoryFeedCache(),
        nowMillis: () -> Long = System::currentTimeMillis
    ): SteamSpaceViewModel = SteamSpaceViewModel(
        gateway, sessionResolver, ioDispatcher, languageProvider, requestTimeoutMillis, cache, nowMillis
    ).also {
        modelStore.put("space-${models.size}", it)
        models += it
    }

    private fun page(id: String, next: SteamSpaceCursor? = null) = SteamSpacePage(listOf(event(id)), next)
    private fun cursor(account: SteamAccount, before: Long = 100L) = SteamSpaceCursor(account.id, account.steamId, before)
    private fun event(id: String) = SteamSpaceEvent(
        id = id,
        kind = SteamSpaceEventKind.STATUS,
        actorSteamId = ACCOUNT_B,
        occurredAtEpochSeconds = 101L,
        body = "Synthetic fixture",
        sourceUrl = "https://steamcommunity.com/my/home/"
    )

    private fun account(id: Long = 1L, steamId: String = ACCOUNT_A) = SteamAccount(
        id = id, steamId = steamId, accountName = "fixture", displayName = "Fixture",
        deviceId = "android:fixture", sharedSecret = "fixture", identitySecret = null,
        revocationCode = null, tokenGid = null, accessToken = "old-token", refreshToken = "refresh-token",
        steamLoginSecure = null, rawSteamGuardJson = "{}", selected = true, sortOrder = 0,
        createdAt = 0L, updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_A = "76561198000000001"
        const val ACCOUNT_B = "76561198000000009"
    }
}
