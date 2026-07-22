package takagi.ru.monica.steam.backup

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

class SteamBackupPayloadTest {
    @Test
    fun organizationAndSecretsRoundTrip() {
        val account = SteamAccount(
            id = 7L,
            steamId = "76561198000000001",
            accountName = "alice",
            displayName = "Alice",
            deviceId = "device",
            sharedSecret = "shared",
            identitySecret = "identity",
            revocationCode = "R123",
            tokenGid = "gid",
            accessToken = "access",
            refreshToken = "refresh",
            steamLoginSecure = "secure",
            rawSteamGuardJson = "{\"shared_secret\":\"shared\"}",
            selected = true,
            sortOrder = 4,
            createdAt = 11L,
            updatedAt = 12L,
            groupName = "家庭",
            tags = listOf("主号", "市场"),
            accentArgb = 0xff112233,
            note = "备份测试",
            pinned = true
        )
        val payload = SteamBackupPayloadCodec.decode(
            SteamBackupPayloadCodec.encode(listOf(account), createdAt = 99L)
        )
        val restored = payload.accounts.single()
        assertEquals(99L, payload.createdAt)
        assertEquals(account.steamId, restored.steamId)
        assertEquals(account.groupName, restored.groupName)
        assertEquals(account.tags, restored.tags)
        assertEquals(account.accentArgb, restored.accentArgb)
        assertEquals(account.note, restored.note)
        assertTrue(restored.pinned)
        assertTrue(restored.maFileJson.contains("shared_secret"))
    }

    @Test
    fun duplicateSteamIdsAreRejected() {
        val account = SteamBackupAccount(
            steamId = "76561198000000001",
            accountName = "name",
            displayName = "name",
            deviceId = "device",
            sharedSecret = "secret",
            maFileJson = "{}",
            createdAt = 1L
        )
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            SteamBackupPayload(createdAt = 1L, accounts = listOf(account, account))
        )
        assertThrows(SteamBackupFormatException::class.java) {
            SteamBackupPayloadCodec.decode(encoded)
        }
    }
}
