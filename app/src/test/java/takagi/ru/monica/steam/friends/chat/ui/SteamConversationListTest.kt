package takagi.ru.monica.steam.friends.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatFailureReason
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.data.SteamAccountSourceState
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceParticipant
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTarget
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType

class SteamConversationListTest {
    @Test
    fun directAndGroupChatsShareOneChronologicalList() {
        val entries = buildSteamConversationEntries(
            sessions = listOf(SteamChatSession("76561198000000001", lastMessageTimestamp = 100L)),
            groups = listOf(group(timestamp = 200L)),
            friends = listOf(SteamFriend("76561198000000001", personaName = "Friend")),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet()
        )

        assertEquals(listOf(SteamConversationType.GROUP, SteamConversationType.DIRECT), entries.map { it.type })
        assertEquals("9001", entries.first().chatId)
    }

    @Test
    fun multiChannelGroupUsesItsMostRecentlyActiveRoom() {
        val activeGroup = SteamGroupChatSummary(
            groupId = "8001",
            name = "Active group",
            defaultChatId = "9001",
            rooms = listOf(
                SteamGroupChatRoom(
                    chatId = "9001",
                    name = "General",
                    lastMessageTimestamp = 100L,
                    lastMessage = "Old default message"
                ),
                SteamGroupChatRoom(
                    chatId = "9002",
                    name = "Games",
                    lastMessageTimestamp = 400L,
                    lastMessage = "Newest group message"
                )
            )
        )

        val entries = buildSteamConversationEntries(
            sessions = listOf(SteamChatSession("76561198000000001", lastMessageTimestamp = 300L)),
            groups = listOf(activeGroup),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet()
        )

        assertEquals(SteamConversationType.GROUP, entries.first().type)
        assertEquals("9002", entries.first().chatId)
        assertEquals(400L, entries.first().timestamp)
        assertEquals("Newest group message", entries.first().subtitle)
    }

    @Test
    fun pinnedConversationWinsOverTimestampAcrossTypes() {
        val entries = buildSteamConversationEntries(
            sessions = listOf(SteamChatSession("76561198000000001", lastMessageTimestamp = 300L)),
            groups = listOf(group(timestamp = 100L)),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = setOf("8001")
        )

        assertEquals(SteamConversationType.GROUP, entries.first().type)
        assertTrue(entries.first().pinned)
    }

    @Test
    fun activeLocalGroupVoiceIsVisibleBeforeTheServerSummaryRefreshes() {
        val entries = buildSteamConversationEntries(
            sessions = emptyList(),
            groups = listOf(group(timestamp = 100L)),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet(),
            voiceState = SteamVoiceCallState(
                accountSteamId = "76561198000000001",
                target = SteamVoiceTarget(
                    type = SteamVoiceTargetType.GROUP,
                    title = "Group",
                    groupId = "8001",
                    chatId = "9002"
                ),
                state = SteamVoiceConnectionState.CONNECTED,
                participants = listOf(SteamVoiceParticipant("76561198000000002"))
            )
        )

        assertTrue(entries.single().voiceActive)
        assertEquals(2, entries.single().voiceMemberCount)
    }

    @Test
    fun transientFailuresDoNotCoverUsableCachedConversations() {
        val cachedDirect = SteamChatUiState(
            sessions = SteamChatSessionsSnapshot(
                accountSteamId = "76561198000000001",
                sessions = listOf(SteamChatSession("76561198000000002")),
                fetchedAt = 1L
            ),
            sessionsFailure = SteamChatFailureReason.NETWORK
        )
        val cachedGroup = SteamGroupChatUiState(
            groups = listOf(group(timestamp = 1L)),
            groupsFailure = true
        )

        assertTrue(!shouldShowDirectConversationFailure(cachedDirect))
        assertTrue(!shouldShowGroupConversationFailure(cachedGroup))
        assertTrue(
            shouldShowDirectConversationFailure(
                SteamChatUiState(sessionsFailure = SteamChatFailureReason.NETWORK)
            )
        )
        assertTrue(shouldShowGroupConversationFailure(SteamGroupChatUiState(groupsFailure = true)))
    }

