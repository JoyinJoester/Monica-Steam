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
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.SteamLibraryResult
import takagi.ru.monica.steam.library.steamLibraryFailureReason
import takagi.ru.monica.steam.session.data.SteamAccountSessionManager
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

internal class SteamAchievementSyncRepository(
    private val cacheRepository: SteamLibraryCacheRepository,
    private val sessionManager: SteamAccountSessionManager,
    private val service: SteamGameLibraryService = SteamGameLibraryService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<SteamLibraryResult<SteamGameAchievements>>>()

    suspend fun syncLibrary(
        handle: SteamAccountSessionHandle,
        forceFull: Boolean,
        onProgress: suspend (completed: Int, total: Int, currentAppId: Int?) -> Unit =
            { _, _, _ -> }
    ): SteamAchievementSyncRunResult {
        val snapshot = cacheRepository.getLibrary(handle.account.id)
            ?: return SteamAchievementSyncRunResult.PartialFailure(
                completedGames = 0,
                totalGames = 0,
                failure = SteamLibraryFailureReason.INVALID_RESPONSE
            )
        val selection = selectSteamAchievementSyncGames(snapshot, forceFull)
        val distinctGameCount = snapshot.games.distinctBy(SteamGame::appId).size
        val completedBeforeRun = if (selection.isFullSync && !forceFull) {
            (distinctGameCount - selection.games.size).coerceAtLeast(0)
        } else {
            0
        }
        val progressTotal = if (selection.isFullSync) {
            distinctGameCount
        } else {
            selection.games.size
        }
        val runResult = runSteamAchievementSync(
            games = selection.games,
            forceFull = forceFull,
            fetch = { game, refreshKnownEmpty ->
                fetchAchievementsSingleFlight(handle, game, refreshKnownEmpty).also { result ->
                    if (result is SteamLibraryResult.Failure) {
                        SteamDiagLogger.append(
                            "library_achievement_game failed app_id=${game.appId} " +
                                "reason=${result.reason.name}"
                        )
                    }
                }
            },
            checkpoint = { details -> saveCheckpoint(details) },
            onProgress = { completed, _, currentAppId ->
                onProgress(completedBeforeRun + completed, progressTotal, currentAppId)
            }
        )
        val result = runResult.withOverallProgress(completedBeforeRun, progressTotal)
        if (result is SteamAchievementSyncRunResult.Completed && selection.isFullSync) {
            cacheRepository.updateLibrary(handle.account.id) { current ->
                current?.withCompletedAchievementFullSync(System.currentTimeMillis())
            }
        }
        return result
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

internal fun SteamAchievementSyncRunResult.withOverallProgress(
    completedBeforeRun: Int,
    totalGames: Int
): SteamAchievementSyncRunResult = when (this) {
    is SteamAchievementSyncRunResult.Completed -> copy(
        completedGames = completedBeforeRun + completedGames,
        totalGames = totalGames
    )
    is SteamAchievementSyncRunResult.Retry -> copy(
        completedGames = completedBeforeRun + completedGames,
        totalGames = totalGames
    )
    is SteamAchievementSyncRunResult.PartialFailure -> copy(
        completedGames = completedBeforeRun + completedGames,
        totalGames = totalGames
    )
}
