package takagi.ru.monica.steam.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamStorageSource

class SteamAccountRequestGuardTest {
    @Test
    fun accountScopedResultRequiresMatchingAccountAndGeneration() {
        val state = SteamUiState(selectedAccountId = 7L)

        assertTrue(steamAccountRequestIsCurrent(state, 7L, 4L, 4L))
        assertFalse(steamAccountRequestIsCurrent(state, 8L, 4L, 4L))
        assertFalse(steamAccountRequestIsCurrent(state, 7L, 3L, 4L))
        assertFalse(steamAccountRequestIsCurrent(state.copy(selectedAccountId = null), 7L, 4L, 4L))
    }

    @Test
    fun mdbxLoadResultRequiresCurrentSourceAndGeneration() {
        val source = SteamStorageSource.Mdbx(databaseId = 11L)
        val state = SteamUiState(storageSource = source)

        assertTrue(steamStorageSourceRequestIsCurrent(state, source, 2L, 2L))
        assertFalse(steamStorageSourceRequestIsCurrent(state, source, 1L, 2L))
        assertFalse(
            steamStorageSourceRequestIsCurrent(
                state.copy(storageSource = SteamStorageSource.Local),
                source,
                2L,
                2L
            )
        )
    }
}
