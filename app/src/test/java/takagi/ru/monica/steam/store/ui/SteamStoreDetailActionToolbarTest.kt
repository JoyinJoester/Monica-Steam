package takagi.ru.monica.steam.store.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamStoreDetailActionToolbarTest {
    @Test
    fun releasedToolbarSnapsToTheNearestPhysicalEdge() {
        assertEquals(
            SteamStoreDetailToolbarEdge.LEFT,
            resolveSteamStoreDetailToolbarEdge(
                leftPx = 120f,
                toolbarWidthPx = 48,
                containerWidthPx = 1_080
            )
        )
        assertEquals(
            SteamStoreDetailToolbarEdge.RIGHT,
            resolveSteamStoreDetailToolbarEdge(
                leftPx = 700f,
                toolbarWidthPx = 48,
                containerWidthPx = 1_080
            )
        )
    }

    @Test
    fun verticalPositionIsClampedAndNormalized() {
        assertEquals(
            0f,
            steamStoreDetailToolbarVerticalFraction(
                topPx = -50f,
                toolbarHeightPx = 180,
                containerHeightPx = 1_000
            )
        )
        assertEquals(
            0.5f,
            steamStoreDetailToolbarVerticalFraction(
                topPx = 410f,
                toolbarHeightPx = 180,
                containerHeightPx = 1_000
            )
        )
        assertEquals(
            1f,
            steamStoreDetailToolbarVerticalFraction(
                topPx = 2_000f,
                toolbarHeightPx = 180,
                containerHeightPx = 1_000
            )
        )
    }
}
