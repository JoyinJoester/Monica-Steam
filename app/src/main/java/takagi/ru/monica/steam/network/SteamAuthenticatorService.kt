package takagi.ru.monica.steam.network

import takagi.ru.monica.steam.data.SteamAccount

data class SteamRemoveAuthenticatorResult(
    val success: Boolean,
    val attemptsRemaining: Int? = null
)

class SteamAuthenticatorService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun remove(account: SteamAccount): SteamRemoveAuthenticatorResult {
        val token = requireNotNull(account.accessToken?.takeIf { it.isNotBlank() }) {
            "Steam access token required"
        }
        val revocationCode = requireNotNull(account.revocationCode?.takeIf { it.isNotBlank() }) {
            "Steam revocation code required"
        }
        val request = SteamProtoWriter().apply {
            writeString(2, revocationCode)
        }
        val fields = SteamProtoReader(
            api.callProtobuf(
                iface = "ITwoFactorService",
                method = "RemoveAuthenticator",
                request = request,
                accessToken = token
            )
        ).parse()

        return SteamRemoveAuthenticatorResult(
            success = fields[1]?.asBool == true,
            attemptsRemaining = fields[5]?.asInt?.takeIf { it > 0 }
        )
    }
}
