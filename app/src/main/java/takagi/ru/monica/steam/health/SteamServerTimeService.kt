package takagi.ru.monica.steam.health

import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamServerTimeService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun queryServerTimeSeconds(): Long {
        val response = api.callProtobuf(
            iface = "ITwoFactorService",
            method = "QueryTime",
            request = SteamProtoWriter()
        )
        return parseServerTimeSeconds(response)
    }

    companion object {
        fun parseServerTimeSeconds(response: ByteArray): Long {
            val seconds = SteamProtoReader(response).parse()[1]?.asLong
                ?: throw IllegalArgumentException("Steam server time response is missing server_time")
            require(seconds > 0L) { "Steam server time response is invalid" }
            return seconds
        }
    }
}
