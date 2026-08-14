package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.diagnostics.ResettableSteamDnsResolver
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamDnsResolver
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamHostProbe
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkResolverSettings

class SteamDynamicDnsTest {
    @Test
    fun delegatesDisabledAndNonSteamLookupsToSystemDns() {
        val systemDns = RecordingDns(listOf("8.8.8.8"))
        val resolverCalls = AtomicInteger()
        val resolver = SteamDnsResolver { provider, hostname ->
            resolverCalls.incrementAndGet()
            resolution(provider, hostname, "104.18.20.10")
        }
        val disabled = SteamDynamicDns(
            systemDns = systemDns,
            resolver = resolver,
            candidateProbe = availableProbe(),
            settingsProvider = { dynamicSettings().copy(dynamicDnsEnabled = false) },
            logger = {}
        )
        val enabled = SteamDynamicDns(
            systemDns = systemDns,
            resolver = resolver,
            candidateProbe = availableProbe(),
            settingsProvider = ::dynamicSettings,
            logger = {}
        )

        assertEquals("8.8.8.8", disabled.lookup(STEAM_HOST).single().hostAddress)
        assertEquals("8.8.8.8", enabled.lookup("example.com").single().hostAddress)
        assertEquals(0, resolverCalls.get())
        assertEquals(2, systemDns.calls.get())
    }

    @Test
    fun configuredDynamicSourceRunsBeforeSystemDnsFallback() {
        val systemDns = RecordingDns(listOf("8.8.8.8"))
        val resolverCalls = AtomicInteger()
        val dns = SteamDynamicDns(
            systemDns = systemDns,
            resolver = SteamDnsResolver { provider, hostname ->
                resolverCalls.incrementAndGet()
                resolution(provider, hostname, "104.18.20.10")
            },
            candidateProbe = availableProbe(),
            settingsProvider = {
                dynamicSettings().copy(useSystemDns = true)
            },
            logger = {}
        )

        assertEquals("104.18.20.10", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(1, resolverCalls.get())
        assertEquals(0, systemDns.calls.get())
    }

    @Test
    fun systemDnsIsUsedWhenConfiguredDynamicSourcesFail() {
        val systemDns = RecordingDns(listOf("8.8.8.8"))
        val resolverCalls = AtomicInteger()
        val dns = SteamDynamicDns(
            systemDns = systemDns,
            resolver = SteamDnsResolver { provider, hostname ->
                resolverCalls.incrementAndGet()
                SteamDnsResolutionResult(provider = provider, hostname = hostname)
            },
            candidateProbe = availableProbe(),
            settingsProvider = {
                dynamicSettings().copy(useSystemDns = true)
            },
            logger = {}
        )

        assertEquals("8.8.8.8", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(1, resolverCalls.get())
        assertEquals(1, systemDns.calls.get())
    }

    @Test
    fun rejectsBadFastCustomAnswerAndUsesVerifiedAlternative() {
        val fastBad = SteamDnsProvider.customDns("1.1.1.1")
        val slowerGood = SteamDnsProvider.customDns("8.8.8.8")
        val settings = SteamNetworkResolverSettings(
            useSystemDns = true,
            useBuiltInDoh = false,
            customDnsServers = listOf("1.1.1.1", "8.8.8.8"),
            dynamicDnsEnabled = true
        )
        val systemDns = RecordingDns(listOf("9.9.9.9"))
        val dns = SteamDynamicDns(
            systemDns = systemDns,
            resolver = SteamDnsResolver { provider, hostname ->
                when (provider.id) {
                    fastBad.id -> resolution(provider, hostname, "203.0.113.10")
                    slowerGood.id -> {
                        Thread.sleep(25)
                        resolution(provider, hostname, "104.18.20.10")
                    }
                    else -> SteamDnsResolutionResult(provider = provider, hostname = hostname)
                }
            },
            candidateProbe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = if (target.address == "104.18.20.10") {
                        SteamHostProbeStatus.AVAILABLE
                    } else {
                        SteamHostProbeStatus.CONNECTION_ERROR
                    },
                    latencyMillis = 1L,
                    httpStatusCode = if (target.address == "104.18.20.10") 200 else null
                )
            },
            settingsProvider = { settings },
            logger = {}
        )

        assertEquals("104.18.20.10", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(0, systemDns.calls.get())
    }

    @Test
    fun fallsBackToSystemDnsWhenDynamicAnswersExistButAllFailHttpsValidation() {
        val systemDns = RecordingDns(listOf("8.8.8.8"))
        val dns = SteamDynamicDns(
            systemDns = systemDns,
            resolver = SteamDnsResolver { provider, hostname ->
                resolution(provider, hostname, "104.18.20.10")
            },
            candidateProbe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.TLS_ERROR,
                    latencyMillis = 1L
                )
            },
            settingsProvider = {
                dynamicSettings().copy(useSystemDns = true)
            },
            logger = {}
        )

        assertEquals("8.8.8.8", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(1, systemDns.calls.get())
    }

