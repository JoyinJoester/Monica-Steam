package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.interest.data.buildSteamGameInterestStateRequest
import takagi.ru.monica.steam.store.interest.data.buildSteamIgnoreMutationRequest
import takagi.ru.monica.steam.store.interest.data.buildSteamIgnoredAppsRequest
import takagi.ru.monica.steam.store.interest.data.effectiveSteamStoreLoginSecure
import takagi.ru.monica.steam.store.interest.data.parseSteamGameInterestState
import takagi.ru.monica.steam.store.interest.data.parseSteamIgnoredAppIds
import takagi.ru.monica.steam.store.interest.data.SteamStoreInterestService
import takagi.ru.monica.steam.store.interest.domain.withoutIgnoredGames
import takagi.ru.monica.steam.store.data.SteamStoreReviewService
import takagi.ru.monica.steam.store.data.SteamStoreService
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem

class SteamStoreIgnoredGamesServiceTest {
    @Test
    fun featuredContentSurvivesUnavailableIgnoredPreferenceSession() {
        val requests = mutableListOf<okhttp3.Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val payload = when (chain.request().url.encodedPath) {
                "/api/featuredcategories" ->
                    """{"specials":{"items":[{"id":620,"name":"Portal 2"}]}}"""
                else -> "<html></html>"
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(payload.toResponseBody("text/plain".toMediaType()))
                .build()
        }.build()
        val service = SteamStoreService(
            client = client,
            api = SteamApiClient(client),
            reviewService = SteamStoreReviewService(client)
        )

        val home = service.featured(steamId = "76561198000000000")

        assertEquals(listOf(620), home.specials.map(SteamStoreItem::appId))
        assertEquals(
            listOf("/api/featuredcategories", "/search/results/", "/"),
            requests.map { it.url.encodedPath }
        )
    }

    @Test
    fun refreshedAccessTokenReplacesAStaleStoreCookieToken() {
        assertEquals(
            "76561198000000000||fresh-token",
            effectiveSteamStoreLoginSecure(
                steamId = "76561198000000000",
                steamLoginSecure = "76561198000000000||stale-token",
                accessToken = "fresh-token"
            )
        )
    }

    @Test
    fun serviceCachesOfficialIgnoredAppsAndUpdatesCacheAfterMutation() {
        val requests = mutableListOf<okhttp3.Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val payload = if (chain.request().url.encodedPath == "/dynamicstore/userdata/") {
                "{\"rgIgnoredApps\":{\"730\":0}}"
            } else {
                "{}"
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(payload.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val service = SteamStoreInterestService(
            client = client,
            api = SteamApiClient(client),
            nowMillis = { 1_000L },
            sessionIdFactory = { "session123" }
        )

        assertEquals(
            setOf(730),
            service.ignoredAppIds(
                steamId = "76561198000000000",
                steamLoginSecure = null,
                accessToken = "fresh-token",
                countryCode = "CN"
            )
        )
        service.setIgnored(
            appId = 3722330,
            ignored = true,
            steamId = "76561198000000000",
            steamLoginSecure = null,
            accessToken = "fresh-token"
        )

        assertEquals(
            setOf(730, 3722330),
            service.ignoredAppIds(
                steamId = "76561198000000000",
                steamLoginSecure = null,
                accessToken = "fresh-token",
                countryCode = "CN"
            )
        )
        assertEquals(2, requests.size)
        assertEquals("POST", requests.last().method)
    }

    @Test
    fun cachedIgnoredAppsSurvivePreferenceSessionRefreshFailure() {
        var rejectSession = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (rejectSession) 401 else 200)
                .message(if (rejectSession) "Unauthorized" else "OK")
                .body(
                    "{\"rgIgnoredApps\":{\"730\":0}}"
                        .toResponseBody("application/json".toMediaType())
                )
                .build()
        }.build()
        val service = SteamStoreInterestService(
            client = client,
            api = SteamApiClient(client),
            nowMillis = { 1_000L }
        )
        val credentials = Triple(
            "76561198000000000",
            "76561198000000000||token",
            "token"
        )
        assertEquals(
            setOf(730),
            service.ignoredAppIds(
                steamId = credentials.first,
                steamLoginSecure = credentials.second,
                accessToken = credentials.third,
                countryCode = "CN"
            )
        )

        rejectSession = true

        assertEquals(
            setOf(730),
            service.ignoredAppIds(
                steamId = credentials.first,
                steamLoginSecure = credentials.second,
                accessToken = credentials.third,
                countryCode = "CN",
                forceRefresh = true
            )
        )
    }

    @Test
    fun requestUsesOfficialDynamicStoreUserDataForTheAuthenticatedAccount() {
        val request = buildSteamIgnoredAppsRequest(
            steamId = "76561198000000000",
            steamLoginSecure = "76561198000000000%7C%7Ctoken",
            countryCode = "cn",
        )

        assertEquals("/dynamicstore/userdata/", request.url.encodedPath)
        assertEquals("39734272", request.url.queryParameter("id"))
        assertEquals("CN", request.url.queryParameter("cc"))
        assertTrue(request.header("Cookie").orEmpty().contains("%7C%7C"))
        assertFalse(request.header("Cookie").orEmpty().contains("%257C"))
    }

