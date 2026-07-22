package takagi.ru.monica.keepass

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Shared invariants for password entries that cross the KeePass seam.
 *
 * Moving an existing Room row into a KDBX must have a stable target UUID before
 * the KDBX write starts, otherwise the later Room update and attachment import
 * can point at a different entry.
 */
object KeePassCrossDatabaseTransfer {
    fun bindPasswordToTarget(
        entry: PasswordEntry,
        databaseId: Long,
        groupPath: String?,
        forceNewEntryUuid: Boolean = false
    ): PasswordEntry {
        val targetUuid = when {
            !forceNewEntryUuid &&
                entry.keepassDatabaseId == databaseId &&
                !entry.keepassEntryUuid.isNullOrBlank() -> entry.keepassEntryUuid

            !forceNewEntryUuid && entry.id > 0 ->
                stablePasswordMoveUuid(entry.id, databaseId)

            forceNewEntryUuid && entry.id > 0 ->
                stablePasswordMoveUuid(entry.id, databaseId, groupPath)

            else -> UUID.randomUUID().toString()
        }

        return entry.copy(
            keepassDatabaseId = databaseId,
            keepassGroupPath = groupPath,
            keepassEntryUuid = targetUuid,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false
        )
    }

    fun secureItemTargetEntryUuid(
        item: SecureItem?,
        databaseId: Long,
        groupPath: String?
    ): String {
        val existingUuid = item
            ?.keepassEntryUuid
            ?.takeIf { item.keepassDatabaseId == databaseId && it.isNotBlank() }
        if (existingUuid != null) return existingUuid

        return if (item != null && item.id > 0L) {
            stableSecureItemMoveUuid(
                itemId = item.id,
                itemType = item.itemType.name,
                databaseId = databaseId,
                groupPath = groupPath
            )
        } else {
            UUID.randomUUID().toString()
        }
    }

    private fun stablePasswordMoveUuid(
        entryId: Long,
        databaseId: Long,
        groupPath: String? = null
    ): String {
        val seed = "takagi.ru.monica.keepass.password.move:$databaseId:${groupPath.orEmpty()}:$entryId"
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun stableSecureItemMoveUuid(
        itemId: Long,
        itemType: String,
        databaseId: Long,
        groupPath: String?
    ): String {
        val seed = "takagi.ru.monica.keepass.secure-item.move:$databaseId:${groupPath.orEmpty()}:$itemType:$itemId"
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
