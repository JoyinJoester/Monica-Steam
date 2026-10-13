package takagi.ru.monica.steam.workshop.download

import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.*
import takagi.ru.monica.steam.workshop.*
import takagi.ru.monica.steam.data.SteamAccount

internal interface WorkshopContentSession : Closeable {
    suspend fun connect()
    suspend fun service(method: String, request: SteamProtoWriter): ByteArray
    suspend fun depotKey(appId: Int): ByteArray
}

internal class WorkshopAccountContentSession(private val account: SteamAccount,
    private val cm: SteamCmGateway = SteamCmClient()) : WorkshopContentSession {
    override suspend fun connect() = Unit
    override suspend fun service(method: String, request: SteamProtoWriter) = runInterruptible(Dispatchers.IO) {
        cm.callService(account, method, request.toByteArray())
    }
    override suspend fun depotKey(appId: Int) = runInterruptible(Dispatchers.IO) {
        cm.exchangeClientMessage(account, 5438, 5439, SteamProtoWriter().apply {
            writeVarint(1, appId.toLong()); writeVarint(2, appId.toLong())
        }.toByteArray())
    }
    override fun close() = Unit // The account socket remains owned by the shared pool.
}

/** Dedicated anonymous socket. It never enters the account/chat pool or reads credentials. */
internal class WorkshopAnonymousSession : WorkshopContentSession {
    private val client = SteamHttpClientProvider.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var connection: SteamCmPersistentConnection? = null

    override suspend fun connect() = runInterruptible(Dispatchers.IO) {
        val response = SteamApiClient().steamApiGetJson("/ISteamDirectory/GetCMListForConnect/v1/",
            mapOf("cellid" to "0", "cmtype" to "websockets"))
        val endpoints = response.obj("response").array("serverlist").map { it.jsonObject }
            .filter { it.text("type") == "websockets" && it.text("realm") == "steamglobal" }
            .map { it.text("endpoint") }.distinct().filter { endpoint ->
                val url = "https://$endpoint/".toHttpUrlOrNull()
                url != null && url.port == 443 && url.encodedPath == "/" &&
                    (url.host.endsWith(".steamserver.net") || url.host.endsWith(".steampowered.com"))
            }.take(3)
        check(endpoints.isNotEmpty()) { "No anonymous CM endpoint" }
        var failure: Exception? = null
        for (endpoint in endpoints) {
            val candidate = SteamCmPersistentConnection(client::newWebSocket, endpoint,
                (1L shl 56) or (10L shl 52), "", 15_000L, logonBody = ::anonymousLogon)
            try {
                candidate.connect()
                connection = candidate
                return@runInterruptible
            } catch (error: Exception) {
                candidate.close()
                if (Thread.currentThread().isInterrupted) throw error
                failure = error
            }
        }
        throw requireNotNull(failure)
    }

    override suspend fun service(method: String, request: SteamProtoWriter): ByteArray = runInterruptible(Dispatchers.IO) {
        requireNotNull(connection).execute(SteamCmOperation(151, 147, request.toByteArray(), method))
    }

    override suspend fun depotKey(appId: Int): ByteArray = runInterruptible(Dispatchers.IO) {
        requireNotNull(connection).execute(SteamCmOperation(5438, 5439, SteamProtoWriter().apply {
            writeVarint(1, appId.toLong()); writeVarint(2, appId.toLong())
        }.toByteArray()))
    }

    override fun close() {
        connection?.close()
        // The HTTP client shares Monica's connection pool; do not shut down shared executors.
    }

    companion object {
        fun anonymousLogon() = SteamProtoWriter().apply {
            writeVarint(1, 65580)
            writeVarint(7, -500L) // EOSType.AndroidUnknown; this is a native content session.
            writeString(6, "english")
            writeVarint(3, 0)
        }.toByteArray()
    }
}
