package takagi.ru.monica.steam.friends.groupchat.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.friends.cache.boundedSteamMessageCache
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGroupsSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatThreadSnapshot

interface SteamGroupChatCache {
    fun loadGroups(accountSteamId: String): SteamGroupChatGroupsSnapshot?
    fun saveGroups(snapshot: SteamGroupChatGroupsSnapshot)
    fun loadThread(accountSteamId: String, groupId: String, chatId: String): SteamGroupChatThreadSnapshot?
    fun saveThread(snapshot: SteamGroupChatThreadSnapshot)
}

class SteamGroupChatPreferencesCache(context: Context) : SteamGroupChatCache {
    private val preferences = context.applicationContext.getSharedPreferences("steam_group_chat_cache", Context.MODE_PRIVATE)
    private val security = SecurityManager(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun loadGroups(accountSteamId: String): SteamGroupChatGroupsSnapshot? =
        load(key("groups|$accountSteamId")) {
            json.decodeFromString(SteamGroupChatGroupsSnapshot.serializer(), it)
        }?.takeIf { it.accountSteamId == accountSteamId }

    override fun saveGroups(snapshot: SteamGroupChatGroupsSnapshot) = save(
        key("groups|${snapshot.accountSteamId}"),
        json.encodeToString(SteamGroupChatGroupsSnapshot.serializer(), snapshot)
    )

    override fun loadThread(accountSteamId: String, groupId: String, chatId: String): SteamGroupChatThreadSnapshot? =
        load(key("thread|$accountSteamId|$groupId|$chatId")) {
            json.decodeFromString(SteamGroupChatThreadSnapshot.serializer(), it)
        }?.takeIf { it.accountSteamId == accountSteamId && it.groupId == groupId && it.chatId == chatId }
            ?.let { snapshot ->
                snapshot.copy(messages = snapshot.messages.map { message ->
                    when (message.deliveryState) {
                        SteamGroupChatDeliveryState.SENT,
                        SteamGroupChatDeliveryState.FAILED_RETRYABLE,
                        SteamGroupChatDeliveryState.FAILED_PERMANENT -> message
                        SteamGroupChatDeliveryState.QUEUED,
                        SteamGroupChatDeliveryState.SENDING,
                        SteamGroupChatDeliveryState.VERIFYING,
                        SteamGroupChatDeliveryState.FAILED -> message.copy(
                            deliveryState = SteamGroupChatDeliveryState.FAILED_RETRYABLE
                        )
                    }
                })
            }
            ?.let(::boundSteamGroupChatThreadForCache)

    override fun saveThread(snapshot: SteamGroupChatThreadSnapshot) {
        val bounded = boundSteamGroupChatThreadForCache(snapshot)
        save(
            key("thread|${snapshot.accountSteamId}|${snapshot.groupId}|${snapshot.chatId}"),
            json.encodeToString(SteamGroupChatThreadSnapshot.serializer(), bounded)
        )
    }

    private fun <T> load(key: String, decode: (String) -> T): T? = runCatching {
        preferences.getString(key, null)?.let(security::decryptDataIfMonicaCiphertext)?.let(decode)
    }.getOrNull()

    private fun save(key: String, value: String) {
        runCatching {
            synchronized(preferences) {
                preferences.edit().putString(key, security.encryptDataLegacyCompat(value)).apply()
            }
        }
    }

    private fun key(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal fun boundSteamGroupChatThreadForCache(
    snapshot: SteamGroupChatThreadSnapshot
): SteamGroupChatThreadSnapshot {
    val boundedMessages = boundedSteamMessageCache(snapshot.messages) { message ->
        message.deliveryState != SteamGroupChatDeliveryState.SENT
    }
    return snapshot.copy(
        messages = boundedMessages,
        moreAvailable = snapshot.moreAvailable || boundedMessages.size < snapshot.messages.size
    )
}
