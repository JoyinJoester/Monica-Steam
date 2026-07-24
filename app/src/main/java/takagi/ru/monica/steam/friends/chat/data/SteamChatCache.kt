package takagi.ru.monica.steam.friends.chat.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot

interface SteamChatCache {
    fun loadSessions(accountSteamId: String): SteamChatSessionsSnapshot?
    fun saveSessions(accountSteamId: String, snapshot: SteamChatSessionsSnapshot)
    fun loadThread(accountSteamId: String, partnerSteamId: String): SteamChatThreadSnapshot?
    fun saveThread(accountSteamId: String, partnerSteamId: String, snapshot: SteamChatThreadSnapshot)
}

class SteamChatPreferencesCache private constructor(
    private val store: SteamChatKeyValueStore,
    private val cipher: SteamChatCipher
) : SteamChatCache {
    constructor(context: Context) : this(
        store = SharedPreferencesSteamChatStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        ),
        cipher = MonicaSteamChatCipher(SecurityManager(context.applicationContext))
    )

    internal constructor(
        store: SteamChatKeyValueStore,
        encrypt: (String) -> String,
        decrypt: (String) -> String
    ) : this(store, LambdaSteamChatCipher(encrypt, decrypt))

    override fun loadSessions(accountSteamId: String): SteamChatSessionsSnapshot? =
        load(sessionsKey(accountSteamId), SteamChatCacheCodec::decodeSessions)
            ?.takeIf { it.accountSteamId == accountSteamId }

    override fun saveSessions(accountSteamId: String, snapshot: SteamChatSessionsSnapshot) {
        if (snapshot.accountSteamId != accountSteamId) return
        save(sessionsKey(accountSteamId), SteamChatCacheCodec.encodeSessions(snapshot))
    }

    override fun loadThread(
        accountSteamId: String,
        partnerSteamId: String
    ): SteamChatThreadSnapshot? = load(
        threadKey(accountSteamId, partnerSteamId),
        SteamChatCacheCodec::decodeThread
    )?.takeIf {
        it.accountSteamId == accountSteamId && it.partnerSteamId == partnerSteamId
    }?.let { snapshot ->
        snapshot.copy(
            messages = snapshot.messages.map { message ->
                if (message.deliveryState == SteamChatDeliveryState.PENDING) {
                    message.copy(deliveryState = SteamChatDeliveryState.FAILED)
                } else {
                    message
                }
            }
        )
    }

    override fun saveThread(
        accountSteamId: String,
        partnerSteamId: String,
        snapshot: SteamChatThreadSnapshot
    ) {
        if (snapshot.accountSteamId != accountSteamId || snapshot.partnerSteamId != partnerSteamId) {
            return
        }
        save(threadKey(accountSteamId, partnerSteamId), SteamChatCacheCodec.encodeThread(snapshot))
    }

    private fun <T> load(key: String, decode: (String) -> T?): T? = runCatching {
        val encrypted = store.get(key) ?: return null
        decode(cipher.decrypt(encrypted))
    }.getOrNull()

    private fun save(key: String, raw: String) {
        runCatching { store.put(key, cipher.encrypt(raw)) }
    }

    private fun sessionsKey(accountSteamId: String): String = hashedKey("sessions|$accountSteamId")

    private fun threadKey(accountSteamId: String, partnerSteamId: String): String =
        hashedKey("thread|$accountSteamId|$partnerSteamId")

    private fun hashedKey(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val PREFERENCES_NAME = "steam_friend_chat_cache"
    }
}

internal interface SteamChatKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

private class SharedPreferencesSteamChatStore(
    private val preferences: SharedPreferences
) : SteamChatKeyValueStore {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

private interface SteamChatCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

private class MonicaSteamChatCipher(
    private val securityManager: SecurityManager
) : SteamChatCipher {
    override fun encrypt(value: String): String = securityManager.encryptDataLegacyCompat(value)

    override fun decrypt(value: String): String = securityManager.decryptDataIfMonicaCiphertext(value)
}

private class LambdaSteamChatCipher(
    private val encryptBlock: (String) -> String,
    private val decryptBlock: (String) -> String
) : SteamChatCipher {
    override fun encrypt(value: String): String = encryptBlock(value)
    override fun decrypt(value: String): String = decryptBlock(value)
}

internal object SteamChatCacheCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeSessions(snapshot: SteamChatSessionsSnapshot): String =
        json.encodeToString(SteamChatSessionsSnapshot.serializer(), snapshot)

    fun decodeSessions(raw: String): SteamChatSessionsSnapshot? = runCatching {
        json.decodeFromString(SteamChatSessionsSnapshot.serializer(), raw)
    }.getOrNull()

    fun encodeThread(snapshot: SteamChatThreadSnapshot): String =
        json.encodeToString(SteamChatThreadSnapshot.serializer(), snapshot)

    fun decodeThread(raw: String): SteamChatThreadSnapshot? = runCatching {
        json.decodeFromString(SteamChatThreadSnapshot.serializer(), raw)
    }.getOrNull()
}
