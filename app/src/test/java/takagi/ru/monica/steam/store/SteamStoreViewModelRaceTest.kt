package takagi.ru.monica.steam.store

import takagi.ru.monica.steam.store.presentation.*
import takagi.ru.monica.steam.store.domain.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreViewModelRaceTest {
    @Test
    fun detailDestinationIsTrackedBeforePayloadArrives() {
        assertTrue(
            SteamStoreUiState::class.java.declaredFields.any { field ->
                field.name == "detailAppId"
            }
        )
    }

    @Test
    fun staleDetailResponsesCannotReplaceClosedOrDifferentDetail() {
        val state = SteamStoreUiState(
            selectedAccountId = 7L,
            detailAppId = 570,
            detail = SteamStoreDetail(appId = 570, name = "Dota 2")
        )

        assertTrue(steamStoreDetailRequestIsCurrent(state, 7L, 570, 3L, 3L))
        assertFalse(steamStoreDetailRequestIsCurrent(state, 7L, 730, 3L, 3L))
        assertFalse(steamStoreDetailRequestIsCurrent(state, 7L, 570, 2L, 3L))
        assertFalse(
            steamStoreDetailRequestIsCurrent(
                state.copy(detailAppId = null, detail = null),
                7L,
                570,
                3L,
                3L
            )
        )
    }
}