    @Test
    fun parserReadsIgnoredAppsFromOfficialDynamicStorePayload() {
        val payload = """
            {
              "rgIgnoredApps": {
                "3722330": 0,
                "730": 2
              }
            }
        """.trimIndent()

        assertEquals(linkedSetOf(3722330, 730), parseSteamIgnoredAppIds(payload))
    }

    @Test
    fun parserAcceptsEmptyOrArrayDynamicStorePayloads() {
        assertTrue(parseSteamIgnoredAppIds("{\"rgIgnoredApps\":[]}").isEmpty())
        assertEquals(
            linkedSetOf(3722330, 730),
            parseSteamIgnoredAppIds("{\"rgIgnoredApps\":[3722330,730]}")
        )
    }

    @Test
    fun parserRejectsLoginHtmlOrUnrelatedJsonInsteadOfTreatingItAsNoIgnoredGames() {
        assertThrows(IllegalArgumentException::class.java) {
            parseSteamIgnoredAppIds("{\"success\":false}")
        }
    }

    @Test
    fun mutationRequestMatchesOfficialSteamIgnoreEndpoint() {
        val request = buildSteamIgnoreMutationRequest(
            appId = 3722330,
            ignored = true,
            steamLoginSecure = "76561198000000000||token",
            sessionId = "session123"
        )
        val body = request.body as FormBody

        assertEquals("/recommended/ignorerecommendation/", request.url.encodedPath)
        assertEquals("session123", body.value("sessionid"))
        assertEquals("3722330", body.value("appid"))
        assertEquals("0", body.value("ignore_reason"))
        assertEquals(null, body.value("remove"))
        assertTrue(request.header("Cookie").orEmpty().contains("sessionid=session123"))
    }

    @Test
    fun mutationRequestUsesRemoveFlagWhenUndoingIgnore() {
        val body = buildSteamIgnoreMutationRequest(
            appId = 3722330,
            ignored = false,
            steamLoginSecure = "76561198000000000||token",
            sessionId = "session123"
        ).body as FormBody

        assertEquals("1", body.value("remove"))
        assertEquals(null, body.value("ignore_reason"))
    }

    @Test
    fun interestStateUsesStableStoreServiceFields() {
        val request = buildSteamGameInterestStateRequest(3722330)
        val ignoredResponse = SteamProtoWriter().apply { writeBool(3, true) }.toByteArray()
        val visibleResponse = SteamProtoWriter().toByteArray()

        assertEquals(3722330, request.toByteArray().let {
            takagi.ru.monica.steam.network.SteamProtoReader(it).parse()[1]?.asInt
        })
        assertTrue(parseSteamGameInterestState(ignoredResponse))
        assertFalse(parseSteamGameInterestState(visibleResponse))
    }

    @Test
    fun ignoredGamesAreRemovedFromEveryHomeCollection() {
        val visible = SteamStoreHome(
            specials = listOf(item(1), item(2)),
            topSellers = listOf(item(2), item(3)),
            newReleases = listOf(item(2)),
            comingSoon = listOf(item(4), item(2))
        ).withoutIgnoredGames(setOf(2))

        assertFalse(visible.specials.any { it.appId == 2 })
        assertFalse(visible.topSellers.any { it.appId == 2 })
        assertTrue(visible.newReleases.isEmpty())
        assertFalse(visible.comingSoon.any { it.appId == 2 })
    }

    @Test
    fun catalogKeepsServerPaginationAfterIgnoredItemsAreRemoved() {
        val page = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = (1..24).map(::item),
            start = 24,
            totalCount = 100
        ).withoutIgnoredGames(setOf(1, 2, 3))

        assertEquals(21, page.items.size)
        assertEquals(48, page.nextStart)
        assertTrue(page.hasMore)
    }

    @Test
    fun emptyCatalogPageDoesNotBecomeLoadableAfterFiltering() {
        val page = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = emptyList(),
            start = 48,
            totalCount = 100
        ).withoutIgnoredGames(setOf(1))

        assertEquals(48, page.nextStart)
        assertFalse(page.hasMore)
    }

    @Test
    fun laterCatalogPageKeepsServerCursorWhenItHasNoIgnoredItems() {
        val firstPage = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = (1..24).map(::item),
            start = 0,
            totalCount = 100
        ).withoutIgnoredGames(setOf(1, 2, 3))
        val secondPage = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = (25..48).map(::item),
            start = 24,
            totalCount = 100
        ).withoutIgnoredGames(emptySet())

        val merged = secondPage.copy(
            start = 0,
            items = firstPage.items + secondPage.items
        )

        assertEquals(48, merged.nextStart)
        assertTrue(merged.hasMore)
    }

    private fun item(appId: Int) = SteamStoreItem(appId = appId, name = "Game $appId")

    private fun FormBody.value(name: String): String? =
        (0 until size).firstOrNull { encodedName(it) == name }?.let(::value)
}
