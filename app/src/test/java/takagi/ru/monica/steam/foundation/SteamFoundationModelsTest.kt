package takagi.ru.monica.steam.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccountTags
import takagi.ru.monica.steam.data.SteamSecurityEventRetention
import takagi.ru.monica.steam.data.SteamSecurityEventSanitizer
import takagi.ru.monica.steam.data.SteamSecurityEventSeverity
import takagi.ru.monica.steam.data.SteamSecurityEventType

class SteamFoundationModelsTest {
    @Test
    fun tagsAreTrimmedBoundedCaseInsensitiveAndStable() {
        val tags = SteamAccountTags.normalize(
            listOf(" Main ", "main", "Trade", "", "x".repeat(40)) +
                (1..20).map { "tag-$it" }
        )

        assertEquals("Main", tags.first())
        assertEquals(1, tags.count { it.equals("main", ignoreCase = true) })
        assertTrue(tags.contains("Trade"))
        assertEquals(SteamAccountTags.MAX_TAGS, tags.size)
        assertTrue(tags.all { it.length <= SteamAccountTags.MAX_TAG_LENGTH })

        val encoded = SteamAccountTags.encode(tags)
        assertEquals(tags, SteamAccountTags.decode(encoded))
        assertEquals(emptyList<String>(), SteamAccountTags.decode("not-json"))
    }

    @Test
    fun eventSanitizerNeverPersistsAccountOrCredentialMaterial() {
        val sanitized = SteamSecurityEventSanitizer.sanitize(
            "steamid=76561198012345678 user=alice@example.com " +
                "access_token=eyJhbGciOiJIUzI1NiJ9.payload.signature " +
                "shared_secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTA= code=R87JJ"
        )

        assertFalse(sanitized.contains("76561198012345678"))
        assertFalse(sanitized.contains("alice@example.com"))
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(sanitized.contains("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA"))
        assertFalse(sanitized.contains("R87JJ"))
        assertTrue(sanitized.contains("<redacted>"))
    }

    @Test
    fun eventContractCoversPlannedSingleDeviceSecuritySurfaces() {
        assertTrue(SteamSecurityEventType.entries.contains(SteamSecurityEventType.HEALTH_CHECK))
        assertTrue(SteamSecurityEventType.entries.contains(SteamSecurityEventType.SESSION_CHANGED))
        assertTrue(SteamSecurityEventType.entries.contains(SteamSecurityEventType.DEVICE_CHANGED))
        assertTrue(SteamSecurityEventType.entries.contains(SteamSecurityEventType.CONFIRMATION_ACTION))
        assertTrue(SteamSecurityEventType.entries.contains(SteamSecurityEventType.BACKUP_EXPORTED))
        assertTrue(SteamSecurityEventType.entries.contains(SteamSecurityEventType.BACKUP_RESTORED))
        assertTrue(SteamSecurityEventType.entries.contains(SteamSecurityEventType.MARKET_ALERT))
        assertEquals(500, SteamSecurityEventRetention.MAX_EVENTS)
        assertEquals(
            listOf("INFO", "WARNING", "CRITICAL"),
            SteamSecurityEventSeverity.entries.map { it.name }
        )
    }
}
