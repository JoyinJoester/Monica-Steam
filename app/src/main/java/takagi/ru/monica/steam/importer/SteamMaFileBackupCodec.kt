package takagi.ru.monica.steam.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import takagi.ru.monica.steam.data.SteamAccount

object SteamMaFileBackupCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(account: SteamAccount): String {
        val root = runCatching {
            json.parseToJsonElement(account.rawSteamGuardJson).jsonObject.toMutableMap()
        }.getOrElse {
            mutableMapOf()
        }

        fun putString(key: String, value: String?) {
            val normalized = value?.trim().orEmpty()
            if (normalized.isNotBlank()) {
                root[key] = JsonPrimitive(normalized)
            }
        }

        if (account.hasRealSteamId) {
            removeMissingSteamIdMarkers(root)
            putString("steamid", account.steamId)
        } else {
            removeSteamIdFields(root)
            root["monica_missing_steamid"] = JsonPrimitive(true)
            putString("monica_local_steamid", account.steamId)
        }
        putString("account_name", account.accountName.ifBlank { account.displayName })
        root.remove("monica_display_name")
        val displayName = account.displayName.trim()
        val remarkName = displayName.takeIf {
            it.isNotBlank() &&
                it != account.accountName &&
                it != account.steamId
        }
        putString("monica_display_name", remarkName)
        putString("device_id", account.deviceId)
        putString("shared_secret", account.sharedSecret)
        putString("identity_secret", account.identitySecret)
        putString("revocation_code", account.revocationCode)
        putString("token_gid", account.tokenGid)
        if (account.hasRealSteamId) {
            putString("access_token", account.accessToken)
            putString("refresh_token", account.refreshToken)
            putString("steamLoginSecure", account.steamLoginSecure)
        } else {
            root.remove("access_token")
            root.remove("accessToken")
            root.remove("refresh_token")
            root.remove("refreshToken")
            root.remove("steamLoginSecure")
            root.remove("steam_login_secure")
        }

        val session = (root["Session"] as? JsonObject)
            ?.toMutableMap()
            ?: (root["session"] as? JsonObject)
                ?.toMutableMap()
            ?: mutableMapOf()

        fun putSessionString(key: String, value: String?) {
            val normalized = value?.trim().orEmpty()
            if (normalized.isNotBlank()) {
                session[key] = JsonPrimitive(normalized)
            }
        }

        if (account.hasRealSteamId) {
            putSessionString("SteamID", account.steamId)
        } else {
            session.remove("SteamID")
            session.remove("steamid")
            session.remove("steam_id")
        }
        putSessionString("AccountName", account.accountName.ifBlank { account.displayName })
        putSessionString("DeviceID", account.deviceId)
        if (account.hasRealSteamId) {
            putSessionString("AccessToken", account.accessToken)
            putSessionString("OAuthToken", account.accessToken)
            putSessionString("RefreshToken", account.refreshToken)
            val steamLoginSecure = account.steamLoginSecure
                ?.takeIf { it.isNotBlank() }
                ?: account.accessToken?.let { "${account.steamId}||$it" }
            putSessionString(
                "SteamLoginSecure",
                steamLoginSecure
            )
        } else {
            session.remove("AccessToken")
            session.remove("access_token")
            session.remove("OAuthToken")
            session.remove("oauth_token")
            session.remove("RefreshToken")
            session.remove("refresh_token")
            session.remove("SteamLoginSecure")
            session.remove("steamLoginSecure")
        }
        if (session.isNotEmpty()) {
            root["Session"] = JsonObject(session)
        }

        return JsonObject(root).toString()
    }

    fun fileName(account: SteamAccount): String {
        val rawName = account.visibleSteamId
            .ifBlank { account.accountName }
            .ifBlank { account.displayName }
            .ifBlank { "steam_${account.id}" }
        val safeName = rawName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "steam_${account.id}" }
        return "$safeName.maFile"
    }

    private fun removeMissingSteamIdMarkers(root: MutableMap<String, kotlinx.serialization.json.JsonElement>) {
        root.remove("monica_missing_steamid")
        root.remove("monicaMissingSteamId")
        root.remove("monica_local_steamid")
        root.remove("monicaLocalSteamId")
    }

    private fun removeSteamIdFields(root: MutableMap<String, kotlinx.serialization.json.JsonElement>) {
        listOf(
            "steamid",
            "steam_id",
            "SteamID",
            "steam64",
            "steam_id64",
            "steamID64",
            "SteamID64",
            "sbeamid"
        ).forEach(root::remove)
        removeMissingSteamIdMarkers(root)
    }
}
