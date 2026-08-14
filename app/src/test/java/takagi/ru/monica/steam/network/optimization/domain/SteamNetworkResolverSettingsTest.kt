package takagi.ru.monica.steam.network.optimization.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.SteamDohBootstrapPreferencesCodec

class SteamNetworkResolverSettingsTest {
    @Test
    fun combinesEnabledDefaultsAndCustomSources() {
        val settings = SteamNetworkResolverSettings(
            useSystemDns = false,
            useBuiltInDoh = true,
            customDnsServers = listOf("1.1.1.1"),
            customDohEndpoints = listOf("https://resolver.example/dns-query")
        )

        assertTrue(settings.hasResolver)
        assertEquals(7, settings.activeProviders.size)
        assertFalse(settings.activeProviders.any(SteamDnsProvider::isSystem))
        assertTrue(settings.activeProviders.any { it.udpServer == "1.1.1.1" })
        assertTrue(settings.activeProviders.any { it.dohUrl?.contains("resolver.example") == true })
    }

    @Test
    fun builtInDefaultsArePublicDohProvidersBeyondSystemFallback() {
        val publicBuiltIns = SteamDnsProvider.DEFAULTS.filterNot(SteamDnsProvider::isSystem)

        assertTrue(publicBuiltIns.isNotEmpty())
        assertTrue(publicBuiltIns.all { it.isDoh })
        assertTrue(publicBuiltIns.all { it.dohUrl?.startsWith("https://") == true })
        assertFalse(publicBuiltIns.any { it.id.startsWith("custom_") })
    }

    @Test
    fun builtInDohProvidersCarryValidLiteralBootstrapAddresses() {
        val publicBuiltIns = SteamDnsProvider.DEFAULTS.filterNot(SteamDnsProvider::isSystem)

        publicBuiltIns.forEach { provider ->
            assertTrue("${provider.id} has no bootstrap IP", provider.bootstrapAddresses.isNotEmpty())
            assertEquals(
                provider.bootstrapAddresses,
                SteamResolverInputValidator.normalizeBootstrapAddresses(
                    provider.bootstrapAddresses.joinToString(",")
                )
            )
        }
    }

    @Test
    fun disabledBuiltInProviderIsRemovedFromActiveProviders() {
        val settings = SteamNetworkResolverSettings(
            useSystemDns = false,
            useBuiltInDoh = true,
            disabledBuiltInProviderIds = setOf(SteamDnsProvider.CLOUDFLARE.id)
        )

        assertFalse(settings.activeProviders.any { it.id == SteamDnsProvider.CLOUDFLARE.id })
        assertTrue(settings.activeProviders.any { it.id == SteamDnsProvider.DNSPOD.id })
    }

    @Test
    fun customDnsAndDohCanBeDisabledIndependentlyWithoutDeletingThem() {
        val customDns = SteamDnsProvider.customDns("1.1.1.1")
        val customDoh = SteamDnsProvider.customDoh("https://resolver.example/dns-query")
        val settings = SteamNetworkResolverSettings(
            useSystemDns = false,
            useBuiltInDoh = false,
            customDnsServers = listOf("1.1.1.1"),
            customDohEndpoints = listOf("https://resolver.example/dns-query"),
            disabledCustomProviderIds = setOf(customDns.id)
        )

        assertTrue(settings.configuredProviders.any { it.id == customDns.id })
        assertTrue(settings.configuredProviders.any { it.id == customDoh.id })
        assertFalse(settings.activeProviders.any { it.id == customDns.id })
        assertTrue(settings.activeProviders.any { it.id == customDoh.id })
        assertFalse(settings.isProviderEnabled(customDns))
        assertTrue(settings.isProviderEnabled(customDoh))
    }

    @Test
    fun learnedProviderPreferenceMovesThatPublicResolverToTheFront() {
        val settings = SteamNetworkResolverSettings(
            useSystemDns = false,
            useBuiltInDoh = true,
            preferredProviderIds = listOf(SteamDnsProvider.GOOGLE.id)
        )

        assertEquals(SteamDnsProvider.GOOGLE.id, settings.activeProviders.first().id)
    }

    @Test
    fun ipv6PreferenceIsOptInAndIndependentFromResolverSelection() {
        val defaults = SteamNetworkResolverSettings()
        val ipv6Preferred = defaults.copy(preferIpv6 = true)

        assertFalse(defaults.preferIpv6)
        assertTrue(ipv6Preferred.preferIpv6)
        assertEquals(defaults.activeProviders.map { it.id }, ipv6Preferred.activeProviders.map { it.id })
    }

