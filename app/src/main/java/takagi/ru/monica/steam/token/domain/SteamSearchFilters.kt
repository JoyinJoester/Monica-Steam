package takagi.ru.monica.steam.token.domain

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.market.SteamInventoryItemStack
import takagi.ru.monica.steam.market.SteamMarketListing
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.organization.SteamAccountOrganizer
import takagi.ru.monica.steam.trade.SteamTradeOffer

internal fun filterSteamAccounts(
    accounts: List<SteamAccount>,
    query: String
): List<SteamAccount> {
    return SteamAccountOrganizer.filter(accounts, query)
}

internal fun filterSteamConfirmations(
    confirmations: List<SteamConfirmation>,
    query: String
): List<SteamConfirmation> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return confirmations

    return confirmations.filter { confirmation ->
        confirmation.headline.contains(normalizedQuery, ignoreCase = true) ||
            confirmation.summary.contains(normalizedQuery, ignoreCase = true) ||
            confirmation.type.contains(normalizedQuery, ignoreCase = true)
    }
}

internal fun filterSteamInventoryStacks(
    stacks: List<SteamInventoryItemStack>,
    query: String
): List<SteamInventoryItemStack> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return stacks
    return stacks.filter { stack ->
        stack.item.name.contains(normalizedQuery, ignoreCase = true) ||
            stack.item.marketHashName.contains(normalizedQuery, ignoreCase = true) ||
            stack.item.type.contains(normalizedQuery, ignoreCase = true)
    }
}

internal fun filterSteamMarketListings(
    listings: List<SteamMarketListing>,
    query: String
): List<SteamMarketListing> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return listings
    return listings.filter { listing ->
        listing.name.contains(normalizedQuery, ignoreCase = true) ||
            listing.marketHashName.contains(normalizedQuery, ignoreCase = true) ||
            listing.listingId.contains(normalizedQuery, ignoreCase = true)
    }
}

internal fun filterSteamTradeOffers(
    offers: List<SteamTradeOffer>,
    query: String
): List<SteamTradeOffer> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return offers
    return offers.filter { offer ->
        offer.id.contains(normalizedQuery, ignoreCase = true) ||
            offer.partnerSteamId.contains(normalizedQuery, ignoreCase = true) ||
            offer.message.contains(normalizedQuery, ignoreCase = true) ||
            offer.itemsToGive.any { item ->
                item.name.contains(normalizedQuery, ignoreCase = true) ||
                    item.type.contains(normalizedQuery, ignoreCase = true)
            } ||
            offer.itemsToReceive.any { item ->
                item.name.contains(normalizedQuery, ignoreCase = true) ||
                    item.type.contains(normalizedQuery, ignoreCase = true)
            }
    }
}

internal fun steamCommunityLanguage(languageCode: String): String {
    return when (languageCode.lowercase()) {
        "zh", "zh-cn", "zh-hans" -> "schinese"
        "zh-tw", "zh-hk", "zh-hant" -> "tchinese"
        "ja" -> "japanese"
        "ru" -> "russian"
        "vi" -> "vietnamese"
        else -> "english"
    }
}
