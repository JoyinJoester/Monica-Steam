package takagi.ru.monica.keepass

import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.passkey.PasskeyCredentialIdCodec
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassPasskeyUpdateExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    suspend fun update(
        existing: PasskeyEntry,
        updated: PasskeyEntry,
        persistUpdate: suspend (PasskeyEntry) -> Unit
    ): Result<PasskeyEntry> {
        val keepassBridge = bridge
        if (keepassBridge == null) {
            persistUpdate(updated)
            return Result.success(updated)
        }

        val oldDatabaseId = existing.keepassDatabaseId
        val newDatabaseId = updated.keepassDatabaseId
        val oldManaged = oldDatabaseId != null && existing.passkeyMode == PasskeyEntry.MODE_KEEPASS_COMPAT
        val newManaged = newDatabaseId != null && updated.passkeyMode == PasskeyEntry.MODE_KEEPASS_COMPAT

        if (newManaged) {
            val conflictCheck = hasTargetCredentialConflict(
                bridge = keepassBridge,
                existing = existing,
                oldDatabaseId = oldDatabaseId,
                newDatabaseId = newDatabaseId!!,
                updated = updated
            )
            if (conflictCheck.isFailure) {
                return Result.failure(
                    conflictCheck.exceptionOrNull()
                        ?: IllegalStateException("KeePass passkey credential conflict check failed")
                )
            }
            if (conflictCheck.getOrDefault(false)) {
                return Result.failure(KeePassPasskeyCredentialConflictException(updated.credentialId))
            }
            keepassBridge.upsertLegacyPasskeys(
                databaseId = newDatabaseId,
                passkeys = listOf(updated)
            ).getOrElse { return Result.failure(it) }
        }

        val shouldDeleteOld = oldManaged && (
            !newManaged ||
                oldDatabaseId != newDatabaseId
            )

        // Cross-store moves must never delete the KDBX source before the target
        // store and Room projection have accepted the passkey. If source cleanup
        // fails afterwards the caller can retry from a duplicated-but-safe state.
        if (shouldDeleteOld) {
            persistUpdate(updated)
            keepassBridge.deleteLegacyPasskeys(
                databaseId = oldDatabaseId!!,
                passkeys = listOf(existing)
            ).getOrElse { error ->
                return Result.failure(error)
            }
            return Result.success(updated)
        }

        persistUpdate(updated)
        return Result.success(updated)
    }

    private suspend fun hasTargetCredentialConflict(
        bridge: KeePassCompatibilityBridge,
        existing: PasskeyEntry,
        oldDatabaseId: Long?,
        newDatabaseId: Long,
        updated: PasskeyEntry
    ): Result<Boolean> {
        val targetCredentialId = normalizedCredentialId(updated.credentialId)
            ?: return Result.success(false)
        val sourceCredentialId = normalizedCredentialId(existing.credentialId)

        val targetPasskeys = bridge.readLegacyPasskeys(newDatabaseId)
            .getOrElse { return Result.failure(it) }
        val hasConflict = targetPasskeys.any { target ->
            if (target.passkeyMode != PasskeyEntry.MODE_KEEPASS_COMPAT) return@any false
            val existingTargetCredentialId = normalizedCredentialId(target.credentialId)
            val isSameSourceCredential = oldDatabaseId == newDatabaseId &&
                sourceCredentialId != null &&
                existingTargetCredentialId == sourceCredentialId
            !isSameSourceCredential && existingTargetCredentialId == targetCredentialId
        }
        return Result.success(hasConflict)
    }

    private fun normalizedCredentialId(value: String?): String? {
        return PasskeyCredentialIdCodec.normalize(value)
            ?: value?.trim()?.takeIf { it.isNotBlank() }
    }
}

class KeePassPasskeyCredentialConflictException(
    credentialId: String
) : IllegalStateException(
    "KeePass target already contains passkey credentialId=${credentialId.takeLast(10)}"
)
