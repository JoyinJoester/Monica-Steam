package takagi.ru.monica.steam.workshop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.domain.*

/** The real session manager sits between Workshop and an account re-login. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopReloginRecoveryTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun reloginReplacesPreviouslyRefreshedCredentialsWhenOpeningWallpaperDetails() = runTest(scheduler) {
        val original = SteamWorkshopViewModelTest.account()
        val origin = SteamAccountSessionOrigin(SteamStorageSource.Local)
        var stored = original
        val persisted = mutableListOf<SteamAccountSessionHandle>()
        val manager = SteamAccountSessionManager(
            refresher = object : SteamAccountSessionRefresher {
                override fun shouldRefresh(account: SteamAccount, nowSeconds: Long) = account.accessToken == "old"
                override suspend fun refresh(account: SteamAccount, force: Boolean) =
                    if (account.accessToken == "old") SteamSessionTokens("previous-session", "previous-refresh") else null
            },
            store = object : SteamAccountSessionStore {
                override suspend fun persist(handle: SteamAccountSessionHandle) { persisted += handle; stored = handle.account }
                override suspend fun load(handle: SteamAccountSessionHandle) = stored
            },
            ioDispatcher = dispatcher
        )
        manager.resolve(SteamAccountSessionHandle(original, origin))
        val attemptedTokens = mutableListOf<String?>()
        val gateway = object : SteamWorkshopGateway {
            override suspend fun supportsWorkshop(appId: Int) = true
            override suspend fun browse(account: SteamAccount?, appId: Int, query: WorkshopQuery, page: Int) =
                WorkshopBatch(emptyList(), page, false, 0)
            override suspend fun details(account: SteamAccount, appId: Int, id: String): WorkshopItem {
                attemptedTokens += account.accessToken
                if (account.accessToken != "new-login") throw SteamApiException("expired", httpStatusCode = 401)
                return WorkshopItem(id, appId, "Wallpaper", subscribed = false)
            }
            override suspend fun setSubscription(account: SteamAccount, item: WorkshopItem, subscribe: Boolean, dependencies: Boolean) = Unit
        }
        val vm = SteamWorkshopViewModel(431960, original, gateway,
            SteamAccountSessionResolver { account, force -> manager.resolve(SteamAccountSessionHandle(account, origin), force).account },
            processingDispatcher = dispatcher)
        vm.open(WorkshopItem("123", 431960, "Wallpaper"))
        advanceUntilIdle()
        assertEquals(WorkshopFailure.LOGIN, vm.state.value.detailFailure)

        // Account screen has persisted a successful new login under the same identity.
        val relogged = original.copy(accessToken = "new-login", refreshToken = "new-refresh", updatedAt = 100)
        stored = relogged
        vm.updateAccount(relogged)
        advanceUntilIdle()

        assertNull("A successful re-login must clear the Workshop login error", vm.state.value.detailFailure)
        assertEquals("new-login", attemptedTokens.last())
        assertEquals(false, vm.state.value.selected?.subscribed)
    }
}
