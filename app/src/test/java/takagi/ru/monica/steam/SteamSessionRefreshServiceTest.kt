package takagi.ru.monica.steam

import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.SteamSessionRefreshService

class SteamSessionRefreshServiceTest {
    @Test
    fun refreshesExpiredAccessTokenWithStoredRefreshToken() {
        val calls = AtomicInteger(0)
        val service = SteamSessionRefreshService(
            api = SteamApiClient(fakeClient(calls))
        )
        val account = account(
            accessToken = jwt(exp = 1_700_000_000L),
            refreshToken = "stored-refresh-token"
        )

        val refreshed = service.refreshIfNeeded(account, nowSeconds = 1_700_001_000L)

        assertEquals("new-access-token", refreshed?.accessToken)
        assertEquals("new-refresh-token", refreshed?.refreshToken)
        assertEquals(1, calls.get())
    }

    @Test
    fun keepsFreshAccessTokenWithoutNetworkRefresh() {
        val calls = AtomicInteger(0)
        val service = SteamSessionRefreshService(
            api = SteamApiClient(fakeClient(calls))
        )
        val account = account(
            accessToken = jwt(exp = 1_700_010_000L),
            refreshToken = "stored-refresh-token"
        )

        val refreshed = service.refreshIfNeeded(account, nowSeconds = 1_700_001_000L)

        assertNull(refreshed)
        assertEquals(0, calls.get())
    }

    @Test
    fun treatsMalformedAccessTokenAsRefreshable() {
        val calls = AtomicInteger(0)
        val service = SteamSessionRefreshService(
            api = SteamApiClient(fakeClient(calls))
        )

        val refreshed = service.refreshIfNeeded(
            account = account(accessToken = "not-a-jwt", refreshToken = "stored-refresh-token"),
            nowSeconds = 1_700_001_000L
        )

        assertEquals("new-access-token", refreshed?.accessToken)
        assertEquals(1, calls.get())
    }

    private fun fakeClient(calls: AtomicInteger): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    calls.incrementAndGet()
                    assertTrue(chain.request().url.encodedPath.contains("GenerateAccessTokenForApp"))
                    val body = SteamProtoWriter().apply {
                        writeString(1, "new-access-token")
                        writeString(2, "new-refresh-token")
                    }.toByteArray().toResponseBody("application/octet-stream".toMediaType())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("x-eresult", "1")
                        .body(body)
                        .build()
                }
            )
            .build()
    }

    private fun account(
        accessToken: String?,
        refreshToken: String?
    ): SteamAccount {
        return SteamAccount(
            id = 1L,
            steamId = "76561198000000001",
            accountName = "steam_user",
            displayName = "steam_user",
            deviceId = "android:test",
            sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
            identitySecret = "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
            revocationCode = "R12345",
            tokenGid = "token-gid",
            accessToken = accessToken,
            refreshToken = refreshToken,
            steamLoginSecure = accessToken?.let { "76561198000000001||$it" },
            rawSteamGuardJson = "{}",
            selected = true,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        )
    }

    private fun jwt(exp: Long): String {
        val header = base64Url("""{"alg":"none"}""")
        val payload = base64Url("""{"exp":$exp}""")
        return "$header.$payload.signature"
    }

    private fun base64Url(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
    }
}
