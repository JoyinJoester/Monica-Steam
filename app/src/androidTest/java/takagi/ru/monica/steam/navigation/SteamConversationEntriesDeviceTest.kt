package takagi.ru.monica.steam.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.ui.*
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceParticipant
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTarget
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType

/** Exercise the exact list projection used by the real inbox across state emissions. */
class SteamConversationEntriesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val initial = SteamChatUiState(sessions = SteamChatSessionsSnapshot(
        "fixture", listOf(SteamChatSession("Synthetic partner", 100, 0, 2)), 100))
    private val state = mutableStateOf(initial)
    private val query = mutableStateOf("")
    private val flags = mutableStateOf(SteamConversationFlags())
    private val friends = mutableStateOf(emptyList<SteamFriend>())
    private val groups = mutableStateOf(SteamGroupChatUiState())
    private val voice = mutableStateOf(SteamVoiceCallState())
    private var rows = emptyList<SteamConversationListEntry>()
    private fun mount() {
        compose.setContent {
            val result = rememberSteamConversationEntries(state.value, groups.value,
                friends.value, query.value, flags.value, voice.value)
            SideEffect { rows = result }
            Text(result.joinToString { "${it.title}:${it.unreadCount}" })
        }
    }
    @Test fun typingAndConnectionUpdatesReuseUnchangedRows() {
        mount()
        compose.waitForIdle()
        val original = rows
        repeat(20) { revision ->
            compose.runOnIdle { state.value = initial.copy(
                typingPartnerSteamIds = setOf("typing-$revision"), realtimeConnected = revision % 2 == 0) }
            compose.waitForIdle()
            assertSame("Unrelated chat state must not rebuild every inbox row", original, rows)
        }
    }
    @Test fun messagesQueryAndLocalFlagsStillInvalidateRows() {
        mount()
        compose.runOnIdle { state.value = initial.copy(sessions = initial.sessions!!.copy(
            sessions = listOf(SteamChatSession("Synthetic partner", 200, 0, 8)))) }
        compose.waitForIdle()
        assertEquals(8, rows.single().unreadCount)
        compose.runOnIdle { flags.value = SteamConversationFlags(archivedDirectIds = setOf("Synthetic partner")) }
        compose.waitForIdle()
        assertTrue(rows.single().archived)
        compose.runOnIdle { query.value = "absent" }
        compose.waitForIdle()
        assertTrue(rows.isEmpty())
        compose.runOnIdle { query.value = ""; flags.value = SteamConversationFlags(hiddenDirectAt = mapOf("Synthetic partner" to 200)) }
        compose.waitForIdle()
        assertTrue(rows.isEmpty())
        compose.runOnIdle { state.value = initial.copy(sessions = initial.sessions!!.copy(
            sessions = listOf(SteamChatSession("Synthetic partner", 201, 0, 1)))) }
        compose.waitForIdle()
        assertEquals(1, rows.single().unreadCount)
    }
    @Test fun friendGroupAndVoiceUpdatesStillRefreshRows() {
        mount()
        compose.runOnIdle {
            friends.value = listOf(SteamFriend("Synthetic partner", personaName = "Renamed friend"))
            groups.value = SteamGroupChatUiState(groups = listOf(SteamGroupChatSummary("group", name = "Synthetic group", defaultChatId = "room")))
        }
        compose.waitForIdle()
        assertEquals("Renamed friend", rows.first { it.type == SteamConversationType.DIRECT }.title)
        assertEquals("Synthetic group", rows.first { it.type == SteamConversationType.GROUP }.title)
        compose.runOnIdle {
            flags.value = SteamConversationFlags(pinnedGroupIds = setOf("group"), archivedGroupIds = setOf("group"))
            voice.value = SteamVoiceCallState(accountSteamId = "self", state = SteamVoiceConnectionState.CONNECTED,
                target = SteamVoiceTarget(type = SteamVoiceTargetType.GROUP, title = "Synthetic group", groupId = "group", chatId = "room"),
                participants = listOf(SteamVoiceParticipant("other")))
        }
        compose.waitForIdle()
        val group = rows.first()
        assertEquals("group", group.id)
        assertTrue(group.pinned); assertTrue(group.archived); assertTrue(group.voiceActive)
        assertEquals(2, group.voiceMemberCount)
        compose.runOnIdle { groups.value = groups.value.copy(groups = listOf(SteamGroupChatSummary("group", name = "Renamed group", defaultChatId = "room"))) }
        compose.waitForIdle()
        assertEquals("Renamed group", rows.first().title)
    }
}
