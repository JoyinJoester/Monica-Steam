package takagi.ru.monica.steam.network.cm

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.data.SteamAccount

/**
 * CM gateway backed by an account-scoped persistent connection pool.
 *
 * The default constructor uses the process-wide pool so the chat, group-chat,
 * reaction, and rich-media services do not each create their own socket.
 */
class SteamCmClient internal constructor(
    private val pool: SteamCmConnectionPool,
    private val eventFlow: SharedFlow<SteamCmEvent>? = null,
    private val accountKeyResolver: (SteamAccount) -> String = ::steamCmAccountKey
) : SteamCmGateway {
    constructor() : this(SteamCmRuntime.pool, SteamCmRuntime.events)

    internal constructor(
        accountKeyResolver: (SteamAccount) -> String
    ) : this(
        pool = SteamCmRuntime.pool,
        eventFlow = SteamCmRuntime.events,
        accountKeyResolver = accountKeyResolver
    )

    internal constructor(
        eventFlow: SharedFlow<SteamCmEvent>,
        accountKeyResolver: (SteamAccount) -> String = ::steamCmAccountKey
    ) : this(
        pool = SteamCmRuntime.pool,
        eventFlow = eventFlow,
        accountKeyResolver = accountKeyResolver
    )

    internal constructor(
        bootstrap: SteamCmBootstrap,
        socketClient: OkHttpClient,
        timeoutMillis: Long,
        eventSink: (SteamCmEvent) -> Unit = {}
    ) : this(
        SteamCmConnectionPool(
            bootstrap = bootstrap,
            socketClient = socketClient,
            timeoutMillis = timeoutMillis,
            eventSink = eventSink
        )
    )

    /** Unsolicited CM envelopes from the shared process pool. */
    internal val events: SharedFlow<SteamCmEvent>
        get() = requireNotNull(eventFlow)

    /** Connects the shared account socket without sending a synthetic request. */
    internal fun connect(account: SteamAccount) = pool.connect(
        account = account,
        accountKey = accountKeyResolver(account)
    )

    internal fun isConnected(account: SteamAccount): Boolean = pool.isConnected(
        account = account,
        accountKey = accountKeyResolver(account)
    )

    internal fun disconnectsFor(account: SteamAccount): Flow<Unit> =
        pool.disconnectsFor(accountKeyResolver(account))

    internal fun latestEvent(account: SteamAccount, eMsg: Int): SteamCmEnvelope? =
        pool.latestEvent(accountKeyResolver(account), eMsg)

    /**
     * Drops the authenticated socket and its bootstrap snapshot for an account.
     * Realtime consumers use this when a collector stops or credentials rotate;
     * the next health check then performs a clean logon instead of continuing to
     * report a stale, half-alive connection.
     */
    internal fun reset(account: SteamAccount) = pool.closeAccount(
        account = account,
        accountKey = accountKeyResolver(account)
    )

    /** Hides the pool's routing key from feature modules. */
    internal fun eventsFor(account: SteamAccount): Flow<SteamCmEnvelope> = events
        .filter { it.accountKey == accountKeyResolver(account) }
        .map { event: SteamCmEvent -> event.envelope }

    override fun callService(
        account: SteamAccount,
        method: String,
        request: ByteArray
    ): ByteArray = pool.execute(
        account = account,
        accountKey = accountKeyResolver(account),
        operation = SteamCmOperation(
            requestEMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_CALL_FROM_CLIENT,
            responseEMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_RESPONSE,
            requestBody = request,
            targetJobName = method
        )
    )

    override fun exchangeClientMessage(
        account: SteamAccount,
        requestEMsg: Int,
        responseEMsg: Int,
        request: ByteArray
    ): ByteArray = pool.execute(
        account = account,
        accountKey = accountKeyResolver(account),
        operation = SteamCmOperation(
            requestEMsg = requestEMsg,
            responseEMsg = responseEMsg,
            requestBody = request
        )
    )

    override fun sendServiceNotification(
        account: SteamAccount,
        method: String,
        request: ByteArray
    ) = pool.sendNotification(
        account = account,
        accountKey = accountKeyResolver(account),
        operation = SteamCmOperation(
            requestEMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_CALL_FROM_CLIENT,
            responseEMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_RESPONSE,
            requestBody = request,
            targetJobName = method
        )
    )
}

/** Process-wide lifecycle boundary; app shutdown can close this pool explicitly. */
internal object SteamCmRuntime {
    private val eventBus = MutableSharedFlow<SteamCmEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val socketClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    val events: SharedFlow<SteamCmEvent> = eventBus.asSharedFlow()
    val pool: SteamCmConnectionPool = SteamCmConnectionPool(
        bootstrap = SteamCmBootstrap(),
        socketClient = socketClient,
        timeoutMillis = DEFAULT_TIMEOUT_MILLIS,
        eventSink = { eventBus.tryEmit(it) }
    )

    fun close() {
        pool.close()
        socketClient.dispatcher.executorService.shutdown()
        socketClient.connectionPool.evictAll()
    }

    private const val DEFAULT_TIMEOUT_MILLIS = 15_000L
}
