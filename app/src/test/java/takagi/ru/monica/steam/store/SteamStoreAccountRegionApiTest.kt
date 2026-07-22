package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamStoreAccountRegionApiTest {
    @Test
    fun parsesCountryFromAccountLevelStoreResponse() {
        val response = SteamProtoWriter().apply {
            writeString(1, "CN")
        }.toByteArray()

        assertEquals("CN", parseSteamStoreAccountCountry(response))
    }

    @Test
    fun extractsAccessTokenFromStoredFieldOrSecureCookie() {
        assertEquals("field-token", effectiveSteamStoreAccessToken("field-token", "id||cookie-token"))
        assertEquals("cookie-token", effectiveSteamStoreAccessToken(null, "76561198000000000||cookie-token"))
        assertNull(effectiveSteamStoreAccessToken(null, "invalid-cookie"))
    }
}
