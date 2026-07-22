package takagi.ru.monica.steam.backup

import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.importer.SteamMaFilePayload

enum class SteamBackupConflictStrategy {
    KEEP_EXISTING,
    REPLACE_EXISTING
}

data class SteamBackupPreview(
    val accountCount: Int,
    val conflictCount: Int,
    val newAccountCount: Int,
    val invalidCount: Int = 0
) {
    val actionCount: Int
        get() = newAccountCount + conflictCount
}

data class SteamBackupRestoreResult(
    val importedCount: Int,
    val replacedCount: Int,
    val skippedCount: Int,
    val selectedAccountId: Long?
)

object SteamBackupPreviewCalculator {
    fun calculate(accounts: List<SteamBackupAccount>, existingSteamIds: Set<String>): SteamBackupPreview {
        val valid = accounts.count { it.steamId.isNotBlank() && it.maFileJson.isNotBlank() }
        val conflicts = accounts.count { it.steamId in existingSteamIds }
        return SteamBackupPreview(
            accountCount = accounts.size,
            conflictCount = conflicts,
            newAccountCount = valid - conflicts,
            invalidCount = accounts.size - valid
        )
    }
}

interface SteamBackupAccountStore {
    suspend fun getAccounts(): List<SteamAccount>
    suspend fun restore(account: SteamBackupAccount): Long
    suspend fun select(id: Long)
}

private class RepositorySteamBackupAccountStore(
    private val repository: SteamAccountRepository
) : SteamBackupAccountStore {
    override suspend fun getAccounts(): List<SteamAccount> = repository.getAccounts()

    override suspend fun restore(account: SteamBackupAccount): Long {
        return repository.restoreFromBackup(
            SteamMaFilePayload(
                steamId = account.steamId,
                accountName = account.accountName,
                displayName = account.displayName,
                deviceId = account.deviceId,
                sharedSecret = account.sharedSecret,
                identitySecret = account.identitySecret,
                revocationCode = account.revocationCode,
                tokenGid = account.tokenGid,
                accessToken = account.accessToken,
                refreshToken = account.refreshToken,
                steamLoginSecure = account.steamLoginSecure,
                rawJson = account.maFileJson
            ),
            groupName = account.groupName,
            tags = account.tags,
            accentArgb = account.accentArgb,
            note = account.note,
            pinned = account.pinned,
            selected = account.selected,
            sortOrder = account.sortOrder,
            createdAt = account.createdAt
        )
    }

    override suspend fun select(id: Long) = repository.select(id)
}

class SteamBackupRepository(
    private val accountStore: SteamBackupAccountStore
) {
    constructor(accountRepository: SteamAccountRepository) :
        this(RepositorySteamBackupAccountStore(accountRepository))

    suspend fun preview(payload: SteamBackupPayload): SteamBackupPreview {
        val existing = accountStore.getAccounts().mapTo(mutableSetOf()) { it.steamId }
        return SteamBackupPreviewCalculator.calculate(payload.accounts, existing)
    }

    suspend fun restore(
        payload: SteamBackupPayload,
        strategy: SteamBackupConflictStrategy
    ): SteamBackupRestoreResult {
        val existingBySteamId = accountStore.getAccounts().associateBy { it.steamId }
        val restoredIds = linkedMapOf<String, Long>()
        var imported = 0
        var replaced = 0
        var skipped = 0

        payload.accounts.forEach { backup ->
            val existing = existingBySteamId[backup.steamId]
            if (existing != null && strategy == SteamBackupConflictStrategy.KEEP_EXISTING) {
                restoredIds[backup.steamId] = existing.id
                skipped++
                return@forEach
            }
            val id = accountStore.restore(backup)
            restoredIds[backup.steamId] = id
            if (existing == null) imported++ else replaced++
        }

        val selectedSteamId = payload.accounts.firstOrNull { it.selected }?.steamId
        val selectedId = selectedSteamId?.let(restoredIds::get)
        if (selectedId != null) accountStore.select(selectedId)
        return SteamBackupRestoreResult(imported, replaced, skipped, selectedId)
    }
}
