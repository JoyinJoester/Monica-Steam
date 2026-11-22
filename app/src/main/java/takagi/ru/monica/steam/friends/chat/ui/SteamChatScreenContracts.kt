package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.data.SteamAccountSourceState

internal enum class SteamChatSubpage { INFO, ADMIN }

/** Keep the current chat session alive while an MDBX/local source is switching. */
internal fun shouldApplySteamAccountSelection(sourceState: SteamAccountSourceState): Boolean =
    !sourceState.loading

internal fun accountIdFromSteamId(steamId: String): Long? = runCatching {
    steamId.toBigInteger().subtract("76561197960265728".toBigInteger()).longValueExact()
}.getOrNull()
