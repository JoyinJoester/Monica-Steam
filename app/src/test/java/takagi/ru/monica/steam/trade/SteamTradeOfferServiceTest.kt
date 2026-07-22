package takagi.ru.monica.steam.trade

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamTradeOfferServiceTest {
    @Test
    fun parsesReceivedAndSentOffersWithDescriptions() {
        val payload = Json.parseToJsonElement(
            """
            {
              "response": {
                "trade_offers_received": [{
                  "tradeofferid": "1001",
                  "accountid_other": 12345,
                  "message": "Gift",
                  "trade_offer_state": 2,
                  "time_created": 100,
                  "time_updated": 120,
                  "items_to_receive": [{
                    "appid": 730, "contextid": "2", "assetid": "8",
                    "classid": "10", "instanceid": "0", "amount": "1"
                  }]
                }],
                "trade_offers_sent": [{
                  "tradeofferid": "1002",
                  "accountid_other": 54321,
                  "trade_offer_state": 9,
                  "items_to_give": [{
                    "appid": 753, "contextid": "6", "assetid": "9",
                    "classid": "20", "instanceid": "1", "amount": "2"
                  }]
                }],
                "descriptions": [
                  {"appid":730,"classid":"10","instanceid":"0","name":"Item A","type":"Rifle","icon_url":"icon_a","tradable":1,"marketable":1},
                  {"appid":753,"classid":"20","instanceid":"1","name":"Card","type":"Trading Card","icon_url":"icon_b","tradable":1,"marketable":0}
                ]
              }
            }
            """.trimIndent()
        ).jsonObject

        val snapshot = SteamTradeOfferService.parseSnapshot(payload)

        assertEquals(1, snapshot.received.size)
        assertEquals(1, snapshot.sent.size)
        assertEquals("Item A", snapshot.received.single().itemsToReceive.single().name)
        assertEquals(2, snapshot.sent.single().itemsToGive.single().amount)
        assertEquals(SteamTradeOfferState.ACTIVE, snapshot.received.single().state)
        assertEquals(SteamTradeOfferState.NEEDS_CONFIRMATION, snapshot.sent.single().state)
        assertEquals("76561197960278073", snapshot.received.single().partnerSteamId)
    }

    @Test
    fun keepsUnknownTradeStateAndMissingItems() {
        val payload = Json.parseToJsonElement(
            """
            {"response":{"trade_offers_received":[{
              "tradeofferid":"2001","accountid_other":1,"trade_offer_state":99,
              "items_to_give":[{"appid":1,"classid":"2","instanceid":"0","missing":true}]
            }]}}
            """.trimIndent()
        ).jsonObject

        val offer = SteamTradeOfferService.parseSnapshot(payload).received.single()

        assertEquals(SteamTradeOfferState.UNKNOWN, offer.state)
        assertEquals(99, offer.rawStateCode)
        assertTrue(offer.itemsToGive.single().missing)
    }

    @Test
    fun parsesAcceptAndConfirmationResults() {
        val accepted = Json.parseToJsonElement("""{"tradeid":"3001"}""").jsonObject
        val needsMobile = Json.parseToJsonElement(
            """{"needs_mobile_confirmation":true}"""
        ).jsonObject
        val failed = Json.parseToJsonElement(
            """{"success":false,"strError":"Expired"}"""
        ).jsonObject

        assertTrue(
            SteamTradeOfferService.parseActionResult(
                accepted,
                SteamTradeOfferAction.ACCEPT
            ).success
        )
        assertTrue(
            SteamTradeOfferService.parseActionResult(
                needsMobile,
                SteamTradeOfferAction.ACCEPT
            ).requiresMobileConfirmation
        )
        val failure = SteamTradeOfferService.parseActionResult(
            failed,
            SteamTradeOfferAction.DECLINE
        )
        assertFalse(failure.success)
        assertEquals("Expired", failure.message)
    }

    @Test
    fun parsesProtobufTradeOffersUsedByAuthenticatedSteamEndpoint() {
        val description = SteamProtoWriter().apply {
            writeVarint(1, 730)
            writeUint64(2, 10)
            writeUint64(3, 0)
            writeString(6, "icon_a")
            writeBool(9, true)
            writeString(14, "Proto Item")
            writeString(16, "Rifle")
            writeBool(25, true)
        }
        val asset = SteamProtoWriter().apply {
            writeVarint(1, 730)
            writeUint64(2, 2)
            writeUint64(3, 8)
            writeUint64(4, 10)
            writeUint64(5, 0)
            writeVarint(7, 1)
        }
        val receivedOffer = SteamProtoWriter().apply {
            writeUint64(1, 1001)
            writeVarint(2, 12345)
            writeString(3, "Proto offer")
            writeVarint(5, 2)
            writeMessage(7, asset)
            writeVarint(9, 100)
            writeVarint(10, 120)
        }
        val response = SteamProtoWriter().apply {
            writeMessage(2, receivedOffer)
            writeMessage(3, description)
        }

        val offer = SteamTradeOfferService.parseProtoSnapshot(response.toByteArray())
            .received
            .single()

        assertEquals("1001", offer.id)
        assertEquals("Proto offer", offer.message)
        assertEquals("Proto Item", offer.itemsToReceive.single().name)
        assertTrue(offer.itemsToReceive.single().tradable)
        assertTrue(offer.itemsToReceive.single().marketable)
    }
}
