package takagi.ru.monica.steam.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.backup.SteamBackupPayloadCodec
import takagi.ru.monica.steam.backup.SteamMaFileZipCodec
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.importer.SteamMaFileBackupCodec
import takagi.ru.monica.steam.importer.SteamMaFilePayload
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.domain.*
import takagi.ru.monica.steam.web.domain.SteamWebAccountSessionPolicy
import takagi.ru.monica.steam.web.domain.SteamWebSessionProblem

class SteamTemporaryAccountsTest {
    @Test fun duplicateIdentityHasIndependentIdsAndSelection() {
        val store = SteamTemporaryAccounts()
        val first = store.add(payload())
        val second = store.add(payload())
        assertNotEquals(first.id, second.id)
        assertFalse(store.get(first.id)!!.selected)
        store.select(first.id)
        assertTrue(store.get(first.id)!!.selected)
        assertFalse(store.get(second.id)!!.selected)
        store.select(123L)
        assertTrue(store.accounts.value.none { it.selected })
        store.remove(first.id)
        assertNull(store.get(first.id))
        assertNotNull(store.get(second.id))
    }

    @Test fun accountAndTokensCannotSurviveANewStoreOrBeResurrected() {
        val store = SteamTemporaryAccounts()
        val account = store.add(payload())
        store.clear()
        store.update(account.id) { account }
        assertTrue(store.accounts.value.isEmpty())
        assertTrue(SteamTemporaryAccounts().accounts.value.isEmpty())
        assertNotEquals(account.id, store.add(payload()).id)
    }

    @Test fun exportsOmitTemporaryCredentials() {
        val temporary = SteamTemporaryAccounts().add(payload())
        val saved = temporary.copy(id = 1, isTemporary = false, accessToken = "saved-token",
            refreshToken = "saved-refresh", steamLoginSecure = "${temporary.steamId}||saved-token")
        val backup = SteamBackupPayloadCodec.decode(SteamBackupPayloadCodec.encode(listOf(saved, temporary)))
        assertEquals(listOf("saved-token"), backup.accounts.map { it.accessToken })
        assertThrows(IllegalArgumentException::class.java) { SteamMaFileBackupCodec.encode(temporary) }
        assertThrows(IllegalArgumentException::class.java) { SteamMaFileZipCodec().encode(listOf(temporary)) }
        val zip = SteamMaFileZipCodec().encode(listOf(saved, temporary))
        java.util.zip.ZipInputStream(zip.inputStream()).use {
            assertNotNull(it.nextEntry)
            assertFalse(it.readBytes().decodeToString().contains("temporary-token"))
            assertNull(it.nextEntry)
        }
    }

    @Test fun temporaryWebSessionsCannotInstallCookiesEvenWithValidTokens() {
        val result = SteamWebAccountSessionPolicy.decide(
            "76561198000000001", "76561198000000001||temporary-token", true, temporarySession = true
        )
        assertFalse(result.canLoad)
        assertFalse(result.installAuthenticatedCookie)
        assertEquals(SteamWebSessionProblem.TEMPORARY_SESSION, result.problem)
    }

    @Test fun logoutDuringRefreshDoesNotPersistOrReturnSession() = runTest {
        val temporary = SteamTemporaryAccounts()
        val account = temporary.add(payload())
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<SteamSessionTokens>()
        var writes = 0
        val manager = SteamAccountSessionManager(
            refresher = object : SteamAccountSessionRefresher {
                override fun shouldRefresh(account: SteamAccount, nowSeconds: Long) = true
                override suspend fun refresh(account: SteamAccount, force: Boolean): SteamSessionTokens {
                    started.complete(Unit)
                    return response.await()
                }
            },
            store = object : SteamAccountSessionStore {
                override suspend fun persist(handle: SteamAccountSessionHandle) { writes++ }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            temporaryAccounts = temporary
        )
        val handle = SteamAccountSessionHandle(account, SteamAccountSessionOrigin(SteamStorageSource.Local))
        val pending = async { runCatching { manager.resolve(handle) } }
        started.await()
        temporary.clear()
        response.complete(SteamSessionTokens("late-token", "late-refresh"))
        assertTrue(pending.await().exceptionOrNull() is IllegalStateException)
        assertEquals(0, writes)
        assertTrue(runCatching { manager.resolve(handle) }.isFailure)
    }

    @Test fun refreshReusesOnlyActiveMemoryTokens() = runTest {
        val temporary = SteamTemporaryAccounts()
        val account = temporary.add(payload())
        var calls = 0
        val manager = SteamAccountSessionManager(
            refresher = object : SteamAccountSessionRefresher {
                override fun shouldRefresh(account: SteamAccount, nowSeconds: Long) = account.accessToken != "fresh"
                override suspend fun refresh(account: SteamAccount, force: Boolean): SteamSessionTokens {
                    calls++
                    return SteamSessionTokens("fresh", "fresh-refresh")
                }
            },
            store = object : SteamAccountSessionStore {
                override suspend fun persist(handle: SteamAccountSessionHandle) {
                    temporary.update(handle.account.id) { handle.account }
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler), temporaryAccounts = temporary
        )
        val handle = SteamAccountSessionHandle(account, SteamAccountSessionOrigin(SteamStorageSource.Local))
        assertEquals("fresh", manager.resolve(handle).account.accessToken)
        assertEquals("fresh", manager.resolve(handle).account.accessToken)
        assertEquals(1, calls)
        temporary.remove(account.id)
        assertTrue(runCatching { manager.resolve(handle) }.isFailure)
    }

    private fun payload() = SteamMaFilePayload(
        steamId = "76561198000000001", accountName = "test", displayName = "Test",
        deviceId = "", sharedSecret = "", identitySecret = null, revocationCode = null,
        tokenGid = null, accessToken = "temporary-token", refreshToken = "temporary-refresh",
        steamLoginSecure = "76561198000000001||temporary-token", rawJson = "{}"
    )
}
