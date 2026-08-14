package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.diagnostics.OkHttpSteamDnsResolver
import takagi.ru.monica.steam.network.optimization.diagnostics.OkHttpSteamHostProbe
import takagi.ru.monica.steam.network.optimization.diagnostics.ResettableSteamDnsResolver
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamDnsResolver
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamHostProbe
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkResolverSettings
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkTargetCatalog

/**
 * App-scoped dynamic DNS for Steam traffic.
 *
 * Dynamic resolver answers are not trusted merely because they are syntactically valid public
 * addresses. On a cache miss, enabled non-system DNS/DoH sources are raced, a small number of
 * candidate addresses are harvested, then candidates are verified with HTTPS while preserving the
 * original Steam hostname for SNI and certificate validation. Only verified candidates are cached
 * and returned. Android system DNS remains the compatibility fallback when configured.
 */
internal class SteamDynamicDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val resolver: SteamDnsResolver = OkHttpSteamDnsResolver(
        systemDns = systemDns,
        timeoutMillis = RESOLVER_TIMEOUT_MILLIS
    ),
    private val candidateProbe: SteamHostProbe = OkHttpSteamHostProbe(
        timeoutMillis = CANDIDATE_PROBE_TIMEOUT_MILLIS
    ),
    private val settingsProvider: () -> SteamNetworkResolverSettings = {
        SteamNetworkResolverSettingsRuntime.settings.value
    },
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val logger: (String) -> Unit = SteamDiagLogger::append
) : Dns {
    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMillis: Long,
        val staleUntilMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = ConcurrentHashMap<String, FutureTask<List<InetAddress>>>()
    private val resolverExecutor = Executors.newFixedThreadPool(
        MAX_PARALLEL_RESOLVERS,
        ResolverThreadFactory("DNS")
    )
    private val probeExecutor = Executors.newFixedThreadPool(
        MAX_PARALLEL_PROBES,
        ResolverThreadFactory("Probe")
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val normalized = SteamHostsRuleParser.normalizeHostname(hostname)
        if (!SteamNetworkTargetCatalog.isSteamHostname(normalized)) {
            return systemDns.lookup(hostname)
        }

        val settings = settingsProvider()
        if (!settings.dynamicDnsEnabled) {
            return systemDns.lookup(hostname)
        }

        val activeProviders = settings.activeProviders
        if (activeProviders.isEmpty()) {
            return systemDns.lookup(hostname)
        }

        // System DNS is a fallback, not a racer. If it races explicit DNS/DoH sources, the local
        // resolver usually wins by latency and silently bypasses the user's dynamic configuration.
        val providers = activeProviders.filterNot(SteamDnsProvider::isSystem)
        if (providers.isEmpty()) {
            return systemDns.lookup(hostname)
        }

        val cacheKey = buildCacheKey(normalized, providers)
        val now = clockMillis()
        val cached = cache[cacheKey]
        if (cached != null && now < cached.expiresAtMillis) {
            return cached.addresses
        }

        val resolved = resolveShared(
            cacheKey = cacheKey,
            providers = providers,
            hostname = normalized
        )
        if (resolved.isNotEmpty()) {
            return resolved
        }

        if (cached != null && now < cached.staleUntilMillis) {
            logSafely("dynamic_dns stale_cache host=$normalized addresses=${cached.addresses.size}")
            return cached.addresses
        }

        if (settings.useSystemDns) {
            val fallback = runCatching { systemDns.lookup(hostname) }
                .getOrDefault(emptyList())
                .filter(SteamHostsRuleParser::isUsableAddress)
                .distinctBy(InetAddress::getHostAddress)
            if (fallback.isNotEmpty()) {
                logSafely("dynamic_dns system_fallback host=$normalized addresses=${fallback.size}")
                return fallback
            }
        }

        logSafely("dynamic_dns failure host=$normalized providers=${providers.size}")
        throw UnknownHostException("Unable to resolve Steam host dynamically: $normalized")
    }

    fun clearCache() {
        cache.clear()
        logSafely("dynamic_dns cache_cleared")
    }

    fun onResolverSettingsChanged() {
        cache.clear()
        (resolver as? ResettableSteamDnsResolver)?.resetRuntimeState()
        logSafely("dynamic_dns resolver_state_reset")
    }

    fun cacheSize(): Int = cache.size

    private fun resolveShared(
        cacheKey: String,
        providers: List<SteamDnsProvider>,
        hostname: String
    ): List<InetAddress> {
        val candidate = FutureTask {
            val harvested = harvestResolverCandidates(providers = providers, hostname = hostname)
            val verified = verifyCandidates(hostname = hostname, candidates = harvested)
            if (verified.isNotEmpty()) {
                val resolvedAt = clockMillis()
                cache[cacheKey] = CacheEntry(
                    addresses = verified,
                    expiresAtMillis = resolvedAt + CACHE_TTL_MILLIS,
                    staleUntilMillis = resolvedAt + STALE_TTL_MILLIS
                )
                pruneExpired(resolvedAt)
                logSafely(
                    "dynamic_dns resolved host=$hostname verified=${verified.size} " +
                        "candidates=${harvested.size} sources=${providers.size}"
                )
            } else if (harvested.isNotEmpty()) {
                logSafely(
                    "dynamic_dns rejected_all host=$hostname candidates=${harvested.size} " +
                        "sources=${providers.size}"
                )
            }
            verified
        }
        val active = inFlight.putIfAbsent(cacheKey, candidate) ?: candidate
        val ownsResolution = active === candidate
        if (ownsResolution) candidate.run()
        return try {
            active.get()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            emptyList()
        } catch (_: ExecutionException) {
            emptyList()
        } finally {
            if (ownsResolution) inFlight.remove(cacheKey, candidate)
        }
    }

    /**
     * Keep the first usable resolver fast, but briefly harvest other enabled sources so a single
     * poisoned or unreachable custom answer cannot become the only route considered.
     */
    private fun harvestResolverCandidates(
        providers: List<SteamDnsProvider>,
        hostname: String
    ): List<InetAddress> {
        if (providers.isEmpty()) return emptyList()

        val candidates = providers.take(MAX_RACE_PROVIDERS)
        val completion = ExecutorCompletionService<List<InetAddress>>(resolverExecutor)
        val futures = mutableListOf<Future<List<InetAddress>>>()
        val globalDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RACE_TIMEOUT_MILLIS)
        var harvestDeadline = globalDeadline
        var completed = 0
        var foundFirstAnswer = false
        val merged = mutableListOf<InetAddress>()

        candidates.forEach { provider ->
            futures += completion.submit(Callable { resolveProvider(provider, hostname) })
        }

        try {
            while (completed < futures.size && merged.size < MAX_DYNAMIC_CANDIDATES) {
                val remaining = harvestDeadline - System.nanoTime()
                if (remaining <= 0L) break
                val future = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                completed += 1
                val addresses = runCatching { future.get() }.getOrDefault(emptyList())
                if (addresses.isNotEmpty() && !foundFirstAnswer) {
                    foundFirstAnswer = true
                    harvestDeadline = minOf(
                        globalDeadline,
                        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                            FIRST_ANSWER_HARVEST_MILLIS
                        )
                    )
                }
                addresses.forEach { address ->
                    if (merged.none { it.hostAddress == address.hostAddress }) {
                        merged += address
                    }
                }
            }
        } finally {
            futures.forEach { future ->
                if (!future.isDone) future.cancel(true)
            }
        }
        return merged.take(MAX_DYNAMIC_CANDIDATES)
    }

    /**
     * Validate candidates in parallel with the same hostname/SNI/certificate semantics used by
     * the existing scanner. Once the first working candidate is found, briefly harvest any other
     * verification already finishing so OkHttp still has a small failover list.
     */
    private fun verifyCandidates(
        hostname: String,
        candidates: List<InetAddress>
    ): List<InetAddress> {
        if (candidates.isEmpty()) return emptyList()

        val limited = candidates.take(MAX_DYNAMIC_CANDIDATES)
        val completion = ExecutorCompletionService<Pair<String, Boolean>>(probeExecutor)
        val futures = mutableListOf<Future<Pair<String, Boolean>>>()
        val globalDeadline = System.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(CANDIDATE_VALIDATION_BUDGET_MILLIS)
        var harvestDeadline = globalDeadline
        var completed = 0
        var foundFirstVerified = false
        val verifiedAddresses = linkedSetOf<String>()

        limited.forEach { address ->
            val rawAddress = address.hostAddress
            futures += completion.submit(Callable {
                val available = runCatching {
                    runBlocking {
                        candidateProbe.probe(
                            SteamHostProbeTarget(
                                hostname = hostname,
                                address = rawAddress
                            )
                        ).isAvailable
                    }
                }.getOrDefault(false)
                rawAddress to available
            })
        }

        try {
            while (completed < futures.size) {
                val remaining = harvestDeadline - System.nanoTime()
                if (remaining <= 0L) break
                val future = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                completed += 1
                val (address, available) = runCatching { future.get() }
                    .getOrDefault("" to false)
                if (available) {
                    verifiedAddresses += address
                    if (!foundFirstVerified) {
                        foundFirstVerified = true
                        harvestDeadline = minOf(
                            globalDeadline,
                            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                                VERIFIED_ANSWER_HARVEST_MILLIS
                            )
                        )
                    }
                }
            }
        } finally {
            futures.forEach { future ->
                if (!future.isDone) future.cancel(true)
            }
        }

        return limited.filter { it.hostAddress in verifiedAddresses }
    }

    private fun resolveProvider(
        provider: SteamDnsProvider,
        hostname: String
    ): List<InetAddress> {
        val result = runBlocking { resolver.resolve(provider, hostname) }
        if (!result.isAvailable) return emptyList()
        return result.addresses
            .mapNotNull { raw -> runCatching { InetAddress.getByName(raw) }.getOrNull() }
            .filter(SteamHostsRuleParser::isUsableAddress)
            .distinctBy(InetAddress::getHostAddress)
    }

    private fun buildCacheKey(hostname: String, providers: List<SteamDnsProvider>): String {
        val resolverSignature = providers.joinToString(";") { provider ->
            buildString {
                append(provider.id)
                append('|')
                append(provider.dohUrl.orEmpty())
                append('|')
                append(provider.udpServer.orEmpty())
                append('|')
                append(provider.bootstrapAddresses.joinToString(","))
            }
        }
        return "$hostname#$resolverSignature"
    }

    private fun pruneExpired(now: Long) {
        if (cache.size <= MAX_CACHE_ENTRIES) return
        cache.entries.removeIf { (_, entry) -> now >= entry.staleUntilMillis }
        if (cache.size <= MAX_CACHE_ENTRIES) return
        cache.entries
            .sortedBy { it.value.expiresAtMillis }
            .take(cache.size - MAX_CACHE_ENTRIES)
            .forEach { cache.remove(it.key) }
    }

    private fun logSafely(message: String) {
        runCatching { logger(message) }
    }

    private class ResolverThreadFactory(private val role: String) : ThreadFactory {
        override fun newThread(runnable: Runnable): Thread = Thread(
            runnable,
            "Monica-Steam-$role-${threadIds.incrementAndGet()}"
        ).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    private companion object {
        const val MAX_PARALLEL_RESOLVERS = 8
        const val MAX_PARALLEL_PROBES = 6
        const val MAX_RACE_PROVIDERS = 24
        const val MAX_DYNAMIC_CANDIDATES = 12
        const val MAX_CACHE_ENTRIES = 256
        const val RESOLVER_TIMEOUT_MILLIS = 2_500L
        const val RACE_TIMEOUT_MILLIS = 3_000L
        const val FIRST_ANSWER_HARVEST_MILLIS = 220L
        const val CANDIDATE_PROBE_TIMEOUT_MILLIS = 1_800L
        const val CANDIDATE_VALIDATION_BUDGET_MILLIS = 2_500L
        const val VERIFIED_ANSWER_HARVEST_MILLIS = 120L
        const val CACHE_TTL_MILLIS = 5 * 60 * 1_000L
        const val STALE_TTL_MILLIS = 30 * 60 * 1_000L
        val threadIds = AtomicInteger(0)
    }
}
