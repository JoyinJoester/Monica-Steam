package takagi.ru.monica.steam.friends.groupchat.avatar.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGroupMemberAvatarGridTest {
    @Test
    fun capsTheCompositeAvatarAtFourTiles() {
        assertEquals(0, steamGroupAvatarTileCount(0))
        assertEquals(1, steamGroupAvatarTileCount(1))
        assertEquals(2, steamGroupAvatarTileCount(2))
        assertEquals(3, steamGroupAvatarTileCount(3))
        assertEquals(4, steamGroupAvatarTileCount(4))
        assertEquals(4, steamGroupAvatarTileCount(7))
        assertEquals(4, steamGroupAvatarTileCount(14))
    }

    @Test
    fun usesOneSectorPerVisibleMember() {
        val circle = Rect(0f, 0f, 96f, 96f)
        assertTrue(steamGroupAvatarSlots(circle, 0).isEmpty())
        assertEquals(1, steamGroupAvatarSlots(circle, 1).size)
        assertEquals(2, steamGroupAvatarSlots(circle, 2).size)
        assertEquals(3, steamGroupAvatarSlots(circle, 3).size)
        assertEquals(4, steamGroupAvatarSlots(circle, 4).size)
        assertEquals(4, steamGroupAvatarSlots(circle, 9).size)
    }

    @Test
    fun quadrantsTileTheDiscWithoutOverflow() {
        val circle = Rect(0f, 0f, 96f, 96f)
        val quadrants = steamGroupAvatarSlots(circle, 4)
        val area = quadrants.sumOf { (it.bounds.width * it.bounds.height).toDouble() }
        assertEquals(96.0 * 96.0, area, 0.001)
        assertEquals(Rect(0f, 0f, 48f, 48f), quadrants[0].bounds)
        assertEquals(Rect(48f, 48f, 96f, 96f), quadrants[3].bounds)
        quadrants.forEach { slot ->
            assertTrue(slot.bounds.left >= circle.left && slot.bounds.right <= circle.right)
            assertTrue(slot.bounds.top >= circle.top && slot.bounds.bottom <= circle.bottom)
        }
    }

    @Test
    fun twoMembersShareTheDiscAsHalves() {
        val circle = Rect(0f, 0f, 96f, 96f)
        val halves = steamGroupAvatarSlots(circle, 2)
        assertEquals(Rect(0f, 0f, 96f, 48f), halves[0].bounds)
        assertEquals(Rect(0f, 48f, 96f, 96f), halves[1].bounds)
        // Halves sweep opposite 180° arcs so together they cover the whole disc.
        assertEquals(180f, halves[0].startAngle, 0.001f)
        assertEquals(180f, halves[0].sweepAngle, 0.001f)
        assertEquals(0f, halves[1].startAngle, 0.001f)
        assertEquals(180f, halves[1].sweepAngle, 0.001f)
    }

    @Test
    fun aLoneMemberOwnsTheWholeDisc() {
        val slots = steamGroupAvatarSlots(Rect(0f, 0f, 96f, 96f), 1)
        assertEquals(1, slots.size)
        assertTrue(slots.single().isWholeDisc)
        assertEquals(360f, slots.single().sweepAngle, 0.001f)
    }

    @Test
    fun nonSquareBoundsAreTrimmedToACircle() {
        // Wide slots (hero frames, list cells) must not stretch the disc into an ellipse.
        assertEquals(Rect(12f, 0f, 108f, 96f), Rect(0f, 0f, 120f, 96f).fitSquare())
        assertEquals(Rect(0f, 12f, 96f, 108f), Rect(0f, 0f, 96f, 120f).fitSquare())
    }
}
