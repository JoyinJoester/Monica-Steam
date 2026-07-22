package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamStoreRegionParserTest {
    @Test
    fun readsWalletCountryFromOfficialAccountMarkup() {
        assertEquals("TW", SteamStoreRegionParser.parseCountryCode("g_rgWalletInfo = {\"wallet_country\":\"TW\"};"))
        assertEquals("CN", SteamStoreRegionParser.parseCountryCode("wallet_country = 'CN'"))
        assertNull(SteamStoreRegionParser.parseCountryCode("g_rgWalletInfo = {};"))
    }
}
