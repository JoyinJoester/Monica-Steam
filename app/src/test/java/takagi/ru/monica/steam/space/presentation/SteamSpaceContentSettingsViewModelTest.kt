package takagi.ru.monica.steam.space.presentation

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.space.data.SteamSpaceContentSettingsStore
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind

@OptIn(ExperimentalCoroutinesApi::class)
class SteamSpaceContentSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var sequence = 0

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    @Test fun initialLoadingDoesNotPretendPreferencesAreReadyAndIgnoresEarlyChanges() = runTest(dispatcher) {
        val store = FakeStore(setOf(SteamSpaceEventKind.ACHIEVEMENT))
        val vm = model(store)
        assertFalse(vm.state.value.ready)
        vm.showAllKinds()
        advanceUntilIdle()
        assertTrue(vm.state.value.ready)
        assertEquals(store.hidden, vm.state.value.hiddenKinds)
        assertEquals(0, store.writes)
    }

    @Test fun selectedCategoriesRestoreAcrossModelsAndShowAllPersists() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = model(store)
        advanceUntilIdle()
        vm.setKindVisible(SteamSpaceEventKind.ACHIEVEMENT, false)
        assertEquals(setOf(SteamSpaceEventKind.ACHIEVEMENT), vm.state.value.hiddenKinds)
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()
        vm.setKindVisible(SteamSpaceEventKind.VIDEO, false)
        advanceUntilIdle()
        val reopened = model(store)
        advanceUntilIdle()
        assertEquals(setOf(SteamSpaceEventKind.ACHIEVEMENT, SteamSpaceEventKind.VIDEO), reopened.state.value.hiddenKinds)
        reopened.showAllKinds()
        advanceUntilIdle()
        assertTrue(store.hidden.isEmpty())
        assertFalse(reopened.state.value.failed)
    }

    @Test fun saveFailureKeepsSessionChoiceAndCanRetryWithoutChangingIt() = runTest(dispatcher) {
        val store = FakeStore().apply { saveSuccess = false }
        val vm = model(store)
        advanceUntilIdle()
        vm.setKindVisible(SteamSpaceEventKind.ACHIEVEMENT, false)
        advanceUntilIdle()
        assertEquals(setOf(SteamSpaceEventKind.ACHIEVEMENT), vm.state.value.hiddenKinds)
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.saving)
        store.saveSuccess = true
        vm.retrySave()
        advanceUntilIdle()
        assertFalse(vm.state.value.failed)
        assertEquals(vm.state.value.hiddenKinds, store.hidden)
    }

    @Test fun switchesAreSerializedAndUnchangedSuccessfulChoiceDoesNotWrite() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = model(store)
        advanceUntilIdle()
        vm.setKindVisible(SteamSpaceEventKind.ACHIEVEMENT, false)
        vm.showAllKinds() // Controls are disabled while disk save is pending.
        advanceUntilIdle()
        vm.setKindVisible(SteamSpaceEventKind.ACHIEVEMENT, false)
        advanceUntilIdle()
        assertEquals(1, store.writes)
        assertEquals(setOf(SteamSpaceEventKind.ACHIEVEMENT), store.hidden)
    }

    @Test fun readFailureIsReportedAndShowAllCanRecover() = runTest(dispatcher) {
        val store = FakeStore().apply { readFails = true }
        val vm = model(store)
        advanceUntilIdle()
        assertTrue(vm.state.value.ready)
        assertTrue(vm.state.value.failed)
        vm.showAllKinds()
        advanceUntilIdle()
        assertFalse(vm.state.value.failed)
        assertEquals(1, store.writes)
    }

    @Test fun saveExceptionDoesNotLeaveControlsDisabled() = runTest(dispatcher) {
        val store = FakeStore().apply { writeThrows = true }
        val vm = model(store)
        advanceUntilIdle()
        vm.setKindVisible(SteamSpaceEventKind.STATUS, false)
        advanceUntilIdle()
        assertFalse(vm.state.value.saving)
        assertTrue(vm.state.value.failed)
        assertTrue(SteamSpaceEventKind.STATUS in vm.state.value.hiddenKinds)
    }

    private fun model(store: FakeStore) = SteamSpaceContentSettingsViewModel({ store }, dispatcher).also {
        models.put("settings-${sequence++}", it)
    }

    private class FakeStore(var hidden: Set<SteamSpaceEventKind> = emptySet()) : SteamSpaceContentSettingsStore {
        var writes = 0
        var readFails = false
        var writeThrows = false
        var saveSuccess = true
        override fun loadHiddenKinds(): Set<SteamSpaceEventKind> {
            if (readFails) throw IOException("synthetic read failure")
            return hidden
        }
        override fun saveHiddenKinds(kinds: Set<SteamSpaceEventKind>): Boolean {
            writes++
            if (writeThrows) throw IOException("synthetic write failure")
            if (saveSuccess) hidden = kinds
            return saveSuccess
        }
    }
}