    @Test
    fun customDohRemainsCustomAndNeverBecomesABuiltInDefault() {
        val customEndpoint = "https://resolver.example/dns-query"
        val settings = SteamNetworkResolverSettings(
            customDohEndpoints = listOf(customEndpoint)
        )

        assertTrue(settings.configuredProviders.any { it.dohUrl == customEndpoint })
        assertFalse(SteamDnsProvider.DEFAULTS.any { it.dohUrl == customEndpoint })
    }

    @Test
    fun customDohBootstrapAddressesAreAttachedWithoutChangingProviderIdentity() {
        val endpoint = "https://gateway.example/dns-query"
        val withoutBootstrap = SteamDnsProvider.customDoh(endpoint)
        val settings = SteamNetworkResolverSettings(
            useSystemDns = false,
            useBuiltInDoh = false,
            customDohEndpoints = listOf(endpoint),
            customDohBootstrapAddresses = mapOf(
                endpoint to listOf("1.1.1.1", "2606:4700:4700::1111")
            )
        )

        val provider = settings.configuredProviders.single()
        assertEquals(withoutBootstrap.id, provider.id)
        assertEquals(endpoint, provider.dohUrl)
        assertEquals(
            listOf("1.1.1.1", "2606:4700:4700::1111"),
            provider.bootstrapAddresses
        )
    }

    @Test
    fun validatesDnsAndDohWithoutAcceptingPortsOrUnsafeSchemes() {
        assertEquals("1.1.1.1", SteamResolverInputValidator.normalizeDnsServer(" 1.1.1.1 "))
        assertEquals(
            "dns.example.com",
            SteamResolverInputValidator.normalizeDnsServer("DNS.Example.Com")
        )
        assertTrue(
            SteamResolverInputValidator.normalizeDnsServer("[2606:4700:4700::1111]")
                ?.contains(':') == true
        )
        assertNull(SteamResolverInputValidator.normalizeDnsServer("1.1.1.1:53"))
        assertNull(SteamResolverInputValidator.normalizeDnsServer("dns server.example"))

        assertEquals(
            "https://resolver.example/dns-query",
            SteamResolverInputValidator.normalizeDohEndpoint(
                "https://resolver.example/dns-query"
            )
        )
        assertNull(
            SteamResolverInputValidator.normalizeDohEndpoint(
                "http://resolver.example/dns-query"
            )
        )
        assertNull(
            SteamResolverInputValidator.normalizeDohEndpoint(
                "https://user:pass@resolver.example/dns-query"
            )
        )
        assertNull(
            SteamResolverInputValidator.normalizeDohEndpoint(
                "https://resolver.example:8443/dns-query"
            )
        )
    }

    @Test
    fun validatesOptionalDohBootstrapIpList() {
        assertEquals(
            listOf("1.1.1.1", "2606:4700:4700::1111"),
            SteamResolverInputValidator.normalizeBootstrapAddresses(
                "1.1.1.1, [2606:4700:4700::1111]"
            )
        )
        assertEquals(
            emptyList<String>(),
            SteamResolverInputValidator.normalizeBootstrapAddresses("   ")
        )
        assertNull(
            SteamResolverInputValidator.normalizeBootstrapAddresses("resolver.example")
        )
        assertNull(
            SteamResolverInputValidator.normalizeBootstrapAddresses("1.1.1.1:53")
        )
        assertNull(SteamResolverInputValidator.normalizeBootstrapAddresses(":::::"))
        assertNull(SteamResolverInputValidator.normalizeBootstrapAddresses("1:2:3"))
        assertNull(
            SteamResolverInputValidator.normalizeBootstrapAddresses("[2606:4700:4700::1111")
        )
        assertNull(
            SteamResolverInputValidator.normalizeBootstrapAddresses("2606:4700:4700::1111]")
        )
    }

    @Test
    fun dohBootstrapPreferencesRoundTripAndIgnoreInvalidOrOrphanEntries() {
        val endpoint = "https://gateway.example/dns-query"
        val encoded = SteamDohBootstrapPreferencesCodec.encode(
            mapOf(
                endpoint to listOf("1.1.1.1", "2606:4700:4700::1111"),
                "invalid endpoint" to listOf("8.8.8.8"),
                "https://empty.example/dns-query" to emptyList()
            )
        )

        assertEquals(1, encoded.size)
        assertEquals(
            mapOf(endpoint to listOf("1.1.1.1", "2606:4700:4700::1111")),
            SteamDohBootstrapPreferencesCodec.decode(
                entries = encoded + setOf(
                    "https://orphan.example/dns-query\t8.8.8.8",
                    "$endpoint\tresolver.example",
                    "malformed"
                ),
                validEndpoints = setOf(endpoint)
            )
        )
        assertEquals(
            emptyMap<String, List<String>>(),
            SteamDohBootstrapPreferencesCodec.decode(encoded, emptySet())
        )
    }
}
