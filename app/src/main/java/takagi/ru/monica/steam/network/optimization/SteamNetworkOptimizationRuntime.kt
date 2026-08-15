package takagi.ru.monica.steam.network.optimization

import android.content.Context
import android.content.SharedPreferences
import java.net.InetAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamAutoHostsFormatter
import takagi.ru.monica.steam.network.optimization.domain.SteamBuiltInHostsPreset
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsParseResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkOptimizationSettings

object SteamNetworkOptimizationRuntime {
    private const val PREFERENCES_NAME = "steam_network_optimization"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CUSTOM_HOSTS = "custom_hosts"
    private const val KEY_FALLBACK_TO_SYSTEM_DNS = "fallback_to_system_dns"
    private const val KEY_BUILT_IN_HOSTS_PRESET_VERSION = "built_in_hosts_preset_version"

    private val mutableSettings = MutableStateFlow(SteamNetworkOptimizationSettings())
    val settings: StateFlow<SteamNetworkOptimizationSettings> = mutableSettings.asStateFlow()
    private val sessionStatsTracker = SteamHostSessionStatsTracker()
    val sessionStats = sessionStatsTracker.stats

    @Volatile
    private var initialized = false
    @Volatile
    private var hostOverrides: Map<String, List<InetAddress>> = emptyMap()
    private lateinit var preferences: SharedPreferences

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

        // New/untouched installs get the maintained static preset. Existing user/scan rules,
        // including an intentionally saved empty Hosts file, are never overwritten on upgrade.
        val shouldSeedBuiltInPreset = !preferences.contains(KEY_CUSTOM_HOSTS)
        val hostsText = if (shouldSeedBuiltInPreset) {
            SteamBuiltInHostsPreset.hostsText
        } else {
            preferences.getString(KEY_CUSTOM_HOSTS, "").orEmpty()
        }
        val parsed = SteamHostsRuleParser.parse(hostsText)
        hostOverrides = parsed.addresses

        val persistedEnabled = if (shouldSeedBuiltInPreset) {
            true
        } else {
            preferences.getBoolean(KEY_ENABLED, false)
        }
        val fallbackToSystemDns = if (shouldSeedBuiltInPreset) {
            true
        } else {
            preferences.getBoolean(KEY_FALLBACK_TO_SYSTEM_DNS, true)
        }
        val enabled = persistedEnabled && parsed.isValid && parsed.addresses.isNotEmpty()

