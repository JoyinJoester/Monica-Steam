package takagi.ru.monica.steam.notifications

import kotlinx.serialization.Serializable

@Serializable
enum class SteamNotificationKind {
    GIFT,
    COMMENT,
    ITEM,
    FRIEND_INVITE,
    SALE,
    PRELOAD,
    WISHLIST,
    TRADE_OFFER,
    GENERAL,
    HELP_REQUEST,
    ASYNC_GAME,
    CHAT_MESSAGE,
    MODERATOR_MESSAGE,
    FAMILY,
    PARENTAL,
    GAME_INVITE,
    TRADE_REVERSED,
    UNKNOWN;

    companion object {
        fun fromType(type: Int): SteamNotificationKind = when (type) {
            2 -> GIFT
            3 -> COMMENT
            4 -> ITEM
            5 -> FRIEND_INVITE
            6 -> SALE
            7 -> PRELOAD
            8 -> WISHLIST
            9 -> TRADE_OFFER
            10 -> GENERAL
            11 -> HELP_REQUEST
            12 -> ASYNC_GAME
            13 -> CHAT_MESSAGE
            14 -> MODERATOR_MESSAGE
            15, 16, 17, 18, 19, 20 -> FAMILY
            21, 22, 23, 24 -> PARENTAL
            25, 26, 27, 28 -> GAME_INVITE
            29, 30 -> TRADE_REVERSED
            else -> UNKNOWN
        }
    }
}

@Serializable
data class SteamNotification(
    val id: String,
    val type: Int,
    val kind: SteamNotificationKind,
    val title: String,
    val summary: String,
    val relatedId: String? = null,
    val bodyData: String = "",
    val read: Boolean = false,
    val timestamp: Long = 0L,
    val hidden: Boolean = false,
    val expiry: Long = 0L,
    val viewed: Long = 0L
)

@Serializable
data class SteamNotificationSnapshot(
    val notifications: List<SteamNotification> = emptyList(),
    val confirmationCount: Int = 0,
    val pendingGiftCount: Int = 0,
    val pendingFriendCount: Int = 0,
    val unreadCount: Int = 0,
    val pendingFamilyInviteCount: Int = 0,
    val pendingGifts: List<SteamPendingGift> = emptyList(),
    val fetchedAt: Long = 0L
)

@Serializable
enum class SteamGiftAction {
    ADD_TO_LIBRARY,
    KEEP_IN_INVENTORY,
    DECLINE
}

@Serializable
data class SteamPendingGift(
    val id: String,
    val senderSteamId: String = "",
    val senderName: String = "",
    val name: String = "Steam gift",
    val message: String = "",
    val actions: Set<SteamGiftAction> = emptySet(),
    val requiresWeb: Boolean = false,
    val giftCardId: String? = null
)

data class SteamGiftActionResult(
    val success: Boolean,
    val message: String? = null
)

data class SteamNotificationsUiState(
    val snapshot: SteamNotificationSnapshot? = null,
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null,
    val actionGiftId: String? = null
)
