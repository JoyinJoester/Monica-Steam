package takagi.ru.monica.keepass

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeePassPendingFlushPlannerTest {
    @Test
    fun prepareRecoversStaleInProgressBeforePlanningReadyChangesAndDoesNotStopAtMaxRetries() = runBlocking {
        val now = 1_000_000L
        val readyChange = pending(
            id = 10,
            changeSet = fieldPatchChangeSet(),
            retryCount = 99,
            maxRetries = 3,
            status = KeePassPendingChange.STATUS_FAILED
        )
        val dao = FakePendingChangeDao(listOf(readyChange))
        val plan = KeePassPendingFlushPlanner(KeePassPendingChangeRepository(dao)).prepare(
            databaseId = DATABASE_ID,
            now = now
        )

        assertEquals(
            now - KeePassPendingFlushPlanner.STALE_IN_PROGRESS_TIMEOUT_MILLIS,
            dao.lastStaleBefore
        )
        assertEquals(KeePassPendingFlushPlanner.STALE_IN_PROGRESS_ERROR, dao.lastStaleError)
        assertEquals(1, plan.ready.size)
        assertEquals(readyChange.id, plan.ready.single().pendingId)
        assertTrue(plan.blocked.isEmpty())
    }

    @Test
    fun prepareKeepsInvalidPayloadBlockedWithoutDroppingValidReadyChange() = runBlocking {
        val invalid = KeePassPendingChange(
            id = 20,
            databaseId = DATABASE_ID,
            changeId = "invalid-json",
            entryUuid = "entry-uuid",
            operation = KeePassChangeOperation.FIELD_PATCH.name,
            target = KeePassChangeTarget.PASSWORD.name,
            baseFingerprint = "base-fingerprint",
            payloadJson = "{"
        )
        val valid = pending(id = 21, changeSet = fieldPatchChangeSet(changeId = "valid-change"))
        val dao = FakePendingChangeDao(listOf(invalid, valid))

        val plan = KeePassPendingFlushPlanner(KeePassPendingChangeRepository(dao)).prepare(
            databaseId = DATABASE_ID,
            now = 2_000_000L
        )

        assertEquals(listOf(valid.id), plan.ready.map { it.pendingId })
        assertEquals(1, plan.blocked.size)
        assertEquals(invalid.id, plan.blocked.single().pendingId)
        assertEquals(KeePassPendingFlushBlockReason.INVALID_PAYLOAD, plan.blocked.single().reason)
    }

    @Test
    fun prepareBlocksIncompleteGroupTreePayloadBeforeReplay() = runBlocking {
        val invalidGroupTree = pending(
            id = 30,
            changeSet = KeePassChangeSet(
                changeId = "delete-group-tree",
                databaseId = DATABASE_ID,
                target = KeePassChangeTarget.GROUP,
                operation = KeePassChangeOperation.DELETE_GROUP_TREE,
                entryUuid = null,
                baseFingerprint = null,
                structurePatch = KeePassStructureChangePatch(
                    sourceGroupPath = "Archive/Moved",
                    groupName = "Moved"
                ),
                groupTreePatch = KeePassGroupTreeChangePatch(
                    root = KeePassGroupTreeSnapshot(
                        uuid = "group-uuid",
                        name = "Moved"
                    )
                )
            )
        )
        val dao = FakePendingChangeDao(listOf(invalidGroupTree))

        val plan = KeePassPendingFlushPlanner(KeePassPendingChangeRepository(dao)).prepare(
            databaseId = DATABASE_ID,
            now = 3_000_000L
        )

        assertTrue(plan.ready.isEmpty())
        assertEquals(1, plan.blocked.size)
        assertEquals(KeePassPendingFlushBlockReason.INVALID_GROUP_TREE_PATCH, plan.blocked.single().reason)
        assertTrue(plan.blocked.single().message.contains("DELETE_GROUP_TREE requires sourceGroupUuid"))
    }

    private fun fieldPatchChangeSet(changeId: String = "field-patch"): KeePassChangeSet {
        return KeePassChangeSet(
            changeId = changeId,
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

    private fun pending(
        id: Long,
        changeSet: KeePassChangeSet,
        retryCount: Int = 0,
        maxRetries: Int = 3,
        status: String = KeePassPendingChange.STATUS_PENDING
    ): KeePassPendingChange {
        return KeePassPendingChange.fromChangeSet(changeSet, status = status).copy(
            id = id,
            retryCount = retryCount,
            maxRetries = maxRetries
        )
    }

    private class FakePendingChangeDao(
        private val readyChanges: List<KeePassPendingChange>
    ) : KeePassPendingChangeDao {
        var lastStaleBefore: Long? = null
            private set
        var lastStaleError: String? = null
            private set

        override fun getRunnableChangesFlow(): Flow<List<KeePassPendingChange>> = flowOf(readyChanges)

        override fun getRunnableChangesByDatabaseFlow(databaseId: Long): Flow<List<KeePassPendingChange>> {
            return flowOf(readyChanges.filter { it.databaseId == databaseId })
        }

        override suspend fun getReadyChangesByDatabase(
            databaseId: Long,
            now: Long
        ): List<KeePassPendingChange> {
            return readyChanges.filter {
                it.databaseId == databaseId &&
                    it.status in setOf(KeePassPendingChange.STATUS_PENDING, KeePassPendingChange.STATUS_FAILED) &&
                    (it.nextAttemptAt == null || it.nextAttemptAt <= now)
            }
        }

        override suspend fun getByChangeId(databaseId: Long, changeId: String): KeePassPendingChange? = null

        override suspend fun getActiveChangesForEntry(
            databaseId: Long,
            entryUuid: String
        ): List<KeePassPendingChange> = emptyList()

        override fun getRunnableCountByDatabaseFlow(databaseId: Long): Flow<Int> = flowOf(0)

        override suspend fun getRunnableCountByDatabase(databaseId: Long): Int = 0

        override suspend fun insert(change: KeePassPendingChange): Long = change.id

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
        ) {
            lastStaleBefore = staleBefore
            lastStaleError = lastError
        }

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
