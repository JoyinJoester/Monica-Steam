package takagi.ru.monica.steam.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.core.SteamTotp
import kotlinx.coroutines.test.runTest
import takagi.ru.monica.steam.confirmations.fetchSteamConfirmationsWithSessionRecovery

class SteamConfirmationServiceTest {
    @Test fun emptyBatchDoesNotQueryTimeOrSendAnyAction() {
        val service = SteamConfirmationService(serverTimeSeconds = { error("No network call expected") })
        assertEquals(SteamBatchResult(0, 0), service.respondMultiple(account(), emptyList(), true))
    }

    @Test fun expiredSessionResponseIsRecoveredWithUpdatedCookieAndFreshSignedRead() = runTest {
        val cookies = mutableListOf<String>()
        val service = serviceFor { request ->
            assertEquals("conf", request.url.queryParameter("tag"))
            assertEquals("1700000000", request.url.queryParameter("t"))
            cookies += request.header("Cookie").orEmpty()
            if (cookies.last().contains("||new-token")) """{"success":true,"conf":[]}"""
            else """{"success":false,"needauth":true,"message":"Oh noooooes!"}"""
        }
        val result = fetchSteamConfirmationsWithSessionRecovery(account(),
            { it.copy(accessToken = "new-token") }, { service.fetch(it) })
        assertTrue(result.isEmpty())
        assertEquals(2, cookies.size)
        assertTrue(cookies.first().contains("||access-token"))
        assertTrue(cookies.last().contains("||new-token"))
    }

    @Test
    fun listRequestUsesConfirmationTagAndMatchingSignature() {
        val service = serviceFor { request ->
            val valid = request.url.queryParameter("tag") == "conf" &&
                request.url.queryParameter("k") == SteamTotp.generateConfirmationHash(account().identitySecret!!, 1L, "conf")
            if (valid) """{"success":true,"conf":[]}"""
            else """{"success":false,"message":"Oh noooooes!"}"""
        }
        assertEquals(emptyList<SteamConfirmation>(), service.fetch(account(), nowSeconds = 1L))
    }

    @Test
    fun defaultFetchSignsSteamServerTimeInsteadOfThePhoneClock() {
        val service = serviceFor { request ->
            assertEquals("1700000000", request.url.queryParameter("t"))
            assertEquals(SteamTotp.generateConfirmationHash(account().identitySecret!!, 1_700_000_000L, "conf"),
                request.url.queryParameter("k"))
            """{"success":true,"conf":[]}"""
        }
        service.fetch(account())
    }

    @Test
    fun authenticationFlagsAreRecognizedBeforeSuccessAndWithoutDependingOnEnglishMessages() {
        for (body in listOf(
            """{"success":false,"needauth":true,"message":"Oh noooooes!"}""",
            """{"success":false,"needsauth":1}""",
            """{"success":true,"needauth":"1","conf":[]}"""
        )) {
            val error = assertThrows(SteamApiException::class.java) { serviceFor(body).fetch(account(), 1L) }
            assertTrue(error.authenticationRequired)
        }
        val error = assertThrows(SteamApiException::class.java) {
            serviceFor("""{"success":false,"message":"Oh noooooes!"}""").fetch(account(), 1L)
        }
        assertFalse(error.authenticationRequired)
    }

    @Test
    fun httpUnauthorizedAndLoginRedirectAreAuthenticationFailuresButForbiddenIsNot() {
        for (status in listOf(302, 401, 403)) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("test").header("Location", "https://steamcommunity.com/login/home/")
                    .body("".toResponseBody()).build()
            }.build()
            val error = assertThrows(SteamApiException::class.java) {
                SteamConfirmationService(SteamApiClient(client)).fetch(account(), 1L)
            }
            assertEquals(status != 403, error.authenticationRequired)
        }
    }

    @Test
    fun signingTimeFailureDoesNotSendAConfirmationUsingAnUntrustedClock() {
        var confirmationRequests = 0
        val service = SteamConfirmationService(
            api = SteamApiClient(OkHttpClient.Builder().addInterceptor { chain ->
                confirmationRequests++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("OK").body("""{"success":true,"conf":[]}""".toResponseBody()).build()
            }.build()),
            serverTimeSeconds = { throw java.io.IOException("Time service unavailable") }
        )
        assertThrows(java.io.IOException::class.java) { service.fetch(account()) }
        assertEquals(0, confirmationRequests)
    }
    @Test
    fun fetchParsesSuccessfulEmptyConfirmationList() {
        val service = serviceFor("""{"success":true,"conf":[]}""")

        assertEquals(emptyList<SteamConfirmation>(), service.fetch(account(), nowSeconds = 1L))
    }

    @Test
    fun fetchExposesSteamFailureInsteadOfReturningEmptyList() {
        val service = serviceFor("""{"success":false,"message":"Session expired"}""")

        val error = assertThrows(SteamApiException::class.java) {
            service.fetch(account(), nowSeconds = 1L)
        }
        assertEquals("Session expired", error.message)
    }

    @Test
    fun fetchParsesTradePartnerSteamIdWhenSteamProvidesCreatorId() {
        val service = serviceFor(
            """{"success":true,"conf":[{"id":"1","nonce":"2","type":2,"creator_id":"76561198000000002","headline":"Partner"}]}"""
        )

        assertEquals(
            "76561198000000002",
            service.fetch(account(), nowSeconds = 1L).single().partnerSteamId
        )
    }

    private fun serviceFor(payload: String): SteamConfirmationService = serviceFor { payload }

    private fun serviceFor(payload: (Request) -> String): SteamConfirmationService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(if (chain.request().url.encodedPath.contains("QueryTime")) {
                        SteamProtoWriter().apply { writeVarint(1, 1_700_000_000L) }.toByteArray()
                            .toResponseBody("application/octet-stream".toMediaType())
                    } else payload(chain.request()).toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return SteamConfirmationService(SteamApiClient(client))
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
