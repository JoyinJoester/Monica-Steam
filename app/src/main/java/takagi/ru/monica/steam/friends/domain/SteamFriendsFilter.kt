package takagi.ru.monica.steam.friends.domain

enum class SteamFriendsFilter {
    ALL,
    ONLINE,
    PLAYING,
    REQUESTS
}

fun filterSteamFriends(
    friends: List<SteamFriend>,
    query: String,
    filter: SteamFriendsFilter
): List<SteamFriend> {
    val needle = query.trim().lowercase()
    return friends.asSequence()
        .filter { friend ->
            when (filter) {
                SteamFriendsFilter.ALL -> friend.relationship == SteamFriendRelationship.FRIEND
                SteamFriendsFilter.ONLINE -> friend.relationship == SteamFriendRelationship.FRIEND &&
                    (friend.personaState.isOnline || friend.isPlaying)
                SteamFriendsFilter.PLAYING -> friend.relationship == SteamFriendRelationship.FRIEND &&
                    friend.isPlaying
                SteamFriendsFilter.REQUESTS -> friend.relationship == SteamFriendRelationship.REQUEST_INCOMING ||
                    friend.relationship == SteamFriendRelationship.REQUEST_OUTGOING
            }
        }
        .filter { friend ->
            needle.isBlank() || sequenceOf(
                friend.displayName,
                friend.personaName,
                friend.realName,
                friend.steamId,
                friend.gameName
            ).any { it.lowercase().contains(needle) }
        }
        .sortedWith(
            compareByDescending<SteamFriend> {
                it.relationship == SteamFriendRelationship.REQUEST_INCOMING
            }
                .thenByDescending(SteamFriend::isPlaying)
                .thenByDescending { it.personaState.isOnline }
                .thenBy { it.displayName.lowercase() }
        )
        .toList()
}
