package takagi.ru.monica.steam.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSupportLogExporterTest {
    @Test
    fun redactsCredentialsTokensAndSteamIds() {
        val redacted = redactSteamSupportLog(
            "steamLoginSecure=76561198000000000||secret access_token: abcdef password=hunter2 " +
                "steamId=76561198000000000 token=abcdefghijklmnopqrst.abcdefghijk.abcdefghijk"
        )

        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("76561198000000000"))
        assertFalse(redacted.contains("abcdefghijklmnopqrst"))
        assertTrue(redacted.contains("[REDACTED]"))
        assertTrue(redacted.contains("[STEAM_ID_REDACTED]"))
        assertTrue(redacted.contains("[TOKEN_REDACTED]"))
    }
}
