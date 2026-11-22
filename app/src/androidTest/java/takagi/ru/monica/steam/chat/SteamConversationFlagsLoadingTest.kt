package takagi.ru.monica.steam.chat

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.*
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.*
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.ui.*
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState

class SteamConversationFlagsLoadingTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()

    @Test fun pendingFlagsHideRowsAndLateAccountReadCannotReplaceNewAccount() {
        val base = compose.activity.applicationContext
        val suffix = java.util.UUID.randomUUID().toString()
        val a = "flags-a-$suffix"
        val b = "flags-b-$suffix"
        val idA = SteamChatConversationId(a, SteamChatConversationType.DIRECT, "peer")
        val idB = idA.copy(accountSteamId = b)
        val realStore = SteamChatInfoPreferencesStore(base)
        realStore.save(idA, SteamChatConversationPreferences(hiddenAt = 200))
        realStore.save(idB, SteamChatConversationPreferences(pinned = true))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val offMain = AtomicBoolean(true)
        val wrapped = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val delegate = super.getSharedPreferences(name, mode)
                if (name != "steam_chat_conversation_preferences") return delegate
                return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
                    arrayOf(SharedPreferences::class.java)) { _, method, args ->
                    if (method.name == "getString") {
                        if (Looper.myLooper() == Looper.getMainLooper()) offMain.set(false)
                        if (args!![0] == realStore.key(idA)) {
                            entered.countDown()
                            release.await(5, TimeUnit.SECONDS)
                            finished.countDown()
                        }
                    }
                    method.invoke(delegate, *(args ?: emptyArray()))
                } as SharedPreferences
            }
        }
        val store = SteamChatInfoPreferencesStore(wrapped)
        var account by mutableStateOf(a)
        var latest = SteamConversationFlags(loaded = false)
        var rows = emptyList<SteamConversationListEntry>()
        try {
            compose.setContent {
                val chat = SteamChatUiState(accountSteamId = account,
                    sessions = SteamChatSessionsSnapshot(account, listOf(SteamChatSession("peer", 100)), 0))
                val groups = SteamGroupChatUiState()
                val local = rememberSteamConversationLocalState(store, chat, groups)
                val entries = rememberSteamConversationEntries(chat, groups, emptyList(), "", local.flags, SteamVoiceCallState())
                SideEffect { latest = local.flags; rows = entries }
                Text(entries.joinToString { it.id })
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            compose.runOnIdle {
                assertFalse(latest.loaded)
                assertTrue("Never flash hidden/archived rows before their flags are ready", rows.isEmpty())
                account = b
            }
            compose.waitUntil(5_000) { latest.loaded && rows.singleOrNull()?.pinned == true }
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            compose.runOnIdle {
                assertEquals(setOf("peer"), latest.pinnedDirectIds)
                assertTrue(latest.hiddenDirectAt.isEmpty())
                assertEquals("peer", rows.single().id)
            }
            assertTrue("Encrypted preferences must be read off the UI thread", offMain.get())
        } finally { release.countDown() }
    }
}
