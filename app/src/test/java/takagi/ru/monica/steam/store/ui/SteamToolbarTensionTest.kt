package takagi.ru.monica.steam.store.ui

import org.junit.Assert.*
import org.junit.Test

class SteamToolbarTensionTest {
    @Test fun neckRemainsUntilBreakAndRetractsFasterThanRoots() {
        assertEquals(SteamToolbarTension(1f, 1f), steamToolbarTension(0f))
        var previous = steamToolbarTension(0f)
        for (frame in 1..99) {
            val next = steamToolbarTension(frame / 100f)
            assertTrue(next.rootFraction > 0f && next.neckFraction > 0f)
            assertTrue(next.rootFraction < previous.rootFraction)
            assertTrue(next.neckFraction < previous.neckFraction)
            assertTrue(next.neckFraction < next.rootFraction)
            previous = next
        }
        assertEquals(SteamToolbarTension(0f, 0f), steamToolbarTension(1f))
    }

    @Test fun endpointsAreClampedAndDoNotPopAtBreak() {
        assertEquals(steamToolbarTension(0f), steamToolbarTension(-1f))
        assertEquals(steamToolbarTension(1f), steamToolbarTension(2f))
        val justBefore = steamToolbarTension(.999f)
        assertTrue(justBefore.rootFraction < .00001f)
        assertTrue(justBefore.neckFraction < .00000001f)
    }
}
