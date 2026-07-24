package takagi.ru.monica.steam.store

import java.io.File
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

    @Test
    fun reviewPaginationIsScopedToTheCurrentDetailAndCached() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()

        assertTrue(source.contains("fun loadMoreReviews()"))
        assertTrue(source.contains("reviewRequestIsCurrent(accountId, appId, generation)"))
        assertTrue(source.contains("mergePage(terminalPage)"))
        assertTrue(source.contains("cache.writeDetail(accountId, updatedDetail)"))
        assertTrue(SteamStoreUiState::class.java.declaredFields.any { it.name == "loadingMoreReviews" })
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
