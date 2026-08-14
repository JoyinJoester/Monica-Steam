package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import okhttp3.Dns
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser

/**
 * App-scoped static Hosts resolver.
 *
 * Static mappings and dynamic DNS/DoH intentionally use separate delegates:
 * - an unmapped hostname continues through the dynamic resolver chain;
 * - a mapped hostname returns its static address immediately, optionally appending only the real
 *   Android system DNS result as the configured compatibility fallback.
 *
 * Keeping those paths separate prevents a static Hosts hit from blocking on a DoH race before
 * OkHttp can even attempt the user-selected/static address.
 */
internal class SteamCustomHostsDns(
    private val unmappedDns: Dns = Dns.SYSTEM,
    private val fallbackDns: Dns = Dns.SYSTEM,
    private val customAddresses: (String) -> List<InetAddress> =
        SteamNetworkOptimizationRuntime::addressesForHost,
    private val fallbackToSystemDns: () -> Boolean =
        SteamNetworkOptimizationRuntime::isSystemDnsFallbackEnabled,
    private val onCustomHostsUsed: (String) -> Unit =
        SteamNetworkOptimizationRuntime::recordHostHit,
    private val logger: (String) -> Unit = SteamDiagLogger::append
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val normalized = SteamHostsRuleParser.normalizeHostname(hostname)
        val overrides = runCatching { customAddresses(normalized) }
            .onFailure { error ->
                logSafely(
                    "custom_hosts lookup_failed host=$normalized " +
                        "type=${error::class.java.simpleName}"
                )
            }
            .getOrDefault(emptyList())
            .filter(SteamHostsRuleParser::isUsableAddress)

        if (overrides.isEmpty()) {
            return unmappedDns.lookup(hostname)
        }

        runCatching { onCustomHostsUsed(normalized) }
        val fallbackAddresses = if (runCatching(fallbackToSystemDns).getOrDefault(true)) {
            runCatching { fallbackDns.lookup(hostname) }
                .onFailure { error ->
                    logSafely(
                        "custom_hosts fallback_lookup_failed host=$normalized " +
                            "type=${error::class.java.simpleName}"
                    )
                }
                .getOrDefault(emptyList())
                .filter(SteamHostsRuleParser::isUsableAddress)
        } else {
            emptyList()
        }
        val resolved = (overrides + fallbackAddresses)
            .distinctBy(InetAddress::getHostAddress)
        logSafely(
            "custom_hosts applied host=$normalized custom=${overrides.size} " +
                "system_fallback=${fallbackAddresses.size}"
        )
        return resolved
    }

    private fun logSafely(message: String) {
        runCatching { logger(message) }
    }
}
