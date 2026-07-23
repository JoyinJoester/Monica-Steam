package takagi.ru.monica.steam.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamFriendsModelsTest {
    @Test
    fun cacheKeepsPresenceRequestsAndCurrentGame() {
        val original = SteamFriendsSnapshot(
            friends = listOf(
                friend(
                    id = "76561198000000002",
                    name = "Alyx",
                    state = SteamPersonaState.ONLINE,
                    gameName = "Half-Life 2"
                ),
                friend(
                    id = "76561198000000003",
                    name = "Gordon",
                    relationship = SteamFriendRelationship.REQUEST_INCOMING
                )
            ),
            fetchedAt = 99L
        )

        val restored = SteamFriendsCacheCodec.decode(
            SteamFriendsCacheCodec.encode(original)
        )!!

        assertEquals(original, restored)
        assertEquals(1, restored.onlineCount)
        assertEquals(1, restored.incomingRequests.size)
        assertEquals("Half-Life 2", restored.acceptedFriends.single().gameName)
    }

    @Test
    fun filtersPrioritizePlayingFriendsAndSearchCurrentGame() {
        val friends = listOf(
            friend("76561198000000002", "Offline"),
            friend("76561198000000003", "Playing", SteamPersonaState.ONLINE, gameName = "Portal 2"),
            friend("76561198000000004", "Online", SteamPersonaState.ONLINE),
            friend(
                "76561198000000005",
                "Pending",
                relationship = SteamFriendRelationship.REQUEST_INCOMING
            )
        )

        assertEquals(
            listOf("Playing", "Online", "Offline"),
            filterSteamFriends(friends, "", SteamFriendsFilter.ALL).map(SteamFriend::displayName)
        )
        assertEquals(
            listOf("Playing"),
            filterSteamFriends(friends, "portal", SteamFriendsFilter.ALL).map(SteamFriend::displayName)
        )
        assertEquals(2, filterSteamFriends(friends, "", SteamFriendsFilter.ONLINE).size)
        assertEquals(1, filterSteamFriends(friends, "", SteamFriendsFilter.PLAYING).size)
        assertTrue(
            filterSteamFriends(friends, "", SteamFriendsFilter.REQUESTS)
                .single().relationship == SteamFriendRelationship.REQUEST_INCOMING
        )
    }

    private fun friend(
        id: String,
        name: String,
        state: SteamPersonaState = SteamPersonaState.OFFLINE,
        gameName: String = "",
        relationship: SteamFriendRelationship = SteamFriendRelationship.FRIEND
    ) = SteamFriend(
        steamId = id,
        relationship = relationship,
        personaName = name,
        personaState = state,
        gameName = gameName
    )
}
