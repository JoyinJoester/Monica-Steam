package takagi.ru.monica.steam.gifts.domain

import kotlinx.serialization.Serializable

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
