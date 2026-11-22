package takagi.ru.monica.steam.chat

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.data.SteamChatPreferencesCache
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.*
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatPreferencesCache
import takagi.ru.monica.steam.outbox.data.SteamOutboxStore

class SteamChatStorageInitializationTest {
    private class ObservedContext(base: Context) : ContextWrapper(base) {
        var secureOpens = 0
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (name == "monica_secure_prefs") secureOpens++
            return super.getSharedPreferences(name, mode)
        }
    }

    @Test fun constructingInboxDependenciesAndReadingMissingRowsDoesNotOpenEncryptedStorage() {
        val context = ObservedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val id = "missing-${java.util.UUID.randomUUID()}"
        val direct = SteamChatPreferencesCache(context)
        val groups = SteamGroupChatPreferencesCache(context)
        val preferences = SteamChatInfoPreferencesStore(context)
        SteamOutboxStore.from(context)
        assertEquals(0, context.secureOpens)
        assertNull(direct.loadSessions(id))
        assertNull(groups.loadGroups(id))
        assertFalse(preferences.load(SteamChatConversationId(id, SteamChatConversationType.DIRECT, id)).pinned)
        assertEquals(0, context.secureOpens)
    }

    @Test fun firstRealWriteStillEncryptsAndColdStoreReadsOriginalValue() {
        val context = ObservedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val account = "storage-fixture-${java.util.UUID.randomUUID()}"
        val snapshot = SteamChatSessionsSnapshot(account, emptyList(), 123)
        SteamChatPreferencesCache(context).saveSessions(account, snapshot)
        assertTrue(context.secureOpens > 0)
        assertEquals(snapshot, SteamChatPreferencesCache(context).loadSessions(account))
        assertFalse(context.getSharedPreferences("steam_friend_chat_cache", Context.MODE_PRIVATE)
            .all.values.any { it.toString().contains(account) })
        val id = SteamChatConversationId(account, SteamChatConversationType.DIRECT, "peer")
        val prefs = SteamChatConversationPreferences(pinned = true, archived = true, hiddenAt = 200)
        SteamChatInfoPreferencesStore(context).save(id, prefs)
        assertEquals(prefs, SteamChatInfoPreferencesStore(context).load(id))
    }
}
