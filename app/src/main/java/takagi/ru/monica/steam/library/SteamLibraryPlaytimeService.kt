package takagi.ru.monica.steam.library

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

internal data class SteamGamePlaytime(
    val recentMinutes: Int,
    val totalMinutes: Int?,
    val lastPlayedAt: Long?
)

internal data class SteamLibraryPlaytimeFetch(
    val items: Map<Int, SteamGamePlaytime> = emptyMap(),
    val unavailableAppIds: Set<Int> = emptySet()
)

/** Reads the selected player's activity, independently of who owns the game license. */
internal class SteamLibraryPlaytimeService(private val api: SteamApiClient) {
    fun fetch(account: SteamAccount, sharedAppIds: Set<Int>): SteamLibraryPlaytimeFetch {
        if (sharedAppIds.isEmpty()) return SteamLibraryPlaytimeFetch()
        val token = account.accessToken?.takeIf(String::isNotBlank)
        if (token == null || !account.hasRealSteamId) {
            return SteamLibraryPlaytimeFetch(unavailableAppIds = sharedAppIds)
        }
        val clientActivity = runCatching {
            parseClientActivity(api.callProtobuf(
                iface = "IPlayerService",
                method = "ClientGetLastPlayedTimes",
                request = SteamProtoWriter().apply { writeVarint(1, 0L) },
                accessToken = token,
                useGet = true
            ))
        }.onFailure { logFailure("client", it) }.getOrDefault(emptyMap())
            .filterKeys { it in sharedAppIds }
        val missingAppIds = sharedAppIds - clientActivity.keys
        if (missingAppIds.isEmpty()) return SteamLibraryPlaytimeFetch(clientActivity)

        // Some token types return an empty or incomplete client activity list. The
        // Web API is also used by our community page; count=0 requests every game.
        return runCatching {
            val recentActivity = parseRecentActivity(api.steamApiGetJson(
                path = "/IPlayerService/GetRecentlyPlayedGames/v1/",
                query = mapOf("steamid" to account.steamId, "count" to "0"),
                accessToken = token
            )).filterKeys { it in sharedAppIds }
            SteamLibraryPlaytimeFetch(items = recentActivity + clientActivity)
        }.getOrElse { error ->
            logFailure("recent", error)
            SteamLibraryPlaytimeFetch(clientActivity, unavailableAppIds = missingAppIds)
        }
    }

    private fun logFailure(source: String, error: Throwable) {
        val apiError = error as? SteamApiException
        runCatching {
            SteamDiagLogger.append(
                "library_playtime source=$source type=${error::class.java.simpleName} " +
                    "http=${apiError?.httpStatusCode ?: -1} eresult=${apiError?.eResult ?: -1}"
            )
        }
    }

    companion object {
        internal fun parseClientActivity(response: ByteArray): Map<Int, SteamGamePlaytime> {
            return SteamProtoReader(response).parseAll().filter { it.number == 1 }.associate { field ->
                require(field.wireType == 2) { "Invalid playtime record" }
                val game = SteamProtoReader(requireNotNull(field.bytes)).parse()
                fun number(index: Int): Long? = game[index]?.let {
                    require(it.wireType == 0) { "Invalid playtime field" }
                    it.asLong
                }
                val appId = requireNotNull(number(1)).asMinutes().also { require(it > 0) }
                appId to SteamGamePlaytime(
                    // Omitted proto2 scalar fields have their normal zero default.
                    recentMinutes = (number(3) ?: 0L).asMinutes(),
                    totalMinutes = number(4)?.asMinutes(),
                    lastPlayedAt = number(2)?.asTimestamp()
                )
            }
        }

        internal fun parseRecentActivity(payload: JsonObject): Map<Int, SteamGamePlaytime> {
            val response = requireNotNull(payload["response"] as? JsonObject)
            val count = requireNotNull(response.number("total_count")).asMinutes()
            val games = (response["games"] as? JsonArray) ?: run {
                require("games" !in response && count == 0) { "Missing recent games" }
                JsonArray(emptyList())
            }
            require(games.size == count) { "Incomplete recent games response" }
            val result = games.associate { element ->
                val game = requireNotNull(element as? JsonObject)
                val appId = requireNotNull(game.number("appid")).asMinutes().also { require(it > 0) }
                appId to SteamGamePlaytime(
                    recentMinutes = requireNotNull(game.number("playtime_2weeks")).asMinutes(),
                    totalMinutes = game.number("playtime_forever")?.asMinutes(),
                    lastPlayedAt = game.number("rtime_last_played")?.asTimestamp()
                )
            }
            require(result.size == count) { "Duplicate recent games" }
            return result
        }

        private fun JsonObject.number(name: String): Long? = this[name]?.let {
            requireNotNull((it as? JsonPrimitive)?.longOrNull) { "Invalid playtime number" }
        }

        private fun Long.asMinutes(): Int {
            require(this in 0L..Int.MAX_VALUE.toLong()) { "Invalid playtime value" }
            return toInt()
        }

        private fun Long.asTimestamp(): Long {
            require(this in 0L..0xffff_ffffL) { "Invalid playtime timestamp" }
            return this
        }
    }
}
