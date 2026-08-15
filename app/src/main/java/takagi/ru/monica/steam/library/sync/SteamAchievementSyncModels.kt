package takagi.ru.monica.steam.library.sync

import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibrarySnapshot
import takagi.ru.monica.steam.library.needsAchievementProgressSync
import takagi.ru.monica.steam.library.planSteamAchievementProgressSync

internal data class SteamAchievementSyncSelection(
    val games: List<SteamGame>,
    val isFullSync: Boolean
)

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

internal fun SteamLibrarySnapshot.withCompletedAchievementFullSync(
    completedAt: Long
): SteamLibrarySnapshot {
    return if (games.none(SteamGame::needsAchievementProgressSync)) {
        copy(achievementProgressFullSyncAt = completedAt)
    } else {
        this
    }
}
