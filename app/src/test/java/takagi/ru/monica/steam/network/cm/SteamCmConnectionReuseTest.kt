package takagi.ru.monica.steam.network.cm

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.friends.chat.presentation.isTransientSteamChatNetworkFailure
import takagi.ru.monica.steam.friends.groupchat.presentation.isTransientGroupChatCmFailure

class SteamCmConnectionReuseTest {
    @Test fun unknownResponseUsesExistingHistoryConfirmationInBothChats() {
        val error = SteamCmRequestOutcomeUnknownException("response lost", IOException("offline"))
        assertTrue(error.isTransientSteamChatNetworkFailure())
        assertTrue(error.isTransientGroupChatCmFailure())
    }
    @Test fun workingFallbackIsReusedByConnectRequestsAndNotifications() = fixture { f ->
        f.endpoints = listOf("bad.steamserver.net:443", "good.steamserver.net:443")
        f.pool.connect(f.account)
        f.pool.connect(f.account)
        assertArrayEquals(byteArrayOf(7), f.pool.execute(f.account, operation))
        f.pool.sendNotification(f.account, operation)
        assertEquals(listOf("bad.steamserver.net", "good.steamserver.net"), f.sockets.map { it.request().url.host })
        assertEquals(1, f.loads)
    }

    @Test fun healthySocketDoesNotRefreshBootstrapOnCacheExpiry() = fixture { f ->
        f.pool.connect(f.account)
        f.now = 120_001
        f.failBootstrap = true
        assertArrayEquals(byteArrayOf(7), f.pool.execute(f.account, operation))
        f.pool.sendNotification(f.account, operation)
        f.pool.connect(f.account)
        assertEquals(1, f.loads)
        assertEquals(1, f.sockets.size)
    }

    @Test fun changedCredentialsReplaceEvenAHealthySocket() = fixture { f ->
        f.pool.connect(f.account)
        f.pool.connect(f.account.copy(accessToken = "new-access"))
        assertEquals(2, f.loads)
        assertEquals(2, f.sockets.size)
        assertTrue(f.sockets.first().closed)
    }

    @Test fun changedCredentialsUseTheNewDirectoryInsteadOfThePreviousPreferredNode() = fixture { f ->
        f.pool.connect(f.account)
        f.endpoints = listOf("new.steamserver.net:443")
        val refreshed = f.account.copy(accessToken = "new-access")
        assertFalse(f.pool.isConnected(refreshed))
        f.pool.connect(refreshed)
        assertEquals("new.steamserver.net", f.sockets.last().request().url.host)
    }

    @Test fun explicitlyClosedAccountDoesNotReuseItsSession() = fixture { f ->
        f.pool.connect(f.account)
        f.pool.closeAccount(f.account)
        f.pool.connect(f.account)
        assertEquals(2, f.loads)
        assertEquals(2, f.sockets.size)
    }

    @Test fun differentStorageKeysNeverShareSockets() = fixture { f ->
        f.pool.connect(f.account, "storage-one")
        f.pool.connect(f.account, "storage-two")
        assertEquals(2, f.loads)
        assertEquals(2, f.sockets.size)
    }

    @Test fun disconnectAfterAcceptedRequestDoesNotReplayOnAnotherNode() = fixture { f ->
        f.endpoints = listOf("one.steamserver.net:443", "two.steamserver.net:443")
        f.disconnectRequest = true
        assertTrue(runCatching { f.pool.execute(f.account, operation) }.isFailure)
        assertEquals("The server may have applied the first request", 1, f.requests)
    }

    private fun fixture(test: (CmReuseFixture) -> Unit) = CmReuseFixture().use(test)
    private val operation = SteamCmOperation(10, 11, byteArrayOf(1), "Example#1")
}

internal class CmReuseFixture : java.io.Closeable {
    var now = 0L
    var loads = 0
    var requests = 0
    var failBootstrap = false
    var disconnectRequest = false
    var endpoints = listOf("good.steamserver.net:443")
    val sockets = mutableListOf<Socket>()
    val account = SteamAccount(1L, "76561198000000001", "test", "Test", "android:test", "secret",
        null, null, null, "access", "refresh", null, "{}", true, 0, 0L, 0L)
    private val client = OkHttpClient()
    val pool = SteamCmConnectionPool(
        bootstrap = SteamCmBootstrapLoader {
            loads++
            if (failBootstrap) throw IOException("bootstrap offline")
            SteamCmBootstrapData(it.steamId.toLong(), "token-$loads", endpoints)
        }, socketClient = client, timeoutMillis = 100, nowMillis = { now },
        socketFactory = { request, listener ->
            Socket(request, listener).also { socket ->
                sockets += socket
                listener.onOpen(socket, Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(101).message("Switching Protocols").build())
            }
        }
    )

    inner class Socket(private val request: Request, val listener: WebSocketListener) : WebSocket {
        var closed = false
        override fun request() = request
        override fun queueSize() = 0L
        override fun send(text: String) = false
        override fun send(bytes: ByteString): Boolean {
            val envelope = SteamCmProtocol.decodeMessages(bytes.toByteArray()).single()
            if (envelope.eMsg == SteamCmProtocol.EMSG_CLIENT_LOGON) {
                if (request.url.host.startsWith("bad.")) return false
                listener.onMessage(this, SteamCmProtocol.encodeMessage(
                    SteamCmProtocol.EMSG_CLIENT_LOGON_RESPONSE, account.steamId.toLong(), 42,
                    SteamProtoWriter().apply { writeVarint(1, 1) }.toByteArray()).toByteString())
            } else {
                requests++
                if (disconnectRequest) listener.onFailure(this, IOException("after send"), null)
                else listener.onMessage(this, SteamCmProtocol.encodeMessage(11, account.steamId.toLong(), 42,
                    byteArrayOf(7), jobIdTarget = envelope.header.jobIdSource).toByteString())
            }
            return true
        }
        fun fail() = listener.onFailure(this, IOException("idle disconnect"), null)
        override fun close(code: Int, reason: String?): Boolean { closed = true; return true }
        override fun cancel() { closed = true }
    }

    override fun close() {
        pool.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
