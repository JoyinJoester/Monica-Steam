package takagi.ru.monica.steam

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.market.SteamMarketFees
import takagi.ru.monica.steam.market.SteamWalletInfo

class SteamMarketFeesTest {
    private val cnyWallet = SteamWalletInfo(
        currency = 23,
        steamFeePercent = 0.05,
        publisherFeePercent = 0.10,
        marketMinimum = 7,
        currencyIncrement = 1
    )

    @Test
    fun buyerPaysMatchesSteamGroundTruth() {
        val fees = SteamMarketFees(cnyWallet)
        val samples = mapOf(
            1 to 21,
            7 to 21,
            10 to 24,
            50 to 64,
            100 to 117,
            233 to 267,
            999 to 1147,
            1000 to 1150,
            12345 to 14196
        )

        samples.forEach { (receive, buyerPays) ->
            assertEquals(buyerPays, fees.buyerPays(receive))
        }
    }

    @Test
    fun receiveFromTotalReversesBuyerPays() {
        val fees = SteamMarketFees(cnyWallet)
        listOf(7, 10, 50, 100, 233, 999, 1000, 12345).forEach { receive ->
            assertEquals(receive, fees.receiveFromTotal(fees.buyerPays(receive)))
        }
    }

    @Test
    fun breakdownUsesSteamAndPublisherMinimums() {
        val breakdown = SteamMarketFees(cnyWallet).breakdown(100)

        assertEquals(7, breakdown.steamFee)
        assertEquals(10, breakdown.publisherFee)
        assertEquals(17, breakdown.totalFee)
        assertEquals(117, breakdown.buyerPays)
    }
}
