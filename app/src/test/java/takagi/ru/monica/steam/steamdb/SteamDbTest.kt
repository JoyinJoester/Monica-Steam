package takagi.ru.monica.steam.steamdb

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.steamdb.data.*
import takagi.ru.monica.steam.steamdb.domain.*

class SteamDbTest {
    @Test fun mapsActualDollarRegionsWithoutGuessingFromLanguage() {
        mapOf("US" to "USD", "PK" to "USD-SASIA", "AR" to "USD-LATAM", "TR" to "USD-MENA", "GE" to "USD-CIS")
            .forEach { (country, expected) -> assertEquals(expected, steamDbPriceRegion(country, "USD")) }
        assertEquals("CNY", steamDbPriceRegion(" cn ", " cny "))
        assertEquals("EUR", steamDbPriceRegion("DE", "EUR"))
        assertNull(steamDbPriceRegion(null, "USD"))
        assertNull(steamDbPriceRegion("unknown", "CNY"))
        assertNull(steamDbPriceRegion("US", "LOL"))
    }

    @Test fun ratingAccountsForSampleSizeAndEmptyReviews() {
        assertNull(SteamDbRating(0, 0).score)
        assertNull(SteamDbRating(-1, 1).score)
        assertEquals(50.0, SteamDbRating(10, 10).score!!, 0.000001)
        assertTrue(SteamDbRating(1, 0).score!! < SteamDbRating(10_000, 0).score!!)
        assertTrue(SteamDbRating(0, 1).score!! > 0)
        assertTrue(SteamDbRating(10_000, 0).score!! < 100)
    }

