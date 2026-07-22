package takagi.ru.monica.steam

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.importer.SteamMaFileBackupCodec
import takagi.ru.monica.steam.importer.SteamMaFileCrypto
import takagi.ru.monica.steam.importer.SteamMaFileParser

class SteamMaFileParserTest {
    private val plainMaFile = """
        {
          "shared_secret": "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
          "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
          "account_name": "tester",
          "steamid": "76561198000000000",
          "device_id": "android:device",
          "revocation_code": "R12345",
          "token_gid": "gid",
          "Session": {
            "OAuthToken": "access-token",
            "RefreshToken": "refresh-token",
            "SteamLoginSecure": "76561198000000000||access-token"
          }
        }
    """.trimIndent()

    @Test
    fun parsesPlainMaFileToSteamPayload() {
        val payload = SteamMaFileParser().parse(plainMaFile)

        assertEquals("76561198000000000", payload.steamId)
        assertEquals("tester", payload.accountName)
        assertEquals("android:device", payload.deviceId)
        assertEquals("access-token", payload.accessToken)
        assertEquals("refresh-token", payload.refreshToken)
        assertEquals("YWJjZGVmZ2hpamtsbW5vcHFyc3Q=", payload.identitySecret)
    }

    @Test
    fun parsesSteamPlusMaFileWithSteam64AndOtpAuthUriSecret() {
        val steamPlusMaFile = """
            {
              "shared_secret": "WRONG-BUT-IGNORED",
              "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
              "account_name": "steam_plus_user",
              "steam64": "76561198000000002",
              "token_gid": "gid",
              "uri": "otpauth://totp/Steam:steam_plus_user?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Steam"
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(steamPlusMaFile)

        assertEquals("76561198000000002", payload.steamId)
        assertEquals("steam_plus_user", payload.accountName)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
        assertEquals("YWJjZGVmZ2hpamtsbW5vcHFyc3Q=", payload.identitySecret)
    }

    @Test
    fun parsesSteamPlusMaFileWithBase32SharedSecretAndFileNameSteamId() {
        val steamPlusMaFile = """
            {
              "shared_secret": "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
              "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
              "account_name": "filename_user",
              "token_gid": "gid"
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(
            maFileContent = steamPlusMaFile,
            fileName = "76561198000000003.maFile"
        )

        assertEquals("76561198000000003", payload.steamId)
        assertEquals("filename_user", payload.accountName)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
    }

    @Test
    fun parsesMaFileMissingSteamIdWithSteamId64Override() {
        val maFile = """
            {
              "shared_secret": "WRONG-BUT-IGNORED",
              "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
              "account_name": "missing_id_user",
              "uri": "otpauth://totp/Steam:missing_id_user?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Steam"
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(
            maFileContent = maFile,
            steamIdOverride = "76561198000000005"
        )

        assertEquals("76561198000000005", payload.steamId)
        assertEquals("missing_id_user", payload.accountName)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
        assertEquals(true, payload.rawJson.contains(""""steamid":"76561198000000005""""))
    }

    @Test
    fun convertsSteamAccountId32OverrideToSteamId64() {
        val maFile = """
            {
              "shared_secret": "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
              "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
              "account_name": "account_id_user"
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(
            maFileContent = maFile,
            steamIdOverride = "123456"
        )

        assertEquals("76561197960389184", payload.steamId)
        assertEquals(true, payload.rawJson.contains(""""steamid":"76561197960389184""""))
    }

    @Test
    fun acceptsSbeamidTypoAsSteamId64CompatibilityAlias() {
        val steamPlusMaFile = """
            {
              "shared_secret": "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
              "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
              "account_name": "typo_user",
              "sbeamid": "76561198000000004"
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(steamPlusMaFile)

        assertEquals("76561198000000004", payload.steamId)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
    }

    @Test
    fun importsMissingSteamIdAsCodeOnlyWhenAllowed() {
        val maFile = """
            {
              "shared_secret": "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
              "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
              "account_name": "code_only_user"
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(
            maFileContent = maFile,
            allowMissingSteamId = true
        )

        assertTrue(payload.steamId.startsWith("monica-missing-steamid-"))
        assertFalse(payload.hasRealSteamId)
        assertEquals("code_only_user", payload.accountName)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
        assertTrue(payload.rawJson.contains(""""monica_missing_steamid":true"""))
        assertTrue(payload.rawJson.contains(""""monica_local_steamid":"${payload.steamId}""""))
        assertFalse(payload.rawJson.contains(""""steamid""""))
    }

    @Test
    fun extractsMissingSteamIdFromSteamLoginSecureWhenPresent() {
        val maFile = """
            {
              "shared_secret": "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
              "account_name": "session_user",
              "Session": {
                "SteamLoginSecure": "76561198000000006||access-token"
              }
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(maFile)

        assertEquals("76561198000000006", payload.steamId)
        assertTrue(payload.hasRealSteamId)
    }

    @Test
    fun decryptsSdaEncryptedMaFileWithManifestEntry() {
        val salt = SteamMaFileCrypto.randomSaltBase64()
        val iv = SteamMaFileCrypto.randomIvBase64()
        val cipher = SteamMaFileCrypto.encryptForTests("pass-key", salt, iv, plainMaFile)
        val manifest = """
            {
              "encrypted": true,
              "entries": [
                {
                  "filename": "76561198000000000.maFile",
                  "steamid": "76561198000000000",
                  "encryption_salt": "$salt",
                  "encryption_iv": "$iv"
                }
              ]
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(
            maFileContent = cipher,
            fileName = "76561198000000000.maFile",
            manifestContent = manifest,
            password = "pass-key"
        )

        assertEquals("tester", payload.accountName)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
    }

    @Test
    fun sdaCryptoMatchesPbkdf2ReferenceVectors() {
        val salt = Base64.getEncoder().encodeToString("salt".toByteArray(Charsets.UTF_8))

        assertEquals(
            "4b007901b765489abead49d926f721d065a429c1",
            SteamMaFileCrypto.deriveKey("password", salt, iterations = 4096, keySizeBits = 160).toHex()
        )
        assertEquals(
            "0c60c80f961f0e71f3a9b524af6012062fe037a6",
            SteamMaFileCrypto.deriveKey("password", salt, iterations = 1, keySizeBits = 160).toHex()
        )
    }

    @Test
    fun wrongEncryptedMaFilePasswordReturnsNullAtCryptoLayer() {
        val salt = SteamMaFileCrypto.randomSaltBase64()
        val iv = SteamMaFileCrypto.randomIvBase64()
        val cipher = SteamMaFileCrypto.encryptForTests("right", salt, iv, plainMaFile)

        assertNull(SteamMaFileCrypto.decrypt("wrong", salt, iv, cipher))
    }

    @Test
    fun encodesSteamAccountAsRestorableMaFileWithSessionTokens() {
        val account = SteamAccount(
            id = 7L,
            steamId = "76561198000000001",
            accountName = "backup_user",
            displayName = "Backup User",
            deviceId = "android:backup-device",
            sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
            identitySecret = "identity-secret",
            revocationCode = "R98765",
            tokenGid = "token-gid",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            steamLoginSecure = "76561198000000001||access-token",
            rawSteamGuardJson = """{"serial_number":"serial","fully_enrolled":true}""",
            selected = true,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 2L
        )

        val maFile = SteamMaFileBackupCodec.encode(account)
        val payload = SteamMaFileParser().parse(maFile)

        assertEquals("76561198000000001", payload.steamId)
        assertEquals("backup_user", payload.accountName)
        assertEquals("Backup User", payload.displayName)
        assertEquals("android:backup-device", payload.deviceId)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
        assertEquals("identity-secret", payload.identitySecret)
        assertEquals("R98765", payload.revocationCode)
        assertEquals("token-gid", payload.tokenGid)
        assertEquals("access-token", payload.accessToken)
        assertEquals("refresh-token", payload.refreshToken)
        assertEquals("76561198000000001||access-token", payload.steamLoginSecure)
    }

    @Test
    fun doesNotMarkAccountNameAsMonicaRemarkInMaFileBackup() {
        val account = SteamAccount(
            id = 8L,
            steamId = "76561198000000002",
            accountName = "same_user",
            displayName = "same_user",
            deviceId = "android:same-device",
            sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
            identitySecret = null,
            revocationCode = null,
            tokenGid = null,
            accessToken = null,
            refreshToken = null,
            steamLoginSecure = null,
            rawSteamGuardJson = "{}",
            selected = false,
            sortOrder = 1,
            createdAt = 1L,
            updatedAt = 2L
        )

        val maFile = SteamMaFileBackupCodec.encode(account)

        assertFalse(maFile.contains("monica_display_name"))
    }

    @Test
    fun encodesCodeOnlyAccountWithoutFakeSteamId() {
        val account = SteamAccount(
            id = 9L,
            steamId = "monica-missing-steamid-0123456789abcdef",
            accountName = "code_only_user",
            displayName = "Code Only",
            deviceId = "",
            sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
            identitySecret = "identity-secret",
            revocationCode = null,
            tokenGid = null,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            steamLoginSecure = "monica-missing-steamid-0123456789abcdef||access-token",
            rawSteamGuardJson = """{"steamid":"76561198000009999","shared_secret":"old"}""",
            selected = false,
            sortOrder = 2,
            createdAt = 1L,
            updatedAt = 2L
        )

        val maFile = SteamMaFileBackupCodec.encode(account)
        val payload = SteamMaFileParser().parse(maFile)

        assertFalse(maFile.contains(""""steamid""""))
        assertFalse(maFile.contains("access-token"))
        assertTrue(maFile.contains(""""monica_missing_steamid":true"""))
        assertEquals(account.steamId, payload.steamId)
        assertFalse(payload.hasRealSteamId)
        assertFalse(account.canUseConfirmations)
        assertFalse(account.canApproveLogins)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
