package takagi.ru.monica.steam.friends.chat.telegram

import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingPosition

/** Steam reading-position decisions, independent of the native rendering toolkit. */
internal sealed interface TelegramListScrollPlan {
    data class Anchor(val position: SteamChatReadingPosition, val requested: Boolean = false) : TelegramListScrollPlan
    data object Latest : TelegramListScrollPlan
    data object None : TelegramListScrollPlan
}

internal fun planTelegramListScroll(
    previousIds: List<String>,
    messageIds: List<String>,
    requestedMessageId: String?,
    handledRequestedMessageId: String?,
    savedPosition: SteamChatReadingPosition?,
    visiblePosition: SteamChatReadingPosition?,
    restored: Boolean,
    latestMessageOutgoing: Boolean,
    messagesBelow: Int,
    followingLatest: Boolean = false
): TelegramListScrollPlan {
    if (messageIds.isEmpty()) return TelegramListScrollPlan.None
    if (requestedMessageId != null && requestedMessageId != handledRequestedMessageId &&
        requestedMessageId in messageIds
    ) {
        return TelegramListScrollPlan.Anchor(SteamChatReadingPosition(requestedMessageId), requested = true)
    }
    if (!restored) {
        return savedPosition?.takeIf { it.messageId in messageIds }
            ?.let { TelegramListScrollPlan.Anchor(it) }
            ?: TelegramListScrollPlan.Latest
    }
    // Deleting the newest item, updating a reaction or prepending history is not a
    // newly received message. The latter must not pull an older reader down.
    val latestAdded = previousIds.isNotEmpty() && messageIds.last() !in previousIds
    if (followingLatest || latestAdded && (latestMessageOutgoing || messagesBelow <= 2)) {
        return TelegramListScrollPlan.Latest
    }
    return visiblePosition?.takeIf { it.messageId in messageIds }
        ?.let { TelegramListScrollPlan.Anchor(it) }
        ?: TelegramListScrollPlan.None
}
