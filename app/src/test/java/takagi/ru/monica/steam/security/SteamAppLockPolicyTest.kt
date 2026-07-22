package takagi.ru.monica.steam.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAppLockPolicyTest {
    @Test
    fun unconfiguredSteamLockRemainsOptional() {
        val state = requireNotNull(SteamAppLockPolicy.resolveUnconfiguredState(false))

        assertFalse(state.isFirstTime)
        assertTrue(state.bypassEnabled)
        assertTrue(state.canEnterMainApp)
        assertEquals("steam_lock_not_configured", state.reason)
    }

    @Test
    fun configuredSteamLockDelegatesToMonicaPolicy() {
        assertNull(SteamAppLockPolicy.resolveUnconfiguredState(true))
    }
}
