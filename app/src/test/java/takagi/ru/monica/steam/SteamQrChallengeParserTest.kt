package takagi.ru.monica.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.network.SteamQrChallenge

class SteamQrChallengeParserTest {
    @Test
    fun parsesStandardSteamQrLink() {
        val challenge = SteamQrChallenge.parse("https://s.team/q/1/123456789")

        assertEquals(1, challenge?.version)
        assertEquals(123456789L, challenge?.clientId)
    }

    @Test
    fun parsesSteamCommunityQrHosts() {
        val challenge = SteamQrChallenge.parse("https://www.steamcommunity.com/q/2/987654321?utm=scan")

        assertEquals(2, challenge?.version)
        assertEquals(987654321L, challenge?.clientId)
    }

    @Test
    fun parsesSteamQrLinkInsideScannedText() {
        val challenge = SteamQrChallenge.parse("Login request: <https://s.team/q/3/111222333>, approve?")

        assertEquals(3, challenge?.version)
        assertEquals(111222333L, challenge?.clientId)
    }

    @Test
    fun parsesSteamOpenUrlWrappedQrLink() {
        val challenge = SteamQrChallenge.parse(
            "steam://openurl/https%3A%2F%2Fs.team%2Fq%2F4%2F444555666%3Futm%3Dscan"
        )

        assertEquals(4, challenge?.version)
        assertEquals(444555666L, challenge?.clientId)
    }

    @Test
    fun parsesEntireUnsigned64ClientIdRangeWithoutLosingBits() {
        assertEquals(
            Long.MAX_VALUE,
            SteamQrChallenge.parse("https://s.team/q/1/9223372036854775807")?.clientId
        )
        assertEquals(
            Long.MIN_VALUE,
            SteamQrChallenge.parse("https://s.team/q/1/9223372036854775808")?.clientId
        )
        assertEquals(
            -1L,
            SteamQrChallenge.parse("https://s.team/q/1/18446744073709551615")?.clientId
        )
    }

    @Test
    fun rejectsNonSteamQrPayloads() {
        assertNull(SteamQrChallenge.parse("1234567890"))
        assertNull(SteamQrChallenge.parse("otpauth://totp/Steam:user?secret=ABC"))
        assertNull(SteamQrChallenge.parse("https://example.com/q/1/123456789"))
        assertNull(SteamQrChallenge.parse("http://s.team/q/1/123456789"))
        assertNull(SteamQrChallenge.parse("https://s.team/q/-1/123456789"))
        assertNull(SteamQrChallenge.parse("https://s.team/q/1/0"))
        assertNull(SteamQrChallenge.parse("https://s.team/q/1/-123456789"))
        assertNull(SteamQrChallenge.parse("https://s.team/q/1/18446744073709551616"))
    }
}
