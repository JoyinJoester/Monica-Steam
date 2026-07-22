package takagi.ru.monica.keepass

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.data.LocalKeePassDatabase

@Entity(
    tableName = "keepass_pending_changes",
    indices = [
        Index(value = ["database_id"]),
        Index(value = ["change_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["operation"]),
        Index(value = ["entry_uuid"]),
        Index(value = ["database_id", "status", "next_attempt_at"]),
        Index(value = ["database_id", "entry_uuid", "status"]),
        Index(value = ["base_remote_etag"]),
        Index(value = ["base_hash"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = LocalKeePassDatabase::class,
            parentColumns = ["id"],
            childColumns = ["database_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class KeePassPendingChange(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "database_id")
    val databaseId: Long,

    @ColumnInfo(name = "change_id")
    val changeId: String,

    @ColumnInfo(name = "entry_uuid")
    val entryUuid: String? = null,

    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "target")
    val target: String,

    @ColumnInfo(name = "base_fingerprint")
    val baseFingerprint: String? = null,

    @ColumnInfo(name = "base_group_path")
    val baseGroupPath: String? = null,

    @ColumnInfo(name = "base_group_uuid")
    val baseGroupUuid: String? = null,

    @ColumnInfo(name = "base_remote_version_token")
    val baseRemoteVersionToken: String? = null,

    @ColumnInfo(name = "base_remote_etag")
    val baseRemoteEtag: String? = null,

    @ColumnInfo(name = "base_remote_last_modified")
    val baseRemoteLastModified: Long? = null,

    @ColumnInfo(name = "base_hash")
    val baseHash: String? = null,

    @ColumnInfo(name = "working_hash_at_change")
    val workingHashAtChange: String? = null,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "status", defaultValue = STATUS_PENDING)
    val status: String = STATUS_PENDING,

    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries", defaultValue = "3")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long? = null,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
) {
    fun canRetry(now: Long = System.currentTimeMillis()): Boolean {
        if (status != STATUS_FAILED && status != STATUS_PENDING) return false
        return nextAttemptAt?.let { it <= now } ?: true
    }

    fun toChangeSet(): KeePassChangeSet = KeePassChangeSetCodec.decode(payloadJson)

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_BLOCKED = "BLOCKED"
        const val STATUS_CANCELLED = "CANCELLED"

        fun fromChangeSet(
            changeSet: KeePassChangeSet,
            status: String = STATUS_PENDING,
            baseSnapshot: KeePassPendingChangeBaseSnapshot? = null,
            now: Long = System.currentTimeMillis()
        ): KeePassPendingChange {
            return KeePassPendingChange(
                databaseId = changeSet.databaseId,
                changeId = changeSet.changeId,
                entryUuid = changeSet.entryUuid,
                operation = changeSet.operation.name,
                target = changeSet.target.name,
                baseFingerprint = changeSet.baseFingerprint,
                baseGroupPath = changeSet.baseGroupPath,
                baseGroupUuid = changeSet.baseGroupUuid,
                baseRemoteVersionToken = baseSnapshot?.remoteVersionToken,
                baseRemoteEtag = baseSnapshot?.remoteEtag,
                baseRemoteLastModified = baseSnapshot?.remoteLastModified,
                baseHash = baseSnapshot?.baseHash,
                workingHashAtChange = baseSnapshot?.workingHashAtChange,
                payloadJson = KeePassChangeSetCodec.encode(changeSet),
                status = status,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

data class KeePassPendingChangeBaseSnapshot(
    val remoteVersionToken: String? = null,
    val remoteEtag: String? = null,
    val remoteLastModified: Long? = null,
    val baseHash: String? = null,
    val workingHashAtChange: String? = null
)

@Dao
interface KeePassPendingChangeDao {
    @Query(
        """
        SELECT * FROM keepass_pending_changes
        WHERE status IN ('PENDING', 'FAILED')
        ORDER BY created_at ASC, id ASC
        """
    )
    fun getRunnableChangesFlow(): Flow<List<KeePassPendingChange>>

    @Query(
        """
        SELECT * FROM keepass_pending_changes
        WHERE database_id = :databaseId
          AND status IN ('PENDING', 'FAILED')
        ORDER BY created_at ASC, id ASC
        """
    )
    fun getRunnableChangesByDatabaseFlow(databaseId: Long): Flow<List<KeePassPendingChange>>

    @Query(
        """
        SELECT * FROM keepass_pending_changes
        WHERE database_id = :databaseId
          AND status IN ('PENDING', 'FAILED')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
        ORDER BY created_at ASC, id ASC
        """
    )
    suspend fun getReadyChangesByDatabase(
        databaseId: Long,
        now: Long = System.currentTimeMillis()
    ): List<KeePassPendingChange>

    @Query(
        """
        SELECT * FROM keepass_pending_changes
        WHERE database_id = :databaseId
          AND change_id = :changeId
        LIMIT 1
        """
    )
    suspend fun getByChangeId(databaseId: Long, changeId: String): KeePassPendingChange?

    @Query(
        """
        SELECT * FROM keepass_pending_changes
        WHERE database_id = :databaseId
          AND entry_uuid = :entryUuid
          AND status IN ('PENDING', 'IN_PROGRESS', 'FAILED')
        ORDER BY created_at ASC, id ASC
        """
    )
    suspend fun getActiveChangesForEntry(
        databaseId: Long,
        entryUuid: String
    ): List<KeePassPendingChange>

    @Query(
        """
        SELECT COUNT(*) FROM keepass_pending_changes
        WHERE database_id = :databaseId
          AND status IN ('PENDING', 'FAILED')
        """
    )
    fun getRunnableCountByDatabaseFlow(databaseId: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM keepass_pending_changes
        WHERE database_id = :databaseId
          AND status IN ('PENDING', 'FAILED')
        """
    )
    suspend fun getRunnableCountByDatabase(databaseId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(change: KeePassPendingChange): Long

    @Update
    suspend fun update(change: KeePassPendingChange)

    @Query(
        """
        UPDATE keepass_pending_changes
        SET status = 'IN_PROGRESS',
            last_attempt_at = :now,
            updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun markInProgress(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE keepass_pending_changes
        SET status = 'FAILED',
            retry_count = retry_count + 1,
            next_attempt_at = :nextAttemptAt,
            last_attempt_at = :now,
            last_error = :lastError,
            updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun markFailed(
        id: Long,
        lastError: String?,
        nextAttemptAt: Long?,
        now: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE keepass_pending_changes
        SET status = 'BLOCKED',
            next_attempt_at = NULL,
            last_attempt_at = :now,
            last_error = :lastError,
            updated_at = :now
        WHERE id = :id
          AND status IN ('PENDING', 'IN_PROGRESS', 'FAILED')
        """
    )
    suspend fun markBlocked(
        id: Long,
        lastError: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE keepass_pending_changes
        SET status = 'FAILED',
            retry_count = retry_count + 1,
            next_attempt_at = NULL,
            last_error = :lastError,
            updated_at = :now
        WHERE database_id = :databaseId
          AND status = 'IN_PROGRESS'
          AND (last_attempt_at IS NULL OR last_attempt_at < :staleBefore)
        """
    )
    suspend fun markStaleInProgressFailed(
        databaseId: Long,
        staleBefore: Long,
        lastError: String,
        now: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE keepass_pending_changes
        SET status = 'COMPLETED',
            completed_at = :now,
            updated_at = :now,
            last_error = NULL
        WHERE id = :id
        """
    )
    suspend fun markCompleted(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE keepass_pending_changes
        SET status = 'PENDING',
            retry_count = 0,
            next_attempt_at = NULL,
            last_attempt_at = NULL,
            last_error = NULL,
            updated_at = :now
        WHERE id = :id AND status IN ('FAILED', 'BLOCKED')
        """
    )
    suspend fun resetForRetry(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE keepass_pending_changes
        SET status = 'CANCELLED',
            updated_at = :now
        WHERE id = :id
          AND status IN ('PENDING', 'IN_PROGRESS', 'FAILED')
        """
    )
    suspend fun cancel(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        DELETE FROM keepass_pending_changes
        WHERE status = 'COMPLETED'
          AND completed_at < :beforeTimestamp
        """
    )
    suspend fun deleteCompletedBefore(beforeTimestamp: Long)

    @Query("DELETE FROM keepass_pending_changes WHERE database_id = :databaseId")
    suspend fun deleteByDatabase(databaseId: Long)
}

class KeePassPendingChangeRepository(
    private val dao: KeePassPendingChangeDao
) {
    fun getRunnableChangesFlow(): Flow<List<KeePassPendingChange>> = dao.getRunnableChangesFlow()

    fun getRunnableChangesByDatabaseFlow(databaseId: Long): Flow<List<KeePassPendingChange>> {
        return dao.getRunnableChangesByDatabaseFlow(databaseId)
    }

    fun getRunnableCountByDatabaseFlow(databaseId: Long): Flow<Int> {
        return dao.getRunnableCountByDatabaseFlow(databaseId)
    }

    suspend fun enqueue(
        changeSet: KeePassChangeSet,
        baseSnapshot: KeePassPendingChangeBaseSnapshot? = null
    ): Long {
        val existing = dao.getByChangeId(changeSet.databaseId, changeSet.changeId)
        if (existing != null) return existing.id
        return dao.insert(KeePassPendingChange.fromChangeSet(changeSet, baseSnapshot = baseSnapshot))
    }

    suspend fun getReadyChangesByDatabase(
        databaseId: Long,
        now: Long = System.currentTimeMillis()
    ): List<KeePassPendingChange> {
        return dao.getReadyChangesByDatabase(databaseId, now)
    }

    suspend fun getActiveChangesForEntry(
        databaseId: Long,
        entryUuid: String
    ): List<KeePassPendingChange> {
        return dao.getActiveChangesForEntry(databaseId, entryUuid)
    }

    suspend fun markInProgress(id: Long, now: Long = System.currentTimeMillis()) {
        dao.markInProgress(id, now)
    }

    suspend fun markFailed(
        id: Long,
        error: Throwable,
        retryDelayMillis: Long?,
        now: Long = System.currentTimeMillis()
    ) {
        val nextAttemptAt = retryDelayMillis?.let { now + it.coerceAtLeast(0L) }
        dao.markFailed(
            id = id,
            lastError = error.message ?: error.javaClass.simpleName,
            nextAttemptAt = nextAttemptAt,
            now = now
        )
    }

    suspend fun markBlocked(
        id: Long,
        reason: String,
        now: Long = System.currentTimeMillis()
    ) {
        dao.markBlocked(
            id = id,
            lastError = reason,
            now = now
        )
    }

    suspend fun markStaleInProgressFailed(
        databaseId: Long,
        staleBefore: Long,
        lastError: String,
        now: Long = System.currentTimeMillis()
    ) {
        dao.markStaleInProgressFailed(
            databaseId = databaseId,
            staleBefore = staleBefore,
            lastError = lastError,
            now = now
        )
    }

    suspend fun markCompleted(id: Long, now: Long = System.currentTimeMillis()) {
        dao.markCompleted(id, now)
    }

    suspend fun resetForRetry(id: Long, now: Long = System.currentTimeMillis()) {
        dao.resetForRetry(id, now)
    }

    suspend fun cancel(id: Long, now: Long = System.currentTimeMillis()) {
        dao.cancel(id, now)
    }
}
