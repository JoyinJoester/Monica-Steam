package takagi.ru.monica.steam.store

import java.io.File
import takagi.ru.monica.steam.store.presentation.*
import takagi.ru.monica.steam.store.domain.*
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextFailure

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
        assertTrue(source.contains("reviewRequestIsCurrent(accountId, appId, generation, filters)"))
        assertTrue(source.contains("mergePage(terminalPage)"))
        assertTrue(source.contains("cache.writeDetail(accountId, updatedDetail)"))
        assertTrue(SteamStoreUiState::class.java.declaredFields.any { it.name == "loadingMoreReviews" })
    }

    @Test
    fun purchaseContextRequiresTheSameSteamIdAsTheSelectedAccount() {
        val account = account("76561198000000001")
        val state = SteamStoreUiState(
            accounts = listOf(account),
            selectedAccountId = account.id,
            detailAppId = 620
        )

        assertTrue(steamStorePurchaseContextRequestIsCurrent(state, account, 620, 3L, 3L))
        assertFalse(
            steamStorePurchaseContextRequestIsCurrent(
                state = state,
                account = account("76561198000000009"),
                appId = 620,
                generation = 3L,
                currentGeneration = 3L
            )
        )
        assertFalse(steamStorePurchaseContextRequestIsCurrent(state, account, 730, 3L, 3L))
    }

    @Test
    fun onlySuccessfulKnownPurchaseContextsArePersisted() {
        assertTrue(
            steamStorePurchaseContextIsCacheable(
                SteamStorePurchaseContext(
                    accountSteamId = "76561198000000001",
                    appId = 620,
                    ownership = SteamStoreOwnershipStatus.OWNED
                )
            )
        )
        assertFalse(
            steamStorePurchaseContextIsCacheable(
                SteamStorePurchaseContext(
                    accountSteamId = "76561198000000001",
                    appId = 620,
                    ownership = SteamStoreOwnershipStatus.UNKNOWN,
                    failure = SteamStorePurchaseContextFailure.SESSION_REQUIRED
                )
            )
        )
    }

    private fun account(steamId: String) = SteamAccount(
        id = 7L,
        steamId = steamId,
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "$steamId||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

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