    @Test fun parsesMissingFieldsWithoutTurningThemIntoZero() = withApi { server, api ->
        server.enqueue(json("""{"success":true,"data":{"cp":0,"mdp":"1,234","mp":"bad","f":null,"u":1790812800}}"""))
        val info = ready(api.info(570))
        assertEquals(0L, info.currentPlayers)
        assertEquals(1234L, info.peakToday)
        assertNull(info.peakAll)
        assertNull(info.followers)
        assertEquals(1790812800L, info.updatedAt)
        val request = server.takeRequest()
        assertEquals("/ExtensionApp/?appid=570", request.path)
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("Authorization"))
        assertEquals("SteamDB", request.getHeader("X-Requested-With"))
    }

    @Test fun priceKeepsRegionOccurrenceAndDate() = withApi { server, api ->
        server.enqueue(json("""{"success":true,"data":{"p":"$ 0.99","d":90,"c":12,"t":1750809600,"l":"$ 1.20"}}"""))
        val price = ready(api.price(620, "USD-SASIA"))
        assertEquals("$ 0.99", price.price)
        assertEquals(90, price.discount)
        assertEquals(12L, price.occurrences)
        assertEquals("$ 1.20", price.twoYearLow)
        assertEquals(1750809600L, price.lastAt)
        assertEquals("USD-SASIA", server.takeRequest().requestUrl!!.queryParameter("currency"))
    }

    @Test fun malformedResponsesAndNoDataRemainUnavailable() = withApi { server, api ->
        listOf("<html>challenge</html>", "[]", """{"success":true,"data":{"p":null}}""", """{"success":false}""")
            .forEach { payload -> server.enqueue(json(payload)); assertTrue(api.price(620, "CNY") is SteamDbResult.Failed) }
        server.enqueue(json("""{"success":true,"data":{}}"""))
        assertEquals(SteamDbFailure.NO_DATA, (api.info(570) as SteamDbResult.Failed).reason)
    }

    @Test fun officialPlayerAndReviewQueriesAreIndependentOfSteamDb() = withApi { server, api ->
        server.enqueue(json("""{"response":{"result":1,"player_count":0}}"""))
        assertEquals(0L, ready(api.players(570)))
        assertNull(server.takeRequest().getHeader("X-Requested-With"))
        server.enqueue(json("""{"success":1,"query_summary":{"total_positive":900,"total_negative":100}}"""))
        assertEquals(1000L, ready(api.rating(620)).total)
        val query = server.takeRequest().requestUrl!!
        assertEquals("all", query.queryParameter("language"))
        assertEquals("steam", query.queryParameter("purchase_type"))
        assertEquals("all", query.queryParameter("review_type"))
        assertEquals("1", query.queryParameter("filter_offtopic_activity"))
    }

    @Test fun rejectsInvalidReviewCounts() = withApi { server, api ->
        listOf("{}", """{"total_positive":900}""", """{"total_positive":-1,"total_negative":1}""",
            """{"total_positive":9223372036854775807,"total_negative":1}""").forEach { summary ->
            server.enqueue(json("""{"success":1,"query_summary":$summary}"""))
            assertTrue(api.rating(620) is SteamDbResult.Failed)
        }
    }

    @Test fun rateLimitStopsBothSteamDbEndpointsWithoutBlockingSteam() = withApi { server, api ->
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        assertEquals(121_000L, (api.info(570) as SteamDbResult.Failed).retryAt)
        assertEquals(SteamDbFailure.RATE_LIMITED, (api.price(620, "CNY") as SteamDbResult.Failed).reason)
        assertEquals(1, server.requestCount)
        server.enqueue(json("""{"response":{"result":1,"player_count":42}}"""))
        assertEquals(42L, ready(api.players(570)))
    }

    @Test fun httpFailureIsNotRetriedAsAnotherTransport() = withApi { server, api ->
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(SteamDbFailure.SERVICE, (api.info(570) as SteamDbResult.Failed).reason)
        assertEquals(1, server.requestCount)
    }

    @Test fun retryAfterSupportsSecondsDatesAndBounds() {
        assertEquals(60_000, SteamDbApi.retryDelayMillis(null, 0))
        assertEquals(1_000, SteamDbApi.retryDelayMillis("0", 0))
        assertEquals(86_400_000, SteamDbApi.retryDelayMillis("9999999", 0))
        assertEquals(60_000, SteamDbApi.retryDelayMillis("Thu, 01 Jan 1970 00:01:00 GMT", 0))
    }

    @Test fun cacheSeparatesRegionsExpiresAndLabelsStaleResults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var now = 1_000L
            val requests = SteamDbRequests<String, Int>(100, scope) { now }
            var calls = 0
            fun fetch(): SteamDbResult<Int> = SteamDbResult.Ready(++calls, now)
            assertEquals(1, ready(requests.load("CNY", fetch = ::fetch)))
            assertEquals(1, ready(requests.load("CNY", fetch = ::fetch)))
            assertEquals(2, ready(requests.load("USD", fetch = ::fetch)))
            now += 101
            val stale = requests.load("CNY") { SteamDbResult.Failed(SteamDbFailure.NETWORK) } as SteamDbResult.Ready
            assertEquals(1, stale.value)
            assertTrue(stale.stale)
            assertEquals(1_000L, stale.fetchedAt)
            assertEquals(3, ready(requests.load("CNY", fetch = ::fetch)))
        } finally { scope.cancel() }
    }

    @Test fun cacheCoalescesInFlightRequestsAndSurvivesObserverCancellation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val requests = SteamDbRequests<Int, Int>(100, scope) { 1_000L }
            val calls = AtomicInteger()
            val fetch = { calls.incrementAndGet(); started.countDown(); check(release.await(5, TimeUnit.SECONDS)); SteamDbResult.Ready(42, 1_000L) }
            val a = async(start = CoroutineStart.UNDISPATCHED) { requests.load(1, fetch = fetch) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val b = async(start = CoroutineStart.UNDISPATCHED) { requests.load(1, fetch = fetch) }
            a.cancelAndJoin()
            release.countDown()
            assertEquals(42, ready(b.await()))
            assertEquals(1, calls.get())
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun forceRefreshStillHonorsRetryAfter() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val requests = SteamDbRequests<Int, Int>(100, scope) { 1_000L }
            var calls = 0
            val fetch = { calls++; SteamDbResult.Failed(SteamDbFailure.RATE_LIMITED, 60_000L) }
            requests.load(1, fetch = fetch)
            requests.load(1, true, fetch)
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun freeAndUnknownRegionSkipPricesWhileOtherDataStillLoads() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gateway = FakeGateway()
            val repository = SteamDbRepository(gateway, scope) { 1_000L }
            assertEquals(SteamDbFailure.FREE, (repository.price(SteamDbQuery(570, "CN", "CNY", true)) as SteamDbResult.Failed).reason)
            assertEquals(SteamDbFailure.UNKNOWN_REGION, (repository.price(SteamDbQuery(570, null, "USD")) as SteamDbResult.Failed).reason)
            assertEquals(0, gateway.prices)
            val state = repository.observe(SteamDbQuery(570, "CN", "CNY", true)).last()
            assertEquals(42L, ready(state.players))
            assertTrue(state.info is SteamDbResult.Failed)
            assertEquals(0, gateway.prices)
        } finally { scope.cancel() }
    }

    @Test fun invalidAppNeverReachesNetwork() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gateway = FakeGateway()
            val state = SteamDbRepository(gateway, scope).observe(SteamDbQuery(0, "CN", "CNY")).last()
            assertTrue(state.players is SteamDbResult.Failed)
            assertEquals(0, gateway.calls)
        } finally { scope.cancel() }
    }

    private class FakeGateway : SteamDbGateway {
        var calls = 0
        var prices = 0
        override fun info(appId: Int): SteamDbResult<SteamDbAppInfo> { calls++; return SteamDbResult.Failed(SteamDbFailure.SERVICE) }
        override fun players(appId: Int): SteamDbResult<Long> { calls++; return SteamDbResult.Ready(42L, 1_000L) }
        override fun rating(appId: Int): SteamDbResult<SteamDbRating> { calls++; return SteamDbResult.Ready(SteamDbRating(9, 1), 1_000L) }
        override fun price(appId: Int, region: String): SteamDbResult<SteamDbLowestPrice> { calls++; prices++; return SteamDbResult.Failed(SteamDbFailure.NO_DATA) }
    }

    private fun json(value: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(value)
    private fun <T> ready(result: SteamDbResult<T>): T { assertTrue("Expected data: $result", result is SteamDbResult.Ready); return (result as SteamDbResult.Ready).value }
    private fun withApi(block: (MockWebServer, SteamDbApi) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server, SteamDbApi(OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
                server.url("/"), server.url("/"), server.url("/"), clock = { 1_000L }))
        }
    }
}
