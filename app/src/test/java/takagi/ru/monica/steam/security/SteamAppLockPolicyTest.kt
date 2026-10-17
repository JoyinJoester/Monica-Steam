package takagi.ru.monica.steam.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAppLockPolicyTest {
    @Test
    fun unconfiguredSteamLockRequiresPasswordSetup() {
        val state = requireNotNull(SteamAppLockPolicy.resolveUnconfiguredState(false))

        assertTrue(state.isFirstTime)
        assertFalse(state.bypassEnabled)
        assertFalse(state.canRestoreSession)
        assertFalse(state.canEnterMainApp)
        assertEquals("steam_master_password_setup_required", state.reason)
    }

    @Test
    fun configuredSteamLockDelegatesToMonicaPolicy() {
        assertNull(SteamAppLockPolicy.resolveUnconfiguredState(true))
    }
}
