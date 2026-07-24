package takagi.ru.monica.steam.store

import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.store.data.SteamStoreReviewService
import takagi.ru.monica.steam.store.data.SteamStoreService

class SteamStoreCompactDetailTest {
    @Test
    fun compactDetailLoadsAppContentWithoutReviewRequests() {
        val requests = CopyOnWriteArrayList<Request>()
        val payload = """
            {
              "430960": {
                "success": true,
                "data": {
                  "steam_appid": 430960,
                  "name": "Wallpaper Engine",
                  "short_description": "Use live wallpapers on your desktop.",
                  "header_image": "https://cdn.example/430960.jpg"
                }
              }
            }
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val service = SteamStoreService(
            client = client,
            api = SteamApiClient(client),
            reviewService = SteamStoreReviewService(client)
        )

        val detail = service.compactDetail(430960)

        assertEquals("Wallpaper Engine", detail.name)
        assertNull(detail.reviews)
        assertEquals(listOf("/api/appdetails"), requests.map { it.url.encodedPath })
    }
}
