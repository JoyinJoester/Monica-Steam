package takagi.ru.monica

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.navigation.SteamDockTab

class MonicaSteamNavigationTest {
    @Test
    fun firstDockItemControlsInitialPage() {
        assertEquals("STORE", initialSteamDockPage(listOf(SteamDockTab.STORE, SteamDockTab.TOKEN)))
        assertEquals("LIBRARY", initialSteamDockPage(listOf(SteamDockTab.LIBRARY, SteamDockTab.SETTINGS)))
        assertEquals("STEAM", initialSteamDockPage(emptyList()))
    }
}