    @Test
    fun cachesSuccessfulResolutionAndCoalescesConcurrentMisses() {
        val resolverCalls = AtomicInteger()
        val resolver = SteamDnsResolver { provider, hostname ->
            resolverCalls.incrementAndGet()
            Thread.sleep(120)
            resolution(provider, hostname, "104.18.20.10")
        }
        val logs = CopyOnWriteArrayList<String>()
        val dns = SteamDynamicDns(
            resolver = resolver,
            candidateProbe = availableProbe(),
            settingsProvider = ::dynamicSettings,
            logger = logs::add
        )
        val executor = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)

        try {
            val futures = List(6) {
                executor.submit<List<InetAddress>> {
                    start.await()
                    dns.lookup(STEAM_HOST)
                }
            }
            start.countDown()
            val answers = futures.map { it.get(3, TimeUnit.SECONDS) }

            assertTrue(answers.all { it.single().hostAddress == "104.18.20.10" })
            assertEquals(1, resolverCalls.get())
            assertEquals("104.18.20.10", dns.lookup(STEAM_HOST).single().hostAddress)
            assertEquals(1, resolverCalls.get())
            assertEquals(1, dns.cacheSize())
            assertEquals(1, logs.count { it.startsWith("dynamic_dns resolved") })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun resolverSettingsChangeClearsAnswerCacheAndTransportState() {
        val resolver = object : SteamDnsResolver, ResettableSteamDnsResolver {
            var resetCount = 0
            var resolveCount = 0

            override suspend fun resolve(
                provider: SteamDnsProvider,
                hostname: String
            ): SteamDnsResolutionResult {
                resolveCount++
                return resolution(provider, hostname, "104.18.20.10")
            }

            override fun resetRuntimeState() {
                resetCount++
            }
        }
        val dns = SteamDynamicDns(
            resolver = resolver,
            candidateProbe = availableProbe(),
            settingsProvider = ::dynamicSettings,
            logger = {}
        )

        assertEquals("104.18.20.10", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(1, dns.cacheSize())
        assertEquals(1, resolver.resolveCount)

        dns.onResolverSettingsChanged()

        assertEquals(0, dns.cacheSize())
        assertEquals(1, resolver.resetCount)
        assertEquals("104.18.20.10", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(2, resolver.resolveCount)
    }

    @Test
    fun staleCacheSurvivesAResolverFailure() {
        var now = 0L
        val resolverCalls = AtomicInteger()
        val resolver = SteamDnsResolver { provider, hostname ->
            if (resolverCalls.incrementAndGet() == 1) {
                resolution(provider, hostname, "104.18.20.10")
            } else {
                SteamDnsResolutionResult(provider = provider, hostname = hostname)
            }
        }
        val dns = SteamDynamicDns(
            resolver = resolver,
            candidateProbe = availableProbe(),
            settingsProvider = ::dynamicSettings,
            clockMillis = { now },
            logger = {}
        )

        assertEquals("104.18.20.10", dns.lookup(STEAM_HOST).single().hostAddress)
        now = TimeUnit.MINUTES.toMillis(6)
        assertEquals("104.18.20.10", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(2, resolverCalls.get())
    }

    @Test
    fun emptyEnabledSourceSetFallsBackToNormalSystemResolution() {
        val systemDns = RecordingDns(listOf("8.8.4.4"))
        val resolverCalls = AtomicInteger()
        val dns = SteamDynamicDns(
            systemDns = systemDns,
            resolver = SteamDnsResolver { provider, hostname ->
                resolverCalls.incrementAndGet()
                resolution(provider, hostname, "104.18.20.10")
            },
            candidateProbe = availableProbe(),
            settingsProvider = {
                SteamNetworkResolverSettings(
                    useSystemDns = false,
                    useBuiltInDoh = false,
                    dynamicDnsEnabled = true
                )
            },
            logger = {}
        )

        assertEquals("8.8.4.4", dns.lookup(STEAM_HOST).single().hostAddress)
        assertEquals(0, resolverCalls.get())
        assertEquals(1, systemDns.calls.get())
    }

    private fun dynamicSettings() = SteamNetworkResolverSettings(
        useSystemDns = false,
        useBuiltInDoh = false,
        customDnsServers = listOf("1.1.1.1"),
        dynamicDnsEnabled = true
    )

    private fun resolution(
        provider: SteamDnsProvider,
        hostname: String,
        address: String
    ) = SteamDnsResolutionResult(
        provider = provider,
        hostname = hostname,
        addresses = listOf(address),
        latencyMillis = 1L
    )

    private fun availableProbe() = SteamHostProbe { target ->
        SteamHostProbeResult(
            target = target,
            status = SteamHostProbeStatus.AVAILABLE,
            latencyMillis = 1L,
            httpStatusCode = 200
        )
    }

    private class RecordingDns(addresses: List<String>) : Dns {
        private val answers = addresses.map(InetAddress::getByName)
        val calls = AtomicInteger()

        override fun lookup(hostname: String): List<InetAddress> {
            calls.incrementAndGet()
            return answers
        }
    }

    private companion object {
        const val STEAM_HOST = "store.steampowered.com"
    }
}
