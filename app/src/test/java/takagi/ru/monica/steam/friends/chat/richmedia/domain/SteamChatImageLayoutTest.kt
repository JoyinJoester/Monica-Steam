package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.*
import org.junit.Test

class SteamChatImageLayoutTest {
    @Test fun albumTilesCoverWholeBubbleWithoutOverlap() {
        for (count in 1..10) for (ratio in listOf(.1f, .65f, 1f, 1.8f, 10f)) for (width in listOf(160f, 280f, 324f, 500f)) {
            val layout = steamChatImageLayout(List(count) { ratio }, width)
            assertEquals(count, layout.tiles.size)
            assertTrue(layout.width > 0 && layout.width <= minOf(width, 324f))
            assertTrue(layout.height in 96f..440f)
            var area = 0f
            layout.tiles.forEachIndexed { index, tile ->
                assertTrue(tile.left >= 0 && tile.top >= 0 && tile.right <= 1 && tile.bottom <= 1)
                assertTrue(tile.right > tile.left && tile.bottom > tile.top)
                area += (tile.right - tile.left) * (tile.bottom - tile.top)
                layout.tiles.drop(index + 1).forEach { other ->
                    val overlapWidth = minOf(tile.right, other.right) - maxOf(tile.left, other.left)
                    val overlapHeight = minOf(tile.bottom, other.bottom) - maxOf(tile.top, other.top)
                    assertFalse("Tiles overlap: $tile $other", overlapWidth > .00001f && overlapHeight > .00001f)
                }
            }
            assertEquals(1f, area, .00001f)
        }
    }

    @Test fun singlePortraitAndLandscapeUseTheirRealAspectRatio() {
        for (ratio in listOf(.5f, .75f, 1f, 4f / 3f, 1.8f)) {
            val result = steamChatImageLayout(listOf(ratio), 324f)
            assertEquals(ratio, result.width / result.height, .001f)
            assertTrue(result.height <= 420)
        }
    }

    @Test fun portraitFirstThreePhotosUseFullHeightLeftTile() {
        val tiles = steamChatImageLayout(listOf(.6f, .6f, .6f), 324f).tiles
        assertEquals(0f, tiles[0].top, 0f)
        assertEquals(1f, tiles[0].bottom, 0f)
        assertEquals(tiles[0].right, tiles[1].left, 0f)
        assertEquals(tiles[1].bottom, tiles[2].top, 0f)
    }

    @Test fun oddGridLastPhotoFillsLastRow() {
        for (count in listOf(5, 7, 9)) {
            val tile = steamChatImageLayout(List(count) { 1f }, 324f).tiles.last()
            assertEquals(0f, tile.left, 0f)
            assertEquals(1f, tile.right, 0f)
            assertEquals(1f, tile.bottom, 0f)
        }
    }

    @Test fun unknownRatiosFallbackAndSmallScreensStayBounded() {
        val fallback = steamChatImageLayout(listOf(4f / 3f), 324f)
        for (ratio in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertEquals(fallback, steamChatImageLayout(listOf(ratio), 324f))
        }
        assertTrue(steamChatImageLayout(List(10) { .6f }, 120f).width <= 120f)
    }
}
