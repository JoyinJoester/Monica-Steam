package takagi.ru.monica.steam.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAlertPolicyTest {
    @Test
    fun enabledTypesProduceOnlyRequestedAlertKinds() {
        val settings = SteamAlertSettings(
            enabled = true,
            confirmationsEnabled = true,
            sessionEnabled = false,
            devicesEnabled = true,
            pricesEnabled = true,
            lastDeviceCount = 2
        )
        val decision = SteamAlertPolicy.evaluate(
            settings,
            SteamAlertObservation(
                pendingConfirmations = 1,
                sessionIssues = 3,
                authorizedDeviceCount = 3,
                stalePriceCaches = 1
            )
        )

        assertEquals(
            setOf(SteamAlertKind.CONFIRMATIONS, SteamAlertKind.DEVICES, SteamAlertKind.PRICES),
            decision.kinds
        )
        assertFalse(SteamAlertKind.SESSION in decision.kinds)
    }

    @Test
    fun deviceBaselineDoesNotAlertOnFirstSuccessfulCheck() {
        val decision = SteamAlertPolicy.evaluate(
            SteamAlertSettings(enabled = true, lastDeviceCount = null),
            SteamAlertObservation(authorizedDeviceCount = 4)
        )

        assertFalse(SteamAlertKind.DEVICES in decision.kinds)
        assertEquals(4, decision.deviceBaseline)
    }

    @Test
    fun identicalAlertIsSuppressedForTwentyFourHours() {
        val decision = SteamAlertDecision(setOf(SteamAlertKind.SESSION), null)
        val settings = SteamAlertSettings(
            enabled = true,
            lastAlertSignature = decision.signature,
            lastNotificationAt = 1_000L
        )

        assertFalse(SteamAlertPolicy.shouldNotify(settings, decision, 2_000L))
        assertTrue(
            SteamAlertPolicy.shouldNotify(
                settings,
                decision,
                1_000L + SteamAlertPolicy.REPEAT_SUPPRESSION_MS
            )
        )
    }

    @Test
    fun intervalIsRestrictedToBatterySafeChoices() {
        assertEquals(12, SteamAlertSettings(intervalHours = 1).normalizedIntervalHours)
        assertEquals(6, SteamAlertSettings(intervalHours = 6).normalizedIntervalHours)
        assertEquals(setOf(6, 12, 24), SteamAlertSettings.allowedIntervals)
    }
}
