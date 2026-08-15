package takagi.ru.monica.steam.library.sync

import takagi.ru.monica.steam.library.SteamLibraryFailureReason

internal sealed interface SteamAchievementSyncRunResult {
    val completedGames: Int
    val totalGames: Int

    data class Completed(
        override val completedGames: Int,
        override val totalGames: Int
    ) : SteamAchievementSyncRunResult

    data class Superseded(
        override val completedGames: Int,
        override val totalGames: Int
    ) : SteamAchievementSyncRunResult

    data class PartialFailure(
        override val completedGames: Int,
        override val totalGames: Int,
        val failure: SteamLibraryFailureReason
    ) : SteamAchievementSyncRunResult
}
