package takagi.ru.monica.steam.friends.chat.domain

import takagi.ru.monica.steam.data.SteamAccount

interface SteamChatGateway {
    fun fetchSessions(account: SteamAccount): SteamChatSessionsSnapshot

    fun fetchMessages(
        account: SteamAccount,
        partnerSteamId: String,
        before: SteamChatHistoryBoundary? = null
    ): SteamChatPage

    fun sendMessage(
        account: SteamAccount,
        partnerSteamId: String,
        body: String,
        clientMessageId: String
    ): SteamChatMessage

    fun acknowledge(
        account: SteamAccount,
        partnerSteamId: String,
        timestamp: Long
    )
}
