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
    private val inFlight = mutableMapOf<String, FlightDecision.Own>()
    private val latestAccounts = mutableMapOf<String, SteamAccount>()

    suspend fun resolve(
        handle: SteamAccountSessionHandle,
        forceRefresh: Boolean = false
    ): SteamSessionResolution {
        val key = handle.stableKey
        // Login writes to Room/MDBX independently of this manager. Never let a cached
        // refresh result or an old screen handle override that authoritative session.
        val stored = loadCurrent(handle)
        val decision = mutex.withLock {
            val known = latestAccounts[key]
            val candidate = stored ?: known ?: handle.account
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
                if (existing != null && !hasSessionChanged(existing.account, candidate)) {
                    FlightDecision.Wait(existing.deferred)
                } else {
                    FlightDecision.Own(
                        CompletableDeferred<Result<SteamSessionResolution>>(),
                        candidate
                    ).also { inFlight[key] = it }
                }
            }
        }

        val resolution = when (decision) {
            is FlightDecision.Immediate -> return decision.resolution
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
                        if (inFlight[key] === decision) {
                            inFlight.remove(key)
                        }
                    }
                }
            }
        }
        // A login can also finish while this caller is waiting for another refresh.
        val current = loadCurrent(handle)
        return if (current != null) resolution.copy(account = current) else resolution
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

        val tokens = try {
            withContext(ioDispatcher) { refresher.refresh(account, forceRefresh) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val current = loadCurrent(handle)
            if (current != null && hasSessionChanged(account, current)) {
                return SteamSessionResolution(current, refreshed = false, refreshAttempted = true)
            }
            throw error
        }
        val current = loadCurrent(handle)
        if (current != null && hasSessionChanged(account, current)) {
            // Discard a rotation started before a successful re-login.
            return SteamSessionResolution(current, refreshed = false, refreshAttempted = true)
        }
        if (tokens == null) return SteamSessionResolution(
            account = current ?: account,
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

    private suspend fun loadCurrent(handle: SteamAccountSessionHandle): SteamAccount? {
        val current = if (handle.account.isTemporary) activeTemporaryAccount(handle.account)
            else withContext(ioDispatcher) { store.load(handle) }
        check(current == null || (current.id == handle.account.id && current.steamId == handle.account.steamId)) {
            "Steam session storage identity changed"
        }
        return current
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
