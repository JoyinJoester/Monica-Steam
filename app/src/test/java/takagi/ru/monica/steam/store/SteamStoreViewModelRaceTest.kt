package takagi.ru.monica.steam.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreViewModelRaceTest {
    @Test
    fun staleDetailResponsesCannotReplaceClosedOrDifferentDetail() {
        val state = SteamStoreUiState(
            selectedAccountId = 7L,
            detail = SteamStoreDetail(appId = 570, name = "Dota 2")
        )

        assertTrue(steamStoreDetailRequestIsCurrent(state, 7L, 570, 3L, 3L))
        assertFalse(steamStoreDetailRequestIsCurrent(state, 7L, 730, 3L, 3L))
        assertFalse(steamStoreDetailRequestIsCurrent(state, 7L, 570, 2L, 3L))
        assertFalse(
            steamStoreDetailRequestIsCurrent(
                state.copy(detail = null),
                7L,
                570,
                3L,
                3L
            )
        )
    }
}
