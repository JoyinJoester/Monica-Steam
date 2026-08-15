package takagi.ru.monica.steam.library.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

internal class SteamAchievementSyncCoordinator private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val accountSourceRepository = SteamAccountSourceRepository.get(appContext)
    private val database = SteamDatabase.getDatabase(appContext)
    private val repository = SteamAchievementSyncRepository(
        cacheRepository = SteamLibraryCacheRepository(
            database.steamLibraryCacheDao(),
            SecurityManager(appContext)
        ),
        sessionManager = accountSourceRepository.sessionManager
    )
    private val _states = MutableStateFlow<Map<String, SteamAchievementSyncState>>(emptyMap())
    val states: StateFlow<Map<String, SteamAchievementSyncState>> = _states.asStateFlow()

    fun enqueue(handle: SteamAccountSessionHandle, forceFull: Boolean): Boolean {
        val key = handle.stableKey
        val current = _states.value[key]
        if (!forceFull && current?.active == true) {
            val workState = runCatching {
                workManager.getWorkInfosForUniqueWork(workName(handle))
                    .get()
                    .firstOrNull()
                    ?.state
            }.getOrNull()
            if (workState == WorkInfo.State.ENQUEUED ||
                workState == WorkInfo.State.BLOCKED ||
                workState == WorkInfo.State.RUNNING
            ) {
                return true
            }
        }
        publish(
            key,
            SteamAchievementSyncState(
                accountId = handle.account.id,
                steamId = handle.account.steamId,
                phase = SteamAchievementSyncPhase.QUEUED
            ),
            resetProgress = true
        )
        val requestId = UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<SteamAchievementSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                Data.Builder()
                    .putString(KEY_HANDLE, key)
                    .putLong(KEY_ACCOUNT_ID, handle.account.id)
                    .putString(KEY_STEAM_ID, handle.account.steamId)
                    .putBoolean(KEY_FORCE_FULL, forceFull)
                    .putString(KEY_REQUEST_ID, requestId)
                    .build()
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            workName(handle),
            if (forceFull) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
        return true
    }

    suspend fun syncGame(
        handle: SteamAccountSessionHandle,
        game: SteamGame,
        forceRefresh: Boolean = false
    ): SteamLibraryResult<SteamGameAchievements> {
        return repository.syncGame(handle, game, forceRefresh)
    }

    suspend fun refreshState(handle: SteamAccountSessionHandle) {
        val info = withContext(Dispatchers.IO) {
            runCatching {
                workManager.getWorkInfosForUniqueWork(workName(handle)).get().lastOrNull()
            }.getOrNull()
        } ?: return
        val data = if (info.state == WorkInfo.State.FAILED) info.outputData else info.progress
        val phase = when (info.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> SteamAchievementSyncPhase.QUEUED
            WorkInfo.State.RUNNING -> SteamAchievementSyncPhase.RUNNING
            WorkInfo.State.SUCCEEDED -> SteamAchievementSyncPhase.COMPLETED
            WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED -> SteamAchievementSyncPhase.FAILED
        }
        val persistedProgress = if (
            phase == SteamAchievementSyncPhase.QUEUED ||
            phase == SteamAchievementSyncPhase.RUNNING
        ) {
            repository.estimateLibraryProgress(handle)
        } else {
            null
        }
        val completedGames = data.getInt(KEY_COMPLETED, 0)
            .coerceAtLeast(persistedProgress?.first ?: 0)
        val totalGames = data.getInt(KEY_TOTAL, 0)
            .coerceAtLeast(persistedProgress?.second ?: 0)
        publish(
            handle.stableKey,
            SteamAchievementSyncState(
                accountId = handle.account.id,
                steamId = handle.account.steamId,
                phase = phase,
                completedGames = completedGames,
                totalGames = totalGames,
                currentAppId = data.getInt(KEY_CURRENT_APP_ID, 0).takeIf { it > 0 },
                failure = data.getString(KEY_FAILURE)?.let { raw ->
                    runCatching { SteamLibraryFailureReason.valueOf(raw) }.getOrNull()
                }
            )
        )
    }

    suspend fun runWorker(
        handle: SteamAccountSessionHandle,
        forceFull: Boolean,
        requestId: String,
        onProgress: suspend (SteamAchievementSyncState) -> Unit
    ): SteamAchievementSyncRunResult {
        return repository.syncLibrary(
            handle = handle,
            forceFull = forceFull,
            requestId = requestId,
            onProgress = { completed, total, currentAppId ->
                val state = SteamAchievementSyncState(
                    accountId = handle.account.id,
                    steamId = handle.account.steamId,
                    phase = SteamAchievementSyncPhase.RUNNING,
                    completedGames = completed,
                    totalGames = total,
                    currentAppId = currentAppId
                )
                publish(handle.stableKey, state)
                onProgress(state)
            }
        )
    }

    fun publishResult(
        handle: SteamAccountSessionHandle,
        result: SteamAchievementSyncRunResult
    ) {
        if (result is SteamAchievementSyncRunResult.Superseded) return
        val failure = when (result) {
            is SteamAchievementSyncRunResult.Completed -> null
            is SteamAchievementSyncRunResult.Superseded -> null
            is SteamAchievementSyncRunResult.PartialFailure -> result.failure
        }
        publish(
            handle.stableKey,
            SteamAchievementSyncState(
                accountId = handle.account.id,
                steamId = handle.account.steamId,
                phase = when (result) {
                    is SteamAchievementSyncRunResult.Completed ->
                        SteamAchievementSyncPhase.COMPLETED
                    is SteamAchievementSyncRunResult.Superseded ->
                        SteamAchievementSyncPhase.COMPLETED
                    is SteamAchievementSyncRunResult.PartialFailure ->
                        SteamAchievementSyncPhase.FAILED
                },
                completedGames = result.completedGames,
                totalGames = result.totalGames,
                failure = failure
            )
        )
    }

    private fun publish(
        key: String,
        state: SteamAchievementSyncState,
        resetProgress: Boolean = false
    ) {
        _states.update { current ->
            val previous = current[key]
            val merged = if (!resetProgress &&
                previous != null &&
                previous.active &&
                previous.accountId == state.accountId &&
                previous.steamId == state.steamId &&
                (state.totalGames == 0 || state.totalGames <= previous.totalGames)
            ) {
                state.copy(
                    completedGames = maxOf(previous.completedGames, state.completedGames),
                    totalGames = maxOf(previous.totalGames, state.totalGames),
                    currentAppId = state.currentAppId ?: previous.currentAppId
                )
            } else {
                state
            }
            current + (key to merged)
        }
    }

    companion object {
        internal const val KEY_HANDLE = "achievement_sync_handle"
        internal const val KEY_ACCOUNT_ID = "achievement_sync_account_id"
        internal const val KEY_STEAM_ID = "achievement_sync_steam_id"
        internal const val KEY_FORCE_FULL = "achievement_sync_force_full"
        internal const val KEY_REQUEST_ID = "achievement_sync_request_id"
        internal const val KEY_COMPLETED = "achievement_sync_completed"
        internal const val KEY_TOTAL = "achievement_sync_total"
        internal const val KEY_CURRENT_APP_ID = "achievement_sync_current_app_id"
        internal const val KEY_FAILURE = "achievement_sync_failure"
        private const val WORK_TAG = "steam_achievement_sync"

        private val instances = ConcurrentHashMap<Context, SteamAchievementSyncCoordinator>()

        fun get(context: Context): SteamAchievementSyncCoordinator {
            val appContext = context.applicationContext
            return instances.getOrPut(appContext) {
                SteamAchievementSyncCoordinator(appContext)
            }
        }

        internal fun workName(handle: SteamAccountSessionHandle): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(handle.stableKey.toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { byte -> "%02x".format(byte) }
            return "steam_achievement_sync_$digest"
        }
    }
}
