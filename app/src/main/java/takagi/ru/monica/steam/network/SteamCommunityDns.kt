package takagi.ru.monica.steam.network

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

internal class SteamCommunityDns(
    private val secureResolvers: List<Dns>,
    private val systemDns: Dns = Dns.SYSTEM,
    private val logger: (String) -> Unit = SteamDiagLogger::append
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (!isSteamCommunityHost(hostname)) {
            return systemDns.lookup(hostname)
        }

        val errors = mutableListOf<String>()
        secureResolvers.forEachIndexed { index, resolver ->
            try {
                val addresses = resolver.lookup(hostname).filter(::isUsableSteamAddress)
                if (addresses.isNotEmpty()) {
                    logSafely(
                        "community_dns source=secure${index + 1} " +
                            "address=${addresses.first().hostAddress}"
                    )
                    return addresses
                }
                errors += "secure${index + 1}: no usable address"
            } catch (error: IOException) {
                errors += "secure${index + 1}: ${error.message.orEmpty()}"
            } catch (error: RuntimeException) {
                errors += "secure${index + 1}: ${error.message.orEmpty()}"
            }
        }

        try {
            val systemAddresses = systemDns.lookup(hostname)
            val usableAddresses = systemAddresses.filter(::isUsableSteamAddress)
            if (usableAddresses.isNotEmpty()) {
                logSafely(
                    "community_dns source=system address=${usableAddresses.first().hostAddress}"
                )
                return usableAddresses
            }
            if (systemAddresses.isNotEmpty()) {
                logSafely(
                    "community_dns rejected_system_addresses=" +
                        systemAddresses.joinToString(",") { it.hostAddress.orEmpty() }
                )
                errors += "system: rejected suspicious address"
            }
        } catch (error: IOException) {
            errors += "system: ${error.message.orEmpty()}"
        } catch (error: RuntimeException) {
            errors += "system: ${error.message.orEmpty()}"
        }

        val failure = UnknownHostException(
            "Unable to resolve Steam Community securely: ${errors.joinToString("; ")}"
        )
        logSafely("community_dns failure=${failure.message}")
        throw failure
    }

    private fun logSafely(message: String) {
        runCatching { logger(message) }
    }

    companion object {
        private data class DnsEndpoint(
            val url: String,
            val bootstrapAddresses: List<String>
        )

        private val endpoints = listOf(
            DnsEndpoint(
                url = "https://cloudflare-dns.com/dns-query",
                bootstrapAddresses = listOf("1.1.1.1", "1.0.0.1")
            ),
            DnsEndpoint(
                url = "https://dns.quad9.net/dns-query",
                bootstrapAddresses = listOf("9.9.9.9", "149.112.112.112")
            )
        )

        fun create(baseClient: OkHttpClient): SteamCommunityDns {
            val dohClient = baseClient.newBuilder()
                .dns(Dns.SYSTEM)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .build()
            val resolvers = endpoints.mapNotNull { endpoint ->
                runCatching {
                    DnsOverHttps.Builder()
                        .client(dohClient)
                        .url(endpoint.url.toHttpUrl())
                        .post(true)
                        .includeIPv6(false)
                        .resolvePrivateAddresses(false)
                        .bootstrapDnsHosts(
                            *endpoint.bootstrapAddresses
                                .map(InetAddress::getByName)
                                .toTypedArray()
                        )
                        .build()
                }.getOrNull()
            }
            return SteamCommunityDns(secureResolvers = resolvers)
        }

        private fun isSteamCommunityHost(hostname: String): Boolean {
            val normalized = hostname.trim().lowercase()
            return normalized == "steamcommunity.com" ||
                normalized.endsWith(".steamcommunity.com")
        }

        internal fun isUsableSteamAddress(address: InetAddress): Boolean {
            if (
                address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return false
            }
            if (address is Inet4Address) {
                val bytes = address.address.map { it.toInt() and 0xff }
                if (bytes[0] == 108 && bytes[1] == 160 && bytes[2] in 160..175) {
                    return false
                }
            }
            return true
        }
    }
}
