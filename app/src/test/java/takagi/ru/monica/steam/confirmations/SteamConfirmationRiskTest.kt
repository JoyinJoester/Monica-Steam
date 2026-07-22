package takagi.ru.monica.steam.confirmations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamConfirmation

class SteamConfirmationRiskTest {
    @Test
    fun loginAndFinancialConfirmationsRequireHighReview() {
        val login = SteamConfirmation("1", "a", "login", "New sign in", "New device", "", 100L)
        val market = SteamConfirmation("2", "b", "market", "Sell item", "USD 12.50", "", 100L)

        assertEquals(
            SteamConfirmationRiskLevel.HIGH,
            SteamConfirmationRiskEvaluator.evaluate(login, nowSeconds = 100L).level
        )
        assertTrue(
            SteamConfirmationRiskReason.FINANCIAL_CONTEXT in
                SteamConfirmationRiskEvaluator.evaluate(market, nowSeconds = 100L).reasons
        )
    }

    @Test
    fun missingContextIsMediumAndOldConfirmationIsHigh() {
        val empty = SteamConfirmation("1", "a", "", "", "", "", 0L)
        val old = SteamConfirmation("2", "b", "trade", "", "", "", 1L)

        assertEquals(
            SteamConfirmationRiskLevel.MEDIUM,
            SteamConfirmationRiskEvaluator.evaluate(empty, nowSeconds = 1L).level
        )
        assertTrue(
            SteamConfirmationRiskReason.EXPIRED in
                SteamConfirmationRiskEvaluator.evaluate(old, nowSeconds = 700L).reasons
        )
    }
}
