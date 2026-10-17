package takagi.ru.monica.steam.backup.share

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class OneShotMaFileStoreTest {
    @Test fun wrongUidCannotReadOrConsumeAndReplayFails() {
        val store = OneShotMaFileStore { 10L }
        val bytes = byteArrayOf(1, 2, 3)
        val token = store.issue(42, bytes)
        assertTrue(token.matches(Regex("[0-9a-f]{64}")))
        assertNull(store.size(token, 43))
        assertNull(store.consume(token, 43))
        assertEquals(3, store.size(token, 42))
        val payload = requireNotNull(store.consume(token, 42))
        assertSame(bytes, payload.bytes)
        assertNull(store.consume(token, 42))
        assertNull(store.size(token, 42))
        payload.bytes.fill(0)
    }

    @Test fun expiryIsEnforcedWithoutTimerAndWipesUnopenedPayload() {
        var now = 100L
        val store = OneShotMaFileStore { now }
        val bytes = byteArrayOf(1, 2, 3)
        val token = store.issue(42, bytes)
        now += SteamMaFileShareContract.TTL_MS - 1
        assertEquals(3, store.size(token, 42))
        now++
        assertNull(store.consume(token, 42))
        assertArrayEquals(ByteArray(3), bytes)
    }

    @Test fun cancellationWipesPayloadAndRejectsRead() {
        val store = OneShotMaFileStore { 0L }
        val bytes = byteArrayOf(1, 2, 3)
        val token = store.issue(42, bytes)
        store.revoke(token)
        assertNull(store.consume(token, 42))
        assertArrayEquals(ByteArray(3), bytes)
    }

    @Test fun rejectedBuffersAreWipedAndCapacityIsBounded() {
        val store = OneShotMaFileStore { 0L }
        val oversized = ByteArray(SteamMaFileShareContract.MAX_BYTES + 1) { 1 }
        assertTrue(runCatching { store.issue(42, oversized) }.isFailure)
        assertTrue(oversized.all { it == 0.toByte() })
        val tokens = (1..4).map { store.issue(42, byteArrayOf(1)) }
        val fifth = byteArrayOf(2)
        assertTrue(runCatching { store.issue(42, fifth) }.isFailure)
        assertEquals(0, fifth[0].toInt())
        store.revoke(tokens.first())
        assertNotNull(store.issue(42, byteArrayOf(3)))
    }

    @Test fun concurrentReadersGetExactlyOnePayload() {
        val store = OneShotMaFileStore { 0L }
        val token = store.issue(42, byteArrayOf(1))
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..8).map { pool.submit<Boolean> {
                start.await()
                store.consume(token, 42)?.let { it.bytes.fill(0); true } ?: false
            } }
            start.countDown()
            assertEquals(1, attempts.count { it.get() })
        } finally { pool.shutdownNow() }
    }
}
