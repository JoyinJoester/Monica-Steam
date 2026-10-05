package takagi.ru.monica.steam.friends.chat.info.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences

class SteamChatInfoPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val security = SecurityManager(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(id: SteamChatConversationId): SteamChatConversationPreferences = runCatching {
        preferences.getString(key(id), null)
            ?.let(security::decryptDataIfMonicaCiphertext)
            ?.let { json.decodeFromString(SteamChatConversationPreferences.serializer(), it) }
    }.getOrNull() ?: SteamChatConversationPreferences()

    fun save(id: SteamChatConversationId, value: SteamChatConversationPreferences) {
        runCatching {
            val encoded = json.encodeToString(SteamChatConversationPreferences.serializer(), value)
            synchronized(preferences) {
                preferences.edit().putString(key(id), security.encryptDataLegacyCompat(encoded)).apply()
            }
        }
    }

    internal fun key(id: SteamChatConversationId): String = conversationKey(id)

    companion object {
        private const val PREFERENCES_NAME = "steam_chat_conversation_preferences"

        internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        internal fun conversationKey(id: SteamChatConversationId): String = sha256(id.storageIdentity)
    }
}
