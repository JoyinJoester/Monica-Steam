package takagi.ru.monica.keepass

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KeePassPendingChangeRepositoryTest {
    @Test
    fun enqueuePersistsBaseSnapshotForRemoteRecovery() = runBlocking {
        val dao = RecordingPendingChangeDao()
        val repository = KeePassPendingChangeRepository(dao)
        val snapshot = KeePassPendingChangeBaseSnapshot(
            remoteVersionToken = "remote-version",
            remoteEtag = "etag",
            remoteLastModified = 1234L,
            baseHash = "base-hash",
            workingHashAtChange = "working-hash"
        )

        val id = repository.enqueue(fieldPatchChangeSet(), snapshot)
        val inserted = dao.inserted.single()

        assertEquals(1L, id)
        assertEquals("remote-version", inserted.baseRemoteVersionToken)
        assertEquals("etag", inserted.baseRemoteEtag)
        assertEquals(1234L, inserted.baseRemoteLastModified)
        assertEquals("base-hash", inserted.baseHash)
        assertEquals("working-hash", inserted.workingHashAtChange)
        assertEquals(KeePassPendingChange.STATUS_PENDING, inserted.status)
    }

    @Test
    fun enqueueIsIdempotentForSameDatabaseAndChangeId() = runBlocking {
        val existing = KeePassPendingChange.fromChangeSet(fieldPatchChangeSet()).copy(id = 77L)
        val dao = RecordingPendingChangeDao(existingChanges = listOf(existing))
        val repository = KeePassPendingChangeRepository(dao)

        val id = repository.enqueue(fieldPatchChangeSet())

        assertEquals(77L, id)
        assertEquals(0, dao.inserted.size)
    }

    private fun fieldPatchChangeSet(): KeePassChangeSet {
        return KeePassChangeSet(
            changeId = "field-patch",
            databaseId = DATABASE_ID,
            target = KeePassChangeTarget.PASSWORD,
            operation = KeePassChangeOperation.FIELD_PATCH,
            entryUuid = "entry-uuid",
            baseFingerprint = "base-fingerprint",
            fieldPatch = KeePassFieldChangePatch(
                managedScope = KeePassManagedFieldScope.PASSWORD,
                replacementFields = listOf(KeePassFieldChange("Title", "New title")),
                baseFields = listOf(KeePassFieldBaseValue("Title", "Old title"))
            )
        )
    }

    private class RecordingPendingChangeDao(
        existingChanges: List<KeePassPendingChange> = emptyList()
    ) : KeePassPendingChangeDao {
        private val existingByKey = existingChanges.associateBy { it.databaseId to it.changeId }
        val inserted = mutableListOf<KeePassPendingChange>()

        override fun getRunnableChangesFlow(): Flow<List<KeePassPendingChange>> = flowOf(emptyList())

        override fun getRunnableChangesByDatabaseFlow(databaseId: Long): Flow<List<KeePassPendingChange>> {
            return flowOf(emptyList())
        }

        override suspend fun getReadyChangesByDatabase(
            databaseId: Long,
            now: Long
        ): List<KeePassPendingChange> = emptyList()

        override suspend fun getByChangeId(databaseId: Long, changeId: String): KeePassPendingChange? {
            return existingByKey[databaseId to changeId]
        }

        override suspend fun getActiveChangesForEntry(
            databaseId: Long,
            entryUuid: String
        ): List<KeePassPendingChange> = emptyList()

        override fun getRunnableCountByDatabaseFlow(databaseId: Long): Flow<Int> = flowOf(0)

        override suspend fun getRunnableCountByDatabase(databaseId: Long): Int = 0

        override suspend fun insert(change: KeePassPendingChange): Long {
            val id = (inserted.size + 1).toLong()
            inserted += change.copy(id = id)
            return id
        }

        override suspend fun update(change: KeePassPendingChange) = Unit

        override suspend fun markInProgress(id: Long, now: Long) = Unit

        override suspend fun markFailed(
            id: Long,
            lastError: String?,
            nextAttemptAt: Long?,
            now: Long
        ) = Unit

        override suspend fun markBlocked(id: Long, lastError: String?, now: Long) = Unit

        override suspend fun markStaleInProgressFailed(
            databaseId: Long,
            staleBefore: Long,
            lastError: String,
            now: Long
        ) = Unit

        override suspend fun markCompleted(id: Long, now: Long) = Unit

        override suspend fun resetForRetry(id: Long, now: Long) = Unit

        override suspend fun cancel(id: Long, now: Long) = Unit

        override suspend fun deleteCompletedBefore(beforeTimestamp: Long) = Unit

        override suspend fun deleteByDatabase(databaseId: Long) = Unit
    }

    private companion object {
        const val DATABASE_ID = 42L
    }
}
