package takagi.ru.monica.steam.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamLibraryModelsTest {
    @Test
    fun summaryCountsPlaytimeAndOnlyPricedGamesInEstimate() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                SteamGame(appId = 10, name = "A", playtimeForeverMinutes = 120, playtimeRecentMinutes = 60,
                    price = SteamGamePrice(
                        currency = "CNY",
                        finalPriceMinor = 1_590,
                        originalPriceMinor = 1_990,
                        isAvailable = true
                    )),
                SteamGame(appId = 20, name = "B", playtimeForeverMinutes = 30, playtimeRecentMinutes = 0,
                    price = null),
                SteamGame(appId = 30, name = "C", playtimeForeverMinutes = 0, playtimeRecentMinutes = 0,
                    price = SteamGamePrice(currency = "CNY", finalPriceMinor = 0, isAvailable = false))
            ),
            fetchedAt = 1_700_000_000_000L
        )

        assertEquals(3, snapshot.gameCount)
        assertEquals(150L, snapshot.totalPlaytimeMinutes)
        assertEquals(60L, snapshot.recentPlaytimeMinutes)
        assertEquals(1_990L, snapshot.estimatedReplacementValueMinor)
        assertEquals(1, snapshot.pricedGameCount)
        assertEquals(2, snapshot.unpricedGameCount)
        assertEquals(1f / 3f, snapshot.priceCoverage, 0.0001f)
    }

    @Test
    fun oldEncryptedSnapshotPayloadStillDecodesWithDashboardDefaults() {
        val snapshot = Json.decodeFromString(
            SteamLibrarySnapshot.serializer(),
            """{"accountId":7,"games":[{"appId":10,"name":"Old","playtimeForeverMinutes":1,"playtimeRecentMinutes":0,"price":{"currency":"CNY","finalPriceMinor":990,"isAvailable":true}}],"fetchedAt":123,"region":"CN","currency":"CNY"}"""
        )

        assertNull(snapshot.inventoryItemCount)
        assertNull(snapshot.inventoryFetchedAt)
        assertNull(snapshot.inventoryFailure)
        assertEquals("", snapshot.games.single().headerImageUrl)
        assertEquals(990L, snapshot.games.single().price?.originalPriceMinor)
        assertTrue(snapshot.games.single().regionalPrices.isEmpty())
        assertNull(snapshot.games.single().achievementUnlockedCount)
        assertNull(snapshot.games.single().achievementTotalCount)
        assertTrue(!snapshot.games.single().allAchievementsUnlocked)
    }

    @Test
    fun inventoryFailureKeepsPreviousCountWhileFreshGamesReplaceLibrary() {
        val cached = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(SteamGame(1, "Old", 1, 0)),
            fetchedAt = 100L,
            inventoryItemCount = 230,
            inventoryFetchedAt = 90L
        )
        val fresh = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(SteamGame(2, "Fresh", 2, 1)),
            fetchedAt = 200L
        )

        val merged = mergeLibraryDashboardSnapshot(
            fresh = fresh,
            cached = cached,
            inventoryResult = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
        )

        assertEquals(listOf("Fresh"), merged.games.map(SteamGame::name))
        assertEquals(230, merged.inventoryItemCount)
        assertEquals(90L, merged.inventoryFetchedAt)
        assertEquals(SteamLibraryFailureReason.NETWORK, merged.inventoryFailure)
    }

    @Test
    fun freshInventorySummaryReplacesCachedInventoryCount() {
        val fresh = SteamLibrarySnapshot(accountId = 7L, games = emptyList(), fetchedAt = 200L)

        val merged = mergeLibraryDashboardSnapshot(
            fresh = fresh,
            cached = null,
            inventoryResult = SteamLibraryResult.Success(
                SteamInventorySummary(itemCount = 42, fetchedAt = 199L)
            )
        )

        assertEquals(42, merged.inventoryItemCount)
        assertEquals(199L, merged.inventoryFetchedAt)
        assertNull(merged.inventoryFailure)
    }

    @Test
    fun failedStoreBatchKeepsCachedHeaderAndPriceForOfflineDisplay() {
        val cached = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                SteamGame(
                    appId = 10,
                    name = "Game",
                    playtimeForeverMinutes = 1,
                    playtimeRecentMinutes = 0,
                    headerImageUrl = "https://example/header.jpg",
                    price = SteamGamePrice("CNY", 990, 1_990, true),
                    achievementUnlockedCount = 12,
                    achievementTotalCount = 12,
                    allAchievementsUnlocked = true
                )
            ),
            fetchedAt = 100L
        )
        val fresh = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(SteamGame(10, "Game", 2, 1)),
            fetchedAt = 200L,
            priceFailure = SteamLibraryFailureReason.NETWORK
        )

        val merged = mergeLibraryDashboardSnapshot(
            fresh = fresh,
            cached = cached,
            inventoryResult = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
        )

        assertEquals(cached.games.single().headerImageUrl, merged.games.single().headerImageUrl)
        assertEquals(1_990L, merged.games.single().price?.originalPriceMinor)
        assertEquals(12, merged.games.single().achievementUnlockedCount)
        assertEquals(12, merged.games.single().achievementTotalCount)
        assertTrue(merged.games.single().isPerfectAchievementGame)
    }

    @Test
    fun freshLibraryKeepsCachedRegionalPricesThatBulkRefreshDoesNotFetch() {
        val cachedRegionalPrices = listOf(
            SteamRegionalPrice("US", "USD", 999, 1_999, true, 100L)
        )
        val cached = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(SteamGame(10, "Game", 1, 0, regionalPrices = cachedRegionalPrices)),
            fetchedAt = 100L
        )
        val fresh = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(SteamGame(10, "Game", 2, 1)),
            fetchedAt = 200L
        )

        val merged = mergeLibraryDashboardSnapshot(
            fresh = fresh,
            cached = cached,
            inventoryResult = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
        )

        assertEquals(cachedRegionalPrices, merged.games.single().regionalPrices)
    }

    @Test
    fun achievementProgressSeparatesUnlockedAndLocked() {
        val details = SteamGameAchievements(
            accountId = 7L,
            appId = 10,
            gameName = "A",
            achievements = listOf(
                SteamAchievement("one", "One", "", true, 1_700_000_000L, null, null),
                SteamAchievement("two", "Two", "", false, null, null, null)
            ),
            fetchedAt = 1L
        )
        assertEquals(1, details.completed.size)
        assertEquals(1, details.incomplete.size)
        assertEquals(0.5f, details.completionRate, 0.0001f)
    }

    @Test
    fun ownedGamesProtobufParsesPlaytimeFields() {
        val game = SteamProtoWriter().apply {
            writeUint64(1, 730L)
            writeString(2, "Portal")
            writeUint64(3, 45L)
            writeUint64(4, 1_234L)
            writeString(5, "icon-hash")
        }
        val response = SteamProtoWriter().apply {
            writeVarint(1, 1L)
            writeMessage(2, game)
        }.toByteArray()

        val parsed = SteamGameLibraryService.parseOwnedGames(response)

        assertEquals(1, parsed.size)
        assertEquals(730, parsed.single().appId)
        assertEquals("Portal", parsed.single().name)
        assertEquals(45, parsed.single().playtimeRecentMinutes)
        assertEquals(1_234, parsed.single().playtimeForeverMinutes)
        assertEquals("icon-hash", parsed.single().iconHash)
    }

    @Test
    fun achievementProgressProtobufParsesPerfectCompletion() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 730L)
                writeVarint(2, 10L)
                writeVarint(3, 10L)
                writeBool(5, true)
                writeBool(7, true)
            })
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 570L)
                writeVarint(2, 5L)
                writeVarint(3, 12L)
                writeBool(5, false)
                writeBool(7, true)
            })
        }.toByteArray()

        val progress = SteamGameLibraryService.parseAchievementProgress(response)

        assertEquals(10, progress.getValue(730).unlocked)
        assertEquals(10, progress.getValue(730).total)
        assertTrue(progress.getValue(730).allUnlocked)
        assertTrue(!progress.getValue(570).allUnlocked)
    }

    @Test
    fun declaredGamesWithoutGameMessagesAreRejectedInsteadOfCachedAsEmpty() {
        val malformedResponse = SteamProtoWriter().apply {
            writeVarint(1, 12L)
        }.toByteArray()

        val failure = runCatching {
            SteamGameLibraryService.parseOwnedGames(malformedResponse)
        }.exceptionOrNull()

        assertTrue(failure != null)
    }

    @Test
    fun privateAchievementsAreNotTreatedAsEmptyGame() {
        val result = SteamGameLibraryService.parseAchievements(
            appId = 730,
            gameName = "Counter-Strike",
            payload = jsonObject("{\"playerstats\":{\"success\":false}}")
        )

        assertTrue(result is SteamLibraryResult.Failure)
        assertEquals(SteamLibraryFailureReason.PRIVATE_PROFILE, (result as SteamLibraryResult.Failure).reason)
    }

    @Test
    fun unavailableStorePriceRemainsUnpriced() {
        val price = SteamGameLibraryService.parsePrice(
            appId = 10,
            payload = jsonObject("{\"10\":{\"success\":true,\"data\":{\"is_free\":true}}}")
        )
        assertNull(price)
    }

    @Test
    fun ownedGamesUsesGetBecauseSteamRejectsPostForThisEndpoint() {
        val requests = mutableListOf<Pair<String, String>>()
        val game = SteamProtoWriter().apply {
            writeUint64(1, 730L)
            writeString(2, "Counter-Strike 2")
        }
        val ownedGamesResponse = SteamProtoWriter().apply {
            writeVarint(1, 1L)
            writeMessage(2, game)
        }.toByteArray()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.method to request.url.encodedPath
                val body = if (request.url.encodedPath.contains("GetOwnedGames")) {
                    ownedGamesResponse.toResponseBody("application/octet-stream".toMediaType())
                } else {
                    "{}".toResponseBody("application/json".toMediaType())
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchLibrary(
            account = account(accessToken = "access-token"),
            countryCode = "CN",
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Success)
        assertEquals(
            "GET",
            requests.single { it.second.contains("GetOwnedGames") }.first
        )
    }

    @Test
    fun storeBrowseUsesOneGetAndReturnsLocalizedHeaderAndOriginalCnPrice() {
        val requests = mutableListOf<Pair<String, String>>()
        val games = listOf(
            ownedGame(appId = 1718570, name = "ASTLIBRA Revision"),
            ownedGame(appId = 730, name = "Counter-Strike 2")
        )
        val ownedGamesResponse = SteamProtoWriter().apply {
            writeVarint(1, games.size.toLong())
            games.forEach { writeMessage(2, it) }
        }.toByteArray()
        val storeResponse = SteamProtoWriter().apply {
            writeMessage(
                1,
                storeItem(
                    appId = 1718570,
                    assetFormat = "steam/apps/1718570/\${FILENAME}?t=1770740786",
                    header = "header_schinese.jpg",
                    finalPrice = 8_000,
                    originalPrice = 8_800
                )
            )
            writeMessage(
                1,
                storeItem(
                    appId = 730,
                    assetFormat = "steam/apps/730/\${FILENAME}",
                    header = "header.jpg",
                    finalPrice = 0,
                    originalPrice = null,
                    isFree = true
                )
            )
        }.toByteArray()
        val progressResponse = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 1718570L)
                writeVarint(2, 63L)
                writeVarint(3, 63L)
                writeBool(5, true)
            })
        }.toByteArray()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.method to request.url.encodedPath
                val body = when {
                    request.url.encodedPath.contains("GetOwnedGames") -> ownedGamesResponse
                    request.url.encodedPath.contains("GetItems") -> storeResponse
                    request.url.encodedPath.contains("GetAchievementsProgress") -> progressResponse
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
            account = account(accessToken = "access-token"),
            countryCode = "CN",
            language = "schinese"
        ) as SteamLibraryResult.Success

        val storeRequests = requests.filter { it.second.contains("GetItems") }
        assertEquals(listOf("GET"), storeRequests.map { it.first })
        assertEquals(
            listOf("GET"),
            requests.filter { it.second.contains("GetAchievementsProgress") }.map { it.first }
        )
        assertTrue(requests.none { it.second.contains("appdetails") })
        val astlibra = result.value.games.first { it.appId == 1718570 }
        assertEquals(
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1718570/header_schinese.jpg?t=1770740786",
            astlibra.headerImageUrl
        )
        assertEquals(8_000L, astlibra.price?.finalPriceMinor)
        assertEquals(8_800L, astlibra.price?.originalPriceMinor)
        assertEquals(63, astlibra.achievementUnlockedCount)
        assertEquals(63, astlibra.achievementTotalCount)
        assertTrue(astlibra.isPerfectAchievementGame)
        assertEquals(8_800L, result.value.estimatedReplacementValueMinor)
        assertEquals("CNY", result.value.currency)
    }

    @Test
    fun regionalPriceLookupReturnsRequestedCountriesWithLocalCurrencies() {
        val storeResponse = SteamProtoWriter().apply {
            writeMessage(
                1,
                storeItem(
                    appId = 1718570,
                    assetFormat = "steam/apps/1718570/\${FILENAME}",
                    header = "header.jpg",
                    finalPrice = 1_290,
                    originalPrice = 8_600
                )
            )
        }.toByteArray()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(storeResponse.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchRegionalPrices(
            account = account(accessToken = "access-token"),
            appId = 1718570,
            countryCodes = listOf("CN", "US", "JP"),
            language = "schinese"
        ) as SteamLibraryResult.Success

        assertEquals(listOf("CN", "US", "JP"), result.value.map(SteamRegionalPrice::countryCode))
        assertEquals(listOf("CNY", "USD", "JPY"), result.value.map(SteamRegionalPrice::currency))
    }

    @Test
    fun regionalPricesConvertToCnyAndKeepChinaFirstThenSortByConvertedPrice() {
        val converted = applyCnyConversions(
            prices = listOf(
                SteamRegionalPrice("US", "USD", 119, 799, true, 100L),
                SteamRegionalPrice("CN", "CNY", 1_290, 8_600, true, 100L),
                SteamRegionalPrice("UA", "UAH", 5_200, 34_900, true, 100L)
            ),
            unitsPerCny = mapOf("USD" to 0.1475, "UAH" to 6.59),
            exchangeRateFetchedAt = 90L
        )

        assertEquals(1_290L, converted.first { it.countryCode == "CN" }.cnyFinalPriceMinor)
        assertEquals(807L, converted.first { it.countryCode == "US" }.cnyFinalPriceMinor)
        assertEquals(789L, converted.first { it.countryCode == "UA" }.cnyFinalPriceMinor)
        assertEquals(
            listOf("CN", "UA", "US"),
            sortedRegionalPricesForDisplay(converted).map(SteamRegionalPrice::countryCode)
        )
    }

    @Test
    fun exchangeFailureKeepsPreviousCnyConversion() {
        val cached = SteamRegionalPrice(
            "US", "USD", 199, 999, true, 50L,
            cnyFinalPriceMinor = 1_430,
            cnyOriginalPriceMinor = 7_190,
            exchangeRateFetchedAt = 40L
        )
        val merged = mergeCachedRegionalPriceConversions(
            fresh = listOf(SteamRegionalPrice("US", "USD", 199, 999, true, 100L)),
            cached = listOf(cached)
        ).single()
        assertEquals(1_430L, merged.cnyFinalPriceMinor)
        assertEquals(7_190L, merged.cnyOriginalPriceMinor)
    }

    @Test
    fun currencyExchangePayloadParsesCnyBasedRates() {
        val rates = SteamCurrencyExchangeService.parseCnyRates(
            """{"result":"success","base_code":"CNY","time_last_update_unix":123,"rates":{"CNY":1,"USD":0.1475,"JPY":23.9}}"""
        )
        assertEquals(0.1475, rates.unitsPerCny.getValue("USD"), 0.000001)
        assertEquals(123_000L, rates.fetchedAt)
    }

    @Test
    fun missingStoreOriginalPriceFallsBackToFinalPrice() {
        val response = SteamProtoWriter().apply {
            writeMessage(
                1,
                storeItem(
                    appId = 10,
                    assetFormat = "steam/apps/10/\${FILENAME}",
                    header = "header.jpg",
                    finalPrice = 2_680,
                    originalPrice = null
                )
            )
        }.toByteArray()

        val metadata = SteamGameLibraryService.parseStoreItems(response).getValue(10)

        assertEquals(2_680L, metadata.price?.originalPriceMinor)
    }

    @Test
    fun protobufAchievementDefinitionsMergeWithUserUnlockStatus() {
        val definitions = SteamProtoWriter().apply {
            writeMessage(1, achievementDefinition(42, "ACH_WIN", "Winner", "Win once"))
            writeMessage(1, achievementDefinition(43, "ACH_SECRET", "Secret", "Hidden"))
        }.toByteArray()
        val unlockedAt = 1_700_000_000L
        val user = SteamProtoWriter().apply {
            writeBytes(1, achievementStatusFixed32(42, unlocked = true, unlockTime = unlockedAt))
        }.toByteArray()

        val parsed = SteamGameLibraryService.parseAchievementResponses(
            accountId = 7L,
            appId = 10,
            gameName = "Game",
            definitionsResponse = definitions,
            userResponse = user
        )

        assertEquals(2, parsed.achievements.size)
        assertTrue(parsed.achievements.first { it.apiName == "ACH_WIN" }.achieved)
        assertEquals(
            unlockedAt,
            parsed.achievements.first { it.apiName == "ACH_WIN" }.unlockTimeSeconds
        )
        assertTrue(!parsed.achievements.first { it.apiName == "ACH_SECRET" }.achieved)
    }

    @Test
    fun achievementDetailsUsePlayerServiceProtobufEndpoints() {
        val requests = mutableListOf<Pair<String, String>>()
        val definitions = SteamProtoWriter().apply {
            writeMessage(1, achievementDefinition(42, "ACH_WIN", "Winner", "Win once"))
        }.toByteArray()
        val user = SteamProtoWriter().apply {
            writeBytes(1, achievementStatusFixed32(42, unlocked = true, unlockTime = 1_700_000_000L))
        }.toByteArray()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.method to request.url.encodedPath
                val body = when {
                    request.url.encodedPath.contains("GetGameAchievements") -> definitions
                    request.url.encodedPath.contains("GetUserAchievements") -> user
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

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchAchievements(
            account = account(accessToken = "access-token"),
            game = SteamGame(10, "Game", 1, 0),
            language = "schinese"
        ) as SteamLibraryResult.Success

        assertEquals(1, result.value.completed.size)
        assertEquals(
            listOf("GET", "GET"),
            requests.filter { it.second.contains("Achievements") }.map { it.first }
        )
        assertTrue(requests.none { it.second.contains("ISteamUserStats") })
    }

    @Test
    fun unauthorizedOwnedGamesResponseRequiresFreshSteamSession() {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("unauthorized".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchLibrary(
            account = account(accessToken = "expired-token"),
            countryCode = "CN",
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Failure)
        assertEquals(
            SteamLibraryFailureReason.SESSION_REQUIRED,
            (result as SteamLibraryResult.Failure).reason
        )
    }

    private fun jsonObject(raw: String): kotlinx.serialization.json.JsonObject {
        return kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
    }

    private fun ownedGame(appId: Int, name: String): SteamProtoWriter = SteamProtoWriter().apply {
        writeUint64(1, appId.toLong())
        writeString(2, name)
    }

    private fun storeItem(
        appId: Int,
        assetFormat: String,
        header: String,
        finalPrice: Long,
        originalPrice: Long?,
        isFree: Boolean = false
    ): SteamProtoWriter = SteamProtoWriter().apply {
        writeVarint(9, appId.toLong())
        writeBool(13, isFree)
        writeMessage(30, SteamProtoWriter().apply {
            writeString(1, assetFormat)
            writeString(4, header)
        })
        if (!isFree) {
            writeMessage(40, SteamProtoWriter().apply {
                writeVarint(5, finalPrice)
                originalPrice?.let { writeVarint(6, it) }
            })
        }
    }

    private fun achievementDefinition(
        key: Long,
        internalName: String,
        displayName: String,
        description: String
    ): SteamProtoWriter = SteamProtoWriter().apply {
        writeString(1, internalName)
        writeString(2, displayName)
        writeString(3, description)
        writeString(4, "https://cdn.example/icon.jpg")
        writeString(5, "https://cdn.example/icon_gray.jpg")
        writeVarint(8, key)
    }

    private fun achievementStatusFixed32(
        key: Int,
        unlocked: Boolean,
        unlockTime: Long
    ): ByteArray {
        return byteArrayOf(
            0x08,
            key.toByte(),
            0x10,
            if (unlocked) 0x01 else 0x00,
            0x1d,
            (unlockTime and 0xff).toByte(),
            ((unlockTime shr 8) and 0xff).toByte(),
            ((unlockTime shr 16) and 0xff).toByte(),
            ((unlockTime shr 24) and 0xff).toByte()
        )
    }

    private fun account(accessToken: String): SteamAccount = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = accessToken,
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}
