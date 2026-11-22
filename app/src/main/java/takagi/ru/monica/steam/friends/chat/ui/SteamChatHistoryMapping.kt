package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryClassifier
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage

/**
 * Direct and group messages expose the same four fields under different types. Mapping them here
 * keeps the info page counters and the history page on one parse, so a message is never counted in
 * one category and listed under another.
 */
internal fun buildSteamChatHistory(
    partnerSteamId: String?,
    selectedFriend: SteamFriend?,
    friendsById: Map<String, SteamFriend>,
    directMessages: List<SteamChatMessage>,
    directAccountSteamId: String,
    groupMessages: List<SteamGroupChatMessage>,
    groupAccountSteamId: String
): List<SteamChatHistoryItem> {
    if (partnerSteamId == null) {
        return groupMessages.map { message ->
            SteamChatHistoryClassifier.item(
                id = message.stableId,
                senderName = nameOf(message.senderSteamId, groupAccountSteamId, friendsById),
                body = message.body,
                timestamp = message.timestamp
            )
        }
    }
    return directMessages.map { message ->
        val name = when {
            message.senderSteamId == partnerSteamId ->
                selectedFriend?.displayName ?: message.senderSteamId
            else -> friendsById[message.senderSteamId]?.displayName ?: message.senderSteamId
        }
        SteamChatHistoryClassifier.item(
            id = message.stableId,
            senderName = nameOf(message.senderSteamId, directAccountSteamId, friendsById, name),
            body = message.body,
            timestamp = message.timestamp
        )
    }
}

private fun nameOf(
    senderSteamId: String,
    accountSteamId: String,
    friendsById: Map<String, SteamFriend>,
    fallback: String = friendsById[senderSteamId]?.displayName ?: senderSteamId
): String = if (senderSteamId == accountSteamId) "我" else fallback
