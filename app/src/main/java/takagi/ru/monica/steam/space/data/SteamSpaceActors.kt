package takagi.ru.monica.steam.space.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.data.SteamFriendsParser
import takagi.ru.monica.steam.network.SteamApiClient

data class SteamSpaceActor(val name: String, val avatarUrl: String)

internal fun loadSteamSpaceActors(account: SteamAccount, ids: List<String>): Map<String, SteamSpaceActor> {
    require(ids.size <= 40)
    val payload = SteamApiClient().steamApiGetJson(
        path = "/ISteamUserOAuth/GetUserSummaries/v1/",
        query = mapOf("steamids" to ids.joinToString(",")), accessToken = requireNotNull(account.accessToken))
    return SteamFriendsParser.parseProfiles(payload).filter { it.steamId in ids }
        .associate { it.steamId to SteamSpaceActor(it.personaName.take(512), SteamSpaceLinks.image(it.avatarUrl).orEmpty()) }
}
