package takagi.ru.monica.steam.friends.chat.info.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationType

class SteamChatInfoPreferencesStoreTest {
    @Test fun oldPreferencesKeepMuteAndPinAndDefaultToTelegramPattern() {
        val old = kotlinx.serialization.json.Json.decodeFromString(
            takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences.serializer(),
            "{\"muted\":true,\"pinned\":true}"
        )
        assertEquals(true, old.muted); assertEquals(true, old.pinned)
        assertEquals(takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset.TELEGRAM_PATTERN, old.wallpaper)
    }
    @Test fun wallpaperPresetsRoundTripWithoutChangingOtherPreferences() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        for (preset in takagi.ru.monica.steam.friends.chat.info.domain.SteamChatWallpaperPreset.entries) {
            val value = takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences(true, true, preset)
            val encoded = json.encodeToString(takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences.serializer(), value)
            assertEquals(value, json.decodeFromString(takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences.serializer(), encoded))
        }
    }
    @Test
    fun conversationKeyIsStableAndIsolatesAccountAndConversation() {
        val direct = SteamChatConversationId("account-a", SteamChatConversationType.DIRECT, "friend-a")

        assertEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(direct.copy())
        )
        assertNotEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(direct.copy(accountSteamId = "account-b"))
        )
        assertNotEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(direct.copy(peerOrGroupId = "friend-b"))
        )
        assertNotEquals(
            SteamChatInfoPreferencesStore.conversationKey(direct),
            SteamChatInfoPreferencesStore.conversationKey(
                direct.copy(type = SteamChatConversationType.GROUP, chatId = "room-a")
            )
        )
    }
}
