package takagi.ru.monica.steam.library.sync

import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult

internal sealed interface SteamAchievementSyncRunResult {
    val completedGames: Int
    val totalGames: Int

    data class Completed(
        override val completedGames: Int,
        override val totalGames: Int
    ) : SteamAchievementSyncRunResult

    data class Retry(
        override val completedGames: Int,
        override val totalGames: Int,
        val failure: SteamLibraryFailureReason
    ) : SteamAchievementSyncRunResult

    data class PartialFailure(
        override val completedGames: Int,
        override val totalGames: Int,
        val failure: SteamLibraryFailureReason
    ) : SteamAchievementSyncRunResult
}

internal suspend fun runSteamAchievementSync(
    games: List<SteamGame>,
    forceFull: Boolean,
    fetch: suspend (SteamGame, Boolean) -> SteamLibraryResult<SteamGameAchievements>,
    checkpoint: suspend (SteamGameAchievements) -> Unit,
    onProgress: suspend (completed: Int, total: Int, currentAppId: Int?) -> Unit = { _, _, _ -> }
): SteamAchievementSyncRunResult {
    var completed = 0
    var firstNonBlockingFailure: SteamLibraryFailureReason? = null
    var firstRetryableFailure: SteamLibraryFailureReason? = null
    var consecutiveNetworkFailures = 0
    val total = games.size
    onProgress(completed, total, null)

    games.forEach { game ->
        onProgress(completed, total, game.appId)
        when (val result = fetch(game, forceFull)) {
            is SteamLibraryResult.Success -> {
                checkpoint(result.value)
                completed++
                consecutiveNetworkFailures = 0
                onProgress(completed, total, game.appId)
            }
            is SteamLibraryResult.Failure -> when (result.reason) {
                SteamLibraryFailureReason.NETWORK -> {
                    if (firstRetryableFailure == null) {
                        firstRetryableFailure = result.reason
                    }
                    consecutiveNetworkFailures++
                    if (consecutiveNetworkFailures < MAX_CONSECUTIVE_NETWORK_FAILURES) {
                        return@forEach
                    }
                    return SteamAchievementSyncRunResult.Retry(
                        completedGames = completed,
                        totalGames = total,
                        failure = result.reason
                    )
                }
                SteamLibraryFailureReason.RATE_LIMITED,
                SteamLibraryFailureReason.SESSION_REQUIRED -> {
                    return SteamAchievementSyncRunResult.Retry(
                        completedGames = completed,
                        totalGames = total,
                        failure = result.reason
                    )
                }
                SteamLibraryFailureReason.PRIVATE_PROFILE -> {
                    return SteamAchievementSyncRunResult.PartialFailure(
                        completedGames = completed,
                        totalGames = total,
                        failure = result.reason
                    )
                }
                SteamLibraryFailureReason.INVALID_RESPONSE -> {
                    if (firstNonBlockingFailure == null) {
                        firstNonBlockingFailure = result.reason
                    }
                }
            }
        }
    }

    return firstRetryableFailure?.let { failure ->
        SteamAchievementSyncRunResult.Retry(
            completedGames = completed,
            totalGames = total,
            failure = failure
        )
    } ?: firstNonBlockingFailure?.let { failure ->
        SteamAchievementSyncRunResult.PartialFailure(
            completedGames = completed,
            totalGames = total,
            failure = failure
        )
    } ?: SteamAchievementSyncRunResult.Completed(
        completedGames = completed,
        totalGames = total
    )
}

private const val MAX_CONSECUTIVE_NETWORK_FAILURES = 3
