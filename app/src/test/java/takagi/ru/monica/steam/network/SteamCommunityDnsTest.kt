package takagi.ru.monica.steam.network

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SteamCommunityDnsTest {
    @Test
    fun steamCommunityUsesSecureResolverBeforeSystemDns() {
        val secure = RecordingDns(
            result = listOf(InetAddress.getByName("184.50.187.66"))
        )
        val system = RecordingDns(
            result = listOf(InetAddress.getByName("108.160.167.165"))
        )
        val dns = SteamCommunityDns(
            secureResolvers = listOf(secure),
            systemDns = system,
            logger = {}
        )

        val addresses = dns.lookup("steamcommunity.com")

        assertEquals(listOf("184.50.187.66"), addresses.map { it.hostAddress })
        assertTrue(secure.called)
        assertFalse(system.called)
    }

    @Test
    fun steamCommunityRejectsKnownPoisonedSystemAddress() {
        val secure = RecordingDns(error = UnknownHostException("secure unavailable"))
        val system = RecordingDns(
            result = listOf(InetAddress.getByName("108.160.167.165"))
        )
        val dns = SteamCommunityDns(
            secureResolvers = listOf(secure),
            systemDns = system,
            logger = {}
        )

        try {
            dns.lookup("steamcommunity.com")
            fail("Expected the poisoned Steam Community address to be rejected")
        } catch (expected: UnknownHostException) {
            assertTrue(expected.message.orEmpty().contains("securely"))
        }
    }

    @Test
    fun unrelatedHostsContinueUsingSystemDns() {
        val secure = RecordingDns(
            result = listOf(InetAddress.getByName("184.50.187.66"))
        )
        val system = RecordingDns(
            result = listOf(InetAddress.getByName("8.8.8.8"))
        )
        val dns = SteamCommunityDns(
            secureResolvers = listOf(secure),
            systemDns = system,
            logger = {}
        )

        val addresses = dns.lookup("api.steampowered.com")

        assertEquals(listOf("8.8.8.8"), addresses.map { it.hostAddress })
        assertFalse(secure.called)
        assertTrue(system.called)
    }

    private class RecordingDns(
        private val result: List<InetAddress> = emptyList(),
        private val error: UnknownHostException? = null
    ) : Dns {
        var called: Boolean = false
            private set

        override fun lookup(hostname: String): List<InetAddress> {
            called = true
            error?.let { throw it }
            return result
        }
    }
}
