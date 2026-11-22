package takagi.ru.monica.steam.space.data

import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.SteamProtoWriter

/** Official batch with a bounded public-store fallback; cache only confirmed names. */
internal class SteamSpaceAppNames(
    private val api: SteamApiClient = SteamApiClient(
        SteamHttpClientProvider.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
    ),
    private val publicClient: OkHttpClient = SteamHttpClientProvider.newBuilder()
        .callTimeout(3, TimeUnit.SECONDS).followRedirects(false).build(),
    private val monotonicNanos: () -> Long = System::nanoTime
) {
    private val names = object : LinkedHashMap<Pair<String, Int>, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, String>?) = size > 256
    }

    fun load(account: SteamAccount, appIds: List<Int>, language: String): Map<Int, String> {
        require(appIds.isNotEmpty() && appIds.size <= SteamSpaceService.MAX_FILES && appIds.all { it > 0 })
        val result = synchronized(names) { appIds.mapNotNull { id -> names[language to id]?.let { id to it } }.toMap().toMutableMap() }
        val missing = appIds.distinct().filterNot(result::containsKey)
        if (missing.isEmpty()) return result
        val deadline = monotonicNanos() + 16_000_000_000L
        val batch = optional {
            val response = api.callProtobuf(
                iface = "IStoreBrowseService", method = "GetItems", useGet = true,
                accessToken = requireNotNull(account.accessToken), request = request(missing, language)
            )
            SteamSpaceParser.appNames(response).filterKeys { it in missing }
        }.orEmpty()
        result.putAll(batch)
        // The store resolves IDs even when the account does not own the friend's game.
        // Never send a token or login cookie to this public metadata endpoint.
        missing.filterNot(result::containsKey).take(MAX_PUBLIC_LOOKUPS).forEach { id ->
            checkCancellation()
            if (monotonicNanos() >= deadline) return@forEach
            optional { publicName(id, language) }?.let { result[id] = it }
        }
        synchronized(names) { result.forEach { (id, name) -> names[language to id] = name } }
        return result
    }

    private fun publicName(appId: Int, language: String): String? {
        val url = "https://store.steampowered.com/api/appdetails".toHttpUrl().newBuilder()
            .addQueryParameter("appids", appId.toString()).addQueryParameter("l", language)
            .addQueryParameter("filters", "basic").build()
        publicClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_PUBLIC_BYTES) return null
            val source = body.source()
            if (source.request(MAX_PUBLIC_BYTES + 1L)) return null
            val bytes = source.readByteArray()
            return parsePublicName(appId, bytes.toString(Charsets.UTF_8))
        }
    }

    private inline fun <T> optional(block: () -> T): T? = try {
        checkCancellation()
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw CancellationException("Name lookup cancelled")
    } catch (failure: SteamApiException) {
        if (failure.authenticationRequired || failure.httpStatusCode in setOf(401, 403) ||
            failure.eResult in setOf(5, 15, 21, 27, 63, 65, 401, 403)) throw failure
        null
    } catch (_: Exception) {
        checkCancellation()
        null
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Name lookup cancelled")
    }

    companion object {
        internal const val MAX_PUBLIC_LOOKUPS = 8
        private const val MAX_PUBLIC_BYTES = 2 * 1024 * 1024
        internal fun parsePublicName(appId: Int, response: String): String? {
            val root = Json.parseToJsonElement(response) as? JsonObject ?: return null
            val item = root[appId.toString()] as? JsonObject ?: return null
            if ((item["success"] as? JsonPrimitive)?.booleanOrNull != true) return null
            val data = item["data"] as? JsonObject ?: return null
            if ((data["steam_appid"] as? JsonPrimitive)?.intOrNull != appId) return null
            return (data["name"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?.trim()?.takeIf { it.isNotBlank() && it.length <= 512 }
        }

        internal fun request(appIds: List<Int>, language: String) = SteamProtoWriter().apply {
            appIds.forEach { id -> writeMessage(1, SteamProtoWriter().apply { writeVarint(1, id.toLong()) }) }
            writeMessage(2, SteamProtoWriter().apply { writeString(1, language) })
            writeMessage(3, SteamProtoWriter().apply {
                writeBool(10, true) // Basic display data, without prices or media.
                writeBool(19, false)
            })
        }
    }
}
