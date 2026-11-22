package takagi.ru.monica.steam.friends.chat.presentation

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatCache
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatPage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class SteamChatRealtimeViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun foregroundThreadUpdatesImmediatelyWithoutWaitingForPolling() = runTest(scheduler) {
        val realtime = FakeRealtimeGateway()
        val viewModel = createViewModel(dispatcher, realtime)
        val account = account(1L, ACCOUNT)

        viewModel.selectAccount(account)
        runCurrent()
        viewModel.setForeground(true)
        viewModel.openThread(PARTNER)
        runCurrent()
        realtime.emit(account, incoming("hello", 100L, 1))
        runCurrent()

        assertEquals("hello", viewModel.uiState.value.thread?.messages?.single()?.body)
        assertEquals(0, viewModel.uiState.value.sessions?.sessions?.single()?.unreadCount)
        viewModel.setForeground(false)
    }

    @Test
    fun backgroundConversationUnreadIsDeduplicated() = runTest(scheduler) {
        val realtime = FakeRealtimeGateway()
        val viewModel = createViewModel(dispatcher, realtime)
        val account = account(1L, ACCOUNT)
        val message = incoming("hello", 100L, 1)

        viewModel.selectAccount(account)
        runCurrent()
        viewModel.setForeground(true)
        runCurrent()
        realtime.emit(account, message)
        realtime.emit(account, message)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.sessions?.sessions?.single()?.unreadCount)
        viewModel.setForeground(false)
    }

    @Test
    fun switchingAccountsCancelsThePreviousRealtimeCollector() = runTest(scheduler) {
        val realtime = FakeRealtimeGateway()
        val viewModel = createViewModel(dispatcher, realtime)
        val accountA = account(1L, ACCOUNT)
        val accountB = account(2L, "76561198000000002")

        viewModel.selectAccount(accountA)
        runCurrent()
        viewModel.setForeground(true)
        runCurrent()
        viewModel.selectAccount(accountB)
        runCurrent()
        realtime.emit(accountA, incoming("stale", 100L, 1))
        runCurrent()

        assertEquals(accountB.steamId, viewModel.uiState.value.accountSteamId)
        assertEquals(0, viewModel.uiState.value.sessions?.sessions?.size ?: 0)
        viewModel.setForeground(false)
    }

    @Test
    fun typingIndicatorExpiresAndRealtimeModeDoesNotPollEveryFifteenSeconds() =
        runTest(scheduler) {
            val realtime = FakeRealtimeGateway()
            val gateway = FakeGateway()
            val viewModel = createViewModel(dispatcher, realtime, gateway)
            val account = account(1L, ACCOUNT)

            viewModel.selectAccount(account)
            runCurrent()
            viewModel.setForeground(true)
            runCurrent()
            realtime.emit(
                account,
                SteamChatRealtimeEvent.Typing(PARTNER, localEcho = false)
            )
            runCurrent()
            assertEquals(setOf(PARTNER), viewModel.uiState.value.typingPartnerSteamIds)

            advanceTimeBy(6_000L)
            runCurrent()
            assertEquals(emptySet<String>(), viewModel.uiState.value.typingPartnerSteamIds)
            assertEquals(1, gateway.sessionFetches)

            advanceTimeBy(174_000L)
            runCurrent()
            assertEquals(2, gateway.sessionFetches)
            viewModel.setForeground(false)
        }

    private fun createViewModel(
        dispatcher: CoroutineDispatcher,
        realtime: FakeRealtimeGateway,
        gateway: FakeGateway = FakeGateway()
    ) = SteamChatViewModel(
        gateway = gateway,
        cache = MemoryCache(),
        ioDispatcher = dispatcher,
        nowMillis = { 100_000L },
        clientMessageId = { "client-1" },
        realtime = realtime
    )

    private fun incoming(body: String, timestamp: Long, ordinal: Int) =
        SteamChatRealtimeEvent.Message(
            SteamChatMessage(
                partnerSteamId = PARTNER,
                senderSteamId = PARTNER,
                timestamp = timestamp,
                ordinal = ordinal,
                body = body
            )
        )

    private fun account(id: Long, steamId: String) = SteamAccount(
        id = id,
        steamId = steamId,
        accountName = "account$id",
        displayName = "Account $id",
        deviceId = "device$id",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token$id",
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = id == 1L,
        sortOrder = id.toInt(),
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT = "76561198000000001"
        const val PARTNER = "76561198000000003"
    }
}

private class FakeRealtimeGateway : SteamChatRealtimeGateway {
    private val flows = ConcurrentHashMap<Long, MutableSharedFlow<SteamChatRealtimeEvent>>()

    override fun events(account: SteamAccount): Flow<SteamChatRealtimeEvent> =
        flows.getOrPut(account.id) { MutableSharedFlow(extraBufferCapacity = 16) }

    suspend fun emit(account: SteamAccount, event: SteamChatRealtimeEvent) {
        flows.getOrPut(account.id) { MutableSharedFlow(extraBufferCapacity = 16) }.emit(event)
    }
}

private class MemoryCache : SteamChatCache {
    private val sessions = mutableMapOf<String, SteamChatSessionsSnapshot>()
    private val threads = mutableMapOf<Pair<String, String>, SteamChatThreadSnapshot>()

    override fun loadSessions(accountSteamId: String) = sessions[accountSteamId]

    override fun saveSessions(accountSteamId: String, snapshot: SteamChatSessionsSnapshot) {
        sessions[accountSteamId] = snapshot
    }

    override fun loadThread(accountSteamId: String, partnerSteamId: String) =
        threads[accountSteamId to partnerSteamId]

    override fun saveThread(
        accountSteamId: String,
        partnerSteamId: String,
        snapshot: SteamChatThreadSnapshot
    ) {
        threads[accountSteamId to partnerSteamId] = snapshot
    }

    override fun removeThread(accountSteamId: String, partnerSteamId: String) {
        threads.remove(accountSteamId to partnerSteamId)
    }
}

private class FakeGateway : SteamChatGateway {
    var sessionFetches: Int = 0
        private set

    override fun fetchSessions(account: SteamAccount): SteamChatSessionsSnapshot {
        sessionFetches++
        return SteamChatSessionsSnapshot(
            accountSteamId = account.steamId,
            sessions = emptyList(),
            fetchedAt = 0L
        )
    }

    override fun fetchMessages(
        account: SteamAccount,
        partnerSteamId: String,
        before: SteamChatHistoryBoundary?
    ) = SteamChatPage(emptyList(), moreAvailable = false)

    override fun sendMessage(
        account: SteamAccount,
        partnerSteamId: String,
        body: String,
        clientMessageId: String
    ) = SteamChatMessage(partnerSteamId, account.steamId, 1L, 1, body, clientMessageId = clientMessageId)

    override fun acknowledge(account: SteamAccount, partnerSteamId: String, timestamp: Long) = Unit
}
