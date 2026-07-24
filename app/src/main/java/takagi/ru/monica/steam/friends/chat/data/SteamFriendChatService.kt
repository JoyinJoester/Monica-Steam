package takagi.ru.monica.steam.friends.chat.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatPage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamFriendChatService(
    private val api: SteamApiClient = SteamApiClient()
) : SteamChatGateway {
    override fun fetchSessions(account: SteamAccount): SteamChatSessionsSnapshot {
        val accessToken = account.requireChatAccessToken()
        val response = api.callProtobuf(
            iface = FRIEND_MESSAGES_INTERFACE,
            method = "GetActiveMessageSessions",
            request = SteamProtoWriter().apply {
                writeVarint(1, 0L)
                writeBool(2, true)
            },
            accessToken = accessToken,
            useGet = true
        )
        return SteamChatSessionsSnapshot(
            accountSteamId = account.steamId,
            sessions = SteamFriendChatParser.parseSessions(response),
            fetchedAt = System.currentTimeMillis()
        )
    }

    override fun fetchMessages(
        account: SteamAccount,
        partnerSteamId: String,
        before: SteamChatHistoryBoundary?
    ): SteamChatPage {
        val accessToken = account.requireChatAccessToken()
        val accountSteamId = account.requireChatSteamId()
        val partner = partnerSteamId.requireSteamId64()
        val response = api.callProtobuf(
            iface = FRIEND_MESSAGES_INTERFACE,
            method = "GetRecentMessages",
            request = SteamProtoWriter().apply {
                writeFixed64(1, accountSteamId)
                writeFixed64(2, partner)
                writeVarint(3, PAGE_SIZE.toLong())
                writeBool(4, before == null)
                before?.let { boundary ->
                    writeFixed32(5, boundary.timestamp)
                    writeVarint(7, boundary.ordinal.toLong())
                }
                // Request the original BBCode so Steam invites, stickers,
                // media links and other structured chat entries are not
                // flattened into an unparseable plain-text surrogate.
                writeBool(6, true)
            },
            accessToken = accessToken,
            useGet = true
        )
        return SteamFriendChatParser.parseMessages(response, partnerSteamId)
    }

    override fun sendMessage(
        account: SteamAccount,
        partnerSteamId: String,
        body: String,
        clientMessageId: String
    ): SteamChatMessage {
        val accessToken = account.requireChatAccessToken()
        val accountSteamId = account.requireChatSteamId()
        val partner = partnerSteamId.requireSteamId64()
        val normalizedBody = body.trim()
        require(normalizedBody.isNotBlank()) { "Steam chat message is empty" }
        val response = api.callProtobuf(
            iface = FRIEND_MESSAGES_INTERFACE,
            method = "SendMessage",
            request = SteamProtoWriter().apply {
                writeFixed64(1, partner)
                writeVarint(2, CHAT_ENTRY_TYPE_MESSAGE)
                writeString(3, normalizedBody)
                // Steam's own friend chat sends rich content such as emoticons,
                // stickers and uploaded media through the BBCode-aware text field.
                writeBool(4, true)
                writeBool(5, true)
                writeBool(6, false)
                writeString(8, clientMessageId)
            },
            accessToken = accessToken
        )
        return SteamFriendChatParser.parseSentMessage(
            response = response,
            accountSteamId = account.steamId,
            partnerSteamId = partnerSteamId,
            requestedBody = normalizedBody
        ).copy(clientMessageId = clientMessageId)
    }

    override fun acknowledge(
        account: SteamAccount,
        partnerSteamId: String,
        timestamp: Long
    ) {
        if (timestamp <= 0L) return
        val accessToken = account.requireChatAccessToken()
        val partner = partnerSteamId.requireSteamId64()
        api.callProtobuf(
            iface = FRIEND_MESSAGES_INTERFACE,
            method = "AckMessage",
            request = SteamProtoWriter().apply {
                writeFixed64(1, partner)
                writeVarint(2, timestamp)
            },
            accessToken = accessToken
        )
    }

    private fun SteamAccount.requireChatAccessToken(): String {
        require(hasRealSteamId) { "Real Steam ID required for chat" }
        return accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required for chat")
    }

    private fun SteamAccount.requireChatSteamId(): Long =
        steamId.requireSteamId64()

    private fun String.requireSteamId64(): Long {
        require(matches(Regex("7656119\\d{10}"))) { "Valid Steam ID required for chat" }
        return toLong()
    }

    private companion object {
        const val FRIEND_MESSAGES_INTERFACE = "IFriendMessagesService"
        const val PAGE_SIZE = 50
        const val CHAT_ENTRY_TYPE_MESSAGE = 1L
    }
}
