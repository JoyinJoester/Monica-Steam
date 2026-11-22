package takagi.ru.monica.steam.space.data

import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.space.domain.SteamSpaceAchievement
import takagi.ru.monica.steam.space.domain.SteamSpaceCacheKey
import takagi.ru.monica.steam.space.domain.SteamSpaceCachedFeed
import takagi.ru.monica.steam.space.domain.SteamSpaceCursor
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.domain.SteamSpaceMedia
import takagi.ru.monica.steam.space.domain.SteamSpaceMemoryFeedCache

class SteamSpaceFeedCacheTest {
    private var now = 1_794_040_000_000L
    private val key = SteamSpaceCacheKey(41L, "76561198000000041", "english", createdAt = 25L)

    @Test fun newInstanceRestoresRichFeedAndPageChainFromEncryptedSnapshot() {
        val disk = FakeDisk()
        val original = feed().copy(items = listOf(event("first").copy(
            appIds = listOf(570), appNames = mapOf(570 to "Dota 2"),
            media = listOf(SteamSpaceMedia("https://steamusercontent.com/image", 80, 90)),
            achievements = listOf(SteamSpaceAchievement("badge", "Badge", "Description", "https://steamcdn-a.akamaihd.net/icon"))
        )), nextCursor = cursor(99), pageCount = 2, visitedCursors = setOf(cursor(100)))
        newCache(disk).save(original)
        assertNotNull(disk.value)
        assertTrue(disk.value!!.startsWith("encrypted:"))
        assertFalse(disk.value!!.contains("Synthetic feed body"))
        val restored = newCache(disk)
        assertNull(restored.peek(key))
        assertEquals(original, restored.load(key))
        assertEquals(original, restored.peek(key))
    }

    @Test fun previousSnapshotWithoutAppNameMappingStillLoads() {
        val original = feed()
        val legacy = requireNotNull(SteamSpaceFeedCacheCodec.encode(listOf(original)))
            .replace(",\"appNames\":{}", "")
        assertFalse(legacy.contains("appNames"))
        assertEquals(listOf(original), SteamSpaceFeedCacheCodec.decode(legacy, now))
    }

    @Test fun appNameMappingsAreDetachedAndBoundedAlongWithTheFeed() {
        val disk = FakeDisk()
        val names = mutableMapOf(570 to "Dota 2")
        val original = feed().copy(items = listOf(event("games").copy(appIds = listOf(570), appNames = names)))
        val cache = newCache(disk)
        cache.save(original)
        val persisted = disk.value
        names[570] = "Changed after save"
        assertEquals("Dota 2", cache.peek(key)!!.items.single().appNames[570])
        val tooLarge = original.copy(fetchedAtMillis = now + 1, items = listOf(original.items.single().copy(
            appNames = mapOf(570 to "x".repeat(512 * 1024 + 1))
        )))
        cache.save(tooLarge)
        assertEquals(persisted, disk.value)
        cache.save(original.copy(fetchedAtMillis = now + 1, items = listOf(original.items.single().copy(
            appNames = mapOf(620 to "Wrong game")
        ))))
        assertEquals(persisted, disk.value)
    }

    @Test fun peekNeverReadsDiskOrInitializesCipher() {
        val cache = SteamSpacePreferencesFeedCache(
            read = { error("Unexpected IO") }, write = { error("Unexpected IO") },
            encrypt = { error("Unexpected cipher") }, decrypt = { error("Unexpected cipher") }, clockMillis = { now }
        )
        assertNull(cache.peek(key))
        val temporary = feed(key.copy(temporary = true))
        cache.save(temporary)
        assertEquals(temporary, cache.peek(temporary.key))
        assertEquals(temporary, cache.load(temporary.key))
    }

    @Test fun rememberMakesResultImmediatelyAvailableWithoutIoAndLaterSavePersistsIt() {
        val disk = FakeDisk()
        var cipherCalls = 0
        val cache = SteamSpacePreferencesFeedCache(
            read = { disk.reads++; disk.value },
            write = { disk.writes++; disk.value = it },
            encrypt = { cipherCalls++; encrypt(it) },
            decrypt = { cipherCalls++; decrypt(it) },
            clockMillis = { now }
        )
        val completed = feed()
        cache.remember(completed)
        assertEquals(completed, cache.peek(key))
        assertEquals(0, disk.reads)
        assertEquals(0, disk.writes)
        assertEquals(0, cipherCalls)
        assertNull(disk.value)
        cache.save(completed)
        assertEquals(1, disk.reads)
        assertEquals(1, disk.writes)
        assertEquals(1, cipherCalls)
        assertEquals(completed, newCache(disk).load(key))
    }

    @Test fun identitiesIncludeLocalIdSteamIdLanguageCreationAndTemporaryScope() {
        val disk = FakeDisk()
        newCache(disk).save(feed())
        val reader = newCache(disk)
        listOf(key.copy(accountId = 42), key.copy(accountSteamId = "76561198000000042"),
            key.copy(language = "japanese"), key.copy(createdAt = 26), key.copy(temporary = true)).forEach {
            assertNull(reader.load(it))
        }
        assertEquals(feed(), reader.load(key))
    }

