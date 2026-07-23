package takagi.ru.monica.steam.trade

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamTradeOfferLazyKeyTest {
    @Test
    fun duplicateOfferIdsReceiveUniqueKeys() {
        val offer = SteamTradeOffer(
            id = "42",
            direction = SteamTradeOfferDirection.RECEIVED,
            partnerAccountId = 1L,
            partnerSteamId = "76561198000000001",
            message = "",
            state = SteamTradeOfferState.ACTIVE,
            rawStateCode = 2,
            itemsToGive = emptyList(),
            itemsToReceive = emptyList(),
            createdAt = 1L,
            updatedAt = 1L,
            expirationTime = 0L,
            escrowEndDate = 0L,
            confirmationMethod = 0
        )

        val keys = listOf(offer, offer).mapIndexed(::steamTradeOfferLazyKey)

        assertEquals(2, keys.distinct().size)
    }
}