    @Test
    fun accountConsumersStayOnThePreviousAccountWhileSourceIsLoading() {
        assertTrue(!shouldApplySteamAccountSelection(SteamAccountSourceState(loading = true)))
        assertTrue(shouldApplySteamAccountSelection(SteamAccountSourceState(loading = false)))
    }

    @Test
    fun archivedChatsLeaveTheMainTabsAndOnlyShowUnderArchive() {
        val entries = buildSteamConversationEntries(
            sessions = listOf(
                SteamChatSession("76561198000000001", lastMessageTimestamp = 300L),
                SteamChatSession("76561198000000002", lastMessageTimestamp = 200L)
            ),
            groups = listOf(group(timestamp = 100L)),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = setOf("76561198000000002"),
            pinnedGroupIds = emptySet(),
            archivedPartnerSteamIds = setOf("76561198000000002"),
            archivedGroupIds = setOf("8001")
        )

        assertTrue(entries.single { it.id == "76561198000000002" }.archived)
        val mainTab = filterSteamConversationEntries(entries, SteamConversationFilter.ALL)
        assertEquals(listOf("76561198000000001"), mainTab.map { it.id })
        assertTrue(filterSteamConversationEntries(entries, SteamConversationFilter.DIRECT).none { it.archived })
        assertTrue(filterSteamConversationEntries(entries, SteamConversationFilter.PINNED).isEmpty())
        assertEquals(
            setOf("76561198000000002", "8001"),
            filterSteamConversationEntries(entries, SteamConversationFilter.ARCHIVED).map { it.id }.toSet()
        )
    }

    @Test
    fun deletedChatStaysHiddenUntilANewerMessageArrives() {
        val deletedAt = 2_000L
        val hidden = buildSteamConversationEntries(
            sessions = listOf(SteamChatSession("76561198000000001", lastMessageTimestamp = 1_900L)),
            groups = emptyList(),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet(),
            hiddenPartnerAt = mapOf("76561198000000001" to deletedAt)
        )
        assertTrue(hidden.isEmpty())

        val revived = buildSteamConversationEntries(
            sessions = listOf(SteamChatSession("76561198000000001", lastMessageTimestamp = 2_100L)),
            groups = emptyList(),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet(),
            hiddenPartnerAt = mapOf("76561198000000001" to deletedAt)
        )
        assertEquals(1, revived.size)
        assertTrue(!revived.single().hidden)
    }

    @Test
    fun unreadTabKeepsOnlyPendingConversations() {
        val entries = buildSteamConversationEntries(
            sessions = listOf(
                SteamChatSession("76561198000000001", lastMessageTimestamp = 300L, unreadCount = 2),
                SteamChatSession("76561198000000002", lastMessageTimestamp = 200L),
                SteamChatSession("archived-unread", lastMessageTimestamp = 100L, unreadCount = 4)
            ),
            groups = listOf(group(timestamp = 250L).copy(unreadCount = 1)),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet(),
            archivedPartnerSteamIds = setOf("archived-unread")
        )

        assertEquals(
            listOf("76561198000000001", "8001"),
            filterSteamConversationEntries(entries, SteamConversationFilter.UNREAD).map { it.id }
        )
        assertEquals(listOf("archived-unread"),
            filterSteamConversationEntries(entries, SteamConversationFilter.ARCHIVED).map { it.id })
        assertEquals(listOf("8001"),
            filterSteamConversationEntries(entries, SteamConversationFilter.GROUP).map { it.id })
    }

    private fun group(timestamp: Long) = SteamGroupChatSummary(
        groupId = "8001",
        name = "Group",
        activeMemberCount = 3,
        defaultChatId = "9001",
        rooms = listOf(
            SteamGroupChatRoom("9001", "General", lastMessageTimestamp = timestamp, lastMessage = "Hi")
        )
    )
}
