package takagi.ru.monica.steam.scanner

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.importer.SteamMaFilePayload
import takagi.ru.monica.utils.SettingsManager

/** Run only on the disposable Debug pilot; retain its data when replacing it with Release. */
class SteamScannerReleaseSeedTest {
    @Test fun seedEncryptedSyntheticAccountForOfflineReleaseScanner() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "takagi.ru.monica.steamapp.toolchainpilot")
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val security = SecurityManager(context)
        val dao = SteamDatabase.getDatabase(context).steamAccountDao()
        check(dao.count() == 0) { "Seed requires an empty disposable pilot" }
        check(!security.isMasterPasswordSet()) { "Seed must never replace an existing master password" }
        SettingsManager(context).prepareSteamMasterPasswordSetup()
        security.setMasterPassword("73194628")
        assertTrue(security.isMasterPasswordSet())
        assertTrue(security.verifyMasterPassword("73194628"))
        val repository = SteamAccountRepository(dao, security)
        val id = repository.upsertFromMaFile(SteamMaFilePayload(
            steamId = "76561198000999109",
            accountName = "scanner-pilot-synthetic",
            displayName = "Scanner pilot synthetic",
            deviceId = "android:scanner-pilot-synthetic",
            sharedSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
            identitySecret = null,
            revocationCode = null,
            tokenGid = null,
            accessToken = "not-a-real-steam-token",
            refreshToken = null,
            steamLoginSecure = null,
            rawJson = "{}"
        ))
        val account = requireNotNull(repository.getAccount(id))
        assertTrue(account.canApproveLogins)
        assertTrue(account.selected)
        assertEquals(1, dao.count())
        assertNotEquals(account.sharedSecret, requireNotNull(dao.getById(id)).sharedSecret)
        assertNotEquals(account.accessToken, requireNotNull(dao.getById(id)).accessToken)
        println("SCANNER_RELEASE_SEED encrypted=true accounts=1 synthetic=true")
        // No Activity/SteamViewModel is mounted and no network service is invoked.
        // Do not delete this fixture: Release is installed with -r over this package next.
    }
}
