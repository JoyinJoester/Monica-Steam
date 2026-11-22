package takagi.ru.monica.steam.space.presentation

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.space.domain.SteamSpacePostGateway
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SteamSpaceComposeViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val store = ViewModelStore()
    private var counter = 0
    private val account = SteamAccount(7, "76561198000000001", "fixture", "Fixture", "test", "",
        null, null, null, "synthetic-token", null, null, "{}", true, 0, 0, 0)
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() { store.clear(); scheduler.runCurrent(); Dispatchers.resetMain() }
    private fun model(gateway: SteamSpacePostGateway, resolver: SteamAccountSessionResolver? = null,
        timeout: Long = 45_000L) = SteamSpaceComposeViewModel(gateway, resolver, dispatcher, timeout).also {
        store.put("compose-${counter++}", it); it.selectAccount(account)
    }

    @Test fun onlyExplicitSubmissionSendsAndRepeatedClicksWhilePreparingSendExactlyOnce() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val resolver = SteamAccountSessionResolver { selected, force ->
            assertFalse(force); gate.await(); selected.copy(accessToken = "fresh-token")
        }
        val vm = model(SteamSpacePostGateway { selected, text ->
            assertEquals("fresh-token", selected.accessToken); assertEquals(" exact\ntext ", text); calls++
        }, resolver)
        vm.updateText(" exact\ntext ")
        runCurrent(); assertEquals(0, calls)
        vm.publish(); vm.publish(); vm.updateText("cannot change while sending")
        runCurrent()
        assertEquals(SteamSpaceComposePhase.SENDING, vm.state.value.phase)
        assertEquals(" exact\ntext ", vm.state.value.text)
        assertFalse(vm.state.value.canSubmit)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(SteamSpaceComposePhase.SUCCEEDED, vm.state.value.phase)
        vm.publish(); assertEquals(1, calls)
    }

    @Test fun lostResponseKeepsDraftAndBlocksAutomaticOrRepeatedUserSubmission() = runTest(scheduler) {
        var calls = 0
        val vm = model(SteamSpacePostGateway { _, _ -> calls++; throw IOException("response lost") })
        vm.updateText("retain me"); vm.publish(); advanceUntilIdle()
        assertEquals(SteamSpaceComposePhase.UNKNOWN, vm.state.value.phase)
        assertEquals("retain me", vm.state.value.text)
        vm.publish(); vm.updateText("replacement")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("retain me", vm.state.value.text)
    }

    @Test fun preparationFailureIsEditableAndNeverSendsOrSilentlySwitchesAccounts() = runTest(scheduler) {
        var sends = 0
        val vm = model(SteamSpacePostGateway { _, _ -> sends++ }, SteamAccountSessionResolver { selected, _ -> selected.copy(id = 99) })
        vm.updateText("draft"); vm.publish(); advanceUntilIdle()
        assertEquals(0, sends)
        assertTrue(vm.state.value.preparationFailed)
        assertEquals(SteamSpaceComposePhase.EDITING, vm.state.value.phase)
        assertEquals("draft", vm.state.value.text)
    }

    @Test fun leavingOrSwitchingAccountCancelsPendingPreparationAndClearsOnlyItsMemoryDraft() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        var sends = 0
        val vm = model(SteamSpacePostGateway { _, _ -> sends++ }, SteamAccountSessionResolver { selected, _ -> gate.await(); selected })
        vm.updateText("private draft"); vm.publish(); runCurrent()
        vm.selectAccount(account.copy(id = 8, steamId = "76561198000000002"))
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(0, sends)
        assertEquals(8L, vm.state.value.accountId)
        assertEquals("", vm.state.value.text)
        vm.updateText("new"); vm.clear(); assertEquals(SteamSpaceComposeState(), vm.state.value)
    }

    @Test fun backgroundAndTimeoutBeforeDispatchKeepEditableDraftAndDoNotPost() = runTest(scheduler) {
        val gate = CompletableDeferred<Unit>()
        var sends = 0
        val vm = model(SteamSpacePostGateway { _, _ -> sends++ }, SteamAccountSessionResolver { selected, _ -> gate.await(); selected }, 50)
        vm.updateText("draft"); vm.publish(); advanceUntilIdle()
        assertEquals(0, sends)
        assertTrue(vm.state.value.preparationFailed)
        vm.publish(); runCurrent(); vm.stop(); gate.complete(Unit); advanceUntilIdle()
        assertEquals(0, sends)
        assertEquals("draft", vm.state.value.text)
        assertEquals(SteamSpaceComposePhase.EDITING, vm.state.value.phase)
    }
}
