package takagi.ru.monica.steam.library

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.family.SteamFamilyLibraryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamFamilyLibraryServiceTest {
    @Test
    fun familyGroupResponseDistinguishesMembershipFromNoGroup() {
        val member = SteamProtoWriter().apply { writeVarint(1, 42L) }.toByteArray()
        val noGroup = SteamProtoWriter().apply { writeBool(2, true) }.toByteArray()

        assertEquals(42L, SteamFamilyLibraryService.parseFamilyGroupId(member))
        assertNull(SteamFamilyLibraryService.parseFamilyGroupId(noGroup))
    }

    @Test
    fun emptyFamilyMembershipResponseIsNotTreatedAsLeavingTheGroup() {
        assertTrue(
            runCatching {
                SteamFamilyLibraryService.parseFamilyGroupId(ByteArray(0))
            }.isFailure
        )
    }

    @Test
    fun sharedAppsParseOwnersPlaytimeAndSkipExcludedLicenses() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, sharedApp(20, "Shared game", 7_200L, excludeReason = 0))
            writeMessage(1, sharedApp(30, "Excluded game", 3_600L, excludeReason = 2))
        }.toByteArray()

        val game = SteamFamilyLibraryService.parseSharedLibraryApps(response).single()

        assertEquals(20, game.appId)
        assertEquals("Shared game", game.name)
        assertEquals(120, game.playtimeForeverMinutes)
        assertEquals("icon-20", game.iconHash)
        assertEquals(listOf(FAMILY_OWNER_STEAM_ID.toString()), game.ownerSteamIds)
        assertEquals(SteamGameOwnership.FAMILY_SHARED, game.ownership)
    }

    @Test
    fun libraryFetchMergesFamilyGamesAndPrefersOwnedDuplicate() {
        val requests = mutableListOf<Pair<String, String>>()
        val ownedResponse = SteamProtoWriter().apply {
            writeVarint(1, 1L)
            writeMessage(2, SteamProtoWriter().apply {
                writeVarint(1, 10L)
                writeString(2, "Owned game")
                writeVarint(4, 60L)
            })
        }.toByteArray()
        val familyGroupResponse = SteamProtoWriter().apply {
            writeVarint(1, 42L)
        }.toByteArray()
        val familyAppsResponse = SteamProtoWriter().apply {
            writeMessage(1, sharedApp(10, "Duplicate shared game", 600L))
            writeMessage(1, sharedApp(20, "Family game", 7_200L))
        }.toByteArray()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.method to request.url.encodedPath
                val body = when {
                    request.url.encodedPath.contains("GetOwnedGames") -> ownedResponse
                    request.url.encodedPath.contains("GetFamilyGroupForUser") -> familyGroupResponse
                    request.url.encodedPath.contains("GetSharedLibraryApps") -> familyAppsResponse
                    request.url.encodedPath.contains("GetItems") -> ByteArray(0)
                    request.url.encodedPath.contains("GetAchievementsProgress") -> ByteArray(0)
                    else -> error("Unexpected request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchLibrary(
            account = account(),
            countryCode = "CN",
            language = "schinese"
        ) as SteamLibraryResult.Success

        assertEquals(listOf(10, 20), result.value.games.map(SteamGame::appId))
        assertEquals(SteamGameOwnership.OWNED, result.value.games.first().ownership)
        assertTrue(result.value.games.last().isFamilyShared)
        assertEquals(1, result.value.gameCount)
        assertEquals(1, result.value.sharedGameCount)
        assertEquals(42L, result.value.familyGroupId)
        assertTrue(
            requests.filter { it.second.contains("IFamilyGroupsService") }
                .all { it.first == "GET" }
        )
    }

    private fun sharedApp(
        appId: Int,
        name: String,
        playtimeSeconds: Long,
        excludeReason: Int = 0
    ): SteamProtoWriter = SteamProtoWriter().apply {
        writeVarint(1, appId.toLong())
        writeFixed64(2, FAMILY_OWNER_STEAM_ID)
        writeString(6, name)
        writeString(9, "icon-$appId")
        writeVarint(10, excludeReason.toLong())
        writeVarint(13, playtimeSeconds)
        writeVarint(14, 1L)
    }

    private fun account(): SteamAccount = SteamAccount(
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
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private companion object {
        const val FAMILY_OWNER_STEAM_ID = 76561198000000002L
    }
}
