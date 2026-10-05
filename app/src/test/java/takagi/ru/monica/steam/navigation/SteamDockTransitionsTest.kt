package takagi.ru.monica.steam.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamDockTransitionsTest {
    @Test
    fun everyPairFollowsVisualOrderAndReversesInRtl() {
        val custom = listOf(SteamDockTab.CHAT, SteamDockTab.LIBRARY, SteamDockTab.STORE)
        val orders = listOf(
            listOf(SteamDockTab.TOKEN) + SteamDockTab.completeOrder(custom),
            SteamDockTab.completeOrder(custom) + SteamDockTab.TOKEN,
            SteamDockTab.completeLiquidGlassOrder(custom),
            SteamDockTab.completeFixedOrder(custom)
        )
        for (order in orders) {
            for ((fromIndex, from) in order.withIndex()) {
                for ((toIndex, to) in order.withIndex()) {
                    val expected = when {
                        toIndex > fromIndex -> 1
                        toIndex < fromIndex -> -1
                        else -> 0
                    }
                    assertEquals(expected, dockPageSlideSign(order, from, to, false, false))
                    assertEquals(-expected, dockPageSlideSign(order, from, to, true, false))
                    assertEquals(0, dockPageSlideSign(order, from, to, false, true))
                    assertEquals(0, dockPageSlideSign(order, from, to, true, true))
                }
            }
        }
    }

    @Test
    fun absentTabsDoNotInventASlideDirection() {
        val order = SteamDockTab.DEFAULT_ORDER
        assertEquals(0, dockPageSlideSign(order, SteamDockTab.TOKEN, SteamDockTab.STORE, false, false))
        assertEquals(0, dockPageSlideSign(order, SteamDockTab.STORE, SteamDockTab.SETTINGS, false, false))
        assertEquals(0, dockPageSlideSign(emptyList(), SteamDockTab.STORE, SteamDockTab.CHAT, false, false))
    }
}
