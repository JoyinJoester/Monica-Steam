package takagi.ru.monica.steam.friends.chat.data

import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.STEAM_ID64_INDIVIDUAL_BASE
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamFriendChatServiceTest {
    @Test
    fun parsesSessionsMessagesAndSendConfirmation() {
        val sessionResponse = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, partnerAccountId())
                writeVarint(2, 1_700_000_100L)
                writeVarint(3, 1_700_000_000L)
                writeVarint(4, 3L)
            })
        }.toByteArray()
        val messageResponse = SteamProtoWriter().apply {
            writeMessage(1, chatMessage(partnerAccountId(), 1_700_000_001L, 1, "Hello"))
            writeMessage(1, chatMessage(accountId(), 1_700_000_002L, 2, "Hi"))
            writeBool(4, true)
        }.toByteArray()
        val sentResponse = SteamProtoWriter().apply {
            writeString(1, "Sent text")
            writeVarint(2, 1_700_000_003L)
            writeVarint(3, 3L)
            writeString(4, "Sent text")
        }.toByteArray()

        val sessions = SteamFriendChatParser.parseSessions(sessionResponse)
        val page = SteamFriendChatParser.parseMessages(messageResponse, PARTNER_STEAM_ID)
        val sent = SteamFriendChatParser.parseSentMessage(
            response = sentResponse,
            accountSteamId = ACCOUNT_STEAM_ID,
            partnerSteamId = PARTNER_STEAM_ID,
            requestedBody = "Requested"
        )

        assertEquals(PARTNER_STEAM_ID, sessions.single().partnerSteamId)
        assertEquals(3, sessions.single().unreadCount)
        assertEquals(listOf("Hello", "Hi"), page.messages.map { it.body })
        assertEquals(PARTNER_STEAM_ID, page.messages.first().senderSteamId)
        assertEquals(ACCOUNT_STEAM_ID, page.messages.last().senderSteamId)
        assertTrue(page.moreAvailable)
        assertEquals("Sent text", sent.body)
        assertEquals(3, sent.ordinal)
    }

    @Test
    fun serviceUsesOfficialFriendMessagesMethodsAndFixed32HistoryBoundary() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val body = when {
                    request.url.encodedPath.contains("GetActiveMessageSessions") ->
                        SteamProtoWriter().toByteArray()
                    request.url.encodedPath.contains("GetRecentMessages") ->
                        SteamProtoWriter().apply { writeBool(4, false) }.toByteArray()
                    request.url.encodedPath.contains("SendMessage") ->
                        SteamProtoWriter().apply {
                            writeVarint(2, 1_700_000_003L)
                            writeVarint(3, 3L)
                        }.toByteArray()
                    request.url.encodedPath.contains("AckMessage") -> ByteArray(0)
                    else -> error("Unexpected request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()
        val service = SteamFriendChatService(SteamApiClient(client))
        val account = account()

        service.fetchSessions(account)
        service.fetchMessages(
            account = account,
            partnerSteamId = PARTNER_STEAM_ID,
            before = SteamChatHistoryBoundary(1_700_000_000L, 9)
        )
        service.sendMessage(account, PARTNER_STEAM_ID, " Message ", "client-1")
        service.acknowledge(account, PARTNER_STEAM_ID, 1_700_000_003L)

        assertEquals(
            listOf("GET", "GET", "POST", "POST"),
            requests.map { it.method }
        )
        assertEquals(
            listOf(
                "/IFriendMessagesService/GetActiveMessageSessions/v1/",
                "/IFriendMessagesService/GetRecentMessages/v1/",
                "/IFriendMessagesService/SendMessage/v1/",
                "/IFriendMessagesService/AckMessage/v1/"
            ),
            requests.map { it.url.encodedPath }
        )
        assertTrue(requests.all { it.url.queryParameter("access_token") == "access-token" })
        val historyRequest = requests[1]
        val encoded = requireNotNull(historyRequest.url.queryParameter("input_protobuf_encoded"))
        val fields = SteamProtoReader(Base64.getDecoder().decode(encoded)).parseAll()
        assertTrue(fields.any { it.number == 5 && it.wireType == 5 })
        assertEquals(1_700_000_000L, fields.first { it.number == 5 }.asFixed32UnsignedLong)
        assertEquals(9, fields.first { it.number == 7 }.asInt)
        val sendBody = requests[2].body as FormBody
        val sendEncoded = (0 until sendBody.size)
            .first { sendBody.name(it) == "input_protobuf_encoded" }
            .let(sendBody::value)
        val sendFields = SteamProtoReader(Base64.getDecoder().decode(sendEncoded)).parseAll()
        assertTrue(sendFields.first { it.number == 4 }.asBool)
    }

    private fun chatMessage(
        senderAccountId: Long,
        timestamp: Long,
        ordinal: Int,
        body: String
    ): SteamProtoWriter = SteamProtoWriter().apply {
        writeVarint(1, senderAccountId)
        writeVarint(2, timestamp)
        writeString(3, body)
        writeVarint(4, ordinal.toLong())
    }

    private fun accountId(): Long = ACCOUNT_STEAM_ID.toLong() - STEAM_ID64_INDIVIDUAL_BASE

    private fun partnerAccountId(): Long =
        PARTNER_STEAM_ID.toLong() - STEAM_ID64_INDIVIDUAL_BASE

    private fun account() = SteamAccount(
        id = 1L,
        steamId = ACCOUNT_STEAM_ID,
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "$ACCOUNT_STEAM_ID||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_STEAM_ID = "76561198000000001"
        const val PARTNER_STEAM_ID = "76561198000000002"
    }
}
