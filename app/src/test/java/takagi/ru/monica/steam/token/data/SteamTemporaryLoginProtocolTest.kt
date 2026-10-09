package takagi.ru.monica.steam.token.data

import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamTemporaryLoginProtocolTest {
    @Test fun temporaryPasswordLoginRequestsEphemeralSession() = verify(temporary = true)
    @Test fun ordinaryPasswordLoginStillRequestsPersistentSession() = verify(temporary = false)
    @Test fun guardCodePreservesTemporaryPurpose() = verify(temporary = true, challenge = true)

    private fun verify(temporary: Boolean, challenge: Boolean = false) = runBlocking {
        var persistence: Long? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val body = when {
                path.contains("GetPasswordRSAPublicKey") ->
                    """{"response":{"publickey_mod":"${"ab".repeat(128)}","publickey_exp":"010001","timestamp":"303723700000"}}"""
                        .toResponseBody("application/json".toMediaType())
                path.contains("BeginAuthSessionViaCredentials") -> {
                    val form = request.body as FormBody
                    val fields = SteamProtoReader(Base64.getDecoder().decode(form.value(0))).parse()
                    persistence = fields[7]?.asLong
                    SteamProtoWriter().apply {
                        writeUint64(1, 123L)
                        writeBytes(2, byteArrayOf(1, 2, 3))
                        writeUint64(5, 76561198000000001L)
                        if (challenge) writeMessage(4, SteamProtoWriter().apply { writeVarint(1, 2L) })
                    }.toByteArray().toResponseBody("application/octet-stream".toMediaType())
                }
                path.contains("UpdateAuthSessionWithSteamGuardCode") ->
                    ByteArray(0).toResponseBody("application/octet-stream".toMediaType())
                path.contains("PollAuthSessionStatus") -> SteamProtoWriter().apply {
                    writeString(3, "synthetic-refresh")
                    writeString(4, "synthetic-access")
                }.toByteArray().toResponseBody("application/octet-stream".toMediaType())
                else -> throw AssertionError("Unexpected request: $path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").header("x-eresult", "1").body(body).build()
        }.build()
        val service = SteamLoginImportService(client)
        val initial = service.beginSessionLogin("test", "password", temporary)
        val result = if (challenge) {
            assertTrue(initial is SteamLoginImportService.LoginResult.ChallengeRequired)
            service.submitSteamGuardCode(
                (initial as SteamLoginImportService.LoginResult.ChallengeRequired).pendingSessionId,
                "ABCDE", 2
            )
        } else initial
        assertTrue("Expected login success, received $result", result is SteamLoginImportService.LoginResult.ReadyForImport)
        result as SteamLoginImportService.LoginResult.ReadyForImport
        assertEquals(if (temporary) 0L else 1L, persistence)
        assertEquals(temporary, result.payload.temporary)
        assertTrue(result.payload.sessionOnly)
        assertEquals("synthetic-access", result.accessToken)
    }

    @Test fun qrApprovalPreservesTemporaryPurpose() = runBlocking {
        val token = "e30." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"76561198000000001"}""".toByteArray()) + ".signature"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = SteamProtoWriter().apply {
                when {
                    request.url.encodedPath.contains("BeginAuthSessionViaQR") -> {
                        writeUint64(1, 123)
                        writeString(2, "https://s.team/q/1/123")
                        writeBytes(3, byteArrayOf(1, 2, 3))
                    }
                    request.url.encodedPath.contains("PollAuthSessionStatus") -> {
                        writeString(3, token)
                        writeString(4, token)
                        writeString(6, "Test")
                    }
                    else -> throw AssertionError("Unexpected QR request")
                }
            }.toByteArray().toResponseBody("application/octet-stream".toMediaType())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").header("x-eresult", "1").body(body).build()
        }.build()
        val service = SteamLoginImportService(client)
        val challenge = service.beginQrLogin(temporary = true) as SteamLoginImportService.QrLoginResult.ChallengeRequired
        val result = service.pollQrLoginSession(challenge.pendingSessionId)
        assertTrue("$result", result is SteamLoginImportService.QrLoginResult.ReadyForImport)
        result as SteamLoginImportService.QrLoginResult.ReadyForImport
        assertTrue(result.result.payload.temporary)
        assertTrue(result.result.payload.sessionOnly)
    }
}
