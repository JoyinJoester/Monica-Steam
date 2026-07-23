package takagi.ru.monica.steam.friends.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot

interface SteamFriendsCache {
    fun load(accountKey: String): SteamFriendsSnapshot?
    fun save(accountKey: String, snapshot: SteamFriendsSnapshot)
}

class SteamFriendsPreferencesCache(context: Context) : SteamFriendsCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        "steam_friends_cache",
        Context.MODE_PRIVATE
    )

    override fun load(accountKey: String): SteamFriendsSnapshot? {
        val raw = preferences.getString(cacheKey(accountKey), null) ?: return null
        return SteamFriendsCacheCodec.decode(raw)
    }

    override fun save(accountKey: String, snapshot: SteamFriendsSnapshot) {
        preferences.edit()
            .putString(cacheKey(accountKey), SteamFriendsCacheCodec.encode(snapshot))
            .apply()
    }

    private fun cacheKey(accountKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(accountKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

internal object SteamFriendsCacheCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: SteamFriendsSnapshot): String =
        json.encodeToString(SteamFriendsSnapshot.serializer(), snapshot)

    fun decode(raw: String): SteamFriendsSnapshot? = runCatching {
        json.decodeFromString(SteamFriendsSnapshot.serializer(), raw)
    }.getOrNull()
}
