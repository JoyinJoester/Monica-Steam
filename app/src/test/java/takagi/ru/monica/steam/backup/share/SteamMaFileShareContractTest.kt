package takagi.ru.monica.steam.backup.share

import org.junit.Assert.*
import org.junit.Test

class SteamMaFileShareContractTest {
    private val nonce = "a0192068-6fef-428e-942b-eef72639718a"

    @Test fun acceptsOnlyVersionedCanonicalRequests() {
        assertTrue(SteamMaFileShareContract.validRequest(SteamMaFileShareContract.ACTION, 1, nonce))
        assertFalse(SteamMaFileShareContract.validRequest(null, 1, nonce))
        assertFalse(SteamMaFileShareContract.validRequest("android.intent.action.VIEW", 1, nonce))
        assertFalse(SteamMaFileShareContract.validRequest(SteamMaFileShareContract.ACTION, 2, nonce))
        for (invalid in listOf(null, "", "1-1-1-1-1", nonce.uppercase(), "$nonce ", "invalid")) {
            assertFalse(SteamMaFileShareContract.validRequest(SteamMaFileShareContract.ACTION, 1, invalid))
        }
    }
}
