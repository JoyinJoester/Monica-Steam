package takagi.ru.monica.steam.network.cm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.selects.select
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

/** Testable boundary around the shared account CM socket. */
internal interface SteamCmRealtimeTransport {
    fun events(account: SteamAccount): Flow<SteamCmEnvelope>
    fun disconnects(account: SteamAccount): Flow<Unit> = emptyFlow()
    fun connect(account: SteamAccount)
    fun isConnected(account: SteamAccount): Boolean
    fun reset(account: SteamAccount) = Unit
}

internal class SteamCmClientRealtimeTransport(
    private val cm: SteamCmClient
) : SteamCmRealtimeTransport {
    override fun events(account: SteamAccount): Flow<SteamCmEnvelope> = cm.eventsFor(account)
    override fun disconnects(account: SteamAccount): Flow<Unit> = cm.disconnectsFor(account)
    override fun connect(account: SteamAccount) = cm.connect(account)
    override fun isConnected(account: SteamAccount): Boolean = cm.isConnected(account)
    override fun reset(account: SteamAccount) = cm.reset(account)
}

/**
 * One lifecycle implementation shared by friend chat, group chat and voice.
 *
 * It keeps the event collector and authenticated socket in the same account
 * generation, reconnects after collector termination, rotates the socket when
 * session credentials change, preserves structured cancellation, and isolates
 * malformed unsolicited envelopes from the rest of the realtime stream.
 */
internal fun <Event> supervisedSteamCmEvents(
    account: SteamAccount,
    transport: SteamCmRealtimeTransport,
    ioDispatcher: CoroutineDispatcher,
    healthyCheckMillis: Long,
    initialRetryMillis: Long,
    maximumRetryMillis: Long,
    sessionResolver: SteamAccountSessionResolver?,
    diagnosticScope: String,
    parse: (SteamCmEnvelope, SteamAccount) -> Event?,
    connectionChanged: (Boolean) -> Event
): Flow<Event> = channelFlow {
    var sessionAccount: SteamAccount? = null
    val collectorFailure = Channel<Unit>(Channel.CONFLATED)
    val disconnected = Channel<Unit>(Channel.CONFLATED)

    fun launchCollector(current: SteamAccount): Job = launch(start = CoroutineStart.UNDISPATCHED) {
        val disconnectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.disconnects(current).collect { disconnected.trySend(Unit) }
        }
        try {
            transport.events(current).collect { envelope ->
                try {
                    parse(envelope, current)?.let { send(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    logSteamCmRealtimeFailure(diagnosticScope, "parse", error)
                }
            }
            if (isActive) collectorFailure.trySend(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logSteamCmRealtimeFailure(diagnosticScope, "collector", error)
            collectorFailure.trySend(Unit)
        } finally {
            disconnectCollector.cancelAndJoin()
        }
    }

    suspend fun restartCollector(current: SteamAccount, previous: Job): Job {
        previous.cancelAndJoin()
        withContext(ioDispatcher) { transport.reset(current) }
        return launchCollector(current)
    }

    var eventCollector: Job? = null
    var retryMillis = initialRetryMillis.coerceAtLeast(1L)
    var announcedConnected = false
    try {
        while (isActive) {
            val resolved = try {
                sessionResolver.resolveOrKeep(account)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logSteamCmRealtimeFailure(diagnosticScope, "session", error)
                if (announcedConnected) {
                    announcedConnected = false
                    send(connectionChanged(false))
                }
                delay(retryMillis)
                retryMillis = (retryMillis * 2L).coerceAtMost(maximumRetryMillis)
                continue
            }

            val previousSession = sessionAccount
            if (previousSession == null) {
                sessionAccount = resolved
                eventCollector = launchCollector(resolved)
            } else if (steamCmSessionChanged(previousSession, resolved)) {
                sessionAccount = resolved
                eventCollector = restartCollector(resolved, requireNotNull(eventCollector))
                if (announcedConnected) {
                    announcedConnected = false
                    send(connectionChanged(false))
                }
            } else {
                sessionAccount = resolved
            }

            if (collectorFailure.tryReceive().isSuccess) {
                val current = requireNotNull(sessionAccount)
                eventCollector = restartCollector(current, requireNotNull(eventCollector))
                if (announcedConnected) {
                    announcedConnected = false
                    send(connectionChanged(false))
                }
            }

            if (disconnected.tryReceive().isSuccess && announcedConnected) {
                announcedConnected = false
                send(connectionChanged(false))
            }
            val connected = try {
                val current = requireNotNull(sessionAccount)
                withContext(ioDispatcher) {
                    if (!transport.isConnected(current)) transport.connect(current)
                    transport.isConnected(current)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (connected != announcedConnected) {
                announcedConnected = connected
                send(connectionChanged(connected))
            }
            if (connected) {
                retryMillis = initialRetryMillis.coerceAtLeast(1L)
                withTimeoutOrNull(healthyCheckMillis.coerceAtLeast(1L)) {
                    select<Boolean> {
                        collectorFailure.onReceive { true }
                        disconnected.onReceive { false }
                    }
                }?.let { restart ->
                    // A socket failure only wakes the health loop. Resetting here
                    // could tear down a replacement already opened by another feature.
                    if (restart) {
                        val current = requireNotNull(sessionAccount)
                        eventCollector = restartCollector(current, requireNotNull(eventCollector))
                    }
                    if (announcedConnected) {
                        announcedConnected = false
                        send(connectionChanged(false))
                    }
                }
            } else {
                delay(retryMillis)
                retryMillis = (retryMillis * 2L).coerceAtMost(maximumRetryMillis)
            }
        }
    } finally {
        eventCollector?.cancelAndJoin()
        collectorFailure.close()
        disconnected.close()
    }
}

private fun steamCmSessionChanged(previous: SteamAccount, current: SteamAccount): Boolean =
    previous.id != current.id ||
        previous.steamId != current.steamId ||
        previous.accessToken != current.accessToken ||
        previous.refreshToken != current.refreshToken ||
        previous.steamLoginSecure != current.steamLoginSecure

private fun logSteamCmRealtimeFailure(scope: String, operation: String, error: Throwable) {
    runCatching {
        SteamDiagLogger.append(
            "${scope}_realtime_$operation failed type=${error::class.java.simpleName}"
        )
    }
}
