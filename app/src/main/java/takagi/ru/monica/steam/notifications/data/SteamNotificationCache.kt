package takagi.ru.monica.steam.notifications.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.notifications.domain.*

interface SteamNotificationCache {
    fun load(accountKey: String): SteamNotificationSnapshot?
    fun save(accountKey: String, snapshot: SteamNotificationSnapshot)
}

class SteamNotificationPreferencesCache(context: Context) : SteamNotificationCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        "steam_notification_cache",
        Context.MODE_PRIVATE
    )
    override fun load(accountKey: String): SteamNotificationSnapshot? {
        val raw = preferences.getString(cacheKey(accountKey), null) ?: return null
        return SteamNotificationCacheCodec.decode(raw)
    }

    override fun save(accountKey: String, snapshot: SteamNotificationSnapshot) {
        preferences.edit()
            .putString(cacheKey(accountKey), SteamNotificationCacheCodec.encode(snapshot))
            .apply()
    }

    private fun cacheKey(accountKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(accountKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

internal object SteamNotificationCacheCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: SteamNotificationSnapshot): String =
        json.encodeToString(SteamNotificationSnapshot.serializer(), snapshot)

    fun decode(raw: String): SteamNotificationSnapshot? = runCatching {
        json.decodeFromString(SteamNotificationSnapshot.serializer(), raw)
    }.getOrNull()
}
