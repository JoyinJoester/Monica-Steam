package takagi.ru.monica.steam.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

class SteamBackupRestoreTest {
    @Test
    fun previewSeparatesConflictsAndNewAccounts() {
        val accounts = listOf(
            backup("76561198000000001"),
            backup("76561198000000002"),
            backup("")
        )
        val preview = SteamBackupPreviewCalculator.calculate(
            accounts,
            setOf("76561198000000001", "76561198000000003")
        )
        assertEquals(3, preview.accountCount)
        assertEquals(1, preview.conflictCount)
        assertEquals(1, preview.newAccountCount)
        assertEquals(1, preview.invalidCount)
    }

    @Test
    fun keepExistingSkipsConflictAndRestoresSelectedAccount() = runTest {
        val existing = steamAccount(id = 10L, steamId = "76561198000000001")
        val store = FakeStore(listOf(existing))
        val payload = SteamBackupPayload(
            createdAt = 1L,
            accounts = listOf(
                backup(existing.steamId).copy(selected = true),
                backup("76561198000000002").copy(groupName = "group", tags = listOf("tag"))
            )
        )

        val result = SteamBackupRepository(store).restore(
            payload,
            SteamBackupConflictStrategy.KEEP_EXISTING
        )

        assertEquals(1, result.importedCount)
        assertEquals(0, result.replacedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(10L, result.selectedAccountId)
        assertEquals(listOf("76561198000000002"), store.restored.map { it.steamId })
        assertEquals("group", store.restored.single().groupName)
        assertEquals(10L, store.selectedId)
    }

    @Test
    fun replaceExistingWritesConflict() = runTest {
        val existing = steamAccount(id = 10L, steamId = "76561198000000001")
        val store = FakeStore(listOf(existing))

        val result = SteamBackupRepository(store).restore(
            SteamBackupPayload(createdAt = 1L, accounts = listOf(backup(existing.steamId))),
            SteamBackupConflictStrategy.REPLACE_EXISTING
        )

        assertEquals(0, result.importedCount)
        assertEquals(1, result.replacedCount)
        assertEquals(0, result.skippedCount)
        assertTrue(store.restored.single().steamId == existing.steamId)
    }

    private fun backup(steamId: String): SteamBackupAccount {
        return SteamBackupAccount(
            steamId = steamId,
            accountName = "name",
            displayName = "name",
            deviceId = "device",
            sharedSecret = "secret",
            maFileJson = if (steamId.isBlank()) "" else "{}",
            createdAt = 1L
        )
    }

    private fun steamAccount(id: Long, steamId: String): SteamAccount {
        return SteamAccount(
            id = id,
            steamId = steamId,
            accountName = "existing",
            displayName = "Existing",
            deviceId = "device",
            sharedSecret = "secret",
            identitySecret = null,
            revocationCode = null,
            tokenGid = null,
            accessToken = null,
            refreshToken = null,
            steamLoginSecure = null,
            rawSteamGuardJson = "{}",
            selected = true,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        )
    }

    private class FakeStore(accounts: List<SteamAccount>) : SteamBackupAccountStore {
        private val current = accounts.associateByTo(linkedMapOf()) { it.steamId }
        val restored = mutableListOf<SteamBackupAccount>()
        var selectedId: Long? = null

        override suspend fun getAccounts(): List<SteamAccount> = current.values.toList()

        override suspend fun restore(account: SteamBackupAccount): Long {
            restored += account
            return current[account.steamId]?.id ?: (100L + restored.size)
        }

        override suspend fun select(id: Long) {
            selectedId = id
        }
    }
}
