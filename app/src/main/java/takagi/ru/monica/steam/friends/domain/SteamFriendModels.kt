package takagi.ru.monica.steam.friends.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SteamFriendRelationship {
    FRIEND,
    REQUEST_INCOMING,
    REQUEST_OUTGOING,
    BLOCKED,
    UNKNOWN;

    companion object {
        fun fromRaw(value: String): SteamFriendRelationship = when (value.trim().lowercase()) {
            "3", "friend" -> FRIEND
            // Steam names the relationship after the other party: when the
            // other account is the recipient, the current account sent it.
            "2", "requestrecipient", "request_recipient" -> REQUEST_OUTGOING
            "4", "requestinitiator", "request_initiator" -> REQUEST_INCOMING
            "1", "5", "6", "blocked", "ignored", "ignoredfriend", "ignored_friend" -> BLOCKED
            else -> UNKNOWN
        }
    }
}

@Serializable
enum class SteamPersonaState {
    OFFLINE,
    ONLINE,
    BUSY,
    AWAY,
    SNOOZE,
    LOOKING_TO_TRADE,
    LOOKING_TO_PLAY,
    INVISIBLE;

    val isOnline: Boolean
        get() = this != OFFLINE && this != INVISIBLE

    companion object {
        fun fromCode(code: Int): SteamPersonaState = when (code) {
            1 -> ONLINE
            2 -> BUSY
            3 -> AWAY
            4 -> SNOOZE
            5 -> LOOKING_TO_TRADE
            6 -> LOOKING_TO_PLAY
            7 -> INVISIBLE
            else -> OFFLINE
        }
    }
}

@Serializable
data class SteamFriend(
    val steamId: String,
    val relationship: SteamFriendRelationship = SteamFriendRelationship.UNKNOWN,
    val friendSince: Long = 0L,
    val nickname: String = "",
    val personaName: String = "",
    val realName: String = "",
    val avatarUrl: String = "",
    val profileUrl: String = "",
    val personaState: SteamPersonaState = SteamPersonaState.OFFLINE,
    val lastLogoff: Long = 0L,
    val gameId: String = "",
    val gameName: String = "",
    val primaryClanId: String = "",
    val countryCode: String = ""
) {
    val displayName: String
        get() = nickname.ifBlank { personaName }.ifBlank { steamId }

    val isPlaying: Boolean
        get() = gameId.isNotBlank() || gameName.isNotBlank()
}

@Serializable
data class SteamFriendsSnapshot(
    val friends: List<SteamFriend> = emptyList(),
    val fetchedAt: Long = 0L
) {
    val acceptedFriends: List<SteamFriend>
        get() = friends.filter { it.relationship == SteamFriendRelationship.FRIEND }

    val incomingRequests: List<SteamFriend>
        get() = friends.filter { it.relationship == SteamFriendRelationship.REQUEST_INCOMING }

    val outgoingRequests: List<SteamFriend>
        get() = friends.filter { it.relationship == SteamFriendRelationship.REQUEST_OUTGOING }

    val onlineCount: Int
        get() = acceptedFriends.count { it.personaState.isOnline || it.isPlaying }
}

data class SteamFriendActionResult(
    val success: Boolean,
    val message: String? = null
)
