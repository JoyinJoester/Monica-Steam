package takagi.ru.monica.steam.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.importer.SteamMaFilePayload

class SteamAccountRepository(
    private val dao: SteamAccountDao,
    private val securityManager: SecurityManager
) {
    fun observeAccounts(): Flow<List<SteamAccount>> {
        return dao.observeAccounts()
            .map { accounts -> accounts.map(::decryptEntity) }
            .flowOn(Dispatchers.Default)
    }

    suspend fun getAccounts(): List<SteamAccount> = dao.getAccounts().map(::decryptEntity)

    suspend fun getAccount(id: Long): SteamAccount? = dao.getById(id)?.let(::decryptEntity)

    suspend fun getSelectedAccount(): SteamAccount? {
        return dao.getSelected()?.let(::decryptEntity)
            ?: dao.getAccounts().firstOrNull()?.let(::decryptEntity)
    }

    suspend fun upsertFromMaFile(payload: SteamMaFilePayload): Long {
        val now = System.currentTimeMillis()
        val existing = findExistingBySteamId(payload.steamId)
        val shouldSelect = existing?.selected ?: (dao.count() == 0)
        val entity = SteamAccountEntity(
            id = existing?.id ?: 0L,
            steamId = encrypt(payload.steamId),
            accountName = encrypt(payload.accountName),
            displayName = encrypt(payload.displayName),
            deviceId = encrypt(payload.deviceId),
            sharedSecret = encrypt(payload.sharedSecret),
            identitySecret = payload.identitySecret?.let(::encrypt),
            revocationCode = payload.revocationCode?.let(::encrypt),
            tokenGid = payload.tokenGid?.let(::encrypt),
            accessToken = payload.accessToken?.let(::encrypt),
            refreshToken = payload.refreshToken?.let(::encrypt),
            steamLoginSecure = payload.steamLoginSecure?.let(::encrypt),
            rawSteamGuardJson = encrypt(payload.rawJson),
            selected = shouldSelect,
            sortOrder = existing?.sortOrder ?: dao.nextSortOrder(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            groupName = existing?.groupName,
            tagsJson = existing?.tagsJson ?: "[]",
            accentArgb = existing?.accentArgb,
            note = existing?.note ?: "",
            pinned = existing?.pinned ?: false,
            lastHealthCheckAt = existing?.lastHealthCheckAt
        )

        val id = if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            existing.id
        }
        if (shouldSelect) dao.selectAccount(id)
        return id
    }

    suspend fun updateDisplayName(id: Long, displayName: String) {
        val existing = dao.getById(id) ?: return
        val existingPlain = decryptEntity(existing)
        dao.update(
            existing.copy(
                displayName = encrypt(displayName.trim().ifBlank { existingPlain.accountName }),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun replaceAccount(account: SteamAccount): Long {
        val existing = dao.getById(account.id) ?: return 0L
        val duplicate = findExistingBySteamId(account.steamId)
            ?.takeIf { it.id != account.id }
        require(duplicate == null) { "Steam account already exists" }
        dao.update(
            SteamAccountEntity(
                id = existing.id,
                steamId = encrypt(account.steamId),
                accountName = encrypt(account.accountName),
                displayName = encrypt(account.displayName),
                deviceId = encrypt(account.deviceId),
                sharedSecret = encrypt(account.sharedSecret),
                identitySecret = account.identitySecret?.let(::encrypt),
                revocationCode = account.revocationCode?.let(::encrypt),
                tokenGid = account.tokenGid?.let(::encrypt),
                accessToken = account.accessToken?.let(::encrypt),
                refreshToken = account.refreshToken?.let(::encrypt),
                steamLoginSecure = account.steamLoginSecure?.let(::encrypt),
                rawSteamGuardJson = encrypt(account.rawSteamGuardJson),
                selected = existing.selected,
                sortOrder = existing.sortOrder,
                createdAt = existing.createdAt,
                updatedAt = System.currentTimeMillis(),
                groupName = account.groupName?.let(::encrypt),
                tagsJson = encrypt(SteamAccountTags.encode(account.tags)),
                accentArgb = account.accentArgb,
                note = encrypt(account.note),
                pinned = account.pinned,
                lastHealthCheckAt = account.lastHealthCheckAt
            )
        )
        return existing.id
    }

    suspend fun delete(id: Long) {
        val wasSelected = dao.getById(id)?.selected == true
        dao.deleteById(id)
        if (wasSelected) {
            dao.getAccounts().firstOrNull()?.let { dao.selectAccount(it.id) }
        }
    }

    suspend fun select(id: Long) {
        dao.selectAccount(id)
    }

    suspend fun updateSortOrders(items: List<Pair<Long, Int>>) {
        dao.updateSortOrders(items)
    }

    suspend fun restoreFromBackup(
        payload: SteamMaFilePayload,
        groupName: String?,
        tags: Iterable<String>,
        accentArgb: Long?,
        note: String,
        pinned: Boolean,
        selected: Boolean,
        sortOrder: Int,
        createdAt: Long
    ): Long {
        val now = System.currentTimeMillis()
        val existing = findExistingBySteamId(payload.steamId)
        val entity = SteamAccountEntity(
            id = existing?.id ?: 0L,
            steamId = encrypt(payload.steamId),
            accountName = encrypt(payload.accountName),
            displayName = encrypt(payload.displayName),
            deviceId = encrypt(payload.deviceId),
            sharedSecret = encrypt(payload.sharedSecret),
            identitySecret = payload.identitySecret?.let(::encrypt),
            revocationCode = payload.revocationCode?.let(::encrypt),
            tokenGid = payload.tokenGid?.let(::encrypt),
            accessToken = payload.accessToken?.let(::encrypt),
            refreshToken = payload.refreshToken?.let(::encrypt),
            steamLoginSecure = payload.steamLoginSecure?.let(::encrypt),
            rawSteamGuardJson = encrypt(payload.rawJson),
            selected = selected,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = now,
            groupName = groupName?.trim()?.takeIf(String::isNotEmpty)?.let(::encrypt),
            tagsJson = encrypt(SteamAccountTags.encode(tags)),
            accentArgb = accentArgb,
            note = encrypt(note.trim()),
            pinned = pinned,
            lastHealthCheckAt = null
        )
        return if (existing == null) dao.insert(entity) else {
            dao.update(entity)
            existing.id
        }
    }

    suspend fun updateOrganization(
        id: Long,
        groupName: String?,
        tags: Iterable<String>,
        accentArgb: Long?,
        note: String,
        pinned: Boolean
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                groupName = groupName?.trim()?.takeIf(String::isNotEmpty)?.let(::encrypt),
                tagsJson = encrypt(SteamAccountTags.encode(tags)),
                accentArgb = accentArgb,
                note = encrypt(note.trim()),
                pinned = pinned,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markHealthChecked(id: Long, checkedAt: Long = System.currentTimeMillis()) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(lastHealthCheckAt = checkedAt))
    }

    suspend fun updateSessionTokens(
        id: Long,
        accessToken: String,
        refreshToken: String?,
        steamLoginSecure: String?
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                accessToken = encrypt(accessToken),
                refreshToken = refreshToken?.let(::encrypt) ?: existing.refreshToken,
                steamLoginSecure = steamLoginSecure?.let(::encrypt) ?: existing.steamLoginSecure,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun encrypt(value: String): String {
        return securityManager.encryptDataLegacyCompat(value)
    }

    private fun decrypt(value: String?): String? {
        return value?.let { securityManager.decryptDataIfMonicaCiphertext(it) }
    }

    private suspend fun findExistingBySteamId(steamId: String): SteamAccountEntity? {
        return dao.getAccounts().firstOrNull { entity ->
            decrypt(entity.steamId) == steamId
        }
    }

    private fun decryptEntity(entity: SteamAccountEntity): SteamAccount {
        return SteamAccount(
            id = entity.id,
            steamId = decrypt(entity.steamId).orEmpty(),
            accountName = decrypt(entity.accountName).orEmpty(),
            displayName = decrypt(entity.displayName).orEmpty(),
            deviceId = decrypt(entity.deviceId).orEmpty(),
            sharedSecret = decrypt(entity.sharedSecret).orEmpty(),
            identitySecret = decrypt(entity.identitySecret),
            revocationCode = decrypt(entity.revocationCode),
            tokenGid = decrypt(entity.tokenGid),
            accessToken = decrypt(entity.accessToken),
            refreshToken = decrypt(entity.refreshToken),
            steamLoginSecure = decrypt(entity.steamLoginSecure),
            rawSteamGuardJson = decrypt(entity.rawSteamGuardJson).orEmpty(),
            selected = entity.selected,
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            groupName = decrypt(entity.groupName)?.takeIf(String::isNotBlank),
            tags = SteamAccountTags.decode(decrypt(entity.tagsJson)),
            accentArgb = entity.accentArgb,
            note = decrypt(entity.note).orEmpty(),
            pinned = entity.pinned,
            lastHealthCheckAt = entity.lastHealthCheckAt
        )
    }
}
