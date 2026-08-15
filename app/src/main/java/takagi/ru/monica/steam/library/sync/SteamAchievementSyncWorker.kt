package takagi.ru.monica.steam.library.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

class SteamAchievementSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val stableKey = inputData.getString(SteamAchievementSyncCoordinator.KEY_HANDLE)
            ?: return failure(SteamLibraryFailureReason.SESSION_REQUIRED, 0, 0)
        val accountId = inputData.getLong(
            SteamAchievementSyncCoordinator.KEY_ACCOUNT_ID,
            Long.MIN_VALUE
        )
        val steamId = inputData.getString(SteamAchievementSyncCoordinator.KEY_STEAM_ID)
            .orEmpty()
        val forceFull = inputData.getBoolean(
            SteamAchievementSyncCoordinator.KEY_FORCE_FULL,
            false
        )
        val requestId = inputData.getString(SteamAchievementSyncCoordinator.KEY_REQUEST_ID)
            ?: return failure(SteamLibraryFailureReason.INVALID_RESPONSE, 0, 0)
        val handle = findHandle(stableKey, accountId, steamId)
            ?: return failure(SteamLibraryFailureReason.SESSION_REQUIRED, 0, 0)
        val coordinator = SteamAchievementSyncCoordinator.get(applicationContext)
        return try {
            val result = coordinator.runWorker(handle, forceFull, requestId) { state ->
                setProgress(state.toWorkData())
            }
            coordinator.publishResult(handle, result)
            SteamDiagLogger.append(
                "library_achievement_worker result=${result::class.java.simpleName} " +
                    "completed=${result.completedGames} total=${result.totalGames}"
            )
            when (result) {
                is SteamAchievementSyncRunResult.Completed ->
                    Result.success(result.toWorkData())
                is SteamAchievementSyncRunResult.Superseded ->
                    Result.success(result.toWorkData())
                is SteamAchievementSyncRunResult.PartialFailure ->
                    Result.failure(result.toWorkData())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamDiagLogger.append(
                "library_achievement_worker exception type=${error::class.java.simpleName}"
            )
            failure(SteamLibraryFailureReason.INVALID_RESPONSE, 0, 0)
        }
    }

    private suspend fun findHandle(
        stableKey: String,
        accountId: Long,
        steamId: String
    ): SteamAccountSessionHandle? {
        return SteamAccountSourceRepository.get(applicationContext)
            .loadAllSessionHandles()
            .firstOrNull { handle ->
                handle.stableKey == stableKey &&
                    handle.account.id == accountId &&
                    handle.account.steamId == steamId
            }
    }

    private fun failure(
        reason: SteamLibraryFailureReason,
        completed: Int,
        total: Int
    ): Result = Result.failure(
        Data.Builder()
            .putInt(SteamAchievementSyncCoordinator.KEY_COMPLETED, completed)
            .putInt(SteamAchievementSyncCoordinator.KEY_TOTAL, total)
            .putString(SteamAchievementSyncCoordinator.KEY_FAILURE, reason.name)
            .build()
    )
}

private fun SteamAchievementSyncState.toWorkData(): Data = Data.Builder()
    .putInt(SteamAchievementSyncCoordinator.KEY_COMPLETED, completedGames)
    .putInt(SteamAchievementSyncCoordinator.KEY_TOTAL, totalGames)
    .putInt(SteamAchievementSyncCoordinator.KEY_CURRENT_APP_ID, currentAppId ?: 0)
    .putString(SteamAchievementSyncCoordinator.KEY_FAILURE, failure?.name)
    .build()

private fun SteamAchievementSyncRunResult.toWorkData(): Data {
    val failure = when (this) {
        is SteamAchievementSyncRunResult.Completed -> null
        is SteamAchievementSyncRunResult.Superseded -> null
        is SteamAchievementSyncRunResult.PartialFailure -> failure
    }
    return Data.Builder()
        .putInt(SteamAchievementSyncCoordinator.KEY_COMPLETED, completedGames)
        .putInt(SteamAchievementSyncCoordinator.KEY_TOTAL, totalGames)
        .putString(SteamAchievementSyncCoordinator.KEY_FAILURE, failure?.name)
        .build()
}
