package takagi.ru.monica.steam.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamAccountHealthEvaluatorTest {
    @Test
    fun completeAccountWithAccurateClockIsHealthy() {
        val report = SteamAccountHealthEvaluator.evaluate(
            account = account(),
            checkedAt = 1_700_000_000_000L,
            serverTimeSeconds = 1_700_000_005L
        )

        assertEquals(SteamHealthStatus.HEALTHY, report.status)
        assertEquals(5L, report.clockOffsetSeconds)
        assertTrue(report.checks.all { it.status == SteamHealthStatus.HEALTHY })
    }

    @Test
    fun invalidSharedSecretIsCritical() {
        val report = SteamAccountHealthEvaluator.evaluate(
            account = account(sharedSecret = "not-base64"),
            checkedAt = 1_700_000_000_000L,
            serverTimeSeconds = 1_700_000_000L
        )

        assertEquals(SteamHealthStatus.CRITICAL, report.status)
        assertEquals(
            SteamHealthStatus.CRITICAL,
            report.checks.first { it.type == SteamHealthCheckType.SHARED_SECRET }.status
        )
    }

    @Test
    fun missingOptionalSecurityAndSessionMaterialNeedsAttention() {
        val report = SteamAccountHealthEvaluator.evaluate(
            account = account(
                steamId = "missing-steamid-local",
                deviceId = "",
                identitySecret = null,
                revocationCode = null,
                accessToken = null,
                refreshToken = null
            ),
            checkedAt = 1_700_000_000_000L,
            serverTimeSeconds = null
        )

        assertEquals(SteamHealthStatus.ATTENTION, report.status)
        assertEquals(SteamHealthStatus.UNKNOWN, report.clockStatus)
        listOf(
            SteamHealthCheckType.STEAM_ID,
            SteamHealthCheckType.DEVICE_ID,
            SteamHealthCheckType.IDENTITY_SECRET,
            SteamHealthCheckType.REVOCATION_CODE,
            SteamHealthCheckType.SESSION
        ).forEach { type ->
            assertEquals(
                SteamHealthStatus.ATTENTION,
                report.checks.first { it.type == type }.status
            )
        }
    }

    @Test
    fun clockThresholdsAreStable() {
        assertEquals(SteamHealthStatus.HEALTHY, SteamClockHealth.statusForOffset(30))
        assertEquals(SteamHealthStatus.ATTENTION, SteamClockHealth.statusForOffset(31))
        assertEquals(SteamHealthStatus.ATTENTION, SteamClockHealth.statusForOffset(-120))
        assertEquals(SteamHealthStatus.CRITICAL, SteamClockHealth.statusForOffset(121))
    }

    @Test
    fun queryTimeProtobufParsesServerSeconds() {
        val response = SteamProtoWriter().apply { writeUint64(1, 1_700_000_123L) }.toByteArray()
        assertEquals(1_700_000_123L, SteamServerTimeService.parseServerTimeSeconds(response))
    }

    @Test
    fun failedQueryPreservesLastSuccessfulClockOffset() {
        val successful = SteamClockSnapshot.merge(
            previous = SteamClockSnapshot(),
            checkedAt = 1_700_000_000_000L,
            serverTimeSeconds = 1_700_000_045L
        )
        val offline = SteamClockSnapshot.merge(
            previous = successful,
            checkedAt = 1_700_000_100_000L,
            serverTimeSeconds = null
        )

        assertEquals(SteamHealthStatus.UNKNOWN, offline.currentStatus)
        assertEquals(null, offline.currentOffsetSeconds)
        assertEquals(45L, offline.lastSuccessfulOffsetSeconds)
        assertEquals(1_700_000_000_000L, offline.lastSuccessfulAt)
    }

    @Test
    fun diagnosticReportContainsNoAccountIdentifiersOrCredentials() {
        val report = SteamAccountHealthEvaluator.evaluate(
            account = account(),
            checkedAt = 1_700_000_000_000L,
            serverTimeSeconds = 1_700_000_005L
        )
        val text = SteamHealthDiagnosticFormatter.format(
            reports = listOf(report),
            generatedAt = 1_700_000_010_000L,
            appVersion = "1.0-test",
            androidApi = 35
        )

        assertTrue(text.contains("accounts_total=1"))
        assertTrue(text.contains("healthy=1"))
        assertTrue(text.contains("app_version=1.0-test"))
        assertFalse(text.contains("76561198012345678"))
        assertFalse(text.contains("test-account"))
        assertFalse(text.contains("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA"))
        assertFalse(text.contains("access-token"))
        assertFalse(text.contains("R12345"))
    }

    private fun account(
        steamId: String = "76561198012345678",
        deviceId: String = "android:12345678-1234-1234-1234-123456789012",
        sharedSecret: String = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
        identitySecret: String? = "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
        revocationCode: String? = "R12345",
        accessToken: String? = "access-token",
        refreshToken: String? = "refresh-token"
    ): SteamAccount {
        return SteamAccount(
            id = 1L,
            steamId = steamId,
            accountName = "test-account",
            displayName = "Test Account",
            deviceId = deviceId,
            sharedSecret = sharedSecret,
            identitySecret = identitySecret,
            revocationCode = revocationCode,
            tokenGid = null,
            accessToken = accessToken,
            refreshToken = refreshToken,
            steamLoginSecure = null,
            rawSteamGuardJson = "{}",
            selected = true,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 2L
        )
    }
}
