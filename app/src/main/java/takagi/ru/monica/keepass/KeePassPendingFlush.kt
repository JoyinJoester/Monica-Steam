package takagi.ru.monica.keepass

/**
 * Builds an ordered entry-level replay batch from persisted KeePass pending changes.
 *
 * This layer intentionally depends only on [KeePassPendingChange] and its
 * serialized [KeePassChangeSet]. It must not read Monica UI/cache projections
 * to rebuild a KDBX database.
 */
class KeePassPendingFlushPlanner(
    private val repository: KeePassPendingChangeRepository
) {
    suspend fun prepare(
        databaseId: Long,
        maxChanges: Int = DEFAULT_MAX_CHANGES,
        now: Long = System.currentTimeMillis()
    ): KeePassPendingFlushPlan {
        require(databaseId > 0) { "databaseId must be positive" }
        require(maxChanges > 0) { "maxChanges must be positive" }

        repository.markStaleInProgressFailed(
            databaseId = databaseId,
            staleBefore = now - STALE_IN_PROGRESS_TIMEOUT_MILLIS,
            lastError = STALE_IN_PROGRESS_ERROR,
            now = now
        )

        val pendingChanges = repository
            .getReadyChangesByDatabase(databaseId, now)
            .take(maxChanges)

        if (pendingChanges.isEmpty()) {
            return KeePassPendingFlushPlan.empty(databaseId, now)
        }

        val ready = mutableListOf<KeePassPendingFlushItem>()
        val blocked = mutableListOf<KeePassPendingFlushBlockedItem>()

        pendingChanges.forEach { pending ->
            val decoded = runCatching { pending.toChangeSet() }
            val changeSet = decoded.getOrNull()
            if (changeSet == null) {
                blocked += KeePassPendingFlushBlockedItem(
                    pendingId = pending.id,
                    changeId = pending.changeId,
                    reason = KeePassPendingFlushBlockReason.INVALID_PAYLOAD,
                    message = decoded.exceptionOrNull()?.message ?: "Unable to decode KeePass change set"
                )
                return@forEach
            }

            val validationError = validatePendingChange(databaseId, pending, changeSet)
            if (validationError != null) {
                blocked += validationError
                return@forEach
            }

            ready += KeePassPendingFlushItem(
                pendingId = pending.id,
                changeSet = changeSet
            )
        }

        return KeePassPendingFlushPlan(
            databaseId = databaseId,
            ready = ready,
            blocked = blocked,
            createdAtEpochMillis = now
        )
    }

    private fun validatePendingChange(
        databaseId: Long,
        pending: KeePassPendingChange,
        changeSet: KeePassChangeSet
    ): KeePassPendingFlushBlockedItem? {
        if (changeSet.databaseId != databaseId || pending.databaseId != databaseId) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.DATABASE_MISMATCH,
                message = "Pending change database does not match requested database"
            )
        }
        if (pending.changeId != changeSet.changeId) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.CHANGE_ID_MISMATCH,
                message = "Pending change id does not match payload change id"
            )
        }
        if (pending.operation != changeSet.operation.name) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.OPERATION_MISMATCH,
                message = "Pending operation does not match payload operation"
            )
        }
        if (pending.target != changeSet.target.name) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.TARGET_MISMATCH,
                message = "Pending target does not match payload target"
            )
        }
        if (pending.entryUuid != changeSet.entryUuid) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.ENTRY_UUID_MISMATCH,
                message = "Pending entry uuid does not match payload entry uuid"
            )
        }
        if (changeSet.requiresBaseFingerprint() && changeSet.baseFingerprint.isNullOrBlank()) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.MISSING_BASE_FINGERPRINT,
                message = "Non-create KeePass changes require a base fingerprint"
            )
        }
        if (changeSet.operation == KeePassChangeOperation.CREATE_ENTRY && changeSet.entryPatch == null) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.MISSING_PATCH,
                message = "CREATE_ENTRY requires entryPatch"
            )
        }
        if (changeSet.operation == KeePassChangeOperation.FIELD_PATCH && changeSet.fieldPatch == null) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.MISSING_PATCH,
                message = "FIELD_PATCH requires fieldPatch"
            )
        }
        if (changeSet.operation.isStructureOperation() && changeSet.structurePatch == null) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.MISSING_PATCH,
                message = "${changeSet.operation.name} requires structurePatch"
            )
        }
        if (changeSet.operation.isAttachmentOperation() && changeSet.attachmentPatch == null) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.MISSING_PATCH,
                message = "${changeSet.operation.name} requires attachmentPatch"
            )
        }
        if (changeSet.operation.isGroupTreeOperation() && changeSet.groupTreePatch == null) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.MISSING_PATCH,
                message = "${changeSet.operation.name} requires groupTreePatch"
            )
        }
        if (changeSet.operation == KeePassChangeOperation.ADD_ATTACHMENT &&
            changeSet.attachmentPatch?.contentBase64.isNullOrBlank()
        ) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.MISSING_PATCH,
                message = "ADD_ATTACHMENT requires contentBase64"
            )
        }
        return validateRecycleBinChange(pending, changeSet)
            ?: validateGroupTreeChange(pending, changeSet)
            ?: validateGroupChange(pending, changeSet)
    }

    private fun validateRecycleBinChange(
        pending: KeePassPendingChange,
        changeSet: KeePassChangeSet
    ): KeePassPendingFlushBlockedItem? {
        val structurePatch = changeSet.structurePatch ?: return null
        return when (changeSet.operation) {
            KeePassChangeOperation.MOVE_TO_RECYCLE_BIN -> {
                if (!structurePatch.isRecycleBinMove()) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_RECYCLE_BIN_PATCH,
                        message = "MOVE_TO_RECYCLE_BIN requires recycleBinGroupUuid and previousParentGroupUuid"
                    )
                } else {
                    null
                }
            }
            KeePassChangeOperation.RESTORE_FROM_RECYCLE_BIN -> {
                if (!structurePatch.isRecycleBinRestore()) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_RECYCLE_BIN_PATCH,
                        message = "RESTORE_FROM_RECYCLE_BIN requires recycleBinGroupUuid and previousParentGroupUuid"
                    )
                } else if (structurePatch.targetGroupUuid == structurePatch.recycleBinGroupUuid) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_RECYCLE_BIN_PATCH,
                        message = "RESTORE_FROM_RECYCLE_BIN target cannot be the recycle bin group"
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun validateGroupChange(
        pending: KeePassPendingChange,
        changeSet: KeePassChangeSet
    ): KeePassPendingFlushBlockedItem? {
        val structurePatch = changeSet.structurePatch ?: return null
        return when (changeSet.operation) {
            KeePassChangeOperation.CREATE_GROUP -> {
                if (structurePatch.sourceGroupUuid.isNullOrBlank() ||
                    structurePatch.targetGroupUuid.isNullOrBlank() ||
                    structurePatch.groupName.isNullOrBlank()
                ) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_GROUP_PATCH,
                        message = "CREATE_GROUP requires sourceGroupUuid, targetGroupUuid, and groupName"
                    )
                } else {
                    null
                }
            }
            KeePassChangeOperation.RENAME_GROUP -> {
                if (structurePatch.sourceGroupUuid.isNullOrBlank() ||
                    structurePatch.newGroupName.isNullOrBlank()
                ) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_GROUP_PATCH,
                        message = "RENAME_GROUP requires sourceGroupUuid and newGroupName"
                    )
                } else {
                    null
                }
            }
            KeePassChangeOperation.DELETE_GROUP -> {
                if (structurePatch.sourceGroupUuid.isNullOrBlank()) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_GROUP_PATCH,
                        message = "DELETE_GROUP requires sourceGroupUuid"
                    )
                } else {
                    null
                }
            }
            KeePassChangeOperation.MOVE_GROUP -> {
                if (structurePatch.sourceGroupUuid.isNullOrBlank() ||
                    structurePatch.targetGroupUuid.isNullOrBlank()
                ) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_GROUP_PATCH,
                        message = "MOVE_GROUP requires sourceGroupUuid and targetGroupUuid"
                    )
                } else if (structurePatch.sourceGroupUuid == structurePatch.targetGroupUuid) {
                    pending.blocked(
                        reason = KeePassPendingFlushBlockReason.INVALID_GROUP_PATCH,
                        message = "MOVE_GROUP target cannot be the source group"
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun validateGroupTreeChange(
        pending: KeePassPendingChange,
        changeSet: KeePassChangeSet
    ): KeePassPendingFlushBlockedItem? {
        if (!changeSet.operation.isGroupTreeOperation()) return null
        val structurePatch = changeSet.structurePatch
        val groupTreePatch = changeSet.groupTreePatch
        if (structurePatch == null || groupTreePatch == null) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.INVALID_GROUP_TREE_PATCH,
                message = "${changeSet.operation.name} requires structurePatch and groupTreePatch"
            )
        }
        if (changeSet.operation == KeePassChangeOperation.CREATE_GROUP_TREE &&
            (structurePatch.targetGroupUuid.isNullOrBlank() || groupTreePatch.targetParentGroupUuid.isNullOrBlank())
        ) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.INVALID_GROUP_TREE_PATCH,
                message = "CREATE_GROUP_TREE requires targetGroupUuid and targetParentGroupUuid"
            )
        }
        if (changeSet.operation == KeePassChangeOperation.DELETE_GROUP_TREE &&
            (structurePatch.sourceGroupUuid.isNullOrBlank() || groupTreePatch.sourceRootGroupUuid.isNullOrBlank())
        ) {
            return pending.blocked(
                reason = KeePassPendingFlushBlockReason.INVALID_GROUP_TREE_PATCH,
                message = "DELETE_GROUP_TREE requires sourceGroupUuid and sourceRootGroupUuid"
            )
        }
        return null
    }

    private fun KeePassPendingChange.blocked(
        reason: KeePassPendingFlushBlockReason,
        message: String
    ): KeePassPendingFlushBlockedItem {
        return KeePassPendingFlushBlockedItem(
            pendingId = id,
            changeId = changeId,
            reason = reason,
            message = message
        )
    }

    companion object {
        const val DEFAULT_MAX_CHANGES = 100
        const val STALE_IN_PROGRESS_TIMEOUT_MILLIS = 10 * 60 * 1000L
        const val STALE_IN_PROGRESS_ERROR = "Stale IN_PROGRESS KeePass pending change recovered for retry"
    }
}

