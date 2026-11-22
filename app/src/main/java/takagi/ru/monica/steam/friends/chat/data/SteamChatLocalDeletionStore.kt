package takagi.ru.monica.steam.friends.chat.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage

/** Local visibility markers, not Steam retractions or secure removal of the message cache. */
internal class SteamChatLocalDeletionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "steam_chat_local_message_deletions", Context.MODE_PRIVATE
    )

    fun load(account: String, partner: String): Set<String> = synchronized(lock) {
        preferences.getStringSet(conversationKey(account, partner), emptySet()).orEmpty().toSet()
    }

    fun observe(account: String, partner: String, onChanged: () -> Unit): () -> Unit {
        val key = conversationKey(account, partner)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) onChanged()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Merge under one lock so two open views cannot discard each other's deletion markers. */
    fun hide(account: String, partner: String, message: SteamChatMessage): Set<String> = synchronized(lock) {
        require(message.partnerSteamId == partner)
        val marker = requireNotNull(steamChatLocalDeletionKey(message))
        val key = conversationKey(account, partner)
        val updated = preferences.getStringSet(key, emptySet()).orEmpty().toSet() + marker
        check(preferences.edit().putStringSet(key, updated).commit())
        updated
    }

    companion object {
        private val lock = Any()

        internal fun conversationKey(account: String, partner: String): String {
            require(account.isNotBlank() && partner.isNotBlank())
            return sha256("${account.length}:$account|${partner.length}:$partner")
        }
    }
}

/** Server identity survives local outbox reconciliation and later history reloads. */
internal fun steamChatLocalDeletionKey(message: SteamChatMessage): String? =
    if (message.deliveryState == SteamChatDeliveryState.SENT && message.timestamp > 0 &&
        message.ordinal >= 0 && message.ordinal != Int.MAX_VALUE && message.senderSteamId.isNotBlank()
    ) sha256("${message.timestamp}|${message.ordinal}|${message.senderSteamId}") else null

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 15])
        }
    }
}
