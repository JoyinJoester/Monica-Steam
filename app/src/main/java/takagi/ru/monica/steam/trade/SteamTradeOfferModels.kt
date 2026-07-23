package takagi.ru.monica.steam.trade

enum class SteamTradeOfferDirection {
    RECEIVED,
    SENT
}

enum class SteamTradeOfferState(val code: Int) {
    INVALID(1),
    ACTIVE(2),
    ACCEPTED(3),
    COUNTERED(4),
    EXPIRED(5),
    CANCELED(6),
    DECLINED(7),
    INVALID_ITEMS(8),
    NEEDS_CONFIRMATION(9),
    CANCELED_BY_SECOND_FACTOR(10),
    IN_ESCROW(11),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): SteamTradeOfferState {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

data class SteamTradeOfferItem(
    val appId: Int,
    val contextId: String,
    val assetId: String,
    val classId: String,
    val instanceId: String,
    val amount: Int,
    val name: String,
    val type: String,
    val iconUrl: String,
    val tradable: Boolean,
    val marketable: Boolean,
    val missing: Boolean
)

data class SteamTradeOffer(
    val id: String,
    val direction: SteamTradeOfferDirection,
    val partnerAccountId: Long,
    val partnerSteamId: String,
    val message: String,
    val state: SteamTradeOfferState,
    val rawStateCode: Int,
    val itemsToGive: List<SteamTradeOfferItem>,
    val itemsToReceive: List<SteamTradeOfferItem>,
    val createdAt: Long,
    val updatedAt: Long,
    val expirationTime: Long,
    val escrowEndDate: Long,
    val confirmationMethod: Int
) {
    val isActive: Boolean
        get() = state == SteamTradeOfferState.ACTIVE ||
            state == SteamTradeOfferState.NEEDS_CONFIRMATION
}

internal fun steamTradeOfferLazyKey(index: Int, offer: SteamTradeOffer): String =
    "${offer.id}-$index"

data class SteamTradeOffersSnapshot(
    val received: List<SteamTradeOffer>,
    val sent: List<SteamTradeOffer>,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val all: List<SteamTradeOffer>
        get() = (received + sent).sortedByDescending { maxOf(it.updatedAt, it.createdAt) }
}

enum class SteamTradeOfferAction {
    ACCEPT,
    DECLINE,
    CANCEL
}

data class SteamTradeOfferActionResult(
    val success: Boolean,
    val requiresMobileConfirmation: Boolean = false,
    val requiresEmailConfirmation: Boolean = false,
    val tradeId: String? = null,
    val message: String? = null
)
