package takagi.ru.monica.steam.backup.share

import java.util.UUID

/** Public v1 contract. Keep in sync with docs/interop/monica-mafile-handoff.md. */
internal object SteamMaFileShareContract {
    const val ACTION = "takagi.ru.monica.steamapp.action.REQUEST_MAFILE"
    const val STEAM_PACKAGE = "com.valvesoftware.android.steam.community"
    const val EXTRA_VERSION = "protocol_version"
    const val EXTRA_REQUEST_ID = "request_id"
    const val VERSION = 1
    const val AUTHORITY_SUFFIX = ".mafile-handoff"
    const val MIME_TYPE = "application/octet-stream"
    const val FILE_NAME = "steam-account.maFile"
    const val MAX_BYTES = 1024 * 1024
    const val TTL_MS = 60_000L

    fun validRequest(action: String?, version: Int, requestId: String?): Boolean =
        action == ACTION && version == VERSION && requestId != null &&
            requestId.length == 36 && runCatching {
                UUID.fromString(requestId).toString() == requestId
            }.getOrDefault(false)
}
