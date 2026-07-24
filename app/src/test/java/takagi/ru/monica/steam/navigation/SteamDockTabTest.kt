package takagi.ru.monica.steam.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamDockTabTest {
    @Test
    fun defaultOrderContainsThreeSortableContentTabs() {
        assertEquals(
            listOf(
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
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
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY
            ),
            SteamDockTab.sanitizeOrder(
                listOf(SteamDockTab.SETTINGS, SteamDockTab.TOKEN, SteamDockTab.SETTINGS)
            )
        )
    }

    @Test
    fun legacyDefaultOrderMigratesButCustomOrderIsPreserved() {
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            resolveStoredDockOrder(
                listOf(SteamDockTab.LIBRARY, SteamDockTab.STORE, SteamDockTab.SETTINGS)
            )
        )
        assertEquals(
            listOf(SteamDockTab.SETTINGS, SteamDockTab.STORE, SteamDockTab.LIBRARY),
            resolveStoredDockOrder(
                listOf(SteamDockTab.SETTINGS, SteamDockTab.STORE, SteamDockTab.LIBRARY)
            )
        )
    }

    @Test
    fun reorderHandlesFirstAndLastItemsWithoutIndexErrors() {
        assertEquals(
            listOf(
                SteamDockTab.LIBRARY,
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE
            ),
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 0, toIndex = 2)
        )
        assertEquals(
            listOf(
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY
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

    @Test
    fun dockSwipeMovesOnlyToAdjacentContentTab() {
        val order = SteamDockTab.DEFAULT_ORDER

        assertEquals(
            SteamDockTab.LIBRARY,
            dockSwipeTarget(order, SteamDockTab.STORE, totalDragPx = -80f, thresholdPx = 56f)
        )
        assertEquals(
            SteamDockTab.STORE,
            dockSwipeTarget(order, SteamDockTab.LIBRARY, totalDragPx = 80f, thresholdPx = 56f)
        )
        assertEquals(
            null,
            dockSwipeTarget(order, SteamDockTab.LIBRARY, totalDragPx = 20f, thresholdPx = 56f)
        )
    }

    @Test
    fun tokenSwipeEntersTheNearestEdgeOfTheContentDock() {
        val order = SteamDockTab.DEFAULT_ORDER

        assertEquals(
            SteamDockTab.STORE,
            dockSwipeTarget(order, SteamDockTab.TOKEN, totalDragPx = -80f, thresholdPx = 56f)
        )
        assertEquals(
            SteamDockTab.SETTINGS,
            dockSwipeTarget(order, SteamDockTab.TOKEN, totalDragPx = 80f, thresholdPx = 56f)
        )
    }
}
