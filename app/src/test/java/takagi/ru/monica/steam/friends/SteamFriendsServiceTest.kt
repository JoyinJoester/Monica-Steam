package takagi.ru.monica.steam.friends

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient

class SteamFriendsServiceTest {
    @Test
    fun fetchesOAuthFriendsAndMergesProfiles() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val payload = when {
                    request.url.encodedPath.contains("GetFriendList") -> """{
                        "friendslist":{"friends":[
                          {"steamid":"76561198000000002","relationship":"friend","friend_since":100},
                          {"steamid":"76561198000000003","relationship":"requestinitiator","friend_since":200}
                        ]}
                    }"""
                    request.url.encodedPath.contains("GetUserSummaries") -> """{
                        "response":{"players":[
                          {"steamid":"76561198000000002","personaname":"Alyx","avatarfull":"https://avatars.cloudflare.steamstatic.com/a.jpg","personastate":1,"gameid":"730","gameextrainfo":"Counter-Strike 2"},
                          {"steamid":"76561198000000003","personaname":"Gordon","personastate":0,"lastlogoff":123}
                        ]}
                    }"""
                    else -> error("Unexpected request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val snapshot = SteamFriendsService(SteamApiClient(client))
            .fetch(account(), fetchedAt = 42L)

        assertEquals(42L, snapshot.fetchedAt)
        assertEquals(2, snapshot.friends.size)
        assertEquals(1, snapshot.acceptedFriends.size)
        assertEquals(1, snapshot.incomingRequests.size)
        assertEquals(1, snapshot.onlineCount)
        val playing = snapshot.acceptedFriends.single()
        assertEquals("Alyx", playing.displayName)
        assertEquals("Counter-Strike 2", playing.gameName)
        assertTrue(playing.isPlaying)
        assertEquals(
            listOf(
                "/ISteamUserOAuth/GetFriendList/v1/",
                "/ISteamUserOAuth/GetUserSummaries/v1/"
            ),
            requests.map { it.url.encodedPath }
        )
        assertTrue(requests.all { it.url.queryParameter("access_token") == "access-token" })
        assertEquals("all", requests.first().url.queryParameter("relationship"))
    }

    @Test
    fun parserAcceptsNumericRelationshipCodesAndMissingProfiles() {
        val relationships = SteamFriendsParser.parseRelationships(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"response":{"friends":[{"ulfriendid":"76561198000000004","efriendrelationship":2,"time_created":9}]}}"""
            ).jsonObject
        )

        val friend = SteamFriendsParser.merge(relationships, emptyMap()).single()

        assertEquals(SteamFriendRelationship.REQUEST_OUTGOING, friend.relationship)
        assertEquals(9L, friend.friendSince)
        assertEquals("76561198000000004", friend.displayName)
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "steam_user",
        displayName = "steam_user",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}