    @Test fun temporaryFeedDoesNotReadOrReplacePersistedSavedAccount() {
        val disk = FakeDisk()
        val cache = newCache(disk)
        cache.save(feed())
        val stored = disk.value
        val writes = disk.writes
        val reads = disk.reads
        val temporary = feed(key.copy(temporary = true)).copy(items = emptyList())
        cache.save(temporary)
        assertEquals(temporary, cache.load(temporary.key))
        assertEquals(reads, disk.reads)
        assertEquals(writes, disk.writes)
        assertEquals(stored, disk.value)
        assertNull(newCache(disk).load(temporary.key))
    }

    @Test fun expiryAndClockRollbackDiscardMemoryAndDiskWithoutDeletingOriginalBytes() {
        val disk = FakeDisk()
        val cache = newCache(disk)
        val original = feed()
        cache.save(original)
        val bytes = disk.value
        now = original.fetchedAtMillis + SteamSpaceMemoryFeedCache.MAX_AGE_MILLIS
        assertNull(cache.peek(key))
        assertNull(newCache(disk).load(key))
        assertEquals(bytes, disk.value)
        now = original.fetchedAtMillis - 1001L
        assertNull(newCache(disk).load(key))
        now = original.fetchedAtMillis - 1L
        assertEquals(original, newCache(disk).load(key))
    }

    @Test fun olderRefreshAndLateEarlierPageCannotReplaceNewerSnapshot() {
        val disk = FakeDisk()
        val cache = newCache(disk)
        val first = feed().copy(nextCursor = cursor(100))
        val second = first.copy(items = first.items + event("second"), pageCount = 2,
            visitedCursors = setOf(cursor(100)), nextCursor = cursor(90))
        cache.save(first)
        cache.save(second)
        cache.save(first)
        assertEquals(second, cache.peek(key))
        assertEquals(second, newCache(disk).load(key))
        val newer = first.copy(items = listOf(event("refreshed")), fetchedAtMillis = now + 1L)
        cache.save(newer)
        newCache(disk).save(second)
        assertEquals(newer, newCache(disk).load(key))
    }

    @Test fun emptyFeedIsCacheableButInvalidCursorOrOversizedFeedPreservesGoodCache() {
        val disk = FakeDisk()
        val cache = newCache(disk)
        val empty = feed().copy(items = emptyList())
        cache.save(empty)
        assertEquals(empty, newCache(disk).load(key))
        listOf(
            feed().copy(nextCursor = cursor(100).copy(accountId = 999)),
            feed().copy(visitedCursors = setOf(cursor(100).copy(accountSteamId = "76561198000000999"))),
            feed().copy(items = (0..400).map { event("$it") }),
            feed().copy(items = listOf(event("large").copy(body = "x".repeat(512 * 1024 + 1)))),
            feed().copy(items = listOf(event("duplicate"), event("duplicate"))),
            feed().copy(pageCount = 21),
            feed().copy(key = key.copy(language = "invalid-language"))
        ).forEach { cache.save(it.copy(fetchedAtMillis = now + 1L)) }
        assertEquals(empty, cache.peek(key))
        assertEquals(empty, newCache(disk).load(key))
    }

    @Test fun memoryAndDiskKeepAtMostFourAccountLanguageEntries() {
        val disk = FakeDisk()
        val cache = newCache(disk)
        val keys = (1L..5L).map { key.copy(accountId = it) }
        keys.forEach { cache.save(feed(it)) }
        assertNull(cache.peek(keys.first()))
        assertNull(newCache(disk).load(keys.first()))
        keys.drop(1).forEach { assertNotNull(newCache(disk).load(it)) }
        assertTrue(decrypt(disk.value!!).length <= SteamSpaceFeedCacheCodec.MAX_CHARACTERS)
    }

    @Test fun codecRejectsMalformedHugeDuplicateAndUnsupportedEnvelopes() {
        assertNull(SteamSpaceFeedCacheCodec.decode("broken-json", now))
        assertNull(SteamSpaceFeedCacheCodec.decode(" ".repeat(SteamSpaceFeedCacheCodec.MAX_CHARACTERS + 1), now))
        val valid = requireNotNull(SteamSpaceFeedCacheCodec.encode(listOf(feed())))
        assertNull(SteamSpaceFeedCacheCodec.decode(valid.replace("\"version\":1", "\"version\":2"), now))
        assertNull(SteamSpaceFeedCacheCodec.decode(requireNotNull(SteamSpaceFeedCacheCodec.encode(listOf(feed(), feed()))), now))
        assertNull(SteamSpaceFeedCacheCodec.decode(requireNotNull(SteamSpaceFeedCacheCodec.encode((1L..5L).map { feed(key.copy(accountId = it)) })), now))
    }

