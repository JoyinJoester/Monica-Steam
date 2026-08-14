package takagi.ru.monica.steam.network.optimization.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNetworkOptimizationIntegrationGuardTest {
    @Test
    fun applicationSettingsAndCoreSteamClientsShareTheNetworkOptimizationRuntime() {
        val application = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamApplication.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val settingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamHttpClientProvider.kt"
        ).readText()
        val apiClient = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamApiClient.kt"
        ).readText()
        val hostsRuntime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/SteamNetworkOptimizationRuntime.kt"
        ).readText()
        val resolverRuntime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/SteamNetworkResolverSettingsRuntime.kt"
        ).readText()
        val optimizationScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationSettingsScreen.kt"
        ).readText()
        val advancedEditor = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamHostsAdvancedEditor.kt"
        ).readText()

        assertTrue(application.contains("SteamNetworkOptimizationRuntime.initialize(this)"))
        assertTrue(application.contains("SteamNetworkResolverSettingsRuntime.initialize(this)"))
        assertTrue(settings.contains("SteamSettingsChild.NETWORK_OPTIMIZATION"))
        assertTrue(settings.contains("SteamNetworkOptimizationAutoScreen("))
        assertTrue(settings.contains("SteamNetworkOptimizationSettingsScreen("))
        assertTrue(settingsHost.contains("SteamNetworkOptimizationPullCard("))
        assertTrue(provider.contains("SteamDynamicDns()"))
        assertTrue(provider.contains("unmappedDns = dynamicDnsDelegate.value"))
        assertTrue(provider.contains("fallbackDns = Dns.SYSTEM"))
        assertFalse(apiClient.contains("SteamCommunityDns"))
        assertTrue(hostsRuntime.contains("KEY_CUSTOM_HOSTS"))
        assertTrue(hostsRuntime.contains("saveHosts("))
        assertTrue(hostsRuntime.contains("applyAutoOptimization("))
        assertTrue(hostsRuntime.contains("applyBuiltInHostsPreset("))
        assertTrue(resolverRuntime.contains("KEY_DYNAMIC_DNS_ENABLED"))
        assertTrue(resolverRuntime.contains("KEY_DISABLED_CUSTOM_PROVIDER_IDS"))
        assertTrue(resolverRuntime.contains("setCustomProviderEnabled("))
        assertTrue(advancedEditor.contains("OutlinedTextField("))
        assertTrue(optimizationScreen.contains("SteamHostsRuleParser.parse("))
        assertTrue(optimizationScreen.contains("SteamNetworkOptimizationRuntime.saveHosts("))
        assertTrue(optimizationScreen.contains("steam_network_static_hosts_title"))
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/network/optimization/SteamOptimizedDns.kt"
            ).exists()
        )
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/network/SteamCommunityDns.kt"
            ).exists()
        )

        listOf(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamApiClient.kt",
            "app/src/main/java/takagi/ru/monica/steam/store/data/SteamStoreService.kt",
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginImportService.kt",
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamImageDownloader.kt",
            "app/src/main/java/takagi/ru/monica/steam/profile/SteamRemoteImageCache.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/data/SteamChatAttachmentUploader.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/avatar/data/SteamGroupAvatarUploader.kt"
        ).forEach { path ->
            assertTrue(path, projectFile(path).readText().contains("SteamHttpClientProvider"))
        }
    }

    @Test
    fun dynamicDnsAndStaticHostsRemainSeparateUserFlows() {
        val settingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val automaticScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationAutoScreen.kt"
        ).readText()
        val models = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/domain/SteamDnsOptimizationModels.kt"
        ).readText()
        val resolver = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/diagnostics/OkHttpSteamDnsResolver.kt"
        ).readText()
        val dynamicDns = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/SteamDynamicDns.kt"
        ).readText()
        val scanner = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/diagnostics/SteamDnsOptimizationScanner.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationViewModel.kt"
        ).readText()
        val automaticCard = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamNetworkAutomaticScanCard.kt"
        ).readText()
        val dynamicEntry = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamDynamicResolverEntryCard.kt"
        ).readText()
        val resolverScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkResolverSettingsScreen.kt"
        ).readText()
        val resolverServers = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamResolverServerBenchmarkCard.kt"
        ).readText()
        val resolverRuntime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/SteamNetworkResolverSettingsRuntime.kt"
        ).readText()
        val dnsCodec = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/domain/SteamDnsWireCodec.kt"
        ).readText()

        assertTrue(settingsHost.contains("homeHeaderContent ="))
        assertTrue(settingsHost.contains("SteamNetworkOptimizationPullCard("))
        assertTrue(automaticScreen.contains("SteamNetworkOptimizationViewModel"))
        assertFalse(automaticScreen.contains("rememberCoroutineScope()"))
        assertTrue(automaticScreen.contains("SteamDynamicResolverEntryCard("))
        assertTrue(automaticScreen.contains("SteamNetworkOptimizationRuntime.applyAutoOptimization("))
        assertFalse(automaticScreen.contains("SteamNetworkResolverSettingsRuntime.applyScanPreference("))
        assertTrue(automaticScreen.contains("SteamAutoHostsFormatter.routes("))
        assertTrue(automaticScreen.contains("applyScannedOptimization {"))
        assertTrue(viewModel.contains("viewModelScope.launch"))
        assertTrue(viewModel.contains("fun applyScannedOptimization("))
        assertTrue(automaticCard.contains("steam_network_static_hosts_apply"))
        assertTrue(automaticCard.contains("steam_network_static_hosts_scan"))
        assertTrue(dynamicEntry.contains("steam_network_dynamic_entry_description"))
        assertTrue(models.contains("val DEFAULTS: List<SteamDnsProvider>"))
        assertTrue(resolver.contains("DnsOverHttps.Builder()"))
        assertTrue(resolver.contains("DatagramSocket()"))
        assertTrue(resolver.contains(".includeIPv6(true)"))
        assertTrue(resolver.contains(".followRedirects(false)"))
        assertTrue(resolver.contains(".followSslRedirects(false)"))
        assertTrue(resolver.contains("resetRuntimeState()"))
        assertTrue(resolver.contains("client.connectionPool.evictAll()"))
        assertTrue(scanner.contains("SteamHostProbeTarget(hostname, address)"))
        assertTrue(scanner.contains("evaluation.candidate.hostname == hostname && evaluation.isStable"))
        assertTrue(dynamicDns.contains("candidates.forEach"))
        assertTrue(dynamicDns.contains("inFlight.putIfAbsent"))
        assertTrue(dynamicDns.contains("activeProviders.filterNot(SteamDnsProvider::isSystem)"))
        assertFalse(dynamicDns.contains("dynamic_dns cache_hit"))
        assertFalse(dynamicDns.contains("PREFERRED_HEAD_START_MILLIS"))
        assertTrue(dynamicDns.contains("CACHE_TTL_MILLIS"))
        assertTrue(automaticScreen.contains("resolverSettings.activeProviders"))
        assertTrue(resolverScreen.contains("SteamNetworkResolverSettingsRuntime"))
        assertTrue(resolverScreen.contains("SteamResolverServerBenchmarkCard("))
        assertTrue(resolverScreen.contains("setPreferIpv6("))
        assertTrue(resolverScreen.contains("refreshDynamicDnsCache()"))
        assertTrue(resolverScreen.contains("OutlinedTextField("))
        assertFalse(resolverScreen.contains("setUseBuiltInDoh("))
        assertTrue(resolverServers.contains("setCustomProviderEnabled("))
        assertTrue(resolverServers.contains("onRemoveCustomDoh"))
        assertTrue(resolverServers.contains("benchmark.benchmark(provider)"))
        assertTrue(resolverRuntime.contains("MAX_CUSTOM_DNS"))
        assertTrue(resolverRuntime.contains("KEY_DISABLED_BUILT_IN_PROVIDER_IDS"))
        assertTrue(resolverRuntime.contains("KEY_DISABLED_CUSTOM_PROVIDER_IDS"))
        assertTrue(dnsCodec.contains("parseAResponse("))
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamNetworkOptimizationHeroCard.kt"
            ).exists()
        )
    }

    @Test
    fun changingNetworkOptimizationEvictsIdleHttpsConnectionsImmediatelyWithoutCancellingActiveCalls() {
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamHttpClientProvider.kt"
        ).readText()
        val cleanupHandler = provider
            .substringAfter("private fun evictInitializedConnections(reason: String)")
            .substringBefore("private fun logCleanupFailure")

        assertTrue(cleanupHandler.contains("connectionPool.evictAll()"))
        assertFalse(cleanupHandler.contains("dispatcher.executorService.execute"))
        assertFalse(provider.contains("dispatcher.cancelAll()"))
        assertTrue(provider.contains("network_optimization cleanup_failed reason="))
        assertFalse(provider.contains("DnsOverHttps"))
        assertFalse(provider.contains("dns.alidns.com"))
        assertFalse(provider.contains("doh.pub"))
    }

    @Test
    fun v2UsesProgressiveM3eComponentsInsteadOfAButtonWall() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationSettingsScreen.kt"
        ).readText()
        val overview = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamNetworkOverviewCard.kt"
        )
        val rules = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamHostsRulesSection.kt"
        )
        val advanced = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamHostsAdvancedEditor.kt"
        )

        assertTrue(overview.exists())
        assertTrue(rules.exists())
        assertTrue(advanced.exists())
        assertTrue(screen.contains("SteamNetworkOverviewCard("))
        assertTrue(screen.contains("SteamHostsRulesSection("))
        assertTrue(screen.contains("SteamHostsAdvancedEditor("))
        assertTrue(screen.contains("SnackbarHost("))
        assertTrue(screen.contains("SteamHostsDiagnosticsRunner("))
        assertTrue(overview.readText().contains("LoadingIndicator("))
        assertTrue(overview.readText().contains("Switch("))
        assertTrue(rules.readText().contains("FilledTonalIconButton("))
        assertTrue(advanced.readText().contains("AnimatedVisibility("))
        assertTrue(advanced.readText().contains("LocalReduceAnimations"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
