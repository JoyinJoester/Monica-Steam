package takagi.ru.monica.steam.friends.chat.data

import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatPage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.steamId64FromAccountId
import takagi.ru.monica.steam.network.SteamProtoReader

internal object SteamFriendChatParser {
    fun parseSessions(response: ByteArray): List<SteamChatSession> {
        return SteamProtoReader(response).parseAll()
            .asSequence()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field ->
                val session = runCatching {
                    SteamProtoReader(field.bytes ?: return@mapNotNull null).parse()
                }.getOrNull() ?: return@mapNotNull null
                val accountId = session[1]?.asLong?.takeIf { it > 0L }
                    ?: return@mapNotNull null
                SteamChatSession(
                    partnerSteamId = steamId64FromAccountId(accountId),
                    lastMessageTimestamp = session[2]?.asLong?.coerceAtLeast(0L) ?: 0L,
                    lastViewTimestamp = session[3]?.asLong?.coerceAtLeast(0L) ?: 0L,
                    unreadCount = session[4]?.asLong?.coerceIn(0L, Int.MAX_VALUE.toLong())
                        ?.toInt() ?: 0
                )
            }
            .distinctBy(SteamChatSession::partnerSteamId)
            .sortedByDescending(SteamChatSession::lastMessageTimestamp)
            .toList()
    }

    fun parseMessages(response: ByteArray, partnerSteamId: String): SteamChatPage {
        val fields = SteamProtoReader(response).parseAll()
        val messages = fields
            .asSequence()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field ->
                val message = runCatching {
                    SteamProtoReader(field.bytes ?: return@mapNotNull null).parse()
                }.getOrNull() ?: return@mapNotNull null
                val accountId = message[1]?.asLong?.takeIf { it > 0L }
                    ?: return@mapNotNull null
                val body = message[3]?.asString.orEmpty().trimEnd()
                if (body.isBlank()) return@mapNotNull null
                SteamChatMessage(
                    partnerSteamId = partnerSteamId,
                    senderSteamId = steamId64FromAccountId(accountId),
                    timestamp = message[2]?.asLong?.coerceAtLeast(0L) ?: 0L,
                    ordinal = message[4]?.asLong?.coerceIn(0L, Int.MAX_VALUE.toLong())
                        ?.toInt() ?: 0,
                    body = body
                )
            }
            .distinctBy(SteamChatMessage::stableId)
            .sortedWith(compareBy<SteamChatMessage> { it.timestamp }.thenBy { it.ordinal })
            .toList()
        return SteamChatPage(
            messages = messages,
            moreAvailable = fields.firstOrNull { it.number == 4 }?.asBool == true
        )
    }

    fun parseSentMessage(
        response: ByteArray,
        accountSteamId: String,
        partnerSteamId: String,
        requestedBody: String
    ): SteamChatMessage {
        val fields = SteamProtoReader(response).parse()
        return SteamChatMessage(
            partnerSteamId = partnerSteamId,
            senderSteamId = accountSteamId,
            timestamp = fields[2]?.asLong?.coerceAtLeast(0L) ?: 0L,
            ordinal = fields[3]?.asLong?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
            body = fields[4]?.asString.orEmpty()
                .ifBlank { fields[1]?.asString.orEmpty() }
                .ifBlank { requestedBody }
        )
    }
}
