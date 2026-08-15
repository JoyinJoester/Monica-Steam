package takagi.ru.monica.steam.library.sync

import takagi.ru.monica.steam.library.SteamAchievementSyncPlan
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievementProgress
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.planSteamAchievementProgressSync

internal data class SteamAchievementSyncSelection(
    val games: List<SteamGame>,
    val isFullSync: Boolean
)

internal data class SteamAchievementSyncBatch(
    val appIds: List<Int>,
    val isRetry: Boolean
)

internal sealed interface SteamAchievementSyncBatchOutcome {
    data class Success(
        val progress: Map<Int, SteamGameAchievementProgress>
    ) : SteamAchievementSyncBatchOutcome

    data class Failure(
        val reason: SteamLibraryFailureReason
    ) : SteamAchievementSyncBatchOutcome
}

internal enum class SteamAchievementSyncPhase {
    IDLE,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

internal data class SteamAchievementSyncState(
    val accountId: Long? = null,
    val steamId: String? = null,
    val phase: SteamAchievementSyncPhase = SteamAchievementSyncPhase.IDLE,
    val completedGames: Int = 0,
    val totalGames: Int = 0,
    val currentAppId: Int? = null,
    val failure: SteamLibraryFailureReason? = null
) {
    val active: Boolean
        get() = phase == SteamAchievementSyncPhase.QUEUED ||
            phase == SteamAchievementSyncPhase.RUNNING
}

internal fun selectSteamAchievementSyncGames(
    snapshot: SteamLibrarySnapshot,
    forceFull: Boolean
): SteamAchievementSyncSelection {
    val plan = planSteamAchievementProgressSync(snapshot, forceFull)
    val requestedAppIds = plan.appIds.toHashSet()
    val games = snapshot.games
        .asSequence()
        .distinctBy(SteamGame::appId)
        .filter { it.appId in requestedAppIds }
        .sortedWith(
            compareByDescending<SteamGame> { it.playtimeRecentMinutes > 0 }
                .thenByDescending(SteamGame::playtimeRecentMinutes)
                .thenByDescending(SteamGame::lastPlayedAt)
                .thenByDescending(SteamGame::playtimeForeverMinutes)
                .thenBy(SteamGame::name)
        )
        .toList()
    return SteamAchievementSyncSelection(
        games = games,
        isFullSync = plan.isFullSync
    )
}

internal fun SteamLibrarySnapshot.prepareAchievementSyncPlan(
    requestId: String,
    forceFull: Boolean,
    nowMillis: Long
): SteamLibrarySnapshot {
    val existing = achievementSyncPlan
    if (existing != null && (!forceFull || existing.requestId == requestId)) {
        return this
    }

    val selection = selectSteamAchievementSyncGames(this, forceFull)
    val distinctGameCount = games.distinctBy(SteamGame::appId).size
    val completedBeforeRun = if (selection.isFullSync && !forceFull) {
        (distinctGameCount - selection.games.size).coerceAtLeast(0)
    } else {
        0
    }
    val totalGames = if (selection.isFullSync) {
        distinctGameCount
    } else {
        selection.games.size
    }
    return copy(
        achievementSyncPlan = SteamAchievementSyncPlan(
            requestId = requestId,
            pendingAppIds = selection.games.map(SteamGame::appId),
            completedGames = completedBeforeRun,
            totalGames = totalGames,
            isFullSync = selection.isFullSync,
            startedAt = nowMillis
        )
    )
}

internal fun SteamAchievementSyncPlan.nextAchievementSyncBatch(
    batchSize: Int = ACHIEVEMENT_SYNC_BATCH_SIZE
): SteamAchievementSyncBatch? {
    require(batchSize > 0)
    return when {
        pendingAppIds.isNotEmpty() -> SteamAchievementSyncBatch(
            appIds = pendingAppIds.take(batchSize),
            isRetry = false
        )
        retryAppIds.isNotEmpty() -> SteamAchievementSyncBatch(
            appIds = retryAppIds.take(batchSize),
            isRetry = true
        )
        else -> null
    }
}

internal fun SteamLibrarySnapshot.applyAchievementSyncBatch(
    requestId: String,
    batch: SteamAchievementSyncBatch,
    outcome: SteamAchievementSyncBatchOutcome,
    nowMillis: Long
): SteamLibrarySnapshot {
    val plan = achievementSyncPlan?.takeIf { it.requestId == requestId } ?: return this
    val source = if (batch.isRetry) plan.retryAppIds else plan.pendingAppIds
    val batchSet = batch.appIds.toHashSet()
    if (batch.appIds.isEmpty() || !source.containsAll(batch.appIds)) return this

    val nextPending = if (batch.isRetry) {
        plan.pendingAppIds
    } else {
        plan.pendingAppIds.filterNot(batchSet::contains)
    }
    val nextRetry = when (outcome) {
        is SteamAchievementSyncBatchOutcome.Success -> {
            if (batch.isRetry) plan.retryAppIds.filterNot(batchSet::contains)
            else plan.retryAppIds
        }
        is SteamAchievementSyncBatchOutcome.Failure -> {
            if (batch.isRetry) {
                plan.retryAppIds.filterNot(batchSet::contains)
            } else {
                (plan.retryAppIds + batch.appIds).distinct()
            }
        }
    }
    val completedDelta = when (outcome) {
        is SteamAchievementSyncBatchOutcome.Success -> batch.appIds.size
        is SteamAchievementSyncBatchOutcome.Failure -> if (batch.isRetry) batch.appIds.size else 0
    }
    val failedAppIds = when (outcome) {
        is SteamAchievementSyncBatchOutcome.Success -> plan.failedAppIds
        is SteamAchievementSyncBatchOutcome.Failure -> {
            if (batch.isRetry) (plan.failedAppIds + batch.appIds).distinct()
            else plan.failedAppIds
        }
    }
    val nextGames = when (outcome) {
        is SteamAchievementSyncBatchOutcome.Success -> games.map { game ->
            if (game.appId !in batchSet) {
                game
            } else {
                val summary = outcome.progress[game.appId]
                    ?: SteamGameAchievementProgress(
                        appId = game.appId,
                        unlocked = 0,
                        total = 0,
                        allUnlocked = false
                    )
                game.copy(
                    achievementUnlockedCount = summary.unlocked,
                    achievementTotalCount = summary.total,
                    allAchievementsUnlocked = summary.total > 0 && summary.allUnlocked,
                    achievementProgressPlaytimeMinutes = game.playtimeForeverMinutes
                )
            }
        }
        is SteamAchievementSyncBatchOutcome.Failure -> {
            if (!batch.isRetry) {
                games
            } else {
                games.map { game ->
                    if (game.appId !in batchSet) {
                        game
                    } else {
                        game.copy(
                            achievementProgressPlaytimeMinutes = game.playtimeForeverMinutes
                        )
                    }
                }
            }
        }
    }
    return copy(
        games = nextGames,
        achievementSyncPlan = plan.copy(
            pendingAppIds = nextPending,
            retryAppIds = nextRetry,
            failedAppIds = failedAppIds,
            completedGames = (plan.completedGames + completedDelta)
                .coerceAtMost(plan.totalGames),
            updatedAt = nowMillis
        )
    )
}

internal fun SteamLibrarySnapshot.finishAchievementSyncPlan(
    requestId: String,
    nowMillis: Long
): SteamLibrarySnapshot {
    val plan = achievementSyncPlan?.takeIf { it.requestId == requestId } ?: return this
    if (plan.pendingAppIds.isNotEmpty() || plan.retryAppIds.isNotEmpty()) return this
    return copy(
        achievementProgressFullSyncAt = if (plan.isFullSync) {
            nowMillis
        } else {
            achievementProgressFullSyncAt
        },
        achievementSyncPlan = null
    )
}

internal fun SteamLibrarySnapshot.withAchievementCheckpoint(
    details: SteamGameAchievements
): SteamLibrarySnapshot {
    if (accountId != details.accountId) return this
    val total = details.achievements.size
    val unlocked = details.completed.size
    return copy(
        games = games.map { game ->
            if (game.appId != details.appId) {
                game
            } else {
                game.copy(
                    achievementUnlockedCount = unlocked,
                    achievementTotalCount = total,
                    allAchievementsUnlocked = total > 0 && unlocked >= total,
                    achievementProgressPlaytimeMinutes = game.playtimeForeverMinutes
                )
            }
        }
    )
}

internal const val ACHIEVEMENT_SYNC_BATCH_SIZE = 100
