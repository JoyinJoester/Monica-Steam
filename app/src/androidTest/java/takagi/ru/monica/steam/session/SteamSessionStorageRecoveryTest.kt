package takagi.ru.monica.steam.session

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.*
import takagi.ru.monica.steam.importer.SteamMaFilePayload
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.data.SteamAccountSourceSessionStore
import takagi.ru.monica.steam.session.domain.*

class SteamSessionStorageRecoveryTest {
    @Test fun reLoginWrittenThroughAccountRepositoryReplacesCachedSessionForAStaleHandle() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".recoverytest"))
        val dao = SteamDatabase.getDatabase(context).steamAccountDao()
        val accounts = SteamAccountRepository(dao, SecurityManager(context))
        val previousSelection = dao.getSelected()?.id
        check(accounts.getAccounts().none { it.steamId == STEAM_ID })
        val id = accounts.upsertFromMaFile(SteamMaFilePayload(
            STEAM_ID, "session-recovery-fixture", "Recovery test", "", "", null, null, null,
            "fixture-old", "fixture-refresh", "$STEAM_ID||fixture-old", "{}"
        ))
        try {
            val handle = SteamAccountSessionHandle(requireNotNull(accounts.getAccount(id)),
                SteamAccountSessionOrigin(SteamStorageSource.Local))
            val manager = SteamAccountSessionManager(object : SteamAccountSessionRefresher {
                override fun shouldRefresh(account: SteamAccount, nowSeconds: Long) = account.accessToken == "fixture-old"
                override suspend fun refresh(account: SteamAccount, force: Boolean) =
                    SteamSessionTokens("fixture-rotated", "fixture-rotated-refresh")
            }, SteamAccountSourceSessionStore(SteamAccountSourceRepository.get(context)))
            assertEquals("fixture-rotated", manager.resolve(handle).account.accessToken)

            // The account screen writes through replaceAccount, outside the session manager.
            accounts.replaceAccount(handle.account.copy(accessToken = "fixture-new-login",
                refreshToken = "fixture-new-refresh", steamLoginSecure = "$STEAM_ID||fixture-new-login"))
            assertFalse(requireNotNull(dao.getById(id)).accessToken!!.contains("fixture-new-login"))
            assertEquals("fixture-new-login", manager.resolve(handle).account.accessToken)

            dao.deleteById(id)
            assertTrue(runCatching { manager.resolve(handle) }.isFailure)
        } finally {
            dao.deleteById(id)
            previousSelection?.let { dao.selectAccount(it) }
        }
    }

    private companion object { const val STEAM_ID = "76561198000991015" }
}