    @Test fun corruptedCiphertextOrPlaintextIsNeverRestoredAsValidFeed() {
        val disk = FakeDisk()
        disk.value = requireNotNull(SteamSpaceFeedCacheCodec.encode(listOf(feed())))
        assertNull(newCache(disk).load(key))
        disk.value = "encrypted:invalid"
        assertNull(newCache(disk).load(key))
        val identityCipher = SteamSpacePreferencesFeedCache(
            read = { requireNotNull(SteamSpaceFeedCacheCodec.encode(listOf(feed()))) },
            write = {}, encrypt = { it }, decrypt = { it }, clockMillis = { now }
        )
        assertNull(identityCipher.load(key))
    }

    @Test fun encryptionOrDiskFailureKeepsMemoryButNeverWritesPlaintext() {
        val disk = FakeDisk()
        val cache = SteamSpacePreferencesFeedCache(read = { null }, write = { disk.value = it },
            encrypt = { throw IllegalStateException("synthetic cipher unavailable") }, decrypt = ::decrypt,
            clockMillis = { now })
        cache.save(feed())
        assertEquals(feed(), cache.peek(key))
        assertNull(disk.value)
        val identityCipher = SteamSpacePreferencesFeedCache(read = { null }, write = { disk.value = it },
            encrypt = { it }, decrypt = { it }, clockMillis = { now })
        identityCipher.save(feed())
        assertNull(disk.value)
        val failedDisk = SteamSpacePreferencesFeedCache(read = { throw IllegalStateException() },
            write = { throw IllegalStateException() }, encrypt = ::encrypt, decrypt = ::decrypt, clockMillis = { now })
        failedDisk.save(feed())
        assertEquals(feed(), failedDisk.peek(key))
    }

    @Test fun cacheCopiesInputAndOutputCollectionsIncludingNestedLists() {
        val cache = SteamSpaceMemoryFeedCache { now }
        val apps = mutableListOf(570)
        val media = mutableListOf(SteamSpaceMedia("https://steamusercontent.com/image"))
        val items = mutableListOf(event("first").copy(appIds = apps, media = media))
        val cursors = mutableSetOf(cursor(100), cursor(90))
        val original = feed().copy(items = items, visitedCursors = cursors)
        cache.save(original)
        apps.clear(); media.clear(); items.clear(); cursors.clear()
        val read = requireNotNull(cache.peek(key))
        assertEquals(listOf(570), read.items.single().appIds)
        assertEquals(1, read.items.single().media.size)
        assertEquals(2, read.visitedCursors.size)
        (read.items as? MutableList<SteamSpaceEvent>)?.let { runCatching { it.clear() } }
        (read.visitedCursors as? MutableSet<SteamSpaceCursor>)?.let { runCatching { it.clear() } }
        assertEquals(1, cache.peek(key)!!.items.size)
        assertEquals(2, cache.peek(key)!!.visitedCursors.size)
    }

    @Test fun slowEncryptedReadNeverBlocksMemoryPeekAndNewerSaveWinsAfterRead() {
        val disk = FakeDisk()
        newCache(disk).save(feed())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var slow = true
        val cache = SteamSpacePreferencesFeedCache(read = {
            if (slow) {
                slow = false
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            }
            disk.value
        }, write = { disk.value = it }, encrypt = ::encrypt, decrypt = ::decrypt, clockMillis = { now })
        val executor = Executors.newFixedThreadPool(2)
        try {
            val loading = executor.submit<SteamSpaceCachedFeed?> { cache.load(key) }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val peek = executor.submit<SteamSpaceCachedFeed?> { cache.peek(key) }
            assertNull(peek.get(1, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(feed(), loading.get(3, TimeUnit.SECONDS))
            val latest = feed().copy(fetchedAtMillis = now + 1L, items = listOf(event("newer")))
            cache.save(latest)
            assertEquals(latest, newCache(disk).load(key))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowedByDiskBoundary() {
        SteamSpacePreferencesFeedCache(read = { throw CancellationException() }, write = {},
            encrypt = ::encrypt, decrypt = ::decrypt, clockMillis = { now }).load(key)
    }

    private fun newCache(disk: FakeDisk) = SteamSpacePreferencesFeedCache(
        read = { disk.reads++; disk.value }, write = { disk.writes++; disk.value = it },
        encrypt = ::encrypt, decrypt = ::decrypt, clockMillis = { now }
    )

    private fun feed(key: SteamSpaceCacheKey = this.key) = SteamSpaceCachedFeed(
        key, listOf(event("first")), null, false, 1, emptySet(), now
    )

    private fun cursor(before: Long) = SteamSpaceCursor(key.accountId, key.accountSteamId, before)

    private fun event(id: String) = SteamSpaceEvent(id, SteamSpaceEventKind.STATUS, "76561198000000099",
        1_794_039_000L, body = "Synthetic feed body", sourceUrl = "https://steamcommunity.com/my/home/")

    private fun encrypt(raw: String): String = "encrypted:" + Base64.getEncoder().encodeToString(raw.toByteArray())
    private fun decrypt(raw: String): String {
        require(raw.startsWith("encrypted:"))
        return Base64.getDecoder().decode(raw.removePrefix("encrypted:")).toString(Charsets.UTF_8)
    }

    private class FakeDisk(var value: String? = null, var reads: Int = 0, var writes: Int = 0)
}
