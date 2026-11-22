package takagi.ru.monica.steam.friends.chat.info.domain

import kotlinx.serialization.Serializable

enum class SteamChatConversationType { DIRECT, GROUP }

data class SteamChatConversationId(
    val accountSteamId: String,
    val type: SteamChatConversationType,
    val peerOrGroupId: String,
    val chatId: String = ""
) {
    val storageIdentity: String
        get() = listOf(accountSteamId, type.name, peerOrGroupId, chatId).joinToString("|")
}

@Serializable
enum class SteamChatWallpaperPreset(val patternStrength: Float) {
    TELEGRAM_PATTERN(1f), TELEGRAM_SOFT(.45f), THEME_PLAIN(0f)
}

@Serializable
data class SteamChatConversationPreferences(
    val muted: Boolean = false,
    val pinned: Boolean = false,
    val wallpaper: SteamChatWallpaperPreset = SteamChatWallpaperPreset.TELEGRAM_PATTERN,
    /** Moved to the archive tab instead of the main inbox. */
    val archived: Boolean = false,
    /**
     * Epoch seconds of the local "delete conversation" action. The row stays hidden while its
     * newest message is not newer than this, so an incoming message brings the chat back.
     */
    val hiddenAt: Long = 0L
) {
    val hidden: Boolean get() = hiddenAt > 0L
}

// SteamChatHistoryItem lives in SteamChatHistoryCategories.kt next to its classifier.
