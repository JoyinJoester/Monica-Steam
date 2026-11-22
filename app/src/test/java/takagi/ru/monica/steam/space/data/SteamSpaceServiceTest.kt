package takagi.ru.monica.steam.space.data

import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.space.domain.SteamSpaceCursor
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.domain.SteamSpaceFormatException

class SteamSpaceServiceTest {
    @Test fun officialWireContractUsesFixedNewsIdsButVarintStatusIdentity() {
        val cm = FakeCm { method, _ ->
            when (method) {
                SteamSpaceService.NEWS_METHOD -> feed(event(16))
                SteamSpaceService.STATUS_METHOD -> status("Original [b]status[/b] <script>literal</script>")
                else -> error("Unexpected method $method")
            }
        }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "schinese", null)
        val news = SteamProtoReader(cm.calls[0].second).parse()
        assertEquals(20, news.getValue(1).asInt)
        assertEquals(1L, news.getValue(2).asLong)
        assertEquals(0L, news.getValue(3).asLong)
        assertEquals("schinese", news.getValue(4).asString)
        assertEquals(137094L, news.getValue(5).asLong)
        assertEquals(0L, news.getValue(6).asLong)
        val posted = SteamProtoReader(cm.calls[1].second).parse()
        assertEquals(0, posted.getValue(1).wireType)
        assertEquals(ACTOR.toLong(), posted.getValue(1).asLong)
        assertEquals(TIME, posted.getValue(2).asLong)
        assertEquals("Original [b]status[/b] <script>literal</script>", page.events.single().body)
        assertEquals(TIME, page.events.single().occurredAtEpochSeconds)
        assertFalse(page.partial)
        assertNull(page.nextCursor)
        assertTrue(page.events.all { SteamSpaceLinks.isAllowedSourceUrl(it.sourceUrl) })
    }

    @Test fun fullPageUsesOldestServerTimeAndBindsCursorToAccount() {
        val cm = FakeCm { _, _ -> feed(*(0 until 20).map { event(777, TIME - it) }.toTypedArray()) }
        val service = service(cm, actorLookup = { _, _ -> emptyMap() })
        val first = service.fetchPage(account(), "english", null)
        assertEquals(SteamSpaceCursor(7L, ACTOR, TIME - 20), first.nextCursor)
        assertTrue(first.partial)
        assertTrue(first.events.all { it.kind == SteamSpaceEventKind.UNKNOWN && it.body == null })
        val cursor = SteamSpaceCursor(7L, ACTOR, TIME)
        service.fetchPage(account(), "english", cursor)
        val history = SteamProtoReader(cm.calls.last().second).parse()
        assertEquals(0L, history.getValue(2).asLong)
        assertEquals(TIME, history.getValue(3).asLong)
        val prior = cm.calls.size
        assertThrows(IllegalArgumentException::class.java) {
            service.fetchPage(account().copy(id = 8), "english", cursor)
        }
        assertEquals(prior, cm.calls.size)
    }

    @Test fun unknownEventsPreserveServerTimeAndDoNotTurnIntoGameActivity() {
        val bytes = feed(event(777, 123456L))
        val parsed = SteamSpaceParser.news(bytes)
        assertTrue(parsed.partial)
        assertEquals(777, parsed.entries.single().event.rawEventType)
        assertEquals(123456L, parsed.entries.single().event.occurredAtEpochSeconds)
        assertNull(parsed.entries.single().event.body)
        assertEquals(parsed.entries.single().event.id, SteamSpaceParser.news(bytes).entries.single().event.id)
    }

    @Test fun aMixedHistoryResponseOutsideRequestedTimeIsRejectedBeforeEnrichment() {
        val cm = FakeCm { method, _ ->
            assertEquals(SteamSpaceService.NEWS_METHOD, method)
            feed(event(16, TIME + 1), event(16, TIME - 1))
        }
        assertThrows(SteamSpaceFormatException::class.java) {
            service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", SteamSpaceCursor(7L, ACTOR, TIME))
        }
        assertEquals(1, cm.calls.size)
    }

    @Test fun invalidTimeAndActorAreNotInventedAndStillMarkPartial() {
        val missingTime = SteamProtoWriter().apply { writeVarint(1, 16); writeFixed64(3, ACTOR.toLong()) }
        val invalidActor = SteamProtoWriter().apply { writeVarint(1, 16); writeVarint(2, TIME); writeFixed64(3, 12) }
        val parsed = SteamSpaceParser.news(feed(missingTime, invalidActor))
        assertTrue(parsed.entries.isEmpty())
        assertTrue(parsed.partial)
        assertEquals(TIME, parsed.oldestEpochSeconds)
    }

    @Test fun duplicateRowsCollapseButDifferentPublishedFilesRemainDistinct() {
        val first = event(13, fileId = "998877")
        val second = event(13, fileId = "998878")
        assertEquals(2, SteamSpaceParser.news(feed(first, first, second)).entries.size)
    }

    @Test fun repeatedAndPackedAppsDoNotTreatNonSteamGameIdAsAnApp() {
        val raw = event(3, appId = null).apply {
            writeFixed64(5, 0x0200000001L)
            writePackedVarints(13, listOf(570, 730))
            writeVarint(13, 570)
        }
        assertEquals(listOf(570, 730), SteamSpaceParser.news(feed(raw)).entries.single().event.appIds)
    }

    @Test fun achievementTextComesOnlyFromMatchingAppAndNamedDisplayData() {
        val item = event(2, appId = 570).apply { writeString(8, "WIN"); writeString(8, "MISSING") }
        val payload = SteamProtoWriter().apply {
            writeMessage(1, item)
            writeMessage(2, SteamProtoWriter().apply {
                writeVarint(1, 570)
                writeMessage(2, SteamProtoWriter().apply {
                    writeString(1, "WIN"); writeString(2, "A real display name")
                    writeString(3, "Original description")
                    writeString(4, "92a08788e62ec08c84036cacb60524e16d56d9ed.jpg")
                })
            })
        }.toByteArray()
        val event = SteamSpaceParser.news(payload).entries.single().event
        assertEquals("A real display name", event.achievements.single().displayName)
        assertEquals("Original description", event.achievements.single().description)
        assertEquals("https://shared.akamai.steamstatic.com/community_assets/images/apps/570/92a08788e62ec08c84036cacb60524e16d56d9ed.jpg",
            event.achievements.single().iconUrl)
        assertTrue(event.partial)
    }

    @Test fun achievementIconAcceptsCdnUrlsAndConfinesFilenamesToTheirGame() {
        val fullUrl = "https://cdn.steamstatic.com/achievement.png"
        assertEquals(fullUrl, SteamSpaceLinks.achievementIcon(570, fullUrl))
        assertEquals("https://shared.akamai.steamstatic.com/community_assets/images/apps/620/coop.png",
            SteamSpaceLinks.achievementIcon(620, "coop.png"))
        assertNull(SteamSpaceLinks.achievementIcon(0, "coop.png"))
        for (raw in listOf(null, "", "../coop.png", "/coop.png", "..%2fcoop.png", "coop.png?token=1", "coop.png#fragment",
            "https://example.org/coop.png", "//example.org/coop.png", "https://user:password@cdn.steamstatic.com/coop.png")) {
            assertNull("Untrusted image value must remain unavailable: $raw", SteamSpaceLinks.achievementIcon(620, raw))
        }
    }

    @Test fun cachedOldAchievementImageRoutesResolveWithoutDependingOnFeedRefresh() {
        val expected = "https://shared.akamai.steamstatic.com/community_assets/images/apps/620/coop.png"
        for (host in listOf("cdn.cloudflare.steamstatic.com", "media.steampowered.com", "cdn.akamai.steamstatic.com")) {
            assertEquals(expected, SteamSpaceLinks.achievementIcon(0,
                "https://$host/steamcommunity/public/images/apps/620/coop.png"))
        }
        assertEquals(expected, SteamSpaceLinks.achievementIcon(620, "coop.png"))
    }

    @Test fun mediaIsBatchedAndSensitivityIsPreservedWithoutExecutingContent() {
        val cm = FakeCm { method, request -> when (method) {
            SteamSpaceService.NEWS_METHOD -> feed(event(13, fileId = "998877"), event(14, fileId = "998878"))
            SteamSpaceService.FILE_METHOD -> {
                val ids = SteamProtoReader(request).parseAll().filter { it.number == 1 }
                assertTrue(ids.all { it.wireType == 1 })
                assertEquals(listOf("998877", "998878"), ids.map { it.asFixed64UnsignedString })
                fileResponse(file("998877", sensitive = true), file("998878"))
            }
            else -> error("Unexpected method $method")
        } }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        assertEquals(2, cm.calls.size)
        assertEquals(2, page.events.size)
        assertTrue(page.events.first().media.single().sensitive)
        assertFalse(page.events.last().media.single().sensitive)
        assertEquals("Original title", page.events.first().title)
        assertEquals("Original text", page.events.first().body)
    }

    @Test fun privateDeletedBannedOrMisattributedMediaNeverReleasesPreview() {
        val cm = FakeCm { method, _ -> when (method) {
            SteamSpaceService.NEWS_METHOD -> feed(*(1..5).map { event(13, fileId = "$it") }.toTypedArray())
            SteamSpaceService.FILE_METHOD -> fileResponse(
                file("1", visibility = 2), file("2", visibility = 3), file("3", result = 9),
                file("4", banned = true), file("5", creator = OTHER)
            )
            else -> error("Unexpected method $method")
        } }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        assertEquals(1, page.events.size)
        assertNull(page.events.single().title)
        assertTrue(page.events.single().media.isEmpty())
        assertTrue(page.partial)
    }

    @Test fun changedStatusIdentityIsPartialAndDeletedStatusIsOmitted() {
        val cm = FakeCm { method, request -> when (method) {
            SteamSpaceService.NEWS_METHOD -> feed(event(16), event(16, TIME - 1))
            SteamSpaceService.STATUS_METHOD -> {
                val post = SteamProtoReader(request).parse().getValue(2).asLong
                if (post == TIME) status("Wrong actor", actor = OTHER) else status("Deleted", post = post, deleted = true)
            }
            else -> error("Unexpected method $method")
        } }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        assertEquals(1, page.events.size)
        assertNull(page.events.single().body)
        assertTrue(page.partial)
    }

    @Test fun enrichmentNetworkFailureIsPartialButAuthenticationAndCancellationEscape() {
        fun failing(failure: Exception) = FakeCm { method, _ ->
            if (method == SteamSpaceService.NEWS_METHOD) feed(event(16)) else throw failure
        }
        assertTrue(service(failing(IOException()), actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null).partial)
        val auth = SteamApiException("Expired", authenticationRequired = true)
        assertSame(auth, assertThrows(SteamApiException::class.java) {
            service(failing(auth), actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        })
        assertThrows(CancellationException::class.java) {
            service(failing(CancellationException()), actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        }
        listOf(SteamApiException("HTTP denied", httpStatusCode = 403), SteamApiException("Not logged on", eResult = 21))
            .forEach { failure -> assertSame(failure, assertThrows(SteamApiException::class.java) {
                service(failing(failure), actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
            }) }
    }

    @Test fun boundedStatusEnrichmentKeepsUnloadedEventsAsPartial() {
        val cm = FakeCm { method, request -> when (method) {
            SteamSpaceService.NEWS_METHOD -> feed(*(0 until 20).map { event(16, TIME - it) }.toTypedArray())
            SteamSpaceService.STATUS_METHOD -> status("Original", post = SteamProtoReader(request).parse().getValue(2).asLong)
            else -> error("Unexpected method $method")
        } }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        assertEquals(9, cm.calls.size)
        assertEquals(20, page.events.size)
        assertEquals(8, page.events.count { it.body != null })
        assertTrue(page.events.drop(8).all { it.partial })
        assertTrue(page.partial)
    }

    @Test fun expiredEnrichmentBudgetMakesNoFurtherRequests() {
        var now = 0L
        val cm = FakeCm { method, _ ->
            assertEquals(SteamSpaceService.NEWS_METHOD, method)
            now = 21_000_000_000L
            feed(event(16))
        }
        val page = service(cm, monotonicNanos = { now }, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        assertEquals(1, cm.calls.size)
        assertTrue(page.partial)
    }

    @Test fun gameNamesAreReadInOneBatchAndUnrelatedOrFailedIdsAreIgnored() {
        val cm = FakeCm { method, request -> when (method) {
            SteamSpaceService.NEWS_METHOD -> feed(event(3, appId = 570), event(9, appId = 730), event(30, appId = 440))
            SteamSpaceService.APPS_METHOD -> {
                val ids = SteamProtoReader(request).parseAll().filter { it.number == 1 }.map {
                    SteamProtoReader(requireNotNull(it.bytes)).parse().getValue(1).asInt
                }
                assertEquals(listOf(570, 730, 440), ids)
                SteamProtoWriter().apply {
                    writeMessage(1, app(570, "Dota 2")); writeMessage(1, app(730, "Counter-Strike 2"))
                    writeMessage(1, app(440, "Wrong", success = 9)); writeMessage(1, app(999, "Unrelated"))
                }.toByteArray()
            }
            else -> error("Unexpected method $method")
        } }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        assertEquals("Dota 2", page.events[0].title)
        assertEquals("Counter-Strike 2", page.events[1].title)
        assertNull(page.events[2].title)
        assertEquals(listOf(440), page.events[2].appIds)
        assertTrue(page.events[2].partial)
    }

    @Test fun singleGameActivityKeepsAllIdsWhenOnlySomeNamesResolve() {
        val cm = FakeCm { method, _ -> when (method) {
            SteamSpaceService.NEWS_METHOD -> feed(event(3).apply { writeVarint(13, 570); writeVarint(13, 620) })
            SteamSpaceService.APPS_METHOD -> SteamProtoWriter().apply {
                writeMessage(1, app(570, "Dota 2"))
                writeMessage(1, app(620, "Unavailable", success = 9))
            }.toByteArray()
            else -> error("Unexpected method $method")
        } }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        val item = page.events.single()
        assertEquals(SteamSpaceEventKind.GAME_ADDED, item.kind)
        assertEquals(listOf(570, 620), item.appIds)
        assertEquals(mapOf(570 to "Dota 2"), item.appNames)
        assertTrue(item.partial)
        assertTrue(page.partial)
    }

    @Test fun missingSessionNeverStartsACmRequest() {
        val cm = FakeCm { _, _ -> error("Must not connect") }
        val failure = assertThrows(SteamApiException::class.java) {
            service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account().copy(accessToken = null), "english", null)
        }
        assertTrue(failure.authenticationRequired)
        assertTrue(cm.calls.isEmpty())
    }

    @Test fun truncatedWrongWireAndOversizedResponsesAreNotEmptySuccess() {
        assertThrows(SteamSpaceFormatException::class.java) { SteamSpaceParser.news(byteArrayOf(10, 127, 8)) }
        assertThrows(SteamSpaceFormatException::class.java) { SteamSpaceParser.news(byteArrayOf(8, 1)) }
        assertThrows(SteamSpaceFormatException::class.java) { SteamSpaceParser.news(ByteArray(2 * 1024 * 1024 + 1)) }
        assertThrows(SteamSpaceFormatException::class.java) {
            SteamSpaceParser.news(feed(*(0..200).map { event(777, TIME - it) }.toTypedArray()))
        }
        assertTrue(SteamSpaceParser.news(ByteArray(0)).entries.isEmpty())
    }

    @Test fun sourceAndImageUrlsRejectCredentialRedirectAndHostConfusion() {
        assertTrue(SteamSpaceLinks.isAllowedSourceUrl(SteamSpaceLinks.HOME))
        assertTrue(SteamSpaceLinks.isAllowedSourceUrl(SteamSpaceLinks.publishedFile("998877")))
        listOf(
            "https://steamcommunity.com.evil.test/my/home/", "https://evil.test/",
            "https://user:password@steamcommunity.com/my/home/", "http://steamcommunity.com/my/home/",
            "https://steamcommunity.com:444/my/home/", "https://steamcommunity.com/linkfilter/?url=https://evil.test",
            "https://steamcommunity.com/my/home/?access_token=secret", "https://steamcommunity.com/my/home/#token",
            "https://steamcommunity.com/sharedfiles/filedetails/?id=1&id=2"
        ).forEach { assertFalse(it, SteamSpaceLinks.isAllowedSourceUrl(it)) }
        assertNull(SteamSpaceLinks.image("https://cdn.steamstatic.com.evil.test/a.png"))
        assertNull(SteamSpaceLinks.image("https://secret@cdn.steamstatic.com/a.png"))
        assertNull(SteamSpaceLinks.image("javascript:alert(1)"))
        assertNotNull(SteamSpaceLinks.image("https://steamuserimages-a.akamaihd.net/a.png"))
    }

    @Test fun actorsAreLoadedOnceByExactIdentityWithoutNeedingAFriendSnapshot() {
        val cm = FakeCm { _, _ -> feed(event(777), event(777, TIME - 1)) }
        var calls = 0
        val page = service(cm, actorLookup = { selected, ids ->
            calls++
            assertEquals(account().id, selected.id)
            assertEquals(listOf(ACTOR), ids)
            mapOf(ACTOR to SteamSpaceActor("Alex", "https://cdn.steamstatic.com/avatar.png"),
                OTHER to SteamSpaceActor("Unrelated", ""))
        }).fetchPage(account(), "english", null)
        assertEquals(1, calls)
        assertTrue(page.events.all { it.actorName == "Alex" && it.actorAvatarUrl.endsWith("avatar.png") })
    }

    @Test fun achievementGameNamesAcceptCanonicalIdWithoutRedundantAppId() {
        val cm = FakeCm { method, _ -> when (method) {
            SteamSpaceService.NEWS_METHOD -> feed(event(2, appId = 620))
            SteamSpaceService.APPS_METHOD -> SteamProtoWriter().apply {
                writeMessage(1, SteamProtoWriter().apply {
                    writeVarint(1, 0); writeVarint(2, 620); writeVarint(3, 1); writeString(6, "Portal 2")
                })
            }.toByteArray()
            else -> error(method)
        } }
        val page = service(cm, actorLookup = { _, _ -> emptyMap() }).fetchPage(account(), "english", null)
        assertEquals(mapOf(620 to "Portal 2"), page.events.single().appNames)
    }

    @Test fun canonicalItemIdStillResolvesWhenRedundantAppIdIsExplicitlyZero() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, SteamProtoWriter().apply {
                writeVarint(1, 0); writeVarint(2, 1091500); writeVarint(3, 1)
                writeString(6, "Cyberpunk 2077"); writeVarint(9, 0)
            })
        }.toByteArray()
        assertEquals(mapOf(1091500 to "Cyberpunk 2077"), SteamSpaceParser.appNames(response))
    }

    @Test fun actorLookupFailurePreservesEventsAndNeverInventsIdentity() {
        val cm = FakeCm { _, _ -> feed(event(777)) }
        val page = service(cm, actorLookup = { _, _ -> throw IOException() }).fetchPage(account(), "english", null)
        assertEquals(ACTOR, page.events.single().actorSteamId)
        assertEquals("", page.events.single().actorName)
        val auth = SteamApiException("Expired", authenticationRequired = true)
        assertSame(auth, assertThrows(SteamApiException::class.java) {
            service(cm, actorLookup = { _, _ -> throw auth }).fetchPage(account(), "english", null)
        })
    }

    // Existing protocol scenarios now use an intercepted HTTP metadata endpoint, never live traffic.
    private fun service(
        cm: SteamCmGateway,
        monotonicNanos: () -> Long = System::nanoTime,
        actorLookup: (SteamAccount, List<String>) -> Map<String, SteamSpaceActor>
    ): SteamSpaceService {
        val api = takagi.ru.monica.steam.network.SteamApiClient(okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("GET", request.method)
                assertEquals("https://api.steampowered.com/IStoreBrowseService/GetItems/v1/", request.url.newBuilder().query(null).build().toString())
                assertEquals("synthetic-token", request.url.queryParameter("access_token"))
                val payload = java.util.Base64.getDecoder().decode(request.url.queryParameter("input_protobuf_encoded"))
                val response = cm.callService(account(), SteamSpaceService.APPS_METHOD, payload)
                okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                    .body(response.toResponseBody()).build()
            }.build())
        val publicClient = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(404).message("Unavailable fixture")
                .body(ByteArray(0).toResponseBody()).build()
        }.build()
        return SteamSpaceService(cm, monotonicNanos, actorLookup, SteamSpaceAppNames(api, publicClient)::load)
    }

    private class FakeCm(val response: (String, ByteArray) -> ByteArray) : SteamCmGateway {
        val calls = mutableListOf<Pair<String, ByteArray>>()
        override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
            calls += method to request
            return response(method, request)
        }
        override fun exchangeClientMessage(account: SteamAccount, requestEMsg: Int, responseEMsg: Int, request: ByteArray): ByteArray =
            error("Activity must not send unrelated client messages")
    }

    private fun event(type: Int, time: Long = TIME, fileId: String? = null, appId: Int? = null) = SteamProtoWriter().apply {
        writeVarint(1, type.toLong()); writeVarint(2, time); writeFixed64(3, ACTOR.toLong())
        fileId?.let { writeFixed64(11, it.toLong()) }; appId?.let { writeFixed64(5, it.toLong()) }
    }
    private fun feed(vararg events: SteamProtoWriter): ByteArray = SteamProtoWriter().apply {
        events.forEach { writeMessage(1, it) }
    }.toByteArray()
    private fun status(text: String, post: Long = TIME, actor: String = ACTOR, deleted: Boolean = false): ByteArray =
        SteamProtoWriter().apply {
            writeVarint(1, (actor.toULong() and 0xFFFFFFFFuL).toLong()); writeUint64(2, post)
            writeString(3, text); writeBool(4, deleted)
        }.toByteArray()
    private fun file(
        id: String, visibility: Long = 0, result: Long = 1, creator: String = ACTOR,
        banned: Boolean = false, sensitive: Boolean = false
    ) = SteamProtoWriter().apply {
        writeVarint(1, result); writeUint64(2, id); writeFixed64(3, creator.toLong())
        writeString(11, "https://steamuserimages-a.akamaihd.net/$id.png")
        writeString(16, "Original title"); writeString(18, "Original text")
        writeVarint(21, visibility); writeBool(28, banned); writeBool(46, sensitive)
    }
    private fun fileResponse(vararg items: SteamProtoWriter): ByteArray = SteamProtoWriter().apply {
        items.forEach { writeMessage(1, it) }
    }.toByteArray()
    private fun app(id: Int, name: String, success: Long = 1) = SteamProtoWriter().apply {
        writeVarint(1, 0); writeVarint(3, success); writeString(6, name); writeVarint(9, id.toLong())
    }
    private fun account() = SteamAccount(
        id = 7, steamId = ACTOR, accountName = "synthetic", displayName = "Synthetic", deviceId = "test",
        sharedSecret = "", identitySecret = null, revocationCode = null, tokenGid = null,
        accessToken = "synthetic-token", refreshToken = null, steamLoginSecure = null,
        rawSteamGuardJson = "{}", selected = true, sortOrder = 0, createdAt = 0, updatedAt = 0
    )
    companion object {
        private const val ACTOR = "76561198000000001"
        private const val OTHER = "76561198000000002"
        private const val TIME = 1_800_000_000L
    }
}
