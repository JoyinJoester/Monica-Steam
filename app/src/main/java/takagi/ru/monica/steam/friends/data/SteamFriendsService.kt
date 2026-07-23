package takagi.ru.monica.steam.friends.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.domain.SteamFriendActionResult
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient

class SteamFriendsService(
    private val api: SteamApiClient = SteamApiClient()
) : SteamFriendsGateway {
    override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val relationshipsPayload = api.steamApiGetJson(
            path = "/ISteamUserOAuth/GetFriendList/v1/",
            query = linkedMapOf(
                "steamid" to account.steamId,
                "relationship" to "all"
            ),
            accessToken = accessToken
        )
        val relationships = SteamFriendsParser.parseRelationships(relationshipsPayload)
        if (relationships.isEmpty()) return SteamFriendsSnapshot(fetchedAt = fetchedAt)

        val profiles = relationships.keys.chunked(MAX_PROFILE_BATCH).flatMap { steamIds ->
            runCatching {
                val profilePayload = api.steamApiGetJson(
                    path = "/ISteamUserOAuth/GetUserSummaries/v1/",
                    query = mapOf("steamids" to steamIds.joinToString(",")),
                    accessToken = accessToken
                )
                SteamFriendsParser.parseProfiles(profilePayload)
            }.getOrDefault(emptyList())
        }.associateBy(SteamFriendProfile::steamId)

        return SteamFriendsSnapshot(
            friends = SteamFriendsParser.merge(relationships, profiles),
            fetchedAt = fetchedAt
        )
    }

    override fun respondToInvite(
        account: SteamAccount,
        friendSteamId: String,
        accept: Boolean
    ): SteamFriendActionResult {
        require(account.hasRealSteamId) { "real Steam ID required" }
        require(friendSteamId.matches(Regex("7656119\\d{10}"))) { "valid friend Steam ID required" }
        require(
            !account.steamLoginSecure.isNullOrBlank() || !account.accessToken.isNullOrBlank()
        ) { "Steam community session required" }
        val sessionId = SteamInventoryService.newSessionId()
        val path = if (accept) "/actions/AddFriendAjax" else "/actions/IgnoreFriendInviteAjax"
        val form = linkedMapOf(
            "sessionID" to listOf(sessionId),
            "sessionid" to listOf(sessionId),
            "steamid" to listOf(friendSteamId)
        )
        if (accept) form["accept_invite"] = listOf("1")
        val payload = api.communityPostJson(
            path = path,
            form = form,
            cookies = SteamInventoryService.marketCookies(account, sessionId),
            referer = "https://steamcommunity.com/my/friends/pending"
        )
        val success = payload.successCode() == 1
        return SteamFriendActionResult(
            success = success,
            message = payload.text("error")
                .ifBlank { payload.text("message") }
                .ifBlank { payload.text("results") }
                .takeIf(String::isNotBlank)
        )
    }

    private fun JsonObject.successCode(): Int {
        val primitive = this["success"] as? JsonPrimitive ?: return 0
        return primitive.intOrNull
            ?: primitive.contentOrNull?.toIntOrNull()
            ?: if (primitive.booleanOrNull == true) 1 else 0
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val MAX_PROFILE_BATCH = 100
    }
}