data class KeePassPendingFlushPlan(
    val databaseId: Long,
    val ready: List<KeePassPendingFlushItem>,
    val blocked: List<KeePassPendingFlushBlockedItem>,
    val createdAtEpochMillis: Long
) {
    val hasReadyChanges: Boolean
        get() = ready.isNotEmpty()

    val hasBlockedChanges: Boolean
        get() = blocked.isNotEmpty()

    companion object {
        fun empty(databaseId: Long, now: Long): KeePassPendingFlushPlan {
            return KeePassPendingFlushPlan(
                databaseId = databaseId,
                ready = emptyList(),
                blocked = emptyList(),
                createdAtEpochMillis = now
            )
        }
    }
}

data class KeePassPendingFlushItem(
    val pendingId: Long,
    val changeSet: KeePassChangeSet
)

data class KeePassPendingFlushBlockedItem(
    val pendingId: Long,
    val changeId: String,
    val reason: KeePassPendingFlushBlockReason,
    val message: String
)

enum class KeePassPendingFlushBlockReason {
    INVALID_PAYLOAD,
    DATABASE_MISMATCH,
    CHANGE_ID_MISMATCH,
    OPERATION_MISMATCH,
    TARGET_MISMATCH,
    ENTRY_UUID_MISMATCH,
    MISSING_BASE_FINGERPRINT,
    MISSING_PATCH,
    INVALID_RECYCLE_BIN_PATCH,
    INVALID_GROUP_PATCH,
    INVALID_GROUP_TREE_PATCH
}
