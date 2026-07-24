package takagi.ru.monica.steam.store

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreReviewService

class SteamStoreReviewServiceTest {
    @Test
    fun reviewPageUsesOfficialCursorPagination() {
        lateinit var captured: Request
        val payload = """{
          "query_summary":{"review_score":8,"total_positive":8,"total_negative":2,"total_reviews":10},
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

        assertEquals("/appreviews/570", captured.url.encodedPath)
        assertEquals("recent", captured.url.queryParameter("filter"))
        assertEquals("20", captured.url.queryParameter("num_per_page"))
        assertEquals("page-token", captured.url.queryParameter("cursor"))
        assertEquals("Great", page.items.single().body)
        assertEquals("next", page.nextCursor)
    }
}
