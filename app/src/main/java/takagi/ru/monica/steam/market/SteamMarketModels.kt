package takagi.ru.monica.steam.market

data class SteamWalletInfo(
    val currency: Int,
    val steamFeePercent: Double,
    val publisherFeePercent: Double,
    val marketMinimum: Int,
    val currencyIncrement: Int
) {
    companion object {
        val Fallback = SteamWalletInfo(
            currency = 1,
            steamFeePercent = 0.05,
            publisherFeePercent = 0.10,
            marketMinimum = 1,
            currencyIncrement = 1
        )
    }
}

data class SteamInventoryGame(
    val appId: Int,
    val contextId: String,
    val name: String,
    val contextName: String,
    val iconUrl: String,
    val itemCount: Int
)

data class SteamInventoryItem(
    val appId: Int,
    val contextId: String,
    val assetId: String,
    val classId: String,
    val instanceId: String,
    val amount: Int,
    val marketHashName: String,
    val name: String,
    val type: String,
    val iconUrl: String,
    val marketable: Boolean,
    val tradable: Boolean,
    val commodity: Boolean,
    val publisherFeePercent: Double?
) {
    val stackKey: String
        get() = "${classId}_${instanceId}"
}

data class SteamInventoryItemStack(
    val item: SteamInventoryItem,
    val assetIds: List<String>,
    val assetAmounts: Map<String, Int> = assetIds
        .groupingBy { it }
        .eachCount()
) {
    val count: Int
        get() = assetAmounts.values
            .fold(0L) { total, amount -> total + amount.coerceAtLeast(0) }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
}

fun steamInventoryGameLazyKey(index: Int, game: SteamInventoryGame): String =
    "${game.appId}_${game.contextId}_$index"

fun steamInventoryStackLazyKey(index: Int, stack: SteamInventoryItemStack): String =
    "${stack.item.stackKey}_${stack.assetIds.firstOrNull().orEmpty()}_$index"

data class SteamInventoryOverview(
    val games: List<SteamInventoryGame>,
    val wallet: SteamWalletInfo
)

data class SteamInventoryPage(
    val items: List<SteamInventoryItem>,
    val lastAssetId: String?,
    val hasMore: Boolean,
    val totalCount: Int
)

data class SteamMarketPrice(
    val lowestPrice: String?,
    val medianPrice: String?,
    val volume: Int?
)

data class SteamMarketHistoryPoint(
    val label: String,
    val price: Double,
    val volume: Int?
)

data class SteamMarketQuote(
    val price: SteamMarketPrice?,
    val history: List<SteamMarketHistoryPoint>
)

enum class SteamBatchPriceMode {
    LOWEST_LISTING,
    MEDIAN,
    RECENT_HIGH,
    RECENT_LOW,
    MANUAL
}

data class SteamBatchSellEntry(
    val stack: SteamInventoryItemStack,
    val priceReceive: Int,
    val quantity: Int = 1
)

data class SteamMarketListing(
    val listingId: String,
    val appId: Int,
    val contextId: String,
    val assetId: String,
    val marketHashName: String,
    val name: String,
    val iconUrl: String,
    val sellerReceives: Int,
    val fee: Int,
    val createdAt: Long,
    val active: Boolean
) {
    val buyerPrice: Int
        get() = sellerReceives + fee
}

fun steamMarketListingLazyKey(index: Int, listing: SteamMarketListing): String =
    "${listing.listingId}_${listing.assetId}_$index"

data class SteamMarketListingsPage(
    val items: List<SteamMarketListing>,
    val totalActive: Int,
    val nextStart: Int,
    val hasMore: Boolean
)

data class SteamMarketSellResult(
    val success: Boolean,
    val requiresConfirmation: Boolean,
    val message: String?
)

data class SteamMarketFeeBreakdown(
    val receive: Int,
    val steamFee: Int,
    val publisherFee: Int,
    val buyerPays: Int
) {
    val totalFee: Int
        get() = steamFee + publisherFee
}
