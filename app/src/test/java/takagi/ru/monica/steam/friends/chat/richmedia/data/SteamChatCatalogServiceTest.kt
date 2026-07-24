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
    fun loadsOnlyOwnedEmoticonsStickersAndEffectsFromOfficialCatalogue() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val responseBytes = when {
                request.url.encodedPath.contains("GetEmoticonList") -> SteamProtoWriter().apply {
                    writeMessage(1, SteamProtoWriter().apply {
                        writeString(1, ":steamthumbsup:")
                        writeVarint(2, 1L)
                        writeVarint(3, 100L)
                        writeVarint(4, 5L)
                        writeVarint(6, 753L)
                    })
                    writeMessage(2, SteamProtoWriter().apply {
                        writeString(1, "Mesmer spin")
                        writeVarint(2, 1L)
                        writeVarint(4, 570L)
                        writeVarint(5, 90L)
                    })
                    writeMessage(3, SteamProtoWriter().apply {
                        writeString(1, "confetti")
                        writeVarint(2, 1L)
                        writeVarint(3, 80L)
                        writeBool(4, true)
                        writeVarint(5, 570L)
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

        val catalog = service.loadCatalog(account())
        val emoticon = catalog.emoticons.single()
        val sticker = catalog.stickers.single()
        val effect = catalog.effects.single()

        assertEquals("steamthumbsup", emoticon.name)
        assertEquals(":steamthumbsup:", emoticon.messageCode)
        assertEquals("Mesmer spin", sticker.name)
        assertTrue(sticker.imageUrl.endsWith("Mesmer%20spin"))
        assertEquals("/roomeffect confetti", effect.messageCode)
        assertTrue(catalog.stickers.none { it.name == "locked-point-shop-item" })
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
