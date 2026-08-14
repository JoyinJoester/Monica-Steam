package takagi.ru.monica.steam.friends.chat.presentation

import java.util.concurrent.ConcurrentHashMap
import java.net.SocketTimeoutException
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
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

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
        val body = "这个好游戏\nhttps://store.steampowered.com/app/2358720/"
        val replyToStableId = "reply-message"
        val gateway = FakeGateway().apply {
            sendBlock = { account, partner, body, clientId ->
                sendCount++
                if (sendCount == 1) throw SocketTimeoutException("offline")
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

        viewModel.sendReply(body, replyToStableId)
        assertEquals(
            SteamChatDeliveryState.QUEUED,
            viewModel.uiState.value.thread?.messages?.single()?.deliveryState
        )
        runCurrent()
        val failed = viewModel.uiState.value.thread?.messages?.single()
        assertEquals(SteamChatDeliveryState.FAILED_RETRYABLE, failed?.deliveryState)
        val originalClientMessageId = failed?.clientMessageId.orEmpty()

        viewModel.retryMessage(originalClientMessageId)
        viewModel.retryMessage(originalClientMessageId)
        runCurrent()

        val messages = viewModel.uiState.value.thread?.messages.orEmpty()
        val sent = messages.single()
        assertEquals(SteamChatDeliveryState.SENT, sent?.deliveryState)
        assertEquals(200L, sent?.timestamp)
        assertEquals(originalClientMessageId, sent?.clientMessageId)
        assertEquals(body, sent?.body)
        assertEquals(replyToStableId, sent?.replyToStableId)
        assertEquals(1, messages.size)
        assertEquals(2, sendCount)
        assertEquals(partner, viewModel.uiState.value.sessions?.sessions?.first()?.partnerSteamId)
        assertEquals(200L, viewModel.uiState.value.sessions?.sessions?.first()?.lastMessageTimestamp)
    }

    @Test
    fun retriesSendOnceAfterSteamRejectsTheSession() = runTest(mainDispatcher.scheduler) {
        var sendCount = 0
        val tokens = mutableListOf<String>()
        val gateway = FakeGateway().apply {
            sendBlock = { account, partner, body, clientId ->
                sendCount++
                tokens += account.accessToken.orEmpty()
                if (sendCount == 1) {
                    throw SteamApiException(
                        message = "Steam session expired",
                        eResult = 15,
                        httpStatusCode = 403
                    )
                }
                SteamChatMessage(
                    partnerSteamId = partner,
                    senderSteamId = account.steamId,
                    timestamp = 300L,
                    ordinal = 3,
                    body = body,
                    clientMessageId = clientId
                )
            }
        }
        val viewModel = SteamChatViewModel(
            gateway = gateway,
            cache = MemoryCache(),
            sessionResolver = SteamAccountSessionResolver { account, forceRefresh ->
                if (forceRefresh) {
                    account.copy(
                        accessToken = "fresh-token",
                        steamLoginSecure = "${account.steamId}||fresh-token"
                    )
                } else {
                    account
                }
            },
            ioDispatcher = mainDispatcher,
            nowMillis = { 100_000L },
            clientMessageId = { "client-2" }
        )
        val account = account(1L, "76561198000000001")
        val partner = "76561198000000003"
        viewModel.selectAccount(account)
        runCurrent()
        viewModel.openThread(partner)
        runCurrent()

        viewModel.sendMessage("hello")
        runCurrent()

        assertEquals(SteamChatDeliveryState.SENT, viewModel.uiState.value.thread?.messages?.single()?.deliveryState)
        assertEquals(2, sendCount)
        assertEquals(listOf("token", "fresh-token"), tokens)
    }

    @Test
    fun timeoutReconcilesServerEchoWithoutSendingADuplicate() = runTest(mainDispatcher.scheduler) {
        var sendCount = 0
        val gateway = FakeGateway().apply {
            sendBlock = { account, partner, body, clientId ->
                sendCount++
                throw SocketTimeoutException("timed out")
            }
            fetchMessagesBlock = { account, requestedPartner, _ ->
                SteamChatPage(
                    messages = listOf(
                        SteamChatMessage(
                            partnerSteamId = requestedPartner,
                            senderSteamId = account.steamId,
                            timestamp = 100L,
                            ordinal = 4,
                            body = "hello"
                        )
                    ),
                    moreAvailable = false
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
        runCurrent()

        assertEquals(SteamChatDeliveryState.SENT, viewModel.uiState.value.thread?.messages?.single()?.deliveryState)
        assertEquals(1, sendCount)
        assertEquals("client-1", viewModel.uiState.value.thread?.messages?.single()?.clientMessageId)
    }

    @Test
    fun stickerReplyKeepsItsLocalReplyTargetAfterServerConfirmation() = runTest(mainDispatcher.scheduler) {
        val gateway = FakeGateway()
        val viewModel = createViewModel(gateway)
        val account = account(1L, "76561198000000001")
        val partner = "76561198000000003"
        viewModel.selectAccount(account)
        runCurrent()
        viewModel.openThread(partner)
        runCurrent()

        viewModel.sendReply("/sticker Mesmer spin", "server-target")
        runCurrent()

        val sent = viewModel.uiState.value.thread?.messages?.single()
        assertEquals(SteamChatDeliveryState.SENT, sent?.deliveryState)
        assertEquals("server-target", sent?.replyToStableId)
    }

    @Test
    fun refreshDoesNotRestoreUnreadAfterThreadWasAcknowledged() =
        runTest(mainDispatcher.scheduler) {
            val account = account(1L, "76561198000000001")
            val partner = "76561198000000003"
            val staleSessions = SteamChatSessionsSnapshot(
                accountSteamId = account.steamId,
                sessions = listOf(
                    SteamChatSession(
                        partnerSteamId = partner,
                        lastMessageTimestamp = 120L,
                        lastViewTimestamp = 100L,
                        unreadCount = 2
                    )
                ),
                fetchedAt = 1L
            )
            val gateway = FakeGateway().apply {
                fetchSessionsBlock = { staleSessions }
                fetchMessagesBlock = { _, requestedPartner, _ ->
                    SteamChatPage(
                        messages = listOf(
                            SteamChatMessage(
                                partnerSteamId = requestedPartner,
                                senderSteamId = requestedPartner,
                                timestamp = 120L,
                                ordinal = 1,
                                body = "hello"
                            )
                        ),
                        moreAvailable = false
                    )
                }
            }
            val viewModel = createViewModel(gateway)
            viewModel.selectAccount(account)
            runCurrent()
            assertEquals(2, viewModel.uiState.value.sessions?.unreadCount)

            viewModel.openThread(partner)
            runCurrent()
            assertEquals(0, viewModel.uiState.value.sessions?.unreadCount)

            viewModel.refreshSessions()
            runCurrent()

            val refreshedSession = viewModel.uiState.value.sessions?.sessions?.single()
            assertEquals(0, refreshedSession?.unreadCount)
            assertEquals(120L, refreshedSession?.lastViewTimestamp)
        }

    private fun createViewModel(
        gateway: SteamChatGateway,
        ioDispatcher: CoroutineDispatcher = mainDispatcher
    ) = SteamChatViewModel(
        gateway = gateway,
        cache = MemoryCache(),
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
        var fetchMessagesBlock: (
            SteamAccount,
            String,
            SteamChatHistoryBoundary?
        ) -> SteamChatPage = { _, _, _ -> SteamChatPage(emptyList(), false) }

        override fun fetchSessions(account: SteamAccount): SteamChatSessionsSnapshot =
            fetchSessionsBlock(account)

        override fun fetchMessages(
            account: SteamAccount,
            partnerSteamId: String,
            before: SteamChatHistoryBoundary?
        ) = fetchMessagesBlock(account, partnerSteamId, before)

        override fun sendMessage(
            account: SteamAccount,
            partnerSteamId: String,
            body: String,
            clientMessageId: String
        ) = sendBlock(account, partnerSteamId, body, clientMessageId)

        override fun acknowledge(account: SteamAccount, partnerSteamId: String, timestamp: Long) = Unit
    }
}
