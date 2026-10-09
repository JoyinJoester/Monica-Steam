package takagi.ru.monica.steam.session

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.importer.SteamMaFilePayload

class SteamTemporaryAccountStorageTest {
    @Test fun sameSteamIdTemporaryLoginAndRotationNeverChangeSavedAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".recoverytest"))
        val database = Room.inMemoryDatabaseBuilder(context, SteamDatabase::class.java).build()
        try {
            val store = SteamTemporaryAccounts()
            val repository = SteamAccountRepository(database.steamAccountDao(), SecurityManager(context), store)
            val savedId = repository.upsertFromMaFile(payload("saved-access", "saved-refresh"))
            val before = database.steamAccountDao().getAccounts()
            val temporary = repository.addTemporaryAccount(payload("temporary-access", "temporary-refresh"))
            assertNotEquals(savedId, temporary.id)
            assertEquals(temporary.id, repository.getSelectedAccount()!!.id)
            assertEquals(1, repository.getAccounts().count { it.selected })
            repository.updateSessionTokens(temporary.id, "fresh", "fresh-refresh", "${temporary.steamId}||fresh")
            repository.updateDisplayName(temporary.id, "Temporary test")
            assertEquals("fresh", repository.getAccount(temporary.id)!!.accessToken)
            assertEquals(before, database.steamAccountDao().getAccounts())
            repository.select(savedId)
            assertEquals(savedId, repository.getSelectedAccount()!!.id)
            repository.delete(temporary.id)
            repository.updateSessionTokens(temporary.id, "late", "late-refresh", null)
            assertNull(repository.getAccount(temporary.id))
            assertEquals(before, database.steamAccountDao().getAccounts())
            assertEquals(listOf(savedId), SteamAccountRepository(
                database.steamAccountDao(), SecurityManager(context), SteamTemporaryAccounts()
            ).getAccounts().map { it.id })
        } finally { database.close() }
    }

    private fun payload(access: String, refresh: String) = SteamMaFilePayload(
        "76561198000000001", "test", "Test", "", "", null, null, null,
        access, refresh, "76561198000000001||$access", "{}"
    )
}
