package takagi.ru.monica.steam.network.cm

import java.io.Closeable
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader

internal data class SteamCmOperation(
    val requestEMsg: Int,
    val responseEMsg: Int,
    val requestBody: ByteArray,
    val targetJobName: String? = null
)

internal class SteamCmResponseTimeoutException(message: String) : SocketTimeoutException(message)

/**
 * One logged-on CM WebSocket. Requests stay on the socket until their response
 * job arrives; unmatched envelopes are delivered as realtime events instead of
 * being discarded or mistaken for another request.
 */
internal class SteamCmPersistentConnection(
    private val socketFactory: (Request, WebSocketListener) -> WebSocket,
    private val endpoint: String,
    private val steamId: Long,
    private val webLogonToken: String,
    private val timeoutMillis: Long,
    private val eventSink: (SteamCmEnvelope) -> Unit = {},
    private val logonBody: () -> ByteArray = { SteamCmProtocol.webLogonBody(webLogonToken) }
) : WebSocketListener(), Closeable {
    private val stateLock = Any()
    private val noJobLock = ReentrantLock()
    private val nextJobId = AtomicLong(1L)
    private val pending = LinkedBlockingDeque<PendingRequest>()

    private var activeSocket: WebSocket? = null
    private var connectWaiter: CompletableFuture<Unit>? = null
    private var sessionId: Int = 0
    private var sessionSteamId: Long = steamId
    private var loggedOn = false
    private var closed = false

    fun isHealthy(): Boolean = synchronized(stateLock) {
        !closed && loggedOn && activeSocket != null
    }

    /**
     * A connection remains shareable while its first caller is still opening
     * or logging on. Closing it merely because `isHealthy()` is not true yet
     * makes concurrent realtime and service callers tear each other down.
     */
    fun canBeReused(): Boolean = synchronized(stateLock) { !closed }

    /** Opens and authenticates the socket without requiring a request first. */
    fun connect() {
        ensureConnected()
    }

    fun execute(operation: SteamCmOperation): ByteArray {
        val serializesNoJobRequest = operation.targetJobName == null
        if (serializesNoJobRequest) noJobLock.lock()
        return try {
            executeCorrelated(operation)
        } finally {
            if (serializesNoJobRequest) noJobLock.unlock()
        }
    }

    /** Sends a target service notification without registering a response job. */
    fun send(operation: SteamCmOperation) {
        ensureConnected()
        val socket = synchronized(stateLock) {
            check(!closed) { "Steam CM connection is closed" }
            check(loggedOn && activeSocket != null) { "Steam CM is not logged on" }
            activeSocket
        }
        val encoded = SteamCmProtocol.encodeMessage(
            eMsg = operation.requestEMsg,
            steamId = sessionSteamId,
            sessionId = sessionId,
            body = operation.requestBody,
            jobIdSource = SteamCmProtocol.JOB_ID_NONE,
            targetJobName = operation.targetJobName
        )
        if (socket?.send(encoded.toByteStringNoSpread()) != true) {
            throw IOException("Steam CM notification send failed")
        }
    }

    /** Marks the socket unusable while allowing the next request to reconnect. */
    fun invalidate(cause: Throwable = IOException("Steam CM connection invalidated")) {
        val socket = synchronized(stateLock) { activeSocket }
        failConnection(socket, cause)
    }

    override fun close() {
        val socket: WebSocket?
        val waiters: List<PendingRequest>
        val waiter: CompletableFuture<Unit>?
        synchronized(stateLock) {
            if (closed) return
            closed = true
            socket = activeSocket
            activeSocket = null
            loggedOn = false
            sessionId = 0
            waiters = pending.toList()
            pending.clear()
            waiter = connectWaiter
            connectWaiter = null
        }
        val error = IOException("Steam CM connection closed")
        waiters.forEach { it.future.completeExceptionally(error) }
        waiter?.completeExceptionally(error)
        socket?.close(1000, "client shutdown")
    }

    private fun executeCorrelated(operation: SteamCmOperation): ByteArray {
        ensureConnected()
        val jobId = if (operation.targetJobName == null) {
            SteamCmProtocol.JOB_ID_NONE
        } else {
            nextJobId.getAndIncrement().takeIf { it > 0L } ?: run {
                nextJobId.set(2L)
                1L
            }
        }
        val request = PendingRequest(
            responseEMsg = operation.responseEMsg,
            jobId = jobId
        )
        val socket = synchronized(stateLock) {
            check(!closed) { "Steam CM connection is closed" }
            check(loggedOn && activeSocket != null) { "Steam CM is not logged on" }
            pending += request
            activeSocket
        }
        val encoded = SteamCmProtocol.encodeMessage(
            eMsg = operation.requestEMsg,
            steamId = sessionSteamId,
            sessionId = sessionId,
            body = operation.requestBody,
            jobIdSource = jobId,
            targetJobName = operation.targetJobName
        )
        if (socket?.send(encoded.toByteStringNoSpread()) != true) {
            pending.remove(request)
            throw IOException("Steam CM operation send failed")
        }
        return await(request)
    }

    private fun await(request: PendingRequest): ByteArray {
        return try {
            request.future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            pending.remove(request)
            throw SteamCmResponseTimeoutException("Steam CM operation timed out").also {
                it.initCause(error)
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            pending.remove(request)
            throw IOException("Interrupted while waiting for Steam CM", error)
        } catch (error: ExecutionException) {
            pending.remove(request)
            val cause = error.cause
            // Preserve Steam's status so callers can refresh expired sessions and
            // distinguish permission/rate-limit failures from transport failures.
            if (cause is SteamApiException) throw cause
            if (cause is RuntimeException) throw cause
            throw IOException("Steam CM operation failed", cause)
        }
    }

    private fun ensureConnected() {
        val waiter: CompletableFuture<Unit>
        var shouldOpen = false
        synchronized(stateLock) {
            check(!closed) { "Steam CM connection is closed" }
            if (loggedOn && activeSocket != null) return
            waiter = connectWaiter ?: CompletableFuture<Unit>().also {
                connectWaiter = it
                shouldOpen = true
            }
        }
        if (shouldOpen) {
            try {
                val request = Request.Builder()
                    .url("wss://$endpoint/cmsocket/")
                    .header("Origin", "https://steamcommunity.com")
                    .header("User-Agent", "Mozilla/5.0 Monica-Steam/1.0")
                    .build()
                val socket = socketFactory(request, this)
                synchronized(stateLock) {
                    if (activeSocket == null && !closed) activeSocket = socket
                }
            } catch (error: Throwable) {
                failConnection(null, error)
            }
        }
        try {
            waiter.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            failConnection(null, SocketTimeoutException("Steam CM logon timed out"))
            throw SocketTimeoutException("Steam CM logon timed out").also { it.initCause(error) }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while logging on to Steam CM", error)
        } catch (error: ExecutionException) {
            val cause = error.cause
            if (cause is SteamApiException) throw cause
            if (cause is RuntimeException) throw cause
            throw IOException("Steam CM logon failed", cause)
        } finally {
            synchronized(stateLock) {
                if (connectWaiter === waiter && waiter.isDone) connectWaiter = null
            }
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        synchronized(stateLock) {
            if (closed) {
                webSocket.cancel()
                return
            }
            activeSocket = webSocket
        }
        runCatching {
            val login = SteamCmProtocol.encodeMessage(
                eMsg = SteamCmProtocol.EMSG_CLIENT_LOGON,
                steamId = steamId,
                sessionId = 0,
                body = logonBody()
            )
            check(webSocket.send(login.toByteStringNoSpread())) { "Steam CM logon send failed" }
        }.onFailure { failConnection(webSocket, it) }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        runCatching {
            SteamCmProtocol.decodeMessages(bytes.toByteArray())
                .forEach { handleEnvelope(webSocket, it) }
        }.onFailure { failConnection(webSocket, it) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        failConnection(webSocket, IOException("Steam CM returned an unexpected text message"))
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        failConnection(webSocket, t)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        val shouldFail = synchronized(stateLock) { !closed && activeSocket === webSocket }
        if (shouldFail) failConnection(webSocket, IOException("Steam CM closed ($code): $reason"))
    }

    private fun handleEnvelope(webSocket: WebSocket, envelope: SteamCmEnvelope) {
        val isLoggedOn = synchronized(stateLock) { loggedOn && activeSocket === webSocket }
        if (!isLoggedOn) {
            if (envelope.eMsg != SteamCmProtocol.EMSG_CLIENT_LOGON_RESPONSE) return
            val eResult = SteamProtoReader(envelope.body).parse()[1]?.asInt ?: 2
            if (eResult != 1) {
                failConnection(
                    webSocket,
                    SteamApiException(
                        message = "Steam CM logon failed (eresult=$eResult)",
                        eResult = eResult
                    )
                )
                return
            }
            val newSessionId = envelope.header.sessionId
            if (newSessionId == 0) {
                failConnection(webSocket, IOException("Steam CM logon returned no session ID"))
                return
            }
            val waiter = synchronized(stateLock) {
                if (activeSocket !== webSocket || closed) return
                sessionId = newSessionId
                sessionSteamId = envelope.header.steamId.takeIf { it > 0L } ?: steamId
                loggedOn = true
                connectWaiter
            }
            waiter?.complete(Unit)
            return
        }

        if (envelope.eMsg == SteamCmProtocol.EMSG_CLIENT_LOGGED_OFF) {
            val eResult = SteamProtoReader(envelope.body).parse()[1]?.asInt ?: 2
            failConnection(
                webSocket,
                SteamApiException(
                    message = "Steam CM logged off (eresult=$eResult)",
                    eResult = eResult
                )
            )
            return
        }

        val response = synchronized(stateLock) {
            pending.firstOrNull { it.matches(envelope) }?.also { pending.remove(it) }
        }
        if (response == null) {
            runCatching { eventSink(envelope) }
            return
        }
        envelope.header.transportError
            ?.takeIf { it != 1 }
            ?.let { error ->
                response.future.completeExceptionally(
                    SteamApiException(
                        message = envelope.header.errorMessage
                            ?: "Steam CM transport failed ($error)",
                        eResult = error
                    )
                )
                return
            }
        envelope.header.eResult
            ?.takeIf { it != 1 }
            ?.let { eResult ->
                response.future.completeExceptionally(
                    SteamApiException(
                        message = envelope.header.errorMessage
                            ?: "Steam CM service failed (eresult=$eResult)",
                        eResult = eResult
                    )
                )
                return
            }
        response.future.complete(envelope.body)
    }

    private fun failConnection(webSocket: WebSocket?, error: Throwable) {
        val waiters: List<PendingRequest>
        val connect: CompletableFuture<Unit>?
        val socket: WebSocket?
        synchronized(stateLock) {
            if (webSocket != null && activeSocket !== webSocket) return
            socket = activeSocket
            activeSocket = null
            loggedOn = false
            sessionId = 0
            waiters = pending.toList()
            pending.clear()
            connect = connectWaiter
            connectWaiter = null
        }
        waiters.forEach { it.future.completeExceptionally(error) }
        connect?.completeExceptionally(error)
        socket?.cancel()
    }

    private data class PendingRequest(
        val responseEMsg: Int,
        val jobId: Long,
        val future: CompletableFuture<ByteArray> = CompletableFuture()
    ) {
        fun matches(envelope: SteamCmEnvelope): Boolean =
            envelope.eMsg == responseEMsg &&
                (jobId == SteamCmProtocol.JOB_ID_NONE || envelope.header.jobIdTarget == jobId)
    }

    private fun ByteArray.toByteStringNoSpread(): ByteString = toByteString()
}
