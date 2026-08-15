package takagi.ru.monica.steam.profile.viewer.data

import kotlinx.serialization.json.JsonObject
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter

internal interface SteamProfileViewerRemote {
    fun fetchProfileSummary(accessToken: String, targetSteamId: String): JsonObject
    fun fetchSteamLevel(accessToken: String, targetSteamId: Long): ByteArray
    fun fetchOwnedGames(accessToken: String, targetSteamId: Long, language: String): ByteArray
    fun fetchAchievementProgress(
        accessToken: String,
        targetSteamId: Long,
        appIds: List<Int>,
        language: String
    ): ByteArray
    fun fetchAchievementDefinitions(accessToken: String, appId: Int, language: String): ByteArray
    fun fetchUserAchievements(accessToken: String, targetSteamId: Long, appId: Int): ByteArray
    fun fetchCommunityProfile(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String
    fun fetchCommunityFriends(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String
    fun fetchCommunityGroups(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String
    fun fetchBadges(accessToken: String, targetSteamId: String): JsonObject
    fun fetchBadgePage(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String,
        page: Int
    ): String
}

internal class SteamProfileViewerSteamRemote(
    private val api: SteamApiClient = SteamApiClient()
) : SteamProfileViewerRemote {
    override fun fetchProfileSummary(
        accessToken: String,
        targetSteamId: String
    ): JsonObject = api.steamApiGetJson(
        path = "/ISteamUserOAuth/GetUserSummaries/v1/",
        query = mapOf("steamids" to targetSteamId),
        accessToken = accessToken
    )

    override fun fetchSteamLevel(accessToken: String, targetSteamId: Long): ByteArray =
        api.callProtobuf(
            iface = "IPlayerService",
            method = "GetSteamLevel",
            request = SteamProtoWriter().apply { writeUint64(1, targetSteamId) },
            accessToken = accessToken,
            useGet = true
        )

    override fun fetchOwnedGames(
        accessToken: String,
        targetSteamId: Long,
        language: String
    ): ByteArray = api.callProtobuf(
        iface = "IPlayerService",
        method = "GetOwnedGames",
        request = SteamProtoWriter().apply {
            writeUint64(1, targetSteamId)
            writeBool(2, true)
            writeBool(3, true)
            writeString(7, language)
        },
        accessToken = accessToken,
        useGet = true
    )

    override fun fetchAchievementProgress(
        accessToken: String,
        targetSteamId: Long,
        appIds: List<Int>,
        language: String
    ): ByteArray = api.callProtobuf(
        iface = "IPlayerService",
        method = "GetAchievementsProgress",
        request = SteamProtoWriter().apply {
            writeUint64(1, targetSteamId)
            writeString(2, language)
            appIds.forEach { appId ->
                writeVarint(3, appId.toLong())
            }
            writeBool(4, true)
        },
        accessToken = accessToken,
        useGet = false
    )

    override fun fetchAchievementDefinitions(
        accessToken: String,
        appId: Int,
        language: String
    ): ByteArray = api.callProtobuf(
        iface = "IPlayerService",
        method = "GetGameAchievements",
        request = SteamProtoWriter().apply {
            writeVarint(1, appId.toLong())
            writeString(2, language)
        },
        accessToken = accessToken,
        useGet = true
    )

    override fun fetchUserAchievements(
        accessToken: String,
        targetSteamId: Long,
        appId: Int
    ): ByteArray = api.callProtobuf(
        iface = "IPlayerService",
        method = "GetUserAchievements",
        request = SteamProtoWriter().apply {
            writeUint64(1, targetSteamId)
            writeVarint(2, appId.toLong())
        },
        accessToken = accessToken,
        useGet = true
    )

    override fun fetchCommunityProfile(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String = api.communityGetText(
        path = "/profiles/$targetSteamId/",
        query = mapOf("l" to language),
        cookies = communityCookies(viewer),
        referer = "https://steamcommunity.com/profiles/$targetSteamId/"
    )

    override fun fetchCommunityFriends(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String = api.communityGetText(
        path = "/profiles/$targetSteamId/friends/",
        query = mapOf("l" to language),
        cookies = communityCookies(viewer),
        referer = "https://steamcommunity.com/profiles/$targetSteamId/"
    )

    override fun fetchCommunityGroups(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String
    ): String = api.communityGetText(
        path = "/profiles/$targetSteamId/groups/",
        query = mapOf("l" to language),
        cookies = communityCookies(viewer),
        referer = "https://steamcommunity.com/profiles/$targetSteamId/"
    )

    override fun fetchBadges(accessToken: String, targetSteamId: String): JsonObject =
        api.steamApiGetJson(
            path = "/IPlayerService/GetBadges/v1/",
            query = mapOf("steamid" to targetSteamId),
            accessToken = accessToken
        )

    override fun fetchBadgePage(
        viewer: SteamAccount,
        targetSteamId: String,
        language: String,
        page: Int
    ): String = api.communityGetText(
        path = "/profiles/$targetSteamId/badges/",
        query = mapOf("p" to page.toString(), "l" to language),
        cookies = communityCookies(viewer),
        referer = "https://steamcommunity.com/profiles/$targetSteamId/"
    )

    private fun communityCookies(account: SteamAccount): Map<String, String> =
        SteamInventoryService.marketCookies(
            account = account,
            sessionId = SteamInventoryService.newSessionId()
        )
}
