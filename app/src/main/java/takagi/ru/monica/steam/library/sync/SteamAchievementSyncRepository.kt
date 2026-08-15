package takagi.ru.monica.steam.library.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamLibraryCacheRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievements
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult
import takagi.ru.monica.steam.library.steamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

internal class SteamAchievementSyncRepository(
    private val cacheRepository: SteamLibraryCacheRepository,
    private val sessionManager: SteamAccountSessionManager,
    private val service: SteamGameLibraryService = SteamGameLibraryService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<SteamLibraryResult<SteamGameAchievements>>>()

    suspend fun estimateLibraryProgress(
        handle: SteamAccountSessionHandle
    ): Pair<Int, Int>? {
        val snapshot = cacheRepository.getLibrary(handle.account.id) ?: return null
        snapshot.achievementSyncPlan?.let { plan ->
            return plan.completedGames to plan.totalGames
        }
        val selection = selectSteamAchievementSyncGames(snapshot, forceFull = false)
        if (!selection.isFullSync) return null
        val total = snapshot.games.distinctBy(SteamGame::appId).size
        return (total - selection.games.size).coerceIn(0, total) to total
    }

    suspend fun syncLibrary(
        handle: SteamAccountSessionHandle,
        forceFull: Boolean,
        requestId: String,
        onProgress: suspend (completed: Int, total: Int, currentAppId: Int?) -> Unit =
            { _, _, _ -> }
    ): SteamAchievementSyncRunResult {
        var snapshot = cacheRepository.updateLibrary(handle.account.id) { current ->
            current?.prepareAchievementSyncPlan(
                requestId = requestId,
                forceFull = forceFull,
                nowMillis = nowMillis()
            )
        } ?: return SteamAchievementSyncRunResult.PartialFailure(
            completedGames = 0,
            totalGames = 0,
            failure = SteamLibraryFailureReason.INVALID_RESPONSE
        )
        var plan = snapshot.achievementSyncPlan
            ?: return SteamAchievementSyncRunResult.PartialFailure(
                completedGames = 0,
                totalGames = 0,
                failure = SteamLibraryFailureReason.INVALID_RESPONSE
            )
        val activeRequestId = plan.requestId
        onProgress(
            plan.completedGames,
            plan.totalGames,
            plan.nextAchievementSyncBatch()?.appIds?.firstOrNull()
        )

        while (true) {
            val batch = plan.nextAchievementSyncBatch() ?: break
            val outcome = fetchAchievementProgressBatch(handle, batch.appIds)
            if (outcome is SteamAchievementSyncBatchOutcome.Failure &&
                (outcome.reason == SteamLibraryFailureReason.SESSION_REQUIRED ||
                    outcome.reason == SteamLibraryFailureReason.PRIVATE_PROFILE)
            ) {
                return SteamAchievementSyncRunResult.PartialFailure(
                    completedGames = plan.completedGames,
                    totalGames = plan.totalGames,
                    failure = outcome.reason
                )
            }
            snapshot = cacheRepository.updateLibrary(handle.account.id) { current ->
                current?.applyAchievementSyncBatch(
                    requestId = activeRequestId,
                    batch = batch,
                    outcome = outcome,
                    nowMillis = nowMillis()
                )
            } ?: return SteamAchievementSyncRunResult.PartialFailure(
                completedGames = plan.completedGames,
                totalGames = plan.totalGames,
                failure = SteamLibraryFailureReason.INVALID_RESPONSE
            )
            val updatedPlan = snapshot.achievementSyncPlan
            if (updatedPlan == null || updatedPlan.requestId != activeRequestId) {
                return SteamAchievementSyncRunResult.Superseded(
                    completedGames = plan.completedGames,
                    totalGames = plan.totalGames
                )
            }
            plan = updatedPlan
            when (outcome) {
                is SteamAchievementSyncBatchOutcome.Success -> SteamDiagLogger.append(
                    "library_achievement_batch saved size=${batch.appIds.size} " +
                        "retry=${batch.isRetry} completed=${plan.completedGames} " +
                        "total=${plan.totalGames}"
                )
                is SteamAchievementSyncBatchOutcome.Failure -> SteamDiagLogger.append(
                    "library_achievement_batch failed size=${batch.appIds.size} " +
                        "retry=${batch.isRetry} reason=${outcome.reason.name}"
                )
            }
            onProgress(
                plan.completedGames,
                plan.totalGames,
                plan.nextAchievementSyncBatch()?.appIds?.firstOrNull()
            )
        }

        cacheRepository.updateLibrary(handle.account.id) { current ->
            current?.finishAchievementSyncPlan(
                requestId = activeRequestId,
                nowMillis = nowMillis()
            )
        }
        SteamDiagLogger.append(
            "library_achievement_sync finished total=${plan.totalGames} " +
                "skipped=${plan.failedAppIds.size}"
        )
        return SteamAchievementSyncRunResult.Completed(
            completedGames = plan.totalGames,
            totalGames = plan.totalGames
        )
    }

    suspend fun syncGame(
        handle: SteamAccountSessionHandle,
        game: SteamGame,
        forceRefresh: Boolean = false
    ): SteamLibraryResult<SteamGameAchievements> {
        return when (
            val result = fetchAchievementsSingleFlight(handle, game, forceRefresh)
        ) {
            is SteamLibraryResult.Success -> {
                saveCheckpoint(result.value)
                result
            }
            is SteamLibraryResult.Failure -> result
        }
    }

    private suspend fun fetchAchievementProgressBatch(
        handle: SteamAccountSessionHandle,
        appIds: List<Int>
    ): SteamAchievementSyncBatchOutcome {
        return try {
            val prepared = sessionManager.resolve(handle, forceRefresh = false).account
            val first = withContext(ioDispatcher) {
                service.fetchAchievementProgress(
                    account = prepared,
                    appIds = appIds,
                    language = "schinese"
                )
            }
            val result = if (first is SteamLibraryResult.Failure &&
                first.reason == SteamLibraryFailureReason.SESSION_REQUIRED
            ) {
                val refreshed = sessionManager.resolve(
                    handle.copy(account = prepared),
                    forceRefresh = true
                ).account
                if (refreshed.accessToken != prepared.accessToken) {
                    withContext(ioDispatcher) {
                        service.fetchAchievementProgress(
                            account = refreshed,
                            appIds = appIds,
                            language = "schinese"
                        )
                    }
                } else {
                    first
                }
            } else {
                first
            }
            when (result) {
                is SteamLibraryResult.Success -> {
                    val fetch = result.value
                    if (fetch.syncedAppIds.containsAll(appIds)) {
                        SteamAchievementSyncBatchOutcome.Success(fetch.progress)
                    } else {
                        SteamAchievementSyncBatchOutcome.Failure(
                            fetch.failure ?: SteamLibraryFailureReason.INVALID_RESPONSE
                        )
                    }
                }
                is SteamLibraryResult.Failure -> {
                    SteamAchievementSyncBatchOutcome.Failure(result.reason)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamAchievementSyncBatchOutcome.Failure(steamLibraryFailureReason(error))
        }
    }

    private suspend fun saveCheckpoint(details: SteamGameAchievements) {
        cacheRepository.saveAchievements(details)
        cacheRepository.updateLibrary(details.accountId) { current ->
            current?.withAchievementCheckpoint(details)
        }
        SteamDiagLogger.append(
            "library_achievement_game saved app_id=${details.appId} " +
                "unlocked=${details.completed.size} total=${details.achievements.size}"
        )
    }

    private suspend fun fetchAchievementsSingleFlight(
        handle: SteamAccountSessionHandle,
        game: SteamGame,
        forceRefresh: Boolean
    ): SteamLibraryResult<SteamGameAchievements> {
        val key = "${handle.stableKey}|${game.appId}|force=$forceRefresh"
        val decision = inFlightMutex.withLock {
            inFlight[key]?.let(FetchDecision::Wait) ?: FetchDecision.Own(
                CompletableDeferred<SteamLibraryResult<SteamGameAchievements>>().also {
                    inFlight[key] = it
                }
            )
        }
        return when (decision) {
            is FetchDecision.Wait -> decision.deferred.await()
            is FetchDecision.Own -> {
                try {
                    val result = fetchAchievementsWithSessionRetry(
                        handle = handle,
                        game = game,
                        forceRefresh = forceRefresh
                    )
                    decision.deferred.complete(result)
                    result
                } catch (cancelled: CancellationException) {
                    decision.deferred.completeExceptionally(cancelled)
                    throw cancelled
                } catch (error: Throwable) {
                    val result = SteamLibraryResult.Failure(steamLibraryFailureReason(error))
                    decision.deferred.complete(result)
                    result
                } finally {
                    inFlightMutex.withLock {
                        if (inFlight[key] === decision.deferred) {
                            inFlight.remove(key)
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchAchievementsWithSessionRetry(
        handle: SteamAccountSessionHandle,
        game: SteamGame,
        forceRefresh: Boolean
    ): SteamLibraryResult<SteamGameAchievements> {
        val prepared = sessionManager.resolve(handle, forceRefresh = false).account
        val first = withContext(ioDispatcher) {
            service.fetchAchievements(
                account = prepared,
                game = game,
                language = "schinese",
                forceRefresh = forceRefresh
            )
        }
        if (first !is SteamLibraryResult.Failure ||
            first.reason != SteamLibraryFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = sessionManager.resolve(
            handle.copy(account = prepared),
            forceRefresh = true
        ).account
        return if (refreshed.accessToken != prepared.accessToken) {
            withContext(ioDispatcher) {
                service.fetchAchievements(
                    account = refreshed,
                    game = game,
                    language = "schinese",
                    forceRefresh = forceRefresh
                )
            }
        } else {
            first
        }
    }

    private sealed interface FetchDecision {
        data class Wait(
            val deferred: CompletableDeferred<SteamLibraryResult<SteamGameAchievements>>
        ) : FetchDecision

        data class Own(
            val deferred: CompletableDeferred<SteamLibraryResult<SteamGameAchievements>>
        ) : FetchDecision
    }
}
