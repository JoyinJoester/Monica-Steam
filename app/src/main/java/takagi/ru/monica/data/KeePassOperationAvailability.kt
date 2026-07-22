package takagi.ru.monica.data

enum class KeePassOperationBlockReason {
    MISSING_DATABASE,
    NEEDS_REFRESH,
    SYNCING,
    CONFLICT,
    FAILED
}

data class KeePassOperationAvailability(
    val canOperate: Boolean,
    val reason: KeePassOperationBlockReason? = null
)

fun LocalKeePassDatabase.writeOperationAvailability(): KeePassOperationAvailability {
    if (!isRemoteSource()) {
        return KeePassOperationAvailability(canOperate = true)
    }

    val hasLocalCopy = !workingCopyPath.isNullOrBlank() || !cacheCopyPath.isNullOrBlank()
    if (!isOfflineAvailable && !hasLocalCopy) {
        return KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.NEEDS_REFRESH
        )
    }

    return when (lastSyncStatus) {
        KeePassSyncStatus.IN_SYNC,
        KeePassSyncStatus.PENDING_UPLOAD -> KeePassOperationAvailability(canOperate = true)
        KeePassSyncStatus.SYNCING -> {
            if (hasLocalCopy && isSyncingStateStale()) {
                KeePassOperationAvailability(canOperate = true)
            } else {
                KeePassOperationAvailability(
                    canOperate = false,
                    reason = KeePassOperationBlockReason.SYNCING
                )
            }
        }
        KeePassSyncStatus.CONFLICT -> KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.CONFLICT
        )
        KeePassSyncStatus.FAILED -> KeePassOperationAvailability(canOperate = true)
        KeePassSyncStatus.LOCAL_ONLY,
        KeePassSyncStatus.REMOTE_CHANGED -> KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.NEEDS_REFRESH
        )
    }
}

private fun LocalKeePassDatabase.isSyncingStateStale(
    now: Long = System.currentTimeMillis()
): Boolean {
    val updatedAt = lastSyncStateUpdatedAt
    if (updatedAt <= 0L) return true
    return now - updatedAt > STALE_SYNCING_OPERATION_BLOCK_TIMEOUT_MILLIS
}

private const val STALE_SYNCING_OPERATION_BLOCK_TIMEOUT_MILLIS = 10 * 60 * 1000L
