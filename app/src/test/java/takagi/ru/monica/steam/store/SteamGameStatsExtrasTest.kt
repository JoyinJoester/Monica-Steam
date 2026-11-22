package takagi.ru.monica.steam.store

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.network.*
import takagi.ru.monica.steam.store.data.*
import takagi.ru.monica.steam.store.ui.*
import takagi.ru.monica.steam.store.points.data.buildSteamPointsShopQuery
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory

class SteamGameStatsExtrasTest {
    private val own = "76561198000000001"
    private fun player(id: String, minutes: Long? = null) = SteamProtoWriter().apply {
        writeFixed64(1, id.toLong()); minutes?.let { writeVarint(3, it) }
    }
    @Test fun duplicateCategoriesDoNotSkewAverageAndMissingIsNotZero() {
        val result = parseSteamGameFriendsActivity(SteamProtoWriter().apply {
            writeMessage(1, player(own, 120))
            writeMessage(2, player("76561198000000002", 60))
            writeMessage(3, player("76561198000000002", 60))
            writeMessage(4, player("76561198000000003", 180))
            writeMessage(5, player("76561198000000004"))
            writeMessage(6, player("76561198000000005", 6000))
        }.toByteArray(), own)
        assertEquals(3, result.friends.size)
        assertEquals(120.0, result.averageMinutes!!, .001)
        assertNull(result.friends.last().minutes)
    }
    @Test fun explicitZeroIsIncludedAndOverflowRemainsUnknown() {
        val result = parseSteamGameFriendsActivity(SteamProtoWriter().apply {
            writeMessage(1, player(own, 0))
            writeMessage(4, player("76561198000000002", 0))
            writeMessage(4, player("76561198000000003", 120))
            writeMessage(4, player("76561198000000004", 4294967295L))
        }.toByteArray(), own)
        assertEquals(60.0, result.averageMinutes!!, .001)
        assertNull(result.friends.last().minutes)
    }
    @Test(expected = IllegalArgumentException::class) fun otherPlayersResponseRejected() {
        parseSteamGameFriendsActivity(SteamProtoWriter().apply {
            writeMessage(1, player("76561198000000002", 5))
        }.toByteArray(), own)
    }
    @Test fun comparisonAxisHasSharedScaleAndHandlesUnknownBounds() {
        assertEquals(.5f, steamGamePlaytimeFraction(1800.0, 3600.0), .001f)
        assertEquals(0f, steamGamePlaytimeFraction(0.0, 0.0))
        assertEquals(0f, steamGamePlaytimeFraction(Double.NaN, 30.0))
        assertEquals(1f, steamGamePlaytimeFraction(500.0, 30.0))
    }
    @Test fun badgeCardsKeepNamesImagesAndExplicitOwnership() {
        val result = parseSteamGameBadgeCatalog("""
            <div class="badge_current"><div class="badge_info_title">Test badge</div>
            <div class="badge_info_image"><img src="https://community.fastly.steamstatic.com/badge.png"></div></div>
            <div class="badge_card_set_cards">
            <div class="badge_card_set_card owned"><img class="gamecard" src="https://community.fastly.steamstatic.com/a.png">
            <div class="badge_card_set_title"><div class="badge_card_set_text_qty">(2)</div>ENGINEER</div></div>
            <div class="badge_card_set_card unowned"><img data-delayed-image="https://community.fastly.steamstatic.com/b.png">
            <div class="badge_card_set_title">DEMOMAN</div></div>
            </div>
        """.trimIndent(), "https://steamcommunity.com/profiles/$own/gamecards/440/")
        assertEquals("Test badge", result.name)
        assertEquals("ENGINEER", result.cards[0].name)
        assertTrue(result.cards[0].owned)
        assertFalse(result.cards[1].owned)
        assertTrue(result.cards[1].imageUrl.endsWith("b.png"))
    }
    @Test(expected = IllegalArgumentException::class) fun loginOrPrivatePageIsNotEmptyCollection() {
        parseSteamGameBadgeCatalog("<html>Sign in</html>", "https://steamcommunity.com/login")
    }
    @Test fun pointsQueryFiltersRequestedGameWithoutChangingGlobalShop() {
        fun query(app: Int?) = SteamProtoReader(SteamProtoReader(buildSteamPointsShopQuery(
            SteamPointsShopCategory.FEATURED, "english", 12, null, app).toByteArray()).parse()[1]!!.bytes!!).parse()
        assertEquals(620, query(620)[1]!!.asInt)
        assertNull(query(null)[1])
        assertEquals(12, query(620)[5]!!.asInt)
    }
}
