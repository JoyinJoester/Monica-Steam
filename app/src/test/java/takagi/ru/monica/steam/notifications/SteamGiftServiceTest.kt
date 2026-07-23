package takagi.ru.monica.steam.notifications

import takagi.ru.monica.steam.gifts.data.*
import takagi.ru.monica.steam.gifts.domain.*

import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient

class SteamGiftServiceTest {
    @Test
    fun pendingGameGiftKeepsAcceptActionsWhenMarkupOnlyExposesDecline() {
        val html = """
            <div id="pending_gift_445566">
              <div class="gift_name">Half-Life</div>
              <a onclick="ShowDeclineGiftOptions(445566, '76561198000000009')">Decline</a>
            </div>
        """.trimIndent()

        val gift = SteamGiftParser.parsePending(html).single()

        assertTrue(SteamGiftAction.ADD_TO_LIBRARY in gift.actions)
        assertTrue(SteamGiftAction.KEEP_IN_INVENTORY in gift.actions)
        assertTrue(SteamGiftAction.DECLINE in gift.actions)
        assertFalse(gift.requiresWeb)
    }

    @Test
    fun numericAcceptFlagsFromCurrentSteamMarkupAreRecognized() {
        val html = """
            <div id="pending_gift_445566">
              <a onclick="DoAcceptGift('445566', 1)">Add to library</a>
              <a onclick="DoAcceptGift('445566', 0)">Keep in inventory</a>
            </div>
        """.trimIndent()

        val gift = SteamGiftParser.parsePending(html).single()

        assertTrue(SteamGiftAction.ADD_TO_LIBRARY in gift.actions)
        assertTrue(SteamGiftAction.KEEP_IN_INVENTORY in gift.actions)
    }

    @Test
    fun parsesPendingGiftIdsSenderAndAvailableActions() {
        val html = """
            <div id="pending_gift_123456">
              <div class="gift_name">Portal 2</div>
              <div class="gift_message">Have fun!</div>
              <a onclick="DoAcceptGift(123456, true)">Add to library</a>
              <a onclick="DoAcceptGift(123456, false)">Keep in inventory</a>
              <a onclick="ShowDeclineGiftOptions(123456, '76561198000000009')">Decline</a>
            </div>
            <div id="pending_gift_654321">
              <div class="gift_name">Digital gift card</div>
              <script>AcceptRejectGiftCard(998877, 1);</script>
            </div>
        """.trimIndent()

        val gifts = SteamGiftParser.parsePending(html)

        assertEquals(2, gifts.size)
        val game = gifts.first()
        assertEquals("123456", game.id)
        assertEquals("76561198000000009", game.senderSteamId)
        assertEquals("Portal 2", game.name)
        assertEquals("Have fun!", game.message)
        assertTrue(SteamGiftAction.ADD_TO_LIBRARY in game.actions)
        assertTrue(SteamGiftAction.KEEP_IN_INVENTORY in game.actions)
        assertTrue(SteamGiftAction.DECLINE in game.actions)
        val giftCard = gifts.last()
        assertTrue(giftCard.requiresWeb)
        assertEquals("998877", giftCard.giftCardId)
    }

    @Test
    fun acceptsGiftIntoLibraryWithCommunitySession() {
        val requests = CopyOnWriteArrayList<Request>()
        val service = SteamGiftService(SteamApiClient(successClient(requests)))

        val result = service.respond(
            account = account(),
            gift = SteamPendingGift(id = "123456", senderSteamId = "76561198000000009"),
            action = SteamGiftAction.ADD_TO_LIBRARY
        )

        assertTrue(result.success)
        val request = requests.single()
        assertEquals("/gifts/123456/acceptunpack", request.url.encodedPath)
        assertTrue(request.header("Cookie").orEmpty().contains("steamLoginSecure=76561198000000001||access-token"))
        assertTrue(request.header("Referer").orEmpty().contains("/profiles/76561198000000001/inventory/"))
        assertTrue(decodedBody(request).contains("sessionid="))
    }

    @Test
    fun declinesGiftWithSenderAndNote() {
        val requests = CopyOnWriteArrayList<Request>()
        val service = SteamGiftService(SteamApiClient(successClient(requests)))

        val result = service.respond(
            account = account(),
            gift = SteamPendingGift(id = "123456", senderSteamId = "76561198000000009"),
            action = SteamGiftAction.DECLINE,
            note = "Thanks"
        )

        assertTrue(result.success)
        val request = requests.single()
        assertEquals("/gifts/123456/decline", request.url.encodedPath)
        val body = decodedBody(request)
        assertTrue(body.contains("steamid_sender=76561198000000009"))
        assertTrue(body.contains("note=Thanks"))
    }

    private fun successClient(requests: MutableList<Request>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"success\":1}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
    }

    private fun decodedBody(request: Request): String {
        val buffer = okio.Buffer()
        request.body!!.writeTo(buffer)
        return URLDecoder.decode(buffer.readUtf8(), "UTF-8")
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "steam_user",
        displayName = "steam_user",
        deviceId = "android:test",
        sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
        identitySecret = "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
        revocationCode = "R12345",
        tokenGid = "token-gid",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}
