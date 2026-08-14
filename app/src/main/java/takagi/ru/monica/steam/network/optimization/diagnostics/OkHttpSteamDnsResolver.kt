package takagi.ru.monica.steam.network.optimization.diagnostics

import java.io.EOFException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import takagi.ru.monica.steam.network.optimization.SteamNetworkResolverSettingsRuntime
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsWireCodec
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser

internal class OkHttpSteamDnsResolver(
    private val systemDns: Dns = Dns.SYSTEM,
    private val timeoutMillis: Long = 4_000L,
    private val clockNanos: () -> Long = System::nanoTime
) : SteamDnsResolver, ResettableSteamDnsResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis * 2L, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val dohResolvers = mutableMapOf<String, Dns>()

    override suspend fun resolve(
        provider: SteamDnsProvider,
        hostname: String
    ): SteamDnsResolutionResult = withContext(Dispatchers.IO) {
        val startedAt = clockNanos()
        try {
            val addresses = if (provider.isUdp) {
                resolveUdp(provider, hostname)
            } else {
                val preferIpv6 = SteamNetworkResolverSettingsRuntime.settings.value.preferIpv6
                resolverFor(provider)
                    .lookup(hostname)
                    .asSequence()
                    .filter(SteamHostsRuleParser::isUsableAddress)
                    .sortedBy { address ->
                        when {
                            preferIpv6 && address is Inet6Address -> 0
                            preferIpv6 -> 1
                            address is Inet6Address -> 1
                            else -> 0
                        }
                    }
                    .mapNotNull(InetAddress::getHostAddress)
                    .distinct()
                    .take(MAX_ADDRESSES_PER_RESOLUTION)
                    .toList()
            }
            SteamDnsResolutionResult(
                provider = provider,
                hostname = hostname,
                addresses = addresses,
                latencyMillis = elapsedMillis(startedAt)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamDnsResolutionResult(
                provider = provider,
                hostname = hostname,
                latencyMillis = elapsedMillis(startedAt),
                errorType = error::class.java.simpleName
            )
        }
    }

    /**
     * Resolver settings can change the DoH endpoint or its bootstrap IPs while the process stays
     * alive. DnsOverHttps instances are keyed by those settings, but they share this OkHttpClient;
     * without evicting its pool a newly-created resolver may reuse an old TLS connection to the
     * same DoH hostname and make a changed bootstrap address appear ineffective.
     */
    override fun resetRuntimeState() {
        synchronized(dohResolvers) {
            dohResolvers.clear()
        }
        client.connectionPool.evictAll()
    }

    private fun resolverFor(provider: SteamDnsProvider): Dns {
        if (provider.isSystem) return systemDns
        val resolverKey = buildString {
            append(provider.id)
            append('|')
            append(provider.dohUrl.orEmpty())
            append('|')
            append(provider.bootstrapAddresses.joinToString(","))
        }
        return synchronized(dohResolvers) {
            dohResolvers.getOrPut(resolverKey) {
                val bootstrapHosts = provider.bootstrapAddresses.map(InetAddress::getByName)
                val builder = DnsOverHttps.Builder()
                    .client(client)
                    .url(requireNotNull(provider.dohUrl).toHttpUrl())
                    .includeIPv6(true)
                    .post(true)
                    .resolvePrivateAddresses(false)
                    .resolvePublicAddresses(true)
                if (bootstrapHosts.isNotEmpty()) builder.bootstrapDnsHosts(bootstrapHosts)
                builder.build()
            }
        }
    }

    private fun resolveUdp(provider: SteamDnsProvider, hostname: String): List<String> {
        val server = requireNotNull(provider.udpServer)
        val transactionId = transactionIds.incrementAndGet() and 0xffff
        val query = SteamDnsWireCodec.buildAQuery(hostname, transactionId)
        val socket = DatagramSocket()
        return try {
            socket.soTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            socket.connect(InetSocketAddress(server, DNS_PORT))
            socket.send(DatagramPacket(query, query.size))
            val buffer = ByteArray(MAX_DNS_MESSAGE_BYTES)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val message = response.data.copyOf(response.length)
            if (SteamDnsWireCodec.isTruncatedResponse(message, transactionId)) {
                resolveTcp(server, hostname, query, transactionId)
            } else {
                SteamDnsWireCodec.parseAResponse(
                    message = message,
                    transactionId = transactionId,
                    expectedHostname = hostname
                ).take(MAX_ADDRESSES_PER_RESOLUTION)
            }
        } finally {
            socket.close()
        }
    }

    private fun resolveTcp(
        server: String,
        hostname: String,
        query: ByteArray,
        transactionId: Int
    ): List<String> {
        val socket = Socket()
        return try {
            val timeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            socket.connect(InetSocketAddress(server, DNS_PORT), timeout)
            socket.soTimeout = timeout
            socket.getOutputStream().apply {
                write((query.size ushr 8) and 0xff)
                write(query.size and 0xff)
                write(query)
                flush()
            }
            val input = socket.getInputStream()
            val high = input.read()
            val low = input.read()
            if (high < 0 || low < 0) throw EOFException("DNS TCP response has no length")
            val size = (high shl 8) or low
            if (size !in 12 until MAX_DNS_MESSAGE_BYTES) {
                throw IllegalArgumentException("DNS TCP response is too large")
            }
            val message = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val read = input.read(message, offset, size - offset)
                if (read < 0) throw EOFException("DNS TCP response ended early")
                offset += read
            }
            SteamDnsWireCodec.parseAResponse(
                message = message,
                transactionId = transactionId,
                expectedHostname = hostname
            ).take(MAX_ADDRESSES_PER_RESOLUTION)
        } finally {
            socket.close()
        }
    }

    private fun elapsedMillis(startedAt: Long): Long =
        ((clockNanos() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private companion object {
        const val MAX_ADDRESSES_PER_RESOLUTION = 8
        const val MAX_DNS_MESSAGE_BYTES = 65_536
        const val DNS_PORT = 53
        val transactionIds = AtomicInteger(0x4d53)
    }
}
