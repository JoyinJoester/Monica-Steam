package takagi.ru.monica.steam.space.data

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.space.domain.STEAM_SPACE_POST_LIMIT

class SteamSpacePostServiceTest {
    private val account = SteamAccount(7, "76561198000000001", "fixture", "Fixture", "test", "",
        null, null, null, "synthetic-token", null, null, "{}", true, 0, 0, 0)
    private fun gateway(block: (SteamAccount, String, ByteArray) -> ByteArray) = object : SteamCmGateway {
        override fun callService(account: SteamAccount, method: String, request: ByteArray) = block(account, method, request)
        override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray) = error("Unrelated call")
    }

    @Test fun postsExactTextOnceThroughOfficialFriendsStatusMethodAndAcceptsEmptyAckBody() {
        val text = "  分享\n第二行 😃  "
        var calls = 0
        SteamSpacePostService(gateway { selected, method, bytes ->
            calls++
            assertSame(account, selected)
            assertEquals("Player.PostStatusToFriends#1", method)
            val fields = SteamProtoReader(bytes).parse()
            assertEquals(0L, fields.getValue(1).asLong)
            assertEquals(text, fields.getValue(2).asString)
            assertEquals(setOf(1, 2), fields.keys)
            ByteArray(0)
        }).publish(account, text)
        assertEquals(1, calls)
    }

    @Test fun blankOversizedInvalidAndUnauthenticatedRequestsNeverReachSteam() {
        val service = SteamSpacePostService(gateway { _, _, _ -> error("Must not send") })
        for (text in listOf(" \n", "x".repeat(STEAM_SPACE_POST_LIMIT + 1), "null\u0000"))
            assertThrows(IllegalArgumentException::class.java) { service.publish(account, text) }
        assertThrows(SteamApiException::class.java) { service.publish(account.copy(accessToken = null), "valid") }
    }

    @Test fun rejectedOrLostResponsesAreNeverAutomaticallyRetried() {
        var calls = 0
        val service = SteamSpacePostService(gateway { _, _, _ -> calls++; throw SteamApiException("Rejected", eResult = 15) })
        assertThrows(SteamApiException::class.java) { service.publish(account, "valid") }
        assertEquals(1, calls)
    }
}
