package takagi.ru.monica.steam.network.optimization.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamBuiltInHostsPresetTest {
    @Test
    fun builtInPresetContainsAllMaintainerSelectedHostsAndParsesCleanly() {
        val parsed = SteamBuiltInHostsPreset.parsed

        assertTrue(parsed.errors.isEmpty())
        assertEquals(18, parsed.hostCount)
        assertEquals(
            "184.84.58.165",
            parsed.addresses.getValue("store.steampowered.com").single().hostAddress
        )
        assertEquals(
            "184.87.199.210",
            parsed.addresses.getValue("api.steampowered.com").single().hostAddress
        )
        assertEquals(
            "199.232.215.52",
            parsed.addresses.getValue("avatars.fastly.steamstatic.com").single().hostAddress
        )
        assertEquals(
            "172.234.232.226",
            parsed.addresses.getValue("support.steampowered.com").single().hostAddress
        )
    }
}
