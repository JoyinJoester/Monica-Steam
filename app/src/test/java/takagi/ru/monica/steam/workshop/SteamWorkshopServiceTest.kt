package takagi.ru.monica.steam.workshop

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamWorkshopServiceTest {
    @Test fun publicBrowseAcceptsTheCurrentValveResponseWithoutAccountCredentials() = runTest {
        val fixture = requireNotNull(javaClass.getResource("/workshop/browse-valve-ssr-data.html")).readBytes()
        val service = SteamWorkshopService(api { request ->
            assertEquals("/workshop/browse/", request.url.encodedPath)
            assertEquals("550", request.url.queryParameter("appid"))
            assertEquals("lastupdated", request.url.queryParameter("browsesort"))
            assertEquals("Campaigns", request.url.queryParameter("requiredtags[0]"))
            assertNull(request.header("Cookie"))
            fixture
        }, StandardTestDispatcher(testScheduler), cm { error("Public browsing must not need account authentication") })
        val batch = service.browse(null, 550, WorkshopQuery(sort = WorkshopSort.UPDATED, tags = setOf("Campaigns")), 1)
        assertEquals(listOf("1001", "1002"), batch.items.map { it.id })
    }

    @Test fun supportDetectionDistinguishesUnsupportedFromFailureAndDoesNotCacheFailures() = runTest {
        var attempts = 0
        val service = SteamWorkshopService(api { request ->
            val id = request.url.queryParameter("appids")
            when (id) {
                "91001" -> """{"91001":{"success":true,"data":{"categories":[{"id":30}]}}}"""
                "91002" -> """{"91002":{"success":true,"data":{"categories":[{"id":2}]}}}"""
                else -> if (attempts++ == 0) """{"91003":{"success":false}}"""
                    else """{"91003":{"success":true,"data":{"categories":[]}}}"""
            }.toByteArray()
        }, StandardTestDispatcher(testScheduler))
        assertTrue(service.supportsWorkshop(91001))
        assertFalse(service.supportsWorkshop(91002))
        try { service.supportsWorkshop(91003); fail("A failed request must not hide Workshop as unsupported") }
        catch (_: WorkshopException) { }
        assertFalse(service.supportsWorkshop(91003))
        assertEquals(2, attempts)
    }

    @Test fun subscribeUsesAccountScopedCmAndReadsBackServerStatus() = runTest {
        val requests = mutableListOf<CmRequest>()
        val account = SteamWorkshopViewModelTest.account()
        val service = SteamWorkshopService(api { error("Authenticated Workshop must not call Web API") },
            StandardTestDispatcher(testScheduler), cm { request ->
                requests += request
                if (request.method == "PublishedFile.Subscribe#1") byteArrayOf() else status(true)
            })
        service.setSubscription(account, item(), true, true)
        assertEquals(listOf("PublishedFile.Subscribe#1", "PublishedFile.AreFilesInSubscriptionList#1"), requests.map { it.method })
        assertTrue(requests.all { it.account === account })
        val fields = SteamProtoReader(requests[0].body).parse()
        assertEquals(true, fields[5]?.asBool)
        assertEquals(true, fields[4]?.asBool)
    }

    @Test fun unsuccessfulReadbackIsNotASuccessfulSubscription() = runTest {
        val service = SteamWorkshopService(api { error("No Web API") }, StandardTestDispatcher(testScheduler),
            cm { if (it.method == "PublishedFile.Subscribe#1") byteArrayOf() else status(false) })
        try { service.setSubscription(SteamWorkshopViewModelTest.account(), item(), true, false); fail("Expected unconfirmed mutation") }
        catch (error: WorkshopException) { assertEquals(WorkshopFailure.UNCONFIRMED, error.reason) }
    }

    @Test fun invalidOrCollectionItemsCannotBeSubscribed() = runTest {
        val service = SteamWorkshopService(api { error("No Web API") }, StandardTestDispatcher(testScheduler), cm { error("No CM mutation") })
        for (item in listOf(item().copy(banned = true), item().copy(fileType = 2), item().copy(canSubscribe = false))) {
            try { service.setSubscription(SteamWorkshopViewModelTest.account(), item, true, false); fail("Expected rejection") }
            catch (_: WorkshopException) { }
        }
    }

    @Test fun subscriptionPageUsesCmAccountGameTagsAndRequestedPage() = runTest {
        var captured: CmRequest? = null
        val service = SteamWorkshopService(api { error("No key-restricted Web API") }, StandardTestDispatcher(testScheduler), cm { request ->
            captured = request
            SteamProtoWriter().apply { writeVarint(1, 0); writeVarint(2, 31) }.toByteArray()
        })
        val account = SteamWorkshopViewModelTest.account()
        val batch = service.browse(account, 294100,
            WorkshopQuery(subscribedOnly = true, sort = WorkshopSort.UPDATED, tags = setOf("Mod", "1.6")), 2)
        assertFalse(batch.hasMore)
        val request = requireNotNull(captured)
        assertEquals("PublishedFile.GetUserFiles#1", request.method)
        assertSame(account, request.account)
        val fields = SteamProtoReader(request.body).parseAll()
        val map = fields.associateBy { it.number }
        assertEquals("76561198000000001", map[1]?.asFixed64UnsignedString)
        assertEquals(294100, map[2]?.asInt)
        assertEquals(2, map[4]?.asInt)
        assertEquals("mysubscriptions", map[6]?.asString)
        assertEquals(setOf("Mod", "1.6"), fields.filter { it.number == 10 }.map { it.asString }.toSet())
        assertEquals("lastupdated", map[7]?.asString)
    }

    @Test fun itemDetailsDecodeTagsDependenciesAndAccountSubscription() = runTest {
        val requests = mutableListOf<String>()
        val service = SteamWorkshopService(api { error("No Web API") }, StandardTestDispatcher(testScheduler), cm { request ->
            requests += request.method
            if (request.method == "PublishedFile.AreFilesInSubscriptionList#1") status(true)
            else SteamProtoWriter().apply { writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 1); writeUint64(2, "123"); writeFixed64(3, 76561198000000001L)
                writeVarint(5, 294100); writeString(16, "Test mod"); writeString(17, "Description")
                writeBool(35, true); writeVarint(49, 1)
                writeMessage(52, SteamProtoWriter().apply { writeString(1, "Mod"); writeString(3, "MOD") })
                writeMessage(53, SteamProtoWriter().apply { writeUint64(1, "456") })
            }) }.toByteArray()
        })
        val detail = service.details(SteamWorkshopViewModelTest.account(), 294100, "123")
        assertEquals("Description", detail.description)
        assertEquals(listOf("MOD"), detail.tags)
        assertEquals(listOf("456"), detail.children)
        assertEquals(true, detail.subscribed)
        assertTrue(detail.canSubscribe)
        assertEquals(listOf("PublishedFile.GetDetails#1", "PublishedFile.AreFilesInSubscriptionList#1", "PublishedFile.GetDetails#1"), requests)
    }

    @Test fun unsubscribeUsesCmAndDoesNotReplayAnAmbiguousWrite() = runTest {
        var calls = 0
        val failure = java.io.IOException("connection interrupted")
        val service = SteamWorkshopService(api { error("No Web API fallback") }, StandardTestDispatcher(testScheduler), cm {
            calls++
            assertEquals("PublishedFile.Unsubscribe#1", it.method)
            assertFalse(SteamProtoReader(it.body).parse().containsKey(5))
            throw failure
        })
        try { service.setSubscription(SteamWorkshopViewModelTest.account(), item(), false, true); fail("Expected failure") }
        // Coroutine stacktrace recovery may copy IOException across withContext.
        catch (error: java.io.IOException) { assertEquals(failure.message, error.message) }
        assertEquals(1, calls)
    }

    @Test fun guestCannotStartAnAuthenticatedCmSession() = runTest {
        val service = SteamWorkshopService(api { error("No Web API") }, StandardTestDispatcher(testScheduler), cm { error("No CM") })
        try { service.browse(null, 550, WorkshopQuery(subscribedOnly = true), 1); fail("Expected login") }
        catch (error: WorkshopException) { assertEquals(WorkshopFailure.LOGIN, error.reason) }
    }

    @Test fun failureDiagnosticsSeparateTransportFromDecodingWithoutAccountOrPayloadData() = runTest {
        val logs = mutableListOf<String>()
        val account = SteamWorkshopViewModelTest.account().copy(accessToken = "private-token")
        val rateLimited = SteamWorkshopService(api { error("No Web API") }, StandardTestDispatcher(testScheduler),
            cm { throw SteamApiException("raw error private-token ${account.steamId}", eResult = 84) }, logs::add)
        try { rateLimited.browse(account, 550, WorkshopQuery(subscribedOnly = true), 1); fail("Expected rate limit") }
        catch (error: SteamApiException) { assertEquals(84, error.eResult) }
        assertEquals(listOf("workshop_cm method=GetUserFiles stage=request failure=RATE_LIMIT http=-1 eresult=84"), logs)

        logs.clear()
        val payload = "<html>private server response</html>".toByteArray()
        val malformed = SteamWorkshopService(api { error("No Web API") }, StandardTestDispatcher(testScheduler),
            cm { payload }, logs::add)
        try { malformed.browse(account, 550, WorkshopQuery(subscribedOnly = true), 1); fail("Expected invalid response") }
        catch (_: Exception) { }
        assertEquals(listOf("workshop_cm method=GetUserFiles stage=decode failure=INVALID_RESPONSE bytes=${payload.size}"), logs)
    }

    @Test fun unavailableDiagnosticSinkCannotBreakAnOtherwiseSuccessfulSubscriptionRead() = runTest {
        val service = SteamWorkshopService(api { error("No Web API") }, StandardTestDispatcher(testScheduler),
            cm { byteArrayOf() }, { error("Diagnostics unavailable") })
        val batch = service.browse(SteamWorkshopViewModelTest.account(), 550, WorkshopQuery(subscribedOnly = true), 1)
        assertTrue(batch.items.isEmpty())
        assertEquals(0, batch.total)
    }

    private data class CmRequest(val account: SteamAccount, val method: String, val body: ByteArray)
    private fun cm(response: (CmRequest) -> ByteArray) = object : SteamCmGateway {
        override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray = response(CmRequest(account, method, request))
        override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray): ByteArray = error("No client message")
    }

    private fun api(response: (Request) -> ByteArray) = SteamApiClient(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("x-eresult", "1").body(response(chain.request()).toResponseBody()).build()
    }.build())
    private fun item() = WorkshopItem("123", 294100, "Test mod", canSubscribe = true)
    private fun status(value: Boolean) = SteamProtoWriter().apply {
        writeMessage(1, SteamProtoWriter().apply { writeFixed64(1, 123); writeBool(2, value) })
    }.toByteArray()
}
