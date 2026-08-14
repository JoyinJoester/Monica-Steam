package takagi.ru.monica.steam.library

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun accountRegionResolutionPrefersSteamThenCacheThenDevice() {
        assertEquals("DE", resolveSteamLibraryCountryCode("de", "CN", "US"))
        assertEquals("JP", resolveSteamLibraryCountryCode(null, "jp", "US"))
        assertEquals("GB", resolveSteamLibraryCountryCode("invalid", null, "gb"))
        assertEquals("US", resolveSteamLibraryCountryCode(null, null, ""))
        assertEquals("EUR", SteamGameLibraryService.currencyForCountry("DE"))
        assertEquals("GBP", SteamGameLibraryService.currencyForCountry("GB"))
        assertEquals("USD", SteamGameLibraryService.currencyForCountry("PK"))
    }

    @Test
    fun cachedReplacementValueCurrencyUsesPriceThenRegionWithoutCnyDefault() {
        val priced = SteamLibrarySnapshot(
            accountId = 1L,
            games = listOf(
                SteamGame(
                    appId = 10,
                    name = "Euro game",
                    playtimeForeverMinutes = 0,
                    playtimeRecentMinutes = 0,
                    price = SteamGamePrice("EUR", 999L, isAvailable = true)
                )
            ),
            fetchedAt = 1L,
            region = "DE",
            currency = ""
        )
        val regionOnly = SteamLibrarySnapshot(
            accountId = 2L,
            games = emptyList(),
            fetchedAt = 1L,
            region = "GB",
            currency = ""
        )

        assertEquals("EUR", resolvedSteamLibraryCurrency(priced))
        assertEquals("GBP", resolvedSteamLibraryCurrency(regionOnly))
        assertEquals("USD", resolvedSteamLibraryCurrency(null))
    }

    @Test
    fun replacementValueRequestsAndWidgetCurrencyAreNotFixedToChina() {
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt"
        ).readText()
        val widget = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/quickaccess/SteamWidgetData.kt"
        ).readText()

        assertTrue(viewModel.contains("storeService.accountCountryCode(account)"))
        assertFalse(viewModel.contains("fetchLibrary(prepared, countryCode = \"CN\""))
        assertFalse(viewModel.contains("fetchLibrary(refreshed, countryCode = \"CN\""))
        assertTrue(widget.contains("resolvedSteamLibraryCurrency(library)"))
        assertFalse(widget.contains("ifBlank { \"CNY\" }"))
    }

    @Test
    fun southAsiaPricingCountriesShareOneDisplayRegion() {
        listOf("PK", "BD", "BT", "NP", "LK").forEach { countryCode ->
            assertTrue(isSteamSouthAsiaPriceCountry(countryCode))
        }
        assertTrue(SteamLibraryViewModel.REGIONAL_PRICE_COUNTRY_CODES.contains("PK"))
    }

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
        assertEquals(SteamGameOwnership.OWNED, snapshot.games.single().ownership)
        assertTrue(snapshot.games.single().ownerSteamIds.isEmpty())
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
    fun familyGamesRemainAvailableWithoutInflatingOwnedCountOrAccountValue() {
        val owned = SteamGame(
            appId = 10,
            name = "Owned",
            playtimeForeverMinutes = 60,
            playtimeRecentMinutes = 0,
            price = SteamGamePrice("CNY", 1_990, 2_990, true)
        )
        val shared = SteamGame(
            appId = 20,
            name = "Shared",
            playtimeForeverMinutes = 120,
            playtimeRecentMinutes = 0,
            price = SteamGamePrice("CNY", 3_990, 4_990, true),
            ownership = SteamGameOwnership.FAMILY_SHARED,
            ownerSteamIds = listOf("76561198000000002")
        )
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(owned, shared),
            fetchedAt = 1L
        )

        assertEquals(1, snapshot.gameCount)
        assertEquals(2, snapshot.availableGameCount)
        assertEquals(1, snapshot.sharedGameCount)
        assertEquals(2_990L, snapshot.estimatedReplacementValueMinor)
        assertEquals(180L, snapshot.totalPlaytimeMinutes)
    }

    @Test
    fun familyRequestFailureKeepsCachedSharedGamesOffline() {
        val cachedShared = SteamGame(
            appId = 20,
            name = "Shared cached",
            playtimeForeverMinutes = 120,
            playtimeRecentMinutes = 0,
            ownership = SteamGameOwnership.FAMILY_SHARED
        )
        val cached = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(cachedShared),
            fetchedAt = 100L,
            familyGroupId = 42L
        )
        val fresh = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(SteamGame(10, "Owned", 10, 0)),
            fetchedAt = 200L,
            familyShareFailure = SteamLibraryFailureReason.NETWORK
        )

        val merged = mergeLibraryDashboardSnapshot(
            fresh = fresh,
            cached = cached,
            inventoryResult = SteamLibraryResult.Failure(SteamLibraryFailureReason.NETWORK)
        )

        assertEquals(listOf(10, 20), merged.games.map(SteamGame::appId))
        assertTrue(merged.games.last().isFamilyShared)
        assertEquals(42L, merged.familyGroupId)
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
                    allAchievementsUnlocked = true,
                    achievementProgressPlaytimeMinutes = 1
                )
            ),
            fetchedAt = 100L,
            achievementProgressFullSyncAt = 90L
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
        assertEquals(1, merged.games.single().achievementProgressPlaytimeMinutes)
        assertEquals(90L, merged.achievementProgressFullSyncAt)
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
    fun loadedAchievementDetailsUpdatePerfectFilterMetadata() {
        val game = SteamGame(10, "Complete", 120, 0)
        val state = SteamLibraryUiState(
            snapshot = SteamLibrarySnapshot(7L, listOf(game), 1L),
            selectedGame = game
        )
        val achievements = SteamGameAchievements(
            accountId = 7L,
            appId = 10,
            gameName = game.name,
            achievements = listOf(
                SteamAchievement("one", "One", "", true, 1L, null, null),
                SteamAchievement("two", "Two", "", true, 2L, null, null)
            ),
            fetchedAt = 2L
        )

        val updated = applyAchievementsToState(state, achievements)

        assertTrue(updated.selectedGame!!.isPerfectAchievementGame)
        assertTrue(updated.snapshot!!.games.single().isPerfectAchievementGame)
    }

    @Test
    fun ownedGamesProtobufParsesPlaytimeFields() {
        val game = SteamProtoWriter().apply {
            writeUint64(1, 730L)
            writeString(2, "Portal")
            writeUint64(3, 45L)
            writeUint64(4, 1_234L)
            writeString(5, "icon-hash")
            writeUint64(11, 1_754_263_800L)
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
        assertEquals(1_754_263_800L, parsed.single().lastPlayedAt)
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
    fun achievementRequestUsesUnpackedAppIdsRequiredBySteam() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/SteamGameLibraryService.kt"
        ).readText()
        val request = source
            .substringAfter("method = \"GetAchievementsProgress\"")
            .substringBefore("accessToken = accessToken")

        assertTrue(request.contains("batch.forEach { appId ->"))
        assertTrue(request.contains("writeVarint(3, appId.toLong())"))
        assertFalse(request.contains("writePackedVarints(3"))
    }

    @Test
    fun profileAchievementRequestUsesUnpackedAppIdsRequiredBySteam() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/data/SteamProfileViewerRemote.kt"
        ).readText()
        val request = source
            .substringAfter("method = \"GetAchievementsProgress\"")
            .substringBefore("accessToken = accessToken")

        assertTrue(request.contains("appIds.forEach { appId ->"))
        assertTrue(request.contains("writeVarint(3, appId.toLong())"))
        assertFalse(request.contains("writePackedVarints(3"))
    }

    @Test
    fun achievementSummaryOnlyShowsKnownGamesWithAchievements() {
        val unknown = SteamGame(1, "Unknown", 0, 0)
        val withoutAchievements = SteamGame(
            appId = 2,
            name = "No achievements",
            playtimeForeverMinutes = 0,
            playtimeRecentMinutes = 0,
            achievementUnlockedCount = 0,
            achievementTotalCount = 0
        )

        assertNull(unknown.achievementSummaryOrNull())
        assertNull(withoutAchievements.achievementSummaryOrNull())
    }

    @Test
    fun achievementSummaryCalculatesProgressAndPerfectState() {
        val inProgress = SteamGame(
            appId = 3,
            name = "In progress",
            playtimeForeverMinutes = 0,
            playtimeRecentMinutes = 0,
            achievementUnlockedCount = 7,
            achievementTotalCount = 46
        ).achievementSummaryOrNull()
        val perfect = SteamGame(
            appId = 4,
            name = "Perfect",
            playtimeForeverMinutes = 0,
            playtimeRecentMinutes = 0,
            achievementUnlockedCount = 13,
            achievementTotalCount = 13
        ).achievementSummaryOrNull()
        val inconsistentPerfect = SteamGame(
            appId = 5,
            name = "Server says perfect",
            playtimeForeverMinutes = 0,
            playtimeRecentMinutes = 0,
            achievementUnlockedCount = 12,
            achievementTotalCount = 13,
            allAchievementsUnlocked = true
        ).achievementSummaryOrNull()

        assertEquals(SteamGameAchievementSummary(7, 46, 15, false), inProgress)
        assertEquals(SteamGameAchievementSummary(13, 13, 100, true), perfect)
        assertEquals(SteamGameAchievementSummary(13, 13, 100, true), inconsistentPerfect)
    }

    @Test
    fun firstAchievementProgressSyncRequestsTheEntireLibrary() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                SteamGame(10, "Unplayed", 0, 0),
                SteamGame(20, "Played", 120, 10)
            ),
            fetchedAt = 2L,
            achievementProgressFullSyncAt = null
        )

        val plan = planSteamAchievementProgressSync(
            current = snapshot,
            forceFull = false
        )

        assertTrue(plan.isFullSync)
        assertEquals(listOf(10, 20), plan.appIds)
    }

    @Test
    fun laterAchievementProgressSyncOnlyRequestsGamesWithMorePlaytime() {
        val current = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                SteamGame(
                    10,
                    "Played again",
                    125,
                    25,
                    achievementProgressPlaytimeMinutes = 100
                ),
                SteamGame(
                    20,
                    "Unchanged",
                    240,
                    20,
                    achievementProgressPlaytimeMinutes = 240
                ),
                SteamGame(
                    30,
                    "Still unplayed",
                    0,
                    0,
                    achievementProgressPlaytimeMinutes = 0
                ),
                SteamGame(40, "New played game", 15, 15),
                SteamGame(50, "New unplayed game", 0, 0)
            ),
            fetchedAt = 2L,
            achievementProgressFullSyncAt = 1L
        )

        val plan = planSteamAchievementProgressSync(
            current = current,
            forceFull = false
        )

        assertFalse(plan.isFullSync)
        assertEquals(listOf(10, 40), plan.appIds)
    }

    @Test
    fun manualAchievementProgressSyncAlwaysRequestsTheEntireLibrary() {
        val snapshot = SteamLibrarySnapshot(
            accountId = 7L,
            games = listOf(
                SteamGame(10, "Unplayed", 0, 0),
                SteamGame(20, "Played", 120, 10)
            ),
            fetchedAt = 2L,
            achievementProgressFullSyncAt = 1L
        )

        val plan = planSteamAchievementProgressSync(
            current = snapshot,
            forceFull = true
        )

        assertTrue(plan.isFullSync)
        assertEquals(listOf(10, 20), plan.appIds)
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

    private fun projectFile(path: String): java.io.File {
        var directory = java.io.File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !java.io.File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return java.io.File(directory, path)
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
    fun ownedGamesTransportFailureRetriesThroughSystemDnsClient() {
        val ownedGamesResponse = SteamProtoWriter().apply {
            writeVarint(1, 1L)
            writeMessage(2, ownedGame(appId = 730, name = "Counter-Strike 2"))
        }.toByteArray()
        val primaryRequests = mutableListOf<String>()
        val fallbackRequests = mutableListOf<String>()
        val primaryClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                primaryRequests += chain.request().url.encodedPath
                throw IOException("simulated optimized route failure")
            }
            .build()
        val fallbackClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                fallbackRequests += request.url.encodedPath
                val body = when {
                    request.url.encodedPath.contains("GetOwnedGames") -> ownedGamesResponse
                    request.url.encodedPath.contains("GetFamilyGroupForUser") ->
                        SteamProtoWriter().apply { writeBool(2, true) }.toByteArray()
                    request.url.encodedPath.contains("GetItems") -> ByteArray(0)
                    else -> error("Unexpected fallback request: ${request.url}")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        body.toResponseBody(
                            "application/octet-stream".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val result = SteamGameLibraryService(
            api = SteamApiClient(primaryClient),
            systemDnsApi = SteamApiClient(fallbackClient)
        ).fetchLibrary(
            account = account(accessToken = "access-token"),
            countryCode = "CN",
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Success)
        assertEquals(listOf(730), (result as SteamLibraryResult.Success).value.games.map { it.appId })
        assertEquals(
            listOf("/IPlayerService/GetOwnedGames/v1/"),
            primaryRequests
        )
        assertEquals(
            listOf(
                "/IPlayerService/GetOwnedGames/v1/",
                "/IFamilyGroupsService/GetFamilyGroupForUser/v1/",
                "/IStoreBrowseService/GetItems/v1/"
            ),
            fallbackRequests
        )
    }

    @Test
    fun ownedGamesAuthenticationFailureDoesNotUseNetworkFallback() {
        var fallbackCalls = 0
        val primaryClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val fallbackClient = OkHttpClient.Builder()
            .addInterceptor {
                fallbackCalls++
                error("System DNS fallback must not retry authentication failures")
            }
            .build()

        val result = SteamGameLibraryService(
            api = SteamApiClient(primaryClient),
            systemDnsApi = SteamApiClient(fallbackClient)
        ).fetchLibrary(
            account = account(accessToken = "expired-token"),
            countryCode = "CN",
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Failure)
        assertEquals(
            SteamLibraryFailureReason.SESSION_REQUIRED,
            (result as SteamLibraryResult.Failure).reason
        )
        assertEquals(0, fallbackCalls)
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
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.method to request.url.encodedPath
                val body = when {
                    request.url.encodedPath.contains("GetOwnedGames") -> ownedGamesResponse
                    request.url.encodedPath.contains("GetItems") -> storeResponse
                    request.url.encodedPath.contains("GetAchievementsProgress") ->
                        error("Core library refresh must not block on achievement progress")
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
        assertTrue(requests.none { it.second.contains("GetAchievementsProgress") })
        assertTrue(requests.none { it.second.contains("appdetails") })
        val astlibra = result.value.games.first { it.appId == 1718570 }
        assertEquals(
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1718570/header_schinese.jpg?t=1770740786",
            astlibra.headerImageUrl
        )
        assertEquals(8_000L, astlibra.price?.finalPriceMinor)
        assertEquals(8_800L, astlibra.price?.originalPriceMinor)
        assertNull(astlibra.achievementUnlockedCount)
        assertNull(astlibra.achievementTotalCount)
        assertEquals(8_800L, result.value.estimatedReplacementValueMinor)
        assertEquals("CNY", result.value.currency)
    }

    @Test
    fun achievementProgressSyncUsesHundredGameBatches() {
        val requests = mutableListOf<String>()
        val progressResponse = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 1L)
                writeVarint(2, 1L)
                writeVarint(3, 10L)
            })
        }.toByteArray()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.url.encodedPath
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(progressResponse.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchAchievementProgress(
            account = account(accessToken = "access-token"),
            appIds = (1..201).toList(),
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Success)
        assertEquals(3, requests.count { it.contains("GetAchievementsProgress") })
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
    fun knownGameWithoutAchievementsSkipsAchievementRequests() {
        var requestCount = 0
        val httpClient = OkHttpClient.Builder()
            .addInterceptor {
                requestCount++
                error("Achievement endpoint must not be called for a known zero-achievement game")
            }
            .build()

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchAchievements(
            account = account(accessToken = "access-token"),
            game = SteamGame(
                appId = 10,
                name = "Game without achievements",
                playtimeForeverMinutes = 1,
                playtimeRecentMinutes = 0,
                achievementTotalCount = 0
            ),
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Success)
        assertTrue((result as SteamLibraryResult.Success).value.achievements.isEmpty())
        assertEquals(0, requestCount)
    }

    @Test
    fun emptyAchievementDefinitionsSkipUserAchievementRequest() {
        val requests = mutableListOf<String>()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request.url.encodedPath
                val responseCode = if (
                    request.url.encodedPath.contains("GetGameAchievements")
                ) 200 else 500
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message(if (responseCode == 200) "OK" else "Unexpected request")
                    .body(ByteArray(0).toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()

        val result = SteamGameLibraryService(SteamApiClient(httpClient)).fetchAchievements(
            account = account(accessToken = "access-token"),
            game = SteamGame(10, "Game without achievements", 1, 0),
            language = "schinese"
        )

        assertTrue(result is SteamLibraryResult.Success)
        assertTrue((result as SteamLibraryResult.Success).value.achievements.isEmpty())
        assertEquals(1, requests.size)
        assertTrue(requests.single().contains("GetGameAchievements"))
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
