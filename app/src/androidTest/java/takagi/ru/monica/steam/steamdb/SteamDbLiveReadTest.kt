package takagi.ru.monica.steam.steamdb

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import takagi.ru.monica.steam.steamdb.data.SteamDbApi
import takagi.ru.monica.steam.steamdb.domain.SteamDbResult

/** Opt-in smoke check of public endpoints; never logs in or reads account credentials. */
class SteamDbLiveReadTest {
    @Test fun publicSourcesReturnUsableDataOnAndroid() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("steamdbLive") == "1")
        val api = SteamDbApi()
        val info = api.info(570)
        val players = api.players(570)
        assertTrue("SteamDB app data: $info; Steam official players: $players", info is SteamDbResult.Ready)
        assertTrue("Steam official player count: $players", players is SteamDbResult.Ready)
        val price = api.price(620, "CNY")
        assertTrue("SteamDB CNY price: $price", price is SteamDbResult.Ready)
        val southAsia = api.price(620, "USD-SASIA")
        assertTrue("SteamDB South Asia price: $southAsia", southAsia is SteamDbResult.Ready)
        assertNotEquals((price as SteamDbResult.Ready).value.price, (southAsia as SteamDbResult.Ready).value.price)
        val rating = api.rating(620)
        assertTrue("Steam review counts", rating is SteamDbResult.Ready)
        assertTrue((rating as SteamDbResult.Ready).value.total > 0)
    }
}
