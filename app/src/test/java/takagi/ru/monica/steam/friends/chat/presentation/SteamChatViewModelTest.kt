package takagi.ru.monica.steam.friends.chat.presentation

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatPage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class SteamChatViewModelTest {
    private val testScheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun requestGuardRejectsLateAccountAndThreadResponses() {
        val guard = SteamChatRequestGuard()
        val accountA = account(1L, "76561198000000001")
        val accountB = account(2L, "76561198000000002")
        val sessionsA = guard.selectAccount(accountA)
        val threadA = guard.selectThread("76561198000000003")

        val sessionsB = guard.selectAccount(accountB)
        val threadB = guard.selectThread("76561198000000004")

        assertEquals(false, guard.isSessionsCurrent(accountA, sessionsA))
        assertEquals(false, guard.isThreadCurrent(accountA, "76561198000000003", threadA))
        assertEquals(true, guard.isSessionsCurrent(accountB, sessionsB))
        assertEquals(true, guard.isThreadCurrent(accountB, "76561198000000004", threadB))
    }

    @Test
    fun failedOptimisticMessageCanRetryInPlace() = runTest(mainDispatcher.scheduler) {
        var sendCount = 0
        val gateway = FakeGateway().apply {
            sendBlock = { account, partner, body, clientId ->
                sendCount++
                if (sendCount == 1) error("offline")
                SteamChatMessage(
                    partnerSteamId = partner,
                    senderSteamId = account.steamId,
                    timestamp = 200L,
                    ordinal = 2,
                    body = body,
                    clientMessageId = clientId
                )
            }
        }
        val viewModel = createViewModel(gateway)
        val account = account(1L, "76561198000000001")
        val partner = "76561198000000003"
        viewModel.selectAccount(account)
        runCurrent()
        viewModel.openThread(partner)
        runCurrent()

        viewModel.sendMessage("hello")
        assertEquals(
            SteamChatDeliveryState.PENDING,
            viewModel.uiState.value.thread?.messages?.single()?.deliveryState
        )
        runCurrent()
        val failed = viewModel.uiState.value.thread?.messages?.single()
        assertEquals(SteamChatDeliveryState.FAILED, failed?.deliveryState)

        viewModel.retryMessage(failed?.clientMessageId.orEmpty())
        runCurrent()

        val sent = viewModel.uiState.value.thread?.messages?.single()
        assertEquals(SteamChatDeliveryState.SENT, sent?.deliveryState)
        assertEquals(200L, sent?.timestamp)
        assertEquals(2, sendCount)
        assertEquals(partner, viewModel.uiState.value.sessions?.sessions?.first()?.partnerSteamId)
        assertEquals(200L, viewModel.uiState.value.sessions?.sessions?.first()?.lastMessageTimestamp)
    }

    private fun createViewModel(
        gateway: SteamChatGateway,
        ioDispatcher: CoroutineDispatcher = mainDispatcher
    ) = SteamChatViewModel(
        gateway = gateway,
        cache = MemoryCache(),
        sessionRefreshService = null,
        ioDispatcher = ioDispatcher,
        nowMillis = { 100_000L },
        clientMessageId = { "client-1" }
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
        accessToken = "token",
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = id == 1L,
        sortOrder = id.toInt(),
        createdAt = 0L,
        updatedAt = 0L
    )

    private class MemoryCache : SteamChatCache {
        private val sessions = ConcurrentHashMap<String, SteamChatSessionsSnapshot>()
        private val threads = ConcurrentHashMap<Pair<String, String>, SteamChatThreadSnapshot>()
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
    }

    private class FakeGateway : SteamChatGateway {
        var fetchSessionsBlock: (SteamAccount) -> SteamChatSessionsSnapshot = { account ->
            SteamChatSessionsSnapshot(account.steamId, emptyList(), 0L)
        }
        var sendBlock: (
            SteamAccount,
            String,
            String,
            String
        ) -> SteamChatMessage = { account, partner, body, clientId ->
            SteamChatMessage(partner, account.steamId, 1L, 1, body, clientMessageId = clientId)
        }

        override fun fetchSessions(account: SteamAccount): SteamChatSessionsSnapshot =
            fetchSessionsBlock(account)

        override fun fetchMessages(
            account: SteamAccount,
            partnerSteamId: String,
            before: SteamChatHistoryBoundary?
        ) = SteamChatPage(emptyList(), false)

        override fun sendMessage(
            account: SteamAccount,
            partnerSteamId: String,
            body: String,
            clientMessageId: String
        ) = sendBlock(account, partnerSteamId, body, clientMessageId)

        override fun acknowledge(account: SteamAccount, partnerSteamId: String, timestamp: Long) = Unit
    }
}
