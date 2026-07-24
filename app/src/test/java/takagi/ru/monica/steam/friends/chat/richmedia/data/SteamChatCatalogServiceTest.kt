package takagi.ru.monica.steam.friends.chat.richmedia.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamChatCatalogServiceTest {
    @Test
    fun loadsOwnedEmoticonsAndPublicStickerDefinitions() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val responseBytes = when {
                request.url.encodedPath.contains("GetEmoticonList") -> SteamProtoWriter().apply {
                    writeMessage(1, SteamProtoWriter().apply {
                        writeString(1, ":steamthumbsup:")
                        writeVarint(3, 100L)
                        writeVarint(4, 5L)
                        writeVarint(6, 753L)
                    })
                }.toByteArray()
                request.url.encodedPath.contains("QueryRewardItems") -> SteamProtoWriter().apply {
                    writeMessage(1, SteamProtoWriter().apply {
                        writeVarint(1, 570L)
                        writeVarint(4, 11L)
                        writeMessage(13, SteamProtoWriter().apply {
                            writeString(1, "Mesmer spin")
                            writeString(2, "Mesmer")
                        })
                    })
                }.toByteArray()
                else -> error("Unexpected request: ${request.url}")
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBytes.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }.build()
        val service = SteamChatCatalogService(SteamApiClient(client))

        val emoticon = service.loadEmoticons(account()).single()
        val sticker = service.loadStickers().single()

        assertEquals("steamthumbsup", emoticon.name)
        assertEquals(":steamthumbsup:", emoticon.messageCode)
        assertEquals("Mesmer spin", sticker.name)
        assertTrue(sticker.imageUrl.endsWith("Mesmer%20spin"))
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = null,
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}
