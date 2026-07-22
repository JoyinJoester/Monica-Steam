package takagi.ru.monica.steam.network

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.steam.data.SteamAccount

data class SteamSessionRefreshResult(
    val accessToken: String,
    val refreshToken: String?
)

class SteamSessionRefreshService(
    private val api: SteamApiClient = SteamApiClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun refreshIfNeeded(
        account: SteamAccount,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): SteamSessionRefreshResult? {
        val refreshToken = account.refreshToken?.takeIf { it.isNotBlank() } ?: return null
        if (!shouldRefresh(account, nowSeconds)) {
            return null
        }
        return refresh(account.steamId, refreshToken)
    }

    fun shouldRefresh(
        account: SteamAccount,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        if (account.refreshToken.isNullOrBlank()) return false
        val accessToken = account.accessToken?.takeIf { it.isNotBlank() } ?: return true
        return isAccessTokenExpiring(accessToken, nowSeconds)
    }

    fun refresh(
        steamId: String,
        refreshToken: String
    ): SteamSessionRefreshResult? {
        val steamIdLong = steamId.toLongOrNull() ?: return null
        val request = SteamProtoWriter().apply {
            writeString(1, refreshToken)
            writeFixed64(2, steamIdLong)
        }
        return try {
            val fields = SteamProtoReader(
                api.callProtobuf(
                    iface = "IAuthenticationService",
                    method = "GenerateAccessTokenForApp",
                    request = request
                )
            ).parse()
            val accessToken = fields[1]?.asString?.takeIf { it.isNotBlank() } ?: return null
            SteamSessionRefreshResult(
                accessToken = accessToken,
                refreshToken = fields[2]?.asString?.takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isAccessTokenExpiring(
        accessToken: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        val exp = jwtPayload(accessToken)?.longAny("exp") ?: return true
        return exp <= nowSeconds + ACCESS_TOKEN_REFRESH_SKEW_SECONDS
    }

    private fun jwtPayload(token: String): JsonObject? {
        val payload = token.split('.').getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val decoded = runCatching {
            Base64.getUrlDecoder().decode(payload.withBase64Padding()).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull()
    }

    private fun JsonObject.longAny(vararg keys: String): Long? {
        keys.forEach { key ->
            val primitive = this[key]?.jsonPrimitive ?: return@forEach
            primitive.longOrNull?.let { return it }
            (primitive as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let { return it }
        }
        return null
    }

    private fun String.withBase64Padding(): String {
        val remainder = length % 4
        return if (remainder == 0) this else this + "=".repeat(4 - remainder)
    }

    private companion object {
        private const val ACCESS_TOKEN_REFRESH_SKEW_SECONDS = 300L
    }
}
