package takagi.ru.monica.steam.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamDockStyleTest {
    @Test
    fun newAndLegacyM3eConfigurationsUseFixedNavigation() {
        assertEquals(SteamDockStyle.FIXED, SteamDockStyle.fromStoredValue(null))
        assertEquals(SteamDockStyle.FIXED, SteamDockStyle.fromStoredValue("UNKNOWN"))
        assertEquals(SteamDockStyle.FIXED, SteamDockStyle.fromStoredValue("M3E"))
        assertEquals(listOf("FIXED", "LIQUID_GLASS"), SteamDockStyle.entries.map { it.name })
        assertEquals(
            SteamDockStyle.LIQUID_GLASS,
            SteamDockStyle.fromStoredValue(SteamDockStyle.LIQUID_GLASS.name)
        )
        assertEquals(
            SteamDockStyle.FIXED,
            SteamDockStyle.fromStoredValue(SteamDockStyle.FIXED.name)
        )
    }

    @Test
    fun liquidGlassDefaultKeepsAllFiveTopLevelDestinations() {
        assertEquals(
            listOf(
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT,
                SteamDockTab.TOKEN,
                SteamDockTab.SETTINGS
            ),
            SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER
        )
    }

    @Test
    fun fixedDefaultKeepsAllFiveTopLevelDestinations() {
        assertEquals(
            SteamDockTab.LIQUID_GLASS_DEFAULT_ORDER,
            SteamDockTab.FIXED_DEFAULT_ORDER
        )
    }

    @Test
    fun retiredToolbarOrderMigratesWithoutOverwritingAnExistingFixedOrder() {
        val legacy = listOf(SteamDockTab.CHAT, SteamDockTab.STORE)
        assertEquals(
            listOf(SteamDockTab.CHAT, SteamDockTab.STORE, SteamDockTab.LIBRARY,
                SteamDockTab.TOKEN, SteamDockTab.SETTINGS),
            resolveFixedDockOrder(stored = null, legacyOrder = legacy)
        )
        val fixed = listOf(SteamDockTab.TOKEN, SteamDockTab.SETTINGS, SteamDockTab.LIBRARY,
            SteamDockTab.STORE, SteamDockTab.CHAT)
        assertEquals(fixed, resolveFixedDockOrder(stored = fixed, legacyOrder = legacy))
    }
}
