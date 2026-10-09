package takagi.ru.monica.steam.session.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamSessionRefreshService
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionRefresher
import takagi.ru.monica.steam.session.domain.SteamAccountSessionStore
import takagi.ru.monica.steam.session.domain.SteamSessionResolution
import takagi.ru.monica.steam.session.domain.SteamSessionTokens
import takagi.ru.monica.steam.session.SteamTemporaryAccounts

/**
 * Account-scoped session coordinator.
 *
 * Every storage origin gets its own key. Concurrent callers share one refresh
 * result, and a successful rotation is persisted through the handle that
 * started the refresh rather than whichever source happens to be selected in
 * the UI when the network call finishes.
 */
class SteamAccountSessionManager(
    private val refresher: SteamAccountSessionRefresher =
        SteamSessionRefreshServiceRefresher(),
    private val store: SteamAccountSessionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val temporaryAccounts: SteamTemporaryAccounts = SteamTemporaryAccounts.shared
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<SteamSessionResolution>>>()
    private val latestAccounts = mutableMapOf<String, SteamAccount>()

    suspend fun resolve(
        handle: SteamAccountSessionHandle,
        forceRefresh: Boolean = false
    ): SteamSessionResolution {
        val key = handle.stableKey
        val decision = mutex.withLock {
            val known = latestAccounts[key]
            val candidate = if (handle.account.isTemporary) activeTemporaryAccount(handle.account) else known ?: handle.account
            if (!forceRefresh && !refresher.shouldRefresh(candidate, nowSeconds())) {
                FlightDecision.Immediate(
                    SteamSessionResolution(
                        account = candidate,
                        refreshed = false,
                        refreshAttempted = false
                    )
                )
            } else {
                val existing = inFlight[key]
                if (existing != null) {
                    FlightDecision.Wait(existing)
                } else {
                    FlightDecision.Own(
                        CompletableDeferred<Result<SteamSessionResolution>>().also {
                            inFlight[key] = it
                        },
                        candidate
                    )
                }
            }
        }

        val resolution = when (decision) {
            is FlightDecision.Immediate -> decision.resolution
            is FlightDecision.Wait -> decision.deferred.await().getOrThrow()
            is FlightDecision.Own -> {
                val ownerHandle = handle.copy(account = decision.account)
                try {
                    val result = runCatching { refresh(ownerHandle, forceRefresh) }
                    decision.deferred.complete(result)
                    result.getOrThrow()
                } catch (cancelled: CancellationException) {
                    decision.deferred.completeExceptionally(cancelled)
                    throw cancelled
                } finally {
                    mutex.withLock {
                        if (inFlight[key] === decision.deferred) {
                            inFlight.remove(key)
                        }
                    }
                }
            }
        }
        if (handle.account.isTemporary) activeTemporaryAccount(handle.account)
        return resolution
    }

    suspend fun clear(handle: SteamAccountSessionHandle) {
        mutex.withLock {
            latestAccounts.remove(handle.stableKey)
        }
    }

    private suspend fun refresh(
        handle: SteamAccountSessionHandle,
        forceRefresh: Boolean
    ): SteamSessionResolution {
        val account = handle.account
        if (!forceRefresh && !refresher.shouldRefresh(account, nowSeconds())) {
            return SteamSessionResolution(account, refreshed = false, refreshAttempted = false)
        }

        val tokens = withContext(ioDispatcher) {
            refresher.refresh(account, forceRefresh)
        } ?: return SteamSessionResolution(
            account = account,
            refreshed = false,
            refreshAttempted = true
        )

        val refreshed = account.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: account.refreshToken,
            steamLoginSecure = "${account.steamId}||${tokens.accessToken}"
        )
        if (account.isTemporary) activeTemporaryAccount(account)
        if (hasSessionChanged(account, refreshed)) {
            store.persist(handle.copy(account = refreshed))
        }
        if (!account.isTemporary) mutex.withLock { latestAccounts[handle.stableKey] = refreshed }
        return SteamSessionResolution(
            account = refreshed,
            refreshed = hasSessionChanged(account, refreshed),
            refreshAttempted = true
        )
    }

    private fun hasSessionChanged(previous: SteamAccount, current: SteamAccount): Boolean {
        return previous.accessToken != current.accessToken ||
            previous.refreshToken != current.refreshToken ||
            previous.steamLoginSecure != current.steamLoginSecure
    }

    private fun activeTemporaryAccount(account: SteamAccount): SteamAccount =
        checkNotNull(temporaryAccounts.get(account.id)) { "Temporary Steam session has ended" }

    private sealed interface FlightDecision {
        data class Immediate(val resolution: SteamSessionResolution) : FlightDecision
        data class Wait(
            val deferred: CompletableDeferred<Result<SteamSessionResolution>>
        ) : FlightDecision
        data class Own(
            val deferred: CompletableDeferred<Result<SteamSessionResolution>>,
            val account: SteamAccount
        ) : FlightDecision
    }
}

private class SteamSessionRefreshServiceRefresher(
    private val service: SteamSessionRefreshService = SteamSessionRefreshService()
) : SteamAccountSessionRefresher {
    override fun shouldRefresh(account: SteamAccount, nowSeconds: Long): Boolean =
        service.shouldRefresh(account, nowSeconds)

    override suspend fun refresh(
        account: SteamAccount,
        force: Boolean
    ): SteamSessionTokens? {
        val result = if (force) {
            val refreshToken = account.refreshToken?.takeIf(String::isNotBlank) ?: return null
            service.refresh(account.steamId, refreshToken)
        } else {
            service.refreshIfNeeded(account)
        } ?: return null
        return SteamSessionTokens(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken
        )
    }
}
