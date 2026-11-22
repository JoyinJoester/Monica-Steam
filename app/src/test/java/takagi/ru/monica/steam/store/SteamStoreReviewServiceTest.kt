package takagi.ru.monica.steam.store

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreReviewService
import takagi.ru.monica.steam.store.domain.SteamReviewFilterSelection
import takagi.ru.monica.steam.store.domain.SteamReviewSentimentFilter
import takagi.ru.monica.steam.store.domain.SteamReviewTimeFilter

class SteamStoreReviewServiceTest {
    @Test
    fun reviewPageUsesOfficialCursorPagination() {
        lateinit var captured: Request
        val payload = """{
          "success":1,"query_summary":{"review_score":8,"total_positive":8,"total_negative":2,"total_reviews":10},
          "reviews":[{"recommendationid":"1","review":"Great","voted_up":true}],
          "cursor":"next"
        }""".trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured = chain.request()
                Response.Builder()
                    .request(captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val page = SteamStoreReviewService(client).fetchPage(
            appId = 570,
            cursor = "page-token"
        )

        assertEquals("/ajaxappreviews/570", captured.url.encodedPath)
        assertEquals("all", captured.url.queryParameter("filter"))
        assertEquals("all", captured.url.queryParameter("review_type"))
        assertNull(captured.url.queryParameter("day_range"))
        assertEquals("20", captured.url.queryParameter("num_per_page"))
        assertEquals("page-token", captured.url.queryParameter("cursor"))
        assertEquals("Great", page.items.single().body)
        assertEquals("next", page.nextCursor)
    }

    @Test
    fun reviewPageAppliesSentimentAndTimeFiltersToSteamRequest() {
        lateinit var captured: Request
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured = chain.request()
                Response.Builder()
                    .request(captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"success":1,"query_summary":{},"reviews":[],"cursor":""}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()

        SteamStoreReviewService(client).fetchPage(
            appId = 620,
            filters = SteamReviewFilterSelection(
                sentiment = SteamReviewSentimentFilter.NEGATIVE,
                time = SteamReviewTimeFilter.RECENT_30_DAYS
            )
        )

        assertEquals("negative", captured.url.queryParameter("review_type"))
        assertEquals("recent", captured.url.queryParameter("filter"))
        assertEquals("30", captured.url.queryParameter("day_range"))
    }

    @Test fun unavailablePageKeepsSummaryAndOffersAnExplicitRetryState() {
        val service = SteamStoreReviewService(OkHttpClient.Builder().addInterceptor { chain ->
            val histogram = chain.request().url.encodedPath.startsWith("/appreviewhistogram")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((if (histogram) """{"results":{"recent":[{"recommendations_up":4,"recommendations_down":1}]}}"""
                    else "<html>Welcome to Steam</html>").toResponseBody("application/json".toMediaType())).build()
        }.build())
        val result = requireNotNull(service.fetch(620))
        org.junit.Assert.assertTrue(result.pageLoadFailed)
        assertEquals(5, result.recent!!.total)
        org.junit.Assert.assertTrue(result.items.isEmpty())
    }

    @Test fun totalFailureRemainsVisibleAndRetryCanRecover() {
        var fail = true
        val service = SteamStoreReviewService(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (fail) 503 else 200).message("fixture")
                .body("""{"success":1,"reviews":[{"recommendationid":"1","review":"Recovered","voted_up":true}]}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build())
        org.junit.Assert.assertTrue(requireNotNull(service.fetch(620)).pageLoadFailed)
        fail = false
        assertEquals("Recovered", service.fetchPage(620).items.single().body)
    }
}
