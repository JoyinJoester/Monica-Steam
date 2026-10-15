package takagi.ru.monica.steam.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.domain.*
import takagi.ru.monica.steam.workshop.SteamWorkshopViewModelTest

@OptIn(ExperimentalCoroutinesApi::class)
class SteamSessionReloginTest {
    @Test fun staleHandlesReadTheCurrentSessionFromTheirOriginalSource() = runTest {
        val first = handle(SteamStorageSource.Local)
        val second = handle(SteamStorageSource.Mdbx(42), "entry")
        val store = Store(first, second)
        val fresh = first.account.copy(accessToken = "login-local", refreshToken = "login-refresh")
        store.accounts[first.stableKey] = fresh
        val manager = SteamAccountSessionManager(Refresher(), store, StandardTestDispatcher(testScheduler))

        assertEquals("login-local", manager.resolve(first).account.accessToken)
        assertEquals("old", manager.resolve(second).account.accessToken)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun reloginDoesNotWaitForOrGetOverwrittenByAnOldRefresh() = runTest {
        val handle = handle()
        val store = Store(handle)
        val gate = CompletableDeferred<SteamSessionTokens?>()
        val refresher = object : Refresher() {
            override suspend fun refresh(account: SteamAccount, force: Boolean): SteamSessionTokens? = gate.await()
        }
        val manager = SteamAccountSessionManager(refresher, store, StandardTestDispatcher(testScheduler))
        val old = async { manager.resolve(handle, true) }
        runCurrent()
        store.accounts[handle.stableKey] = handle.account.copy(accessToken = "new-login", refreshToken = "new-refresh")

        assertEquals("new-login", manager.resolve(handle).account.accessToken)
        assertFalse(old.isCompleted)
        gate.complete(SteamSessionTokens("late-old-rotation", "late-refresh"))
        assertEquals("new-login", old.await().account.accessToken)
        assertEquals("new-login", store.accounts.getValue(handle.stableKey).accessToken)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun aNewLoginWithAnExpiringTokenGetsItsOwnRefresh() = runTest {
        val handle = handle()
        val store = Store(handle)
        val gate = CompletableDeferred<SteamSessionTokens?>()
        val calls = mutableListOf<String?>()
        val refresher = object : Refresher() {
            override fun shouldRefresh(account: SteamAccount, nowSeconds: Long) = account.accessToken != "fresh-login"
            override suspend fun refresh(account: SteamAccount, force: Boolean): SteamSessionTokens? {
                calls += account.refreshToken
                return if (account.accessToken == "old") gate.await() else SteamSessionTokens("fresh-login", "fresh-refresh")
            }
        }
        val manager = SteamAccountSessionManager(refresher, store, StandardTestDispatcher(testScheduler))
        val old = async { manager.resolve(handle) }
        runCurrent()
        store.accounts[handle.stableKey] = handle.account.copy(accessToken = "new-login", refreshToken = "new-refresh")
        assertEquals("fresh-login", manager.resolve(handle).account.accessToken)
        assertEquals(listOf("refresh", "new-refresh"), calls)
        gate.complete(SteamSessionTokens("late-old", null))
        assertEquals("fresh-login", old.await().account.accessToken)
        assertEquals(listOf("fresh-login"), store.writes.map { it.account.accessToken })
    }

    @Test fun unavailableOrReboundStorageNeverFallsBackToCachedCredentials() = runTest {
        for (rebound in listOf(false, true)) {
            val handle = handle()
            val store = Store(handle)
            val manager = SteamAccountSessionManager(Refresher(), store, StandardTestDispatcher(testScheduler))
            manager.resolve(handle)
            if (rebound) store.accounts[handle.stableKey] = handle.account.copy(steamId = "76561198000000002")
            else store.accounts.clear()
            val result = runCatching { manager.resolve(handle) }
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertTrue(store.writes.isEmpty())
        }
    }

    @Test fun rejectionOfAnOldRefreshDoesNotInvalidateTheNewLogin() = runTest {
        val handle = handle()
        val store = Store(handle)
        val gate = CompletableDeferred<Unit>()
        val manager = SteamAccountSessionManager(object : Refresher() {
            override suspend fun refresh(account: SteamAccount, force: Boolean): SteamSessionTokens? {
                gate.await()
                throw java.io.IOException("Old refresh was revoked")
            }
        }, store, StandardTestDispatcher(testScheduler))
        val pending = async { manager.resolve(handle, true) }
        runCurrent()
        store.accounts[handle.stableKey] = handle.account.copy(accessToken = "new-login", refreshToken = "new-refresh")
        gate.complete(Unit)
        assertEquals("new-login", pending.await().account.accessToken)
        assertTrue(store.writes.isEmpty())
    }

    private open class Refresher : SteamAccountSessionRefresher {
        override fun shouldRefresh(account: SteamAccount, nowSeconds: Long) = false
        override suspend fun refresh(account: SteamAccount, force: Boolean): SteamSessionTokens? = null
    }

    private class Store(vararg handles: SteamAccountSessionHandle) : SteamAccountSessionStore {
        val accounts = handles.associate { it.stableKey to it.account }.toMutableMap()
        val writes = mutableListOf<SteamAccountSessionHandle>()
        override suspend fun load(handle: SteamAccountSessionHandle) = checkNotNull(accounts[handle.stableKey])
        override suspend fun persist(handle: SteamAccountSessionHandle) {
            writes += handle
            accounts[handle.stableKey] = handle.account
        }
    }

    private fun handle(source: SteamStorageSource = SteamStorageSource.Local, entryId: String? = null) =
        SteamAccountSessionHandle(SteamWorkshopViewModelTest.account(), SteamAccountSessionOrigin(source, entryId))
}
