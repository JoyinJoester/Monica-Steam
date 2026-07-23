package takagi.ru.monica.steam.friends

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient

interface SteamFriendsGateway {
    fun fetch(account: SteamAccount, fetchedAt: Long = System.currentTimeMillis()): SteamFriendsSnapshot
    fun respondToInvite(
        account: SteamAccount,
        friendSteamId: String,
        accept: Boolean
    ): SteamFriendActionResult
}

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

internal data class SteamFriendRelationshipRecord(
    val steamId: String,
    val relationship: SteamFriendRelationship,
    val friendSince: Long,
    val nickname: String
)

internal data class SteamFriendProfile(
    val steamId: String,
    val personaName: String,
    val realName: String,
    val avatarUrl: String,
    val profileUrl: String,
    val personaState: SteamPersonaState,
    val lastLogoff: Long,
    val gameId: String,
    val gameName: String,
    val primaryClanId: String,
    val countryCode: String
)

object SteamFriendsParser {
    internal fun parseRelationships(payload: JsonObject): LinkedHashMap<String, SteamFriendRelationshipRecord> {
        val root = payload.obj("friendslist") ?: payload.obj("response") ?: payload
        val result = linkedMapOf<String, SteamFriendRelationshipRecord>()
        root.array("friends").forEach { element ->
            val raw = element as? JsonObject ?: return@forEach
            val steamId = raw.string("steamid").ifBlank { raw.string("ulfriendid") }
            if (!steamId.matches(Regex("7656119\\d{10}"))) return@forEach
            val relationship = SteamFriendRelationship.fromRaw(
                raw.string("relationship").ifBlank { raw.string("efriendrelationship") }
            )
            result[steamId] = SteamFriendRelationshipRecord(
                steamId = steamId,
                relationship = relationship,
                friendSince = raw.long("friend_since").takeIf { it > 0L }
                    ?: raw.long("time_created"),
                nickname = raw.string("nickname")
            )
        }
        return result
    }

    internal fun parseProfiles(payload: JsonObject): List<SteamFriendProfile> {
        val root = payload.obj("response") ?: payload
        return root.array("players").mapNotNull { element ->
            val raw = element as? JsonObject ?: return@mapNotNull null
            val steamId = raw.string("steamid")
            if (!steamId.matches(Regex("7656119\\d{10}"))) return@mapNotNull null
            SteamFriendProfile(
                steamId = steamId,
                personaName = raw.string("personaname"),
                realName = raw.string("realname"),
                avatarUrl = raw.string("avatarfull")
                    .ifBlank { raw.string("avatarmedium") }
                    .ifBlank { raw.string("avatar") },
                profileUrl = raw.string("profileurl"),
                personaState = SteamPersonaState.fromCode(raw.int("personastate")),
                lastLogoff = raw.long("lastlogoff"),
                gameId = raw.string("gameid"),
                gameName = raw.string("gameextrainfo"),
                primaryClanId = raw.string("primaryclanid"),
                countryCode = raw.string("loccountrycode")
            )
        }
    }

    internal fun merge(
        relationships: Map<String, SteamFriendRelationshipRecord>,
        profiles: Map<String, SteamFriendProfile>
    ): List<SteamFriend> = relationships.values.map { relationship ->
        val profile = profiles[relationship.steamId]
        SteamFriend(
            steamId = relationship.steamId,
            relationship = relationship.relationship,
            friendSince = relationship.friendSince,
            nickname = relationship.nickname,
            personaName = profile?.personaName.orEmpty(),
            realName = profile?.realName.orEmpty(),
            avatarUrl = profile?.avatarUrl.orEmpty(),
            profileUrl = profile?.profileUrl.orEmpty(),
            personaState = profile?.personaState ?: SteamPersonaState.OFFLINE,
            lastLogoff = profile?.lastLogoff ?: 0L,
            gameId = profile?.gameId.orEmpty(),
            gameName = profile?.gameName.orEmpty(),
            primaryClanId = profile?.primaryClanId.orEmpty(),
            countryCode = profile?.countryCode.orEmpty()
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: return ""
        return primitive.contentOrNull.orEmpty()
    }
    private fun JsonObject.int(key: String): Int {
        val primitive = this[key] as? JsonPrimitive ?: return 0
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: 0
    }
    private fun JsonObject.long(key: String): Long {
        val primitive = this[key] as? JsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
    }
}
