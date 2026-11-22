package takagi.ru.monica.steam.store.data

import org.jsoup.Jsoup
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.data.SteamFriendsParser
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.ui.*

internal class SteamGameStatsExtrasService(private val api: SteamApiClient = SteamApiClient()) {
    fun friends(account: SteamAccount, appId: Int): SteamGameFriendsActivity {
        require(account.hasRealSteamId && appId > 0)
        val token = requireNotNull(account.accessToken?.takeIf(String::isNotBlank))
        val activity = parseSteamGameFriendsActivity(api.callProtobuf(
            iface = "IPlayerService", method = "GetFriendsGameplayInfo",
            request = SteamProtoWriter().apply { writeVarint(1, appId.toLong()); writeBool(2, true) },
            accessToken = token, useGet = true), account.steamId)
        // One bounded batch supplies visible avatars/names. The axis still uses every
        // returned, readable time, rather than only the preview's longest players.
        val visible = activity.friends.sortedByDescending { it.minutes ?: -1 }.take(100)
        val profiles = if (visible.isEmpty()) emptyMap() else runCatching {
            SteamFriendsParser.parseProfiles(api.steamApiGetJson(
                path = "/ISteamUserOAuth/GetUserSummaries/v1/",
                query = mapOf("steamids" to visible.joinToString(",") { it.steamId }), accessToken = token))
                .associateBy { it.steamId }
        }.getOrDefault(emptyMap())
        return activity.copy(friends = activity.friends.map { friend ->
            profiles[friend.steamId]?.let { friend.copy(name = it.personaName.ifBlank { friend.steamId }, avatarUrl = it.avatarUrl) } ?: friend
        }.sortedByDescending { it.minutes ?: -1 })
    }

    fun badges(account: SteamAccount, appId: Int, language: String): SteamGameBadgeCatalog {
        require(account.hasRealSteamId && appId > 0)
        val url = "https://steamcommunity.com/profiles/${account.steamId}/gamecards/$appId/"
        return parseSteamGameBadgeCatalog(api.communityGetText(
            path = "/profiles/${account.steamId}/gamecards/$appId/", query = mapOf("l" to language),
            cookies = SteamInventoryService.marketCookies(account, SteamInventoryService.newSessionId()), referer = url), url)
    }
}

internal fun parseSteamGameFriendsActivity(bytes: ByteArray, ownSteamId: String): SteamGameFriendsActivity {
    val fields = SteamProtoReader(bytes).parseAll()
    fun player(bytes: ByteArray): SteamGameFriendTime? {
        val row = SteamProtoReader(bytes).parse()
        val id = row[1]?.takeIf { it.wireType == 1 }?.asFixed64UnsignedString?.takeIf { it.matches(Regex("7656119\\d{10}")) } ?: return null
        fun minutes(field: Int): Int? = row[field]?.takeIf { it.wireType == 0 }?.varint?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
        return SteamGameFriendTime(id, minutes(3), minutes(2))
    }
    val own = fields.firstOrNull { it.number == 1 }?.bytes?.let(::player)
    require(own?.steamId == ownSteamId) { "Gameplay response belongs to another player or is unavailable" }
    val friends = fields.filter { it.number in 2..5 }.mapNotNull { it.bytes?.let(::player) }
        .filter { it.steamId != ownSteamId }.groupBy { it.steamId }.map { (_, rows) ->
            rows.first().copy(minutes = rows.mapNotNull { it.minutes }.maxOrNull(),
                recentMinutes = rows.mapNotNull { it.recentMinutes }.maxOrNull())
        }
    return SteamGameFriendsActivity(own, friends)
}

internal fun parseSteamGameBadgeCatalog(html: String, url: String): SteamGameBadgeCatalog {
    val doc = Jsoup.parse(html, url)
    require(doc.selectFirst(".badge_details, .badge_card_set_cards, .badge_current") != null) {
        "Badge page unavailable or private"
    }
    fun image(element: org.jsoup.nodes.Element?): String = element?.let {
        it.absUrl("data-delayed-image").ifBlank { it.absUrl("src") }
    }.orEmpty().takeIf { it.startsWith("https://") && !it.contains("trans.gif") }.orEmpty()
    val cards = doc.select(".badge_card_set_card").mapNotNull { card ->
        val name = card.selectFirst(".badge_card_set_title")?.ownText().orEmpty().trim()
        val image = image(card.selectFirst("img.gamecard, img"))
        if (name.isBlank() || image.isBlank()) null else SteamGameBadgeCard(name, image,
            card.hasClass("owned") && !card.hasClass("unowned"))
    }.distinctBy { it.imageUrl }
    return SteamGameBadgeCatalog(
        name = doc.selectFirst(".badge_current .badge_info_title, .badge_info_title")?.text().orEmpty(),
        iconUrl = image(doc.selectFirst(".badge_current .badge_icon, .badge_current .badge_info_image img, .badge_info_image img")),
        description = doc.selectFirst(".badge_current .badge_info_description")?.text().orEmpty(), cards = cards)
}
