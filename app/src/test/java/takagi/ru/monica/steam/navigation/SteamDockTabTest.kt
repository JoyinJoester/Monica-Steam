package takagi.ru.monica.steam.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamDockTabTest {
    @Test
    fun defaultOrderContainsThreeSortableContentTabs() {
        assertEquals(
            listOf(
                SteamDockTab.LIBRARY,
                SteamDockTab.STORE,
                SteamDockTab.SETTINGS
            ),
            SteamDockTab.DEFAULT_ORDER
        )
    }

    @Test
    fun sanitizeRemovesDuplicatesAndRestoresMissingTabs() {
        assertEquals(
            listOf(
                SteamDockTab.SETTINGS,
                SteamDockTab.LIBRARY,
                SteamDockTab.STORE
            ),
            SteamDockTab.sanitizeOrder(
                listOf(SteamDockTab.SETTINGS, SteamDockTab.TOKEN, SteamDockTab.SETTINGS)
            )
        )
    }

    @Test
    fun reorderHandlesFirstAndLastItemsWithoutIndexErrors() {
        assertEquals(
            listOf(
                SteamDockTab.STORE,
                SteamDockTab.SETTINGS,
                SteamDockTab.LIBRARY
            ),
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 0, toIndex = 2)
        )
        assertEquals(
            listOf(
                SteamDockTab.SETTINGS,
                SteamDockTab.LIBRARY,
                SteamDockTab.STORE
            ),
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 2, toIndex = 0)
        )
    }

    @Test
    fun reorderIgnoresLazyListHeaderIndicesInsteadOfThrowing() {
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 3, toIndex = 1)
        )
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 1, toIndex = 3)
        )
    }
}