        if (shouldSeedBuiltInPreset) {
            preferences.edit()
                .putString(KEY_CUSTOM_HOSTS, hostsText)
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_FALLBACK_TO_SYSTEM_DNS, true)
                .putInt(KEY_BUILT_IN_HOSTS_PRESET_VERSION, SteamBuiltInHostsPreset.VERSION)
                .apply()
            runCatching {
                SteamDiagLogger.append(
                    "static_hosts builtin_seeded version=${SteamBuiltInHostsPreset.VERSION} " +
                        "hosts=${parsed.hostCount} enabled=$enabled fallback=true"
                )
            }
        } else if (persistedEnabled != enabled) {
            preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        }

        mutableSettings.value = SteamNetworkOptimizationSettings(
            enabled = enabled,
            hostsText = hostsText,
            hostCount = parsed.hostCount,
            fallbackToSystemDns = fallbackToSystemDns
        )
        initialized = true
    }

    @Synchronized
    fun applyBuiltInHostsPreset(context: Context): SteamHostsParseResult {
        initialize(context)
        val parsed = SteamBuiltInHostsPreset.parsed
        if (!parsed.isValid || parsed.addresses.isEmpty()) return parsed

        preferences.edit()
            .putString(KEY_CUSTOM_HOSTS, SteamBuiltInHostsPreset.hostsText)
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_FALLBACK_TO_SYSTEM_DNS, true)
            .putInt(KEY_BUILT_IN_HOSTS_PRESET_VERSION, SteamBuiltInHostsPreset.VERSION)
            .apply()
        hostOverrides = parsed.addresses
        mutableSettings.value = SteamNetworkOptimizationSettings(
            enabled = true,
            hostsText = SteamBuiltInHostsPreset.hostsText,
            hostCount = parsed.hostCount,
            fallbackToSystemDns = true
        )
        SteamHttpClientProvider.onCustomHostsChanged()
        runCatching {
            SteamDiagLogger.append(
                "static_hosts builtin_applied version=${SteamBuiltInHostsPreset.VERSION} " +
                    "hosts=${parsed.hostCount} fallback=true"
            )
        }
        return parsed
    }

    @Synchronized
    fun setFallbackToSystemDns(context: Context, enabled: Boolean) {
        initialize(context)
        if (mutableSettings.value.fallbackToSystemDns == enabled) return
        preferences.edit().putBoolean(KEY_FALLBACK_TO_SYSTEM_DNS, enabled).apply()
        mutableSettings.value = mutableSettings.value.copy(fallbackToSystemDns = enabled)
        SteamHttpClientProvider.onCustomHostsChanged()
        runCatching {
            SteamDiagLogger.append("custom_hosts system_dns_fallback=$enabled")
        }
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        val acceptedEnabled = enabled && hostOverrides.isNotEmpty()
        if (mutableSettings.value.enabled == acceptedEnabled) return
        preferences.edit().putBoolean(KEY_ENABLED, acceptedEnabled).apply()
        mutableSettings.value = mutableSettings.value.copy(enabled = acceptedEnabled)
        SteamHttpClientProvider.onCustomHostsChanged()
        runCatching {
            SteamDiagLogger.append(
                "custom_hosts enabled=$acceptedEnabled hosts=${hostOverrides.size} scope=app"
            )
        }
    }

    @Synchronized
    fun saveHosts(context: Context, hostsText: String): SteamHostsParseResult {
        initialize(context)
        val parsed = SteamHostsRuleParser.parse(hostsText)
        if (!parsed.isValid) return parsed

        val enabled = mutableSettings.value.enabled && parsed.addresses.isNotEmpty()
        preferences.edit()
            .putString(KEY_CUSTOM_HOSTS, hostsText)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        hostOverrides = parsed.addresses
        mutableSettings.value = SteamNetworkOptimizationSettings(
            enabled = enabled,
            hostsText = hostsText,
            hostCount = parsed.hostCount,
            fallbackToSystemDns = mutableSettings.value.fallbackToSystemDns
        )
        SteamHttpClientProvider.onCustomHostsChanged()
        runCatching {
            SteamDiagLogger.append("custom_hosts saved hosts=${parsed.hostCount} enabled=$enabled")
        }
        return parsed
    }

    @Synchronized
    fun applyAutoOptimization(
        context: Context,
        result: SteamDnsOptimizationScanResult
    ): Boolean {
        initialize(context)
        if (!result.isApplicable) return false
        val mergedHosts = runCatching {
            SteamAutoHostsFormatter.merge(
                existingText = mutableSettings.value.hostsText,
                result = result
            )
        }.getOrNull() ?: return false
        val parsed = SteamHostsRuleParser.parse(mergedHosts)
        if (!parsed.isValid || parsed.addresses.isEmpty()) return false

        preferences.edit()
            .putString(KEY_CUSTOM_HOSTS, mergedHosts)
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_FALLBACK_TO_SYSTEM_DNS, true)
            .apply()
        hostOverrides = parsed.addresses
        mutableSettings.value = SteamNetworkOptimizationSettings(
            enabled = true,
            hostsText = mergedHosts,
            hostCount = parsed.hostCount,
            fallbackToSystemDns = true
        )
        SteamHttpClientProvider.onCustomHostsChanged()
        runCatching {
            SteamDiagLogger.append(
                "auto_dns applied hosts=${result.availableHostCount} " +
                    "providers=${result.providerIds.size} " +
                    "average_ms=${result.averageLatencyMillis ?: -1L} scope=app"
            )
        }
        return true
    }

    internal fun addressesForHost(hostname: String): List<InetAddress> {
        if (!mutableSettings.value.enabled) return emptyList()
        return hostOverrides[SteamHostsRuleParser.normalizeHostname(hostname)].orEmpty()
    }

    internal fun isSystemDnsFallbackEnabled(): Boolean =
        mutableSettings.value.fallbackToSystemDns

    internal fun recordHostHit(hostname: String) {
        sessionStatsTracker.record(SteamHostsRuleParser.normalizeHostname(hostname))
    }

    fun clearSessionStats() {
        sessionStatsTracker.clear()
    }
}
