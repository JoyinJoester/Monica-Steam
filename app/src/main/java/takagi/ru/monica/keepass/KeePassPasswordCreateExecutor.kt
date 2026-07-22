package takagi.ru.monica.keepass

import android.util.Log
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.utils.KeePassCustomFieldData

class KeePassPasswordCreateExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    suspend fun create(
        localEntry: PasswordEntry,
        syncEntry: PasswordEntry,
        insertEntry: suspend (PasswordEntry) -> Long,
        rollbackEntry: suspend (Long) -> Unit,
        resolvePassword: (PasswordEntry) -> String,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): Long? {
        val id = insertEntry(localEntry)
        val databaseId = syncEntry.keepassDatabaseId

        if (databaseId == null) {
            Log.w(TAG, "Create entry id=$id skipped KeePass sync because keepassDatabaseId is null")
            return id
        }

        val keepassBridge = bridge
        if (keepassBridge == null) {
            Log.w(TAG, "Create entry id=$id skipped KeePass sync because bridge is unavailable")
            return id
        }

        Log.d(
            TAG,
            "Create entry id=$id will sync to KeePass db=$databaseId group=${syncEntry.keepassGroupPath ?: "<root>"}"
        )
        val syncResult = keepassBridge.upsertLegacyPasswordEntries(
            databaseId = databaseId,
            entries = listOf(syncEntry.copy(id = id)),
            resolvePassword = resolvePassword,
            customFieldsByEntryId = mapOf(id to customFields)
        )
        if (syncResult.isFailure) {
            rollbackEntry(id)
            Log.e(TAG, "KeePass write failed: ${syncResult.exceptionOrNull()?.message}")
            return null
        }

        Log.d(TAG, "KeePass write success for entry id=$id db=$databaseId")
        return id
    }

    private companion object {
        const val TAG = "KeePassPasswordCreate"
    }
}
