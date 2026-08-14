package takagi.ru.monica.steam.store.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamStoreDetailActionToolbarTest {
    @Test
    fun collapsedToolbarRemainsAVisibleTouchTarget() {
        assertEquals(48, STEAM_STORE_DETAIL_TOOLBAR_COLLAPSED_SIZE_DP)
    }

    @Test
    fun draggingToolbarUsesFourFullyRoundedCorners() {
        val expected = SteamStoreDetailToolbarCornerRadii(
            topStartDp = 24,
            topEndDp = 24,
            bottomStartDp = 24,
            bottomEndDp = 24
        )

        assertEquals(
            expected,
            steamStoreDetailToolbarCornerRadii(
                edge = SteamStoreDetailToolbarEdge.LEFT,
                dragging = true
            )
        )
        assertEquals(
            expected,
            steamStoreDetailToolbarCornerRadii(
                edge = SteamStoreDetailToolbarEdge.RIGHT,
                dragging = true
            )
        )
    }

    @Test
    fun releasedToolbarKeepsTheAttachedEdgeShape() {
        assertEquals(
            SteamStoreDetailToolbarCornerRadii(
                topStartDp = 5,
                topEndDp = 24,
                bottomStartDp = 5,
                bottomEndDp = 24
            ),
            steamStoreDetailToolbarCornerRadii(
                edge = SteamStoreDetailToolbarEdge.LEFT,
                dragging = false
            )
        )
        assertEquals(
            SteamStoreDetailToolbarCornerRadii(
                topStartDp = 24,
                topEndDp = 5,
                bottomStartDp = 24,
                bottomEndDp = 5
            ),
            steamStoreDetailToolbarCornerRadii(
                edge = SteamStoreDetailToolbarEdge.RIGHT,
                dragging = false
            )
        )
    }

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
