package takagi.ru.monica.steam.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.notifications.domain.SteamNotificationDetailParser

class SteamNotificationDetailParserTest {
    @Test
    fun wishlistTechnicalMetadataIsNotPresentedAsNotificationContent() {
        val details = SteamNotificationDetailParser.parse(
            bodyData = """{"appid":430960,"count":1}""",
            title = "Wishlist update",
            summary = ""
        )

        assertTrue(details.fields.isEmpty())
        assertEquals(listOf(430960), details.appIds)
    }

    @Test
    fun wishlistBodyExposesMessageAndStructuredFieldsWithoutRawJson() {
        val details = SteamNotificationDetailParser.parse(
            bodyData = """{
                "appid": 570,
                "count": 2,
                "discount_percent": 75,
                "message": "Two games on your wishlist are discounted"
            }""".trimIndent(),
            title = "Wishlist update",
            summary = ""
        )

        assertEquals("Two games on your wishlist are discounted", details.message)
        assertEquals(listOf(570), details.appIds)
        assertEquals("75", details.fields.first { it.key == "discount_percent" }.value)
        assertFalse(details.fields.any { it.key == "message" })
    }

    @Test
    fun nestedValuesAreFlattenedAndDuplicateTitleTextIsSuppressed() {
        val details = SteamNotificationDetailParser.parse(
            bodyData = """{
                "title": "Trade offer",
                "trade": {"tradeofferid": "123", "items": ["A", "B"]}
            }""".trimIndent(),
            title = "Trade offer",
            summary = ""
        )

        assertEquals(null, details.message)
        assertEquals("123", details.fields.first { it.key == "trade.tradeofferid" }.value)
        assertEquals("A, B", details.fields.first { it.key == "trade.items" }.value)
        assertFalse(details.fields.any { it.value == "Trade offer" })
    }

    @Test
    fun plainTextBodyRemainsReadable() {
        val details = SteamNotificationDetailParser.parse(
            bodyData = "Steam Support replied to your request.",
            title = "Steam Support",
            summary = ""
        )

        assertEquals("Steam Support replied to your request.", details.message)
        assertTrue(details.fields.isEmpty())
    }

    @Test
    fun malformedStructuredBodyIsNotExposedAsRawJson() {
        val details = SteamNotificationDetailParser.parse(
            bodyData = """{"appid":570""",
            title = "Wishlist update",
            summary = ""
        )

        assertEquals(null, details.message)
        assertTrue(details.fields.isEmpty())
    }
}
