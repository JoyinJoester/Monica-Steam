package takagi.ru.monica.keepass

import app.keemobile.kotpass.database.KeePassDatabase

object KeePassRemoteRebase {
    fun requireNoBlockedChanges(pendingPlan: KeePassPendingFlushPlan) {
        if (pendingPlan.hasBlockedChanges) {
            val blockedSummary = pendingPlan.blocked.joinToString(limit = 3) { blocked ->
                "${blocked.changeId}:${blocked.reason}"
            }
            throw IllegalStateException(
                "KeePass pending rebase blocked by validation errors: $blockedSummary"
            )
        }
    }

    fun applyReadyChanges(
        remoteDatabase: KeePassDatabase,
        pendingPlan: KeePassPendingFlushPlan
    ): KeePassChangeSetApplyBatchResult? {
        if (!pendingPlan.hasReadyChanges) {
            return null
        }
        requireNoBlockedChanges(pendingPlan)
        return KeePassChangeSetApplier().applyAll(
            database = remoteDatabase,
            changes = pendingPlan.ready.map { it.changeSet }
        )
    }
}
