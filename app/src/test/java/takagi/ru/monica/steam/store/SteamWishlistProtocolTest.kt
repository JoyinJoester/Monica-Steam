package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.data.buildSteamWishlistMutationProtoRequest
import takagi.ru.monica.steam.store.data.buildSteamWishlistProtoRequest
import takagi.ru.monica.steam.store.data.parseSteamWishlistProtoResponse

class SteamWishlistProtocolTest {
    @Test
    fun storeServiceUsesCurrentWishlistApiInsteadOfDeprecatedWebRoutes() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/data/SteamStoreService.kt"
        ).readText()

        assertTrue(source.contains("iface = \"IWishlistService\""))
        assertTrue(source.contains("GetWishlistSortedFiltered"))
        assertTrue(source.contains("AddToWishlist"))
        assertTrue(source.contains("RemoveFromWishlist"))
        assertFalse(source.contains("wishlistdata"))
        assertFalse(source.contains("/api/addtowishlist"))
        assertFalse(source.contains("/api/removefromwishlist"))
    }

    @Test
    fun buildsCurrentWishlistServiceRequestWithPagingAndStoreData() {
        val request = SteamProtoReader(
            buildSteamWishlistProtoRequest(
                steamId = "76561198000000000",
                startIndex = 200,
                pageSize = 100,
                countryCode = "cn",
                language = "schinese"
            ).toByteArray()
        ).parse()

        assertEquals("76561198000000000", request[1]?.asFixed64UnsignedString)
        val context = SteamProtoReader(requireNotNull(request[2]?.bytes)).parse()
        assertEquals("schinese", context[1]?.asString)
        assertEquals("CN", context[3]?.asString)
        val dataRequest = SteamProtoReader(requireNotNull(request[3]?.bytes)).parse()
        assertTrue(dataRequest[1]?.asBool == true)
        assertTrue(dataRequest[4]?.asBool == true)
        assertEquals(200, request[6]?.asInt)
        assertEquals(100, request[7]?.asInt)
    }

    @Test
    fun parsesWishlistServiceItemsWithAssetsAndLocalizedPrices() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 620)
                writeVarint(2, 3)
                writeVarint(3, 1_700_000_000)
                writeMessage(4, SteamProtoWriter().apply {
                    writeString(6, "Portal 2")
                    writeMessage(30, SteamProtoWriter().apply {
                        writeString(1, "apps/620/\${FILENAME}")
                        writeString(2, "capsule.jpg")
                    })
                    writeMessage(40, SteamProtoWriter().apply {
                        writeVarint(1, 1234)
                        writeVarint(5, 2100)
                        writeVarint(6, 4200)
                        writeString(8, "¥ 21.00")
                        writeString(9, "¥ 42.00")
                        writeVarint(10, 50)
                    })
                })
            })
        }

        val item = parseSteamWishlistProtoResponse(response.toByteArray()).single()

        assertEquals(620, item.appId)
        assertEquals("Portal 2", item.name)
        assertEquals(
            "https://shared.akamai.steamstatic.com/store_item_assets/apps/620/capsule.jpg",
            item.imageUrl
        )
        assertEquals(1234, item.packageId)
        assertEquals(50, item.discountPercent)
        assertEquals("¥ 42.00", item.formattedInitialPrice)
        assertEquals("¥ 21.00", item.formattedFinalPrice)
        assertEquals(3, item.priority)
        assertEquals(1_700_000_000L, item.addedAtEpochSeconds)
    }

    @Test
    fun wishlistMutationUsesCurrentServiceAppIdRequest() {
        val request = SteamProtoReader(
            buildSteamWishlistMutationProtoRequest(620).toByteArray()
        ).parse()

        assertEquals(620, request[1]?.asInt)
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
