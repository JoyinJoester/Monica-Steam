package takagi.ru.monica.steam

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.market.SteamInventoryItem
import takagi.ru.monica.steam.market.SteamInventoryItemStack
import takagi.ru.monica.steam.market.SteamMarketListing
import takagi.ru.monica.steam.token.domain.filterSteamAccounts
import takagi.ru.monica.steam.token.domain.filterSteamConfirmations
import takagi.ru.monica.steam.token.domain.filterSteamInventoryStacks
import takagi.ru.monica.steam.token.domain.filterSteamMarketListings
import takagi.ru.monica.steam.token.domain.filterSteamTradeOffers
import takagi.ru.monica.steam.token.domain.steamCommunityLanguage
import takagi.ru.monica.steam.trade.SteamTradeOffer
import takagi.ru.monica.steam.trade.SteamTradeOfferDirection
import takagi.ru.monica.steam.trade.SteamTradeOfferItem
import takagi.ru.monica.steam.trade.SteamTradeOfferState

class SteamSearchFiltersTest {

    private val account = SteamAccount(
        id = 1L,
        steamId = "76561199871008657",
        accountName = "joyinsana1",
        displayName = "Joyin Steam",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = "identity",
        revocationCode = null,
        tokenGid = null,
        accessToken = "token",
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun accountSearchCoversNameDisplayNameAndSteamId() {
        val accounts = listOf(account)

        assertEquals(accounts, filterSteamAccounts(accounts, " JOYINSANA1 "))
        assertEquals(accounts, filterSteamAccounts(accounts, "joyin steam"))
        assertEquals(accounts, filterSteamAccounts(accounts, "008657"))
        assertEquals(emptyList<SteamAccount>(), filterSteamAccounts(accounts, "missing"))
    }

    @Test
    fun confirmationSearchCoversHeadlineSummaryAndType() {
        val confirmation = SteamConfirmation(
            id = "1",
            nonce = "nonce",
            type = "MarketSell",
            headline = "CS2 Market Listing",
            summary = "AK-47 item confirmation",
            imageUrl = "",
            creationTime = 0L
        )
        val confirmations = listOf(confirmation)

        assertEquals(confirmations, filterSteamConfirmations(confirmations, " market "))
        assertEquals(confirmations, filterSteamConfirmations(confirmations, "ak-47"))
        assertEquals(confirmations, filterSteamConfirmations(confirmations, "marketsell"))
        assertEquals(emptyList<SteamConfirmation>(), filterSteamConfirmations(confirmations, "trade only"))
    }

    @Test
    fun blankQueryReturnsOriginalLists() {
        assertEquals(listOf(account), filterSteamAccounts(listOf(account), "  "))
    }

    @Test
    fun inventoryAndListingsSearchStayInsideCurrentPage() {
        val item = SteamInventoryItem(
            appId = 730,
            contextId = "2",
            assetId = "asset",
            classId = "class",
            instanceId = "0",
            amount = 1,
            marketHashName = "AK-47 | Redline",
            name = "AK-47",
            type = "Rifle",
            iconUrl = "",
            marketable = true,
            tradable = true,
            commodity = false,
            publisherFeePercent = null
        )
        val stack = SteamInventoryItemStack(item, listOf(item.assetId))
        val listing = SteamMarketListing(
            listingId = "listing-555",
            appId = 730,
            contextId = "2",
            assetId = "asset",
            marketHashName = item.marketHashName,
            name = item.name,
            iconUrl = "",
            sellerReceives = 100,
            fee = 17,
            createdAt = 0L,
            active = true
        )

        assertEquals(listOf(stack), filterSteamInventoryStacks(listOf(stack), " rifle "))
        assertEquals(listOf(listing), filterSteamMarketListings(listOf(listing), "555"))
        assertEquals(emptyList<SteamInventoryItemStack>(), filterSteamInventoryStacks(listOf(stack), "card"))
    }

    @Test
    fun steamLanguageFollowsSupportedAppLocales() {
        assertEquals("schinese", steamCommunityLanguage("zh-CN"))
        assertEquals("tchinese", steamCommunityLanguage("zh-Hant"))
        assertEquals("japanese", steamCommunityLanguage("ja"))
        assertEquals("english", steamCommunityLanguage("de"))
    }

    @Test
    fun tradeOfferSearchCoversPartnerMessageAndItems() {
        val item = SteamTradeOfferItem(
            appId = 730,
            contextId = "2",
            assetId = "asset",
            classId = "class",
            instanceId = "0",
            amount = 1,
            name = "AK-47 Redline",
            type = "Rifle",
            iconUrl = "",
            tradable = true,
            marketable = true,
            missing = false
        )
        val offer = SteamTradeOffer(
            id = "offer-123",
            direction = SteamTradeOfferDirection.RECEIVED,
            partnerAccountId = 1L,
            partnerSteamId = "76561198000000001",
            message = "Birthday gift",
            state = SteamTradeOfferState.ACTIVE,
            rawStateCode = 2,
            itemsToGive = emptyList(),
            itemsToReceive = listOf(item),
            createdAt = 0L,
            updatedAt = 0L,
            expirationTime = 0L,
            escrowEndDate = 0L,
            confirmationMethod = 0
        )

        assertEquals(listOf(offer), filterSteamTradeOffers(listOf(offer), "redline"))
        assertEquals(listOf(offer), filterSteamTradeOffers(listOf(offer), "birthday"))
        assertEquals(listOf(offer), filterSteamTradeOffers(listOf(offer), "000001"))
        assertEquals(emptyList<SteamTradeOffer>(), filterSteamTradeOffers(listOf(offer), "knife"))
    }
}
