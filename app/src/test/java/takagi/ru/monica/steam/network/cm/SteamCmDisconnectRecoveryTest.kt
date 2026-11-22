package takagi.ru.monica.steam.network.cm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount

@OptIn(ExperimentalCoroutinesApi::class)
class SteamCmDisconnectRecoveryTest {
    @Test fun idleSocketFailureReconnectsWithoutWaitingForHealthTimer() = runTest {
        CmReuseFixture().use { f ->
            val transport = object : SteamCmRealtimeTransport {
                override fun events(account: SteamAccount) = MutableSharedFlow<SteamCmEnvelope>()
                override fun disconnects(account: SteamAccount) = f.pool.disconnectsFor("test-storage")
                override fun connect(account: SteamAccount) = f.pool.connect(account, "test-storage")
                override fun isConnected(account: SteamAccount) = f.pool.isConnected(account, "test-storage")
            }
            val changes = mutableListOf<Boolean>()
            val job = backgroundScope.launch {
                supervisedSteamCmEvents(f.account, transport, StandardTestDispatcher(testScheduler),
                    60_000, 100, 1_000, null, "test", { _, _ -> null as Boolean? }, { it })
                    .collect { changes += it }
            }
            runCurrent()
            assertEquals(listOf(true), changes)
            val first = f.sockets.single()
            first.fail()
            runCurrent()
            assertEquals("Recovery must not depend on a 60 second poll", listOf(true, false, true), changes)
            assertEquals(0L, testScheduler.currentTime)
            assertEquals(2, f.sockets.size)
            first.fail()
            runCurrent()
            assertTrue(f.pool.isConnected(f.account, "test-storage"))
            assertEquals(2, f.sockets.size)
            job.cancel()
            runCurrent()
        }
    }

    @Test fun disconnectSignalsAreIsolatedByStorageIdentity() = runTest {
        CmReuseFixture().use { f ->
            var first = 0
            var second = 0
            backgroundScope.launch { f.pool.disconnectsFor("one").collect { first++ } }
            backgroundScope.launch { f.pool.disconnectsFor("two").collect { second++ } }
            runCurrent()
            f.pool.connect(f.account, "one")
            f.pool.connect(f.account, "two")
            f.sockets.first().fail()
            runCurrent()
            assertEquals(1, first)
            assertEquals(0, second)
            assertTrue(f.pool.isConnected(f.account, "two"))
        }
    }
}
