package takagi.ru.monica.steam.confirmations

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.network.SteamConfirmation

class SteamConfirmationKindTest {
    @Test
    fun recognizesGiftFromGenericConfirmationText() {
        assertEquals(
            SteamConfirmationKind.GIFT,
            SteamConfirmationKindClassifier.classify(
                confirmation(type = "1", headline = "Send gift", summary = "Gift copy to a friend")
            )
        )
        assertEquals(
            SteamConfirmationKind.GIFT,
            SteamConfirmationKindClassifier.classify(
                confirmation(type = "", headline = "确认赠礼", summary = "送给好友")
            )
        )
    }

    @Test
    fun mapsKnownSteamNumericTypes() {
        assertEquals(SteamConfirmationKind.TRADE, classify("2"))
        assertEquals(SteamConfirmationKind.MARKET, classify("3"))
        assertEquals(SteamConfirmationKind.SECURITY, classify("5"))
        assertEquals(SteamConfirmationKind.SECURITY, classify("7"))
        assertEquals(SteamConfirmationKind.FAMILY, classify("8"))
    }

    @Test
    fun classifiesNamedAndUnknownFutureTypesWithoutDroppingThem() {
        assertEquals(
            SteamConfirmationKind.MARKET,
            SteamConfirmationKindClassifier.classify(
                confirmation(type = "MarketSellTransaction", headline = "Sell item")
            )
        )
        assertEquals(
            SteamConfirmationKind.OTHER,
            SteamConfirmationKindClassifier.classify(
                confirmation(type = "99", headline = "New Steam action")
            )
        )
    }

    private fun classify(type: String): SteamConfirmationKind {
        return SteamConfirmationKindClassifier.classify(confirmation(type = type))
    }

    private fun confirmation(
        type: String,
        headline: String = "",
        summary: String = ""
    ) = SteamConfirmation(
        id = "1",
        nonce = "nonce",
        type = type,
        headline = headline,
        summary = summary,
        imageUrl = "",
        creationTime = 0L
    )
}

